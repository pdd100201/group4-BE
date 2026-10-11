package com.onlinelearning.service;

import com.onlinelearning.dto.course.ManagerCourseResponse;
import com.onlinelearning.dto.course.ManagerCourseResponse.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ManagerCourseService {
    private final CourseRepository courses;
    private final EntityManager em;

    public Page<ManagerCourseResponse> list(String search, String category, CourseStatus status, int page, int size) {
        Specification<Course> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        if (category != null && !category.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        if (status != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        return courses.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(c -> response(c, List.of(), List.of()));
    }

    public ManagerCourseResponse get(Long id) {
        Course c = courses.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
        List<LessonPreview> lessons = em.createQuery("select l from Lesson l where l.course.id = :id order by l.position, l.id", Lesson.class)
            .setParameter("id", id).getResultList().stream()
            .map(l -> new LessonPreview(l.getId(), l.getTitle(), l.getContent(), l.getPosition(), l.isActive(), l.getVideo() == null ? null : l.getVideo().getId())).toList();
        List<QuizPreview> quizzes = em.createQuery("select q from Quiz q where q.course.id = :id order by q.id", Quiz.class)
            .setParameter("id", id).getResultList().stream().map(q -> new QuizPreview(q.getId(), q.getTitle(), q.getDurationMinutes(),
                q.getPassRate(), q.getMaxAttempts(), q.isActive(), q.getQuizQuestions().stream()
                .sorted(Comparator.comparing(QuizQuestion::getPosition, Comparator.nullsLast(Integer::compareTo)).thenComparing(QuizQuestion::getId))
                .map(link -> {
                    Question question = link.getQuestion();
                    return new QuestionPreview(question.getId(), question.getContent(), question.getType(), question.getExplanation(), link.getPoints(),
                        question.getOptions().stream().sorted(Comparator.comparing(QuestionOption::getId))
                            .map(o -> new OptionPreview(o.getId(), o.getContent(), o.isCorrect())).toList());
                }).toList())).toList();
        return response(c, lessons, quizzes);
    }
    private ManagerCourseResponse response(Course c, List<LessonPreview> lessons, List<QuizPreview> quizzes) {
        return new ManagerCourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getCategory(), c.getPrice(), c.getStatus(),
            c.getExpert().getFullName(), c.getRejectionReason(), c.getReviewedBy() == null ? null : c.getReviewedBy().getFullName(), lessons, quizzes, c.getThumbnail() == null ? null : c.getThumbnail().getSecureUrl());
    }
}
