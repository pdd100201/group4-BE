package com.onlinelearning.service;

import com.onlinelearning.dto.quiz.StudentQuizDtos.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class StudentQuizService {
    private final UserRepository users;
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final QuizQuestionRepository links;
    private final CourseRegistrationRepository registrations;
    private final EntityManager em;

    public Info info(Long id,String email) {
        User student=student(email);Quiz quiz=available(id,student);var history=attempts.findByStudentIdAndQuizIdOrderByStartedAtDescIdDesc(student.getId(),id);
        history.forEach(this::expire);
        return new Info(id,quiz.getCourse().getId(),quiz.getLesson()==null?null:quiz.getLesson().getId(),quiz.getCourse().getTitle(),quiz.getTitle(),quiz.getDurationMinutes(),quiz.getPassRate(),quiz.getMaxAttempts(),history.size(),
            history.stream().map(a->new History(a.getId(),a.getStartedAt(),a.getSubmittedAt(),a.getScore(),a.isPassed())).toList());
    }
    public Attempt start(Long id,String email) {
        User student=student(email);Quiz quiz=available(id,student);
        em.lock(quiz.getCourse(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var history=attempts.findByStudentIdAndQuizIdOrderByStartedAtDescIdDesc(student.getId(),id);
        for(var a:history){expire(a);if(a.getSubmittedAt()==null)return state(a);}
        if(history.size()>=quiz.getMaxAttempts())throw conflict("Bạn đã hết số lượt làm bài kiểm tra.");
        var attempt=new QuizAttempt();attempt.setStudent(student);attempt.setQuiz(quiz);attempt.setDurationSnapshot(quiz.getDurationMinutes());attempt.setPassRateSnapshot(quiz.getPassRate());attempts.saveAndFlush(attempt);
        return state(attempt);
    }
    public Attempt read(Long id,String email) {
        var attempt=owned(id,student(email));available(attempt.getQuiz().getId(),attempt.getStudent());expire(attempt);return state(attempt);
    }
    public Attempt save(Long id,String email,Answer answer) {
        var attempt=owned(id,student(email));available(attempt.getQuiz().getId(),attempt.getStudent());
        if(attempt.getSubmittedAt()!=null)throw conflict("Bài kiểm tra đã nộp, không thể thay đổi đáp án.");
        if(expire(attempt))return state(attempt);
        setAnswer(attempt,answer);em.flush();return state(attempt);
    }
    public Result submit(Long id,String email,Submission submission) {
        var attempt=owned(id,student(email));
        if(attempt.getSubmittedAt()!=null)return result(attempt);
        available(attempt.getQuiz().getId(),attempt.getStudent());
        if(!expire(attempt)) {
            Set<Long> seen=new HashSet<>();
            for(var answer:submission.answers()){if(!seen.add(answer.questionId()))throw bad("Câu hỏi bị lặp trong bài nộp.");setAnswer(attempt,answer);}
            grade(attempt,Instant.now());
        }
        return result(attempt);
    }
    public Result review(Long id,String email) {
        var attempt=owned(id,student(email));expire(attempt);
        if(attempt.getSubmittedAt()==null)throw conflict("Bài kiểm tra chưa được nộp.");
        return result(attempt);
    }
    private Quiz available(Long id,User student) {
        Quiz quiz=quizzes.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy bài kiểm tra."));
        if(!quiz.isActive() || quiz.getCourse().getStatus()!=CourseStatus.PUBLISHED || quiz.getLesson()!=null && !quiz.getLesson().isActive()) throw conflict("Bài kiểm tra hiện không khả dụng.");
        if(registrations.countCurrentAccess(student.getId(),quiz.getCourse().getId(),Instant.now())==0)throw new ApiException(HttpStatus.FORBIDDEN,"Bạn không có quyền học hoặc đăng ký đã hết hạn.");
        var items=items(quiz);
        if(quiz.getDurationMinutes()==null||quiz.getDurationMinutes()<1||quiz.getPassRate()==null||quiz.getMaxAttempts()==null||quiz.getMaxAttempts()<1||items.isEmpty())throw conflict("Bài kiểm tra chưa được cấu hình đầy đủ.");
        for(var item:items) {
            var q=item.getQuestion();long correct=q.getOptions().stream().filter(QuestionOption::isCorrect).count();
            if(!q.isActive()||q.getType()==null||!Set.of("SINGLE_CHOICE","MULTIPLE_CHOICE").contains(q.getType())||q.getOptions().size()<2||correct==0||"SINGLE_CHOICE".equals(q.getType())&&correct!=1||item.getPoints()==null||item.getPoints()<1||item.getPosition()==null)throw conflict("Câu hỏi trong bài kiểm tra chưa hợp lệ.");
        }
        return quiz;
    }
    private User student(String email) {
        User user=users.findByEmailIgnoreCase(email).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Không tìm thấy tài khoản."));
        em.lock(user,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!user.isEnabled()||user.isBlocked()||user.getRoles().stream().noneMatch(r->"STUDENT".equals(r.getCode())))throw new ApiException(HttpStatus.FORBIDDEN,"Chỉ Student đang hoạt động được làm bài.");
        return user;
    }
    private QuizAttempt owned(Long id,User student) {
        var a=attempts.findByIdAndStudentId(id,student.getId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy lượt làm bài của bạn."));
        em.lock(a,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return a;
    }
    private List<QuizQuestion> items(Quiz quiz){return links.findByQuizIdOrderByPositionAscIdAsc(quiz.getId());}
    private List<QuestionOption> options(com.onlinelearning.entity.Question q){return q.getOptions().stream().sorted(Comparator.comparing(QuestionOption::getId)).toList();}
    private QuizAnswer answer(QuizAttempt a,com.onlinelearning.entity.Question q) {
        return a.getAnswers().stream().filter(v->v.getQuestion().getId().equals(q.getId())).findFirst().orElseGet(()->{var v=new QuizAnswer();v.setAttempt(a);v.setQuestion(q);a.getAnswers().add(v);return v;});
    }
    private List<Long> selected(QuizAttempt a,Long id){return a.getAnswers().stream().filter(v->v.getQuestion().getId().equals(id)).findFirst().map(v->v.getSelectedOptions().stream().map(o->o.getQuestionOption().getId()).toList()).orElse(List.of());}
    private void setAnswer(QuizAttempt a,Answer input) {
        var item=items(a.getQuiz()).stream().filter(i->i.getQuestion().getId().equals(input.questionId())).findFirst().orElseThrow(()->bad("Câu hỏi không thuộc bài kiểm tra."));
        var question=item.getQuestion();Set<Long> ids=new HashSet<>(input.optionIds());
        if(ids.size()!=input.optionIds().size()||"SINGLE_CHOICE".equals(question.getType())&&ids.size()>1)throw bad("Danh sách đáp án không hợp lệ.");
        if(!question.getOptions().stream().map(QuestionOption::getId).collect(java.util.stream.Collectors.toSet()).containsAll(ids))throw bad("Đáp án không thuộc câu hỏi.");
        var value=answer(a,question);value.getSelectedOptions().clear();em.flush();
        for(var option:options(question))if(ids.contains(option.getId())){var link=new QuizAnswerOption();link.setQuizAnswer(value);link.setQuestionOption(option);value.getSelectedOptions().add(link);}
        attempts.saveAndFlush(a);
    }
    private Instant deadline(QuizAttempt a){return a.getStartedAt().plusSeconds((long)(a.getDurationSnapshot()==null?a.getQuiz().getDurationMinutes():a.getDurationSnapshot())*60);}
    private boolean expire(QuizAttempt a){if(a.getSubmittedAt()==null && !Instant.now().isBefore(deadline(a))){grade(a,deadline(a));return true;}return false;}
    private void grade(QuizAttempt a,Instant submitted) {
        long total=0;double earned=0;
        for(var item:items(a.getQuiz())){
            var q=item.getQuestion();var value=answer(a,q);Set<Long> correct=q.getOptions().stream().filter(QuestionOption::isCorrect).map(QuestionOption::getId).collect(java.util.stream.Collectors.toSet());
            boolean match=!correct.isEmpty()&&correct.equals(new HashSet<>(selected(a,q.getId())));value.setCorrect(match);value.setAwardedPoints(match?(double)item.getPoints():0d);total+=item.getPoints();earned+=value.getAwardedPoints();
        }
        a.setScore(total==0?0d:Math.round(earned*10000/total)/100d);a.setPassed(a.getScore()>=(a.getPassRateSnapshot()==null?a.getQuiz().getPassRate():a.getPassRateSnapshot()));a.setSubmittedAt(submitted);attempts.saveAndFlush(a);
    }
    private Attempt state(QuizAttempt a) {
        return new Attempt(a.getId(),a.getQuiz().getId(),a.getQuiz().getCourse().getId(),a.getQuiz().getLesson()==null?null:a.getQuiz().getLesson().getId(),a.getQuiz().getTitle(),Instant.now(),deadline(a),a.getSubmittedAt(),
            items(a.getQuiz()).stream().map(i->{var q=i.getQuestion();return new com.onlinelearning.dto.quiz.StudentQuizDtos.Question(q.getId(),i.getPosition(),q.getContent(),q.getType(),q.getImage()==null?null:q.getImage().getSecureUrl(),i.getPoints(),options(q).stream().map(o->new Option(o.getId(),o.getContent())).toList(),selected(a,q.getId()));}).toList());
    }
    private Result result(QuizAttempt a) {
        var questions=items(a.getQuiz()).stream().map(i->{var q=i.getQuestion();var v=answer(a,q);return new Review(q.getId(),i.getPosition(),q.getContent(),q.getType(),q.getImage()==null?null:q.getImage().getSecureUrl(),i.getPoints(),v.getAwardedPoints()==null?0:v.getAwardedPoints(),v.isCorrect(),q.getExplanation(),options(q).stream().map(o->new Option(o.getId(),o.getContent())).toList(),selected(a,q.getId()),q.getOptions().stream().filter(QuestionOption::isCorrect).map(QuestionOption::getId).toList());}).toList();
        return new Result(a.getId(),a.getQuiz().getId(),a.getQuiz().getCourse().getId(),a.getQuiz().getLesson()==null?null:a.getQuiz().getLesson().getId(),a.getQuiz().getTitle(),a.getScore(),a.isPassed(),a.getPassRateSnapshot()==null?a.getQuiz().getPassRate():a.getPassRateSnapshot(),(int)questions.stream().filter(Review::correct).count(),questions.size(),Math.max(0,Duration.between(a.getStartedAt(),a.getSubmittedAt()).toSeconds()),questions);
    }
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
    private ApiException conflict(String message){return new ApiException(HttpStatus.CONFLICT,message);}
}
