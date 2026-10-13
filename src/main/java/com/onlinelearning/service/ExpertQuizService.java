package com.onlinelearning.service;
import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class ExpertQuizService {
    private final CourseRepository courses;
    private final QuizRepository quizzes;
    private final QuizQuestionRepository quizQuestions;
    private final QuizAttemptRepository attempts;
    private final QuestionRepository questions;
    private final LessonRepository lessons;
    private final AuditLogRepository audit;

    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<QuizListItem> search(String email, Long courseId, String search,
            Boolean active, int page, int size) {
        return search(email, courseId, search, "", active, page, size);
    }

    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<QuizListItem> search(String email, Long courseId, String search, String category,
            Boolean active, int page, int size) {
        validatePage(page, size);
        if (search.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "Tên quiz tối đa 200 ký tự.");
        if (category.length() > 255) throw new ApiException(HttpStatus.BAD_REQUEST, "Danh mục tối đa 255 ký tự.");
        if (courseId != null) owned(courseId, email, false);
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        org.springframework.data.jpa.domain.Specification<Quiz> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(cb.lower(root.get("course").get("expert").get("email")), email.toLowerCase(Locale.ROOT)));
            predicates.add(cb.like(cb.lower(root.get("title")), pattern, '\\'));
            if (courseId != null) predicates.add(cb.equal(root.get("course").get("id"), courseId));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (!category.isBlank()) predicates.add(cb.equal(root.get("course").get("category"), category.trim()));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return quizzes.findAll(spec, org.springframework.data.domain.PageRequest.of(page, size,
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id")))
            .map(q -> new QuizListItem(q.getId(), q.getCourse().getId(), q.getCourse().getTitle(), q.getCourse().getCategory(), q.getCourse().getStatus(),
                q.getTitle(), quizQuestions.countByQuizId(q.getId()), q.getDurationMinutes(), q.getPassRate(), q.getMaxAttempts(),
                q.isActive(), attempts.existsByQuizId(q.getId())));
    }

    @Transactional(readOnly=true)
    public org.springframework.data.domain.Page<QuestionChoice> questionBank(Long courseId, String email, String search, int page, int size) {
        owned(courseId, email, false);
        validatePage(page, size);
        if (search.length() > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "Từ khóa tối đa 200 ký tự.");
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        org.springframework.data.jpa.domain.Specification<Question> spec = (root, query, cb) -> cb.and(
            cb.equal(root.get("course").get("id"), courseId), cb.isTrue(root.get("active")), cb.like(cb.lower(root.get("content")), pattern, '\\'));
        return questions.findAll(spec, org.springframework.data.domain.PageRequest.of(page, size,
            org.springframework.data.domain.Sort.by("id"))).map(q -> new QuestionChoice(q.getId(), q.getContent(), q.getType(), q.isActive()));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ.");
    }

    @Transactional(readOnly=true)
    public List<QuizResponse> list(Long courseId, String email) {
        owned(courseId, email, false);
        return quizzes.findByCourseIdOrderByIdAsc(courseId).stream()
            .map(q -> QuizResponse.from(q, quizQuestions.findByQuizIdOrderByPositionAscIdAsc(q.getId())))
            .toList();
    }

    @Transactional(readOnly=true)
    public QuizResponse get(Long courseId, Long id, String email) {
        owned(courseId, email, false);
        Quiz q = quiz(courseId, id);
        return QuizResponse.from(q, quizQuestions.findByQuizIdOrderByPositionAscIdAsc(id));
    }

    public QuizResponse create(Long courseId, String email, QuizRequest request) {
        Course c = owned(courseId, email, true);
        editable(c);
        Quiz q = new Quiz();
        q.setCourse(c);
        apply(q, request);
        q.setLesson(lesson(courseId, request.lessonId()));
        quizzes.save(q);
        List<QuizQuestion> items = link(q, courseId, request.questions());
        record(q, "QUIZ_CREATED");
        return QuizResponse.from(q, items);
    }

    public QuizResponse update(Long courseId, Long id, String email, QuizRequest request) {
        Course c = owned(courseId, email, true);
        editable(c);
        Quiz q = quiz(courseId, id);
        retainable(q);
        apply(q, request);
        q.setLesson(lesson(courseId, request.lessonId()));
        quizQuestions.deleteByQuizId(id);
        List<QuizQuestion> items = link(q, courseId, request.questions());
        record(q, "QUIZ_UPDATED");
        return QuizResponse.from(q, items);
    }

    public void delete(Long courseId, Long id, String email) {
        Course c = owned(courseId, email, true);
        editable(c);
        Quiz q = quiz(courseId, id);
        if (attempts.existsByQuizId(id))
            throw new ApiException(HttpStatus.CONFLICT, "Không thể xóa quiz đã có lượt làm bài của học viên.");
        record(q, "QUIZ_DELETED");
        quizQuestions.deleteByQuizId(id);
        quizzes.delete(q);
        quizzes.flush();
    }

    private List<QuizQuestion> link(Quiz q, Long courseId, List<QuizQuestionItem> items) {
        if (items == null || items.isEmpty()) return List.of();
        Set<Long> questionIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        List<QuizQuestion> toSave = new ArrayList<>();
        for (QuizQuestionItem it : items) {
            if (!questionIds.add(it.questionId()))
                throw new ApiException(HttpStatus.CONFLICT, "Câu hỏi bị lặp trong quiz.");
            if (!positions.add(it.position()))
                throw new ApiException(HttpStatus.CONFLICT, "Thứ tự câu hỏi bị trùng trong quiz.");
            Question question = questions.findByIdAndCourseId(it.questionId(), courseId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Câu hỏi không thuộc khóa học này."));
            if (q.isActive() && !question.isActive())
                throw new ApiException(HttpStatus.BAD_REQUEST, "Quiz hoạt động không được dùng câu hỏi không hoạt động.");
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(q); qq.setQuestion(question); qq.setPosition(it.position());
            qq.setPoints(it.points() == null ? 1 : it.points());
            toSave.add(qq);
        }
        return quizQuestions.saveAll(toSave);
    }

    private void retainable(Quiz q) {
        if (attempts.existsByQuizId(q.getId()))
            throw new ApiException(HttpStatus.CONFLICT, "Không thể sửa quiz đã có lượt làm bài của học viên.");
    }

    private Course owned(Long id, String email, boolean lock) {
        Course c = (lock ? courses.findForUpdate(id) : courses.findById(id))
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
        if (!c.getExpert().getEmail().equalsIgnoreCase(email))
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ được quản lý quiz trong khóa học của mình.");
        return c;
    }

    private void editable(Course c) {
        if (c.getStatus() != CourseStatus.DRAFT && c.getStatus() != CourseStatus.REJECTED)
            throw new ApiException(HttpStatus.CONFLICT, "Chỉ được thay đổi quiz khi khóa học là Nháp hoặc Bị từ chối.");
    }

    private Quiz quiz(Long courseId, Long id) {
        return quizzes.findByIdAndCourseId(id, courseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Quiz không thuộc khóa học này hoặc không tồn tại."));
    }

    private Lesson lesson(Long courseId, Long lessonId) {
        if (lessonId == null) return null;
        return lessons.findByIdAndCourseId(lessonId, courseId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Bài học không thuộc khóa học này."));
    }

    private void apply(Quiz q, QuizRequest r) {
        if (Boolean.TRUE.equals(r.active()) && (r.durationMinutes() == null || r.passRate() == null || r.maxAttempts() == null))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quiz hoạt động cần thời lượng, tỷ lệ đạt và số lượt làm tối đa.");
        q.setTitle(r.title().trim());
        q.setDurationMinutes(r.durationMinutes());
        q.setPassRate(r.passRate());
        q.setMaxAttempts(r.maxAttempts());
        q.setActive(r.active());
    }

    private void record(Quiz q, String action) {
        AuditLog log = new AuditLog();
        log.setActor(q.getCourse().getExpert());
        log.setAction(action);
        log.setTargetType("QUIZ");
        log.setTargetId(q.getId().toString());
        log.setMetadata("courseId=" + q.getCourse().getId());
        log.setOccurredAt(Instant.now());
        audit.save(log);
    }
}
