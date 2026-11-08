package com.onlinelearning.service;

import com.onlinelearning.dto.chat.ChatContactResponse;
import com.onlinelearning.dto.chat.ChatUserResponse;
import com.onlinelearning.entity.Course;
import com.onlinelearning.entity.CourseRegistration;
import com.onlinelearning.entity.User;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRegistrationRepository;
import com.onlinelearning.repository.CourseRepository;
import com.onlinelearning.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class ChatAccessService {
    private final CourseRegistrationRepository registrations;
    private final CourseRepository courses;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Page<ChatContactResponse> contacts(Long userId, String query, int page) {
        if (page < 0 || query == null || query.length() > 100)
            throw badRequest("Tìm kiếm không hợp lệ.");
        User me = activeUser(userId);
        Instant now = Instant.now();
        List<CourseRegistration> available = new ArrayList<>(registrations.findChatRegistrationsForStudent(userId, now));
        available.addAll(registrations.findChatRegistrationsForExpert(userId, now));
        var distinct = new LinkedHashMap<String, ChatContactResponse>();
        for (CourseRegistration registration : available) {
            Course course = registration.getCourse();
            User peer = course.getExpert().getId().equals(me.getId()) ? registration.getStudent() : course.getExpert();
            if (!peer.isEnabled() || peer.isBlocked() || peer.getId().equals(me.getId())) continue;
            distinct.putIfAbsent(course.getId() + ":" + peer.getId(),
                new ChatContactResponse(course.getId(), course.getTitle(), ChatUserResponse.from(peer)));
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<ChatContactResponse> matches = distinct.values().stream()
            .filter(c -> c.courseTitle().toLowerCase(Locale.ROOT).contains(needle)
                || c.peer().fullName().toLowerCase(Locale.ROOT).contains(needle))
            .sorted(Comparator.comparing(ChatContactResponse::courseTitle, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> c.peer().fullName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ChatContactResponse::courseId).thenComparing(c -> c.peer().id()))
            .toList();
        long start = (long) page * 30;
        int from = (int) Math.min(start, matches.size());
        int to = Math.min(from + 30, matches.size());
        return new PageImpl<>(matches.subList(from, to), PageRequest.of(page, 30), matches.size());
    }

    @Transactional(readOnly = true)
    public Course requireConversation(Long userId, Long peerId, Long courseId) {
        if (peerId == null || courseId == null || peerId <= 0 || courseId <= 0 || peerId.equals(userId))
            throw badRequest("Cuộc trò chuyện không hợp lệ.");
        User me = activeUser(userId);
        activeUser(peerId);
        Course course = courses.findById(courseId)
            .orElseThrow(() -> badRequest("Khóa học không tồn tại."));
        Long expertId = course.getExpert().getId();
        Long studentId;
        if (expertId.equals(userId)) studentId = peerId;
        else if (expertId.equals(peerId)) studentId = me.getId();
        else throw forbidden();
        if (registrations.countCurrentAccess(studentId, courseId, Instant.now()) == 0) throw forbidden();
        return course;
    }

    private User activeUser(Long id) {
        if (id == null || id <= 0) throw badRequest("Tài khoản không hợp lệ.");
        return users.findById(id).filter(u -> u.isEnabled() && !u.isBlocked())
            .orElseThrow(() -> badRequest("Tài khoản không khả dụng."));
    }

    private static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền trò chuyện trong khóa học này."); }
}
