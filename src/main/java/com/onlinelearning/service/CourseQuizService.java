package com.onlinelearning.service;

import com.onlinelearning.dto.question.QuestionRequest;
import com.onlinelearning.dto.question.QuestionResponse;
import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class CourseQuizService {
    private final ExpertQuestionService questionService;
    private final ExpertQuizService quizService;
    private final QuizRepository quizzes;
    private final QuizQuestionRepository links;
    private final QuizAttemptRepository attempts;
    private final Validator validator;

    public record Item(Long questionId, Integer position, Integer points, QuestionResponse question) {}
    public record Assessment(Long id, Long lessonId, String title, Integer durationMinutes, Integer passRate,
                             Integer maxAttempts, boolean active, boolean retained, List<Item> questions) {}

    @Transactional(readOnly=true)
    public List<Assessment> list(Long courseId, String email) {
        questionService.owned(courseId, email, false);
        return quizzes.findByCourseIdOrderByIdAsc(courseId).stream().map(this::response).toList();
    }

    public Assessment save(Long courseId, String email, CourseQuizRequest request) {
        questionService.owned(courseId, email, true); // Serializes all quiz writes on the course.
        if (!validator.validate(request).isEmpty()) throw bad("Kiểm tra cấu hình quiz và nội dung câu hỏi.");
        var existing = quizzes.findByCourseIdOrderByIdAsc(courseId);
        if (existing.size() > 1) throw conflict("Khóa học cũ có nhiều quiz. Giữ lại một quiz trước khi lưu.");
        Quiz current = request.id() == null ? null : existing.stream().filter(q -> q.getId().equals(request.id()))
            .findFirst().orElseThrow(() -> bad("Quiz không thuộc khóa học này."));
        if (current == null && !existing.isEmpty()) throw conflict("Mỗi khóa học chỉ có một quiz. Hãy chỉnh sửa quiz hiện có.");
        if (current != null && attempts.existsByQuizId(current.getId())) throw conflict("Không thể sửa quiz đã có lượt làm bài.");
        Set<Long> allowed = new HashSet<>();
        if (current != null) links.findByQuizIdOrderByPositionAscIdAsc(current.getId()).forEach(i -> allowed.add(i.getQuestion().getId()));
        Set<Long> used = new HashSet<>();
        // Validate all questions before changing any entity.
        for (var item : request.questions()) {
            if (item.questionId() != null && (!allowed.contains(item.questionId()) || !used.add(item.questionId())))
                throw bad("Câu hỏi bị trùng hoặc không thuộc quiz này.");
            if (item.question().lessonId() != null) throw bad("Câu hỏi thuộc quiz toàn khóa, không gắn bài học.");
            if (!Boolean.TRUE.equals(item.question().active())) throw bad("Câu hỏi trong quiz phải hoạt động.");
            questionService.validate(item.question());
        }
        List<QuizQuestionItem> items = new ArrayList<>();
        int position = 1;
        for (var item : request.questions()) {
            QuestionRequest input = item.question();
            var saved = item.questionId() == null ? questionService.create(courseId, email, input)
                : questionService.update(courseId, item.questionId(), email, input);
            items.add(new QuizQuestionItem(saved.id(), position++, item.points()));
        }
        var input = new QuizRequest(request.title(), request.durationMinutes(), request.passRate(), request.maxAttempts(), true, null, items);
        var saved = current == null ? quizService.create(courseId, email, input) : quizService.update(courseId, current.getId(), email, input);
        return response(quizzes.findById(saved.id()).orElseThrow());
    }

    private Assessment response(Quiz q) {
        return new Assessment(q.getId(), q.getLesson() == null ? null : q.getLesson().getId(), q.getTitle(), q.getDurationMinutes(), q.getPassRate(), q.getMaxAttempts(),
            q.isActive(), attempts.existsByQuizId(q.getId()), links.findByQuizIdOrderByPositionAscIdAsc(q.getId()).stream()
            .map(i -> new Item(i.getQuestion().getId(), i.getPosition(), i.getPoints(), QuestionResponse.from(i.getQuestion()))).toList());
    }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
}
