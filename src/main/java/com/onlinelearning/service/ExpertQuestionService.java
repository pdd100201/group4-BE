package com.onlinelearning.service;

import com.onlinelearning.dto.question.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class ExpertQuestionService {
    private final QuestionRepository questions;
    private final CourseRepository courses;
    private final AuditLogRepository audit;
    private final MediaService media;
    private final EntityManager em;
    private final Validator validator;

    @Transactional(readOnly=true)
    public Page<QuestionResponse> list(Long courseId, String email, String search, String type, Boolean active, int page, int size) {
        owned(courseId, email, false);
        if (page < 0 || size < 1 || size > 100 || search.length() > 200) throw bad("Bộ lọc hoặc phân trang không hợp lệ.");
        if (!type.isBlank() && !Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE").contains(type)) throw bad("Loại câu hỏi không hợp lệ.");
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<Question> spec = (root, query, cb) -> {
            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
            filters.add(cb.equal(root.get("course").get("id"), courseId));
            filters.add(cb.like(cb.lower(root.get("content")), pattern, '\\'));
            if (!type.isBlank()) filters.add(cb.equal(root.get("type"), type));
            if (active != null) filters.add(cb.equal(root.get("active"), active));
            return cb.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return questions.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(QuestionResponse::from);
    }

    public QuestionResponse create(Long courseId, String email, QuestionRequest request) {
        Course course = owned(courseId, email, true); validate(request);
        Question q = new Question(); q.setCourse(course); apply(q, request, email);
        questions.saveAndFlush(q); record(q, "QUESTION_CREATED"); return QuestionResponse.from(q);
    }
    public QuestionResponse update(Long courseId, Long id, String email, QuestionRequest request) {
        owned(courseId, email, true); Question q = question(courseId, id); retained(q); validate(request);
        if (!request.active()) activeQuiz(q);
        q.getOptions().clear(); em.flush(); apply(q, request, email);
        questions.saveAndFlush(q); record(q, "QUESTION_UPDATED"); return QuestionResponse.from(q);
    }
    public QuestionResponse status(Long courseId, Long id, String email, boolean active) {
        owned(courseId, email, true); Question q = question(courseId, id); retained(q);
        if (!active) activeQuiz(q);
        q.setActive(active); q.setStatus(active ? "ACTIVE" : "INACTIVE"); record(q, "QUESTION_STATUS_UPDATED");
        return QuestionResponse.from(q);
    }
    public void delete(Long courseId, Long id, String email) {
        owned(courseId, email, true); Question q = question(courseId, id); retained(q);
        if (em.createQuery("select count(i) from QuizQuestion i where i.question.id=:id", Long.class).setParameter("id", id).getSingleResult() > 0)
            throw conflict("Câu hỏi đang được sử dụng trong quiz, không thể xóa.");
        record(q, "QUESTION_DELETED"); questions.delete(q); questions.flush();
    }
    public void validate(QuestionRequest request) {
        if (!validator.validate(request).isEmpty()) throw bad("Kiểm tra nội dung, 4 đáp án và độ dài các trường.");
        if (!Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE").contains(request.type())) throw bad("Chỉ hỗ trợ một hoặc nhiều đáp án đúng.");
        long correct = request.options().stream().filter(QuestionRequest.Option::correct).count();
        if (correct == 0 || ("SINGLE_CHOICE".equals(request.type()) && correct != 1)) throw bad("Chọn đáp án đúng phù hợp loại câu hỏi.");
        if (request.options().stream().map(o -> o.content().trim().toLowerCase(Locale.ROOT)).distinct().count() != 4)
            throw bad("Các đáp án không được trùng nhau.");
    }
    private void apply(Question q, QuestionRequest r, String email) {
        q.setContent(r.content().trim()); q.setType(r.type()); q.setQuestionType(r.type());
        q.setExplanation(r.explanation()); q.setActive(r.active()); q.setStatus(r.active() ? "ACTIVE" : "INACTIVE");
        q.setImage(media.attachment(r.imageMediaId(), email, q.getCourse().getId(), "QUESTION_IMAGE"));
        for (var input : r.options()) { var o = new QuestionOption(); o.setQuestion(q); o.setContent(input.content().trim()); o.setCorrect(input.correct()); q.getOptions().add(o); }
    }
    public Course owned(Long courseId, String email, boolean write) {
        Course course = (write ? courses.findForUpdate(courseId) : courses.findById(courseId)).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy khóa học."));
        if (!course.getExpert().getEmail().equalsIgnoreCase(email)) throw new ApiException(HttpStatus.FORBIDDEN, "Khóa học không thuộc bạn.");
        if (write && course.getStatus() != CourseStatus.DRAFT && course.getStatus() != CourseStatus.REJECTED)
            throw conflict("Chỉ được thay đổi câu hỏi khi khóa học là Nháp hoặc Bị từ chối.");
        return course;
    }
    private Question question(Long courseId, Long id) {
        return questions.findByIdAndCourseId(id, courseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi trong khóa học."));
    }
    private void retained(Question q) {
        long answers = em.createQuery("select count(a) from QuizAnswer a where a.question.id=:id", Long.class).setParameter("id", q.getId()).getSingleResult();
        long attempts = em.createQuery("select count(i) from QuizQuestion i where i.question.id=:id and exists (select a.id from QuizAttempt a where a.quiz=i.quiz)", Long.class).setParameter("id", q.getId()).getSingleResult();
        if (answers > 0 || attempts > 0) throw conflict("Câu hỏi có dữ liệu làm bài cần lưu giữ, không được thay đổi hoặc xóa.");
    }
    private void activeQuiz(Question q) {
        if (em.createQuery("select count(i) from QuizQuestion i where i.question.id=:id and i.quiz.active=true", Long.class).setParameter("id", q.getId()).getSingleResult() > 0)
            throw conflict("Câu hỏi thuộc quiz hoạt động, hãy ngừng hoạt động quiz trước.");
    }
    private void record(Question q, String action) {
        var log = new AuditLog(); log.setActor(q.getCourse().getExpert()); log.setAction(action); log.setTargetType("QUESTION"); log.setTargetId(q.getId().toString()); log.setOccurredAt(Instant.now()); audit.save(log);
    }
    private ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
}
