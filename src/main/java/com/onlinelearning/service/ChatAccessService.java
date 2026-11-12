package com.onlinelearning.service;

import com.onlinelearning.dto.chat.ChatContactResponse;
import com.onlinelearning.dto.chat.ChatUserResponse;
import com.onlinelearning.entity.Course;
import com.onlinelearning.entity.CourseRegistration;
import com.onlinelearning.entity.CourseStatus;
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
        var distinct = new LinkedHashMap<Long, ChatContactResponse>();
        var matchingPeers = new java.util.HashSet<Long>();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (CourseRegistration registration : availableRegistrations(userId, now)) {
            Course course = registration.getCourse();
            User peer = course.getExpert().getId().equals(me.getId()) ? registration.getStudent() : course.getExpert();
            if (!peer.isEnabled() || peer.isBlocked() || peer.getId().equals(me.getId())) continue;
            distinct.merge(peer.getId(), new ChatContactResponse(course.getId(), course.getTitle(), ChatUserResponse.from(peer)),
                (current, candidate) -> current.courseId() < candidate.courseId() ? current : candidate);
            if (course.getTitle().toLowerCase(Locale.ROOT).contains(needle)
                || peer.getFullName().toLowerCase(Locale.ROOT).contains(needle)) matchingPeers.add(peer.getId());
        }
        List<ChatContactResponse> matches = distinct.values().stream()
            .filter(c -> matchingPeers.contains(c.peer().id()))
            .sorted(Comparator.comparing((ChatContactResponse c) -> c.peer().fullName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(c -> c.peer().id()))
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
        // Archived courses retain registration history for refund discussions.
        boolean canChat = course.getStatus() == CourseStatus.ARCHIVED
            ? registrations.existsByStudentIdAndCourseId(studentId, courseId)
            : registrations.countCurrentAccess(studentId, courseId, Instant.now()) > 0;
        if (!canChat) throw forbidden();
        return course;
    }

    @Transactional(readOnly = true)
    public Course requireSharedCourse(Long userId, Long peerId) {
        if (peerId == null || peerId.equals(userId)) throw badRequest("Cuộc trò chuyện không hợp lệ.");
        activeUser(userId);
        activeUser(peerId);
        return availableRegistrations(userId, Instant.now()).stream()
            .filter(r -> r.getStudent().getId().equals(userId)
                ? r.getCourse().getExpert().getId().equals(peerId)
                : r.getStudent().getId().equals(peerId))
            .map(CourseRegistration::getCourse)
            .min(Comparator.comparing(Course::getId))
            .orElseThrow(ChatAccessService::forbidden);
    }

    private List<CourseRegistration> availableRegistrations(Long userId, Instant now) {
        var available = new ArrayList<>(registrations.findChatRegistrationsForStudent(userId, now));
        available.addAll(registrations.findChatRegistrationsForExpert(userId, now));
        return available;
    }

    private User activeUser(Long id) {
        if (id == null || id <= 0) throw badRequest("Tài khoản không hợp lệ.");
        return users.findById(id).filter(u -> u.isEnabled() && !u.isBlocked())
            .orElseThrow(() -> badRequest("Tài khoản không khả dụng."));
    }

    private static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền trò chuyện trong khóa học này."); }
}
