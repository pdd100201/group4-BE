package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.*;

/** Per-lesson assessments reuse the existing quiz/attempt storage, preserving old results. */
@Service @RequiredArgsConstructor
public class LessonAssessmentService {
    private final EntityManager em;
    public record Description(Long id,Long lessonId,String title,int durationMinutes,int passRate,int questionCount) {}
    private record Entry(Question question,int points) {}
    private record Spec(Lesson lesson,String title,int duration,int passRate,int maxAttempts,List<Entry> entries) {}

    public List<Description> describe(Long courseId) {
        var active=activeAssessments(courseId);
        return specs(courseId).stream().map(spec->{
            Quiz match=active.stream().filter(quiz->matches(quiz,spec)).findFirst().orElse(null);
            return new Description(match==null?null:match.getId(),spec.lesson().getId(),spec.title(),spec.duration(),spec.passRate(),spec.entries().size());
        }).toList();
    }

    // Caller validates student access and holds the course write lock.
    public Long prepare(Course course,Long lessonId) {
        Spec spec=specs(course.getId()).stream().filter(value->value.lesson().getId().equals(lessonId)).findFirst()
            .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Bài học chưa có câu hỏi đang hoạt động."));
        var active=activeAssessments(course.getId()).stream().filter(quiz->quiz.getLesson().getId().equals(lessonId)).toList();
        Quiz current=null;
        for(Quiz quiz:active) {
            if(current==null && matches(quiz,spec)) current=quiz;
            else quiz.setActive(false);
        }
        if(current!=null) return current.getId();
        Quiz quiz=new Quiz();quiz.setCourse(course);quiz.setLesson(spec.lesson());quiz.setTitle(spec.title());
        quiz.setDurationMinutes(spec.duration());quiz.setPassRate(spec.passRate());quiz.setMaxAttempts(spec.maxAttempts());quiz.setActive(true);
        em.persist(quiz);
        int position=1;
        for(Entry entry:spec.entries()) {
            var link=new QuizQuestion();link.setQuiz(quiz);link.setQuestion(entry.question());link.setPoints(entry.points());link.setPosition(position++);em.persist(link);
        }
        em.flush();
        return quiz.getId();
    }

    // Retire definitions when their source questions/configuration/assignments change.
    // Attempts and answers remain attached to the original definitions.
    public void invalidate(Long courseId,Long lessonId) {
        for(Quiz quiz:activeAssessments(courseId)) if(lessonId==null || quiz.getLesson().getId().equals(lessonId)) quiz.setActive(false);
    }

    private List<Quiz> activeAssessments(Long courseId) {
        return em.createQuery("select q from Quiz q join fetch q.lesson where q.course.id=:course and q.lesson is not null and q.active=true order by q.id desc",Quiz.class)
            .setParameter("course",courseId).getResultList();
    }

    private List<Spec> specs(Long courseId) {
        var bank=em.createQuery("select q from Quiz q where q.course.id=:course and q.lesson is null and q.active=true order by q.id desc",Quiz.class)
            .setParameter("course",courseId).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        Map<Long,Integer> points=new HashMap<>(),positions=new HashMap<>();
        if(bank!=null) for(var link:links(bank)) {points.put(link.getQuestion().getId(),link.getPoints()==null?1:Math.max(1,link.getPoints()));positions.put(link.getQuestion().getId(),link.getPosition()==null?Integer.MAX_VALUE:link.getPosition());}
        var questions=em.createQuery("select distinct q from Question q left join fetch q.lessons where q.course.id=:course and q.active=true and (q.status is null or q.status<>'RETIRED') order by q.id",Question.class)
            .setParameter("course",courseId).getResultList();
        var lessons=em.createQuery("select l from Lesson l where l.course.id=:course and l.active=true order by l.position,l.id",Lesson.class).setParameter("course",courseId).getResultList();
        List<Spec> result=new ArrayList<>();
        for(Lesson lesson:lessons) {
            var entries=questions.stream().filter(question->question.getLessons().stream().anyMatch(value->value.getId().equals(lesson.getId())))
                .sorted(Comparator.<Question,Integer>comparing(question->positions.getOrDefault(question.getId(),Integer.MAX_VALUE)).thenComparing(Question::getId))
                .map(question->new Entry(question,Math.max(1,points.getOrDefault(question.getId(),1)))).toList();
            if(entries.isEmpty()) continue;
            String title="Câu hỏi · "+lesson.getTitle();if(title.length()>255) title=title.substring(0,255);
            result.add(new Spec(lesson,title,bank==null||bank.getDurationMinutes()==null?30:bank.getDurationMinutes(),bank==null||bank.getPassRate()==null?70:bank.getPassRate(),bank==null||bank.getMaxAttempts()==null?3:bank.getMaxAttempts(),entries));
        }
        return result;
    }

    private boolean matches(Quiz quiz,Spec spec) {
        if(!quiz.getLesson().getId().equals(spec.lesson().getId()) || !Objects.equals(quiz.getTitle(),spec.title())
            || !Objects.equals(quiz.getDurationMinutes(),spec.duration()) || !Objects.equals(quiz.getPassRate(),spec.passRate()) || !Objects.equals(quiz.getMaxAttempts(),spec.maxAttempts())) return false;
        var links=links(quiz);if(links.size()!=spec.entries().size())return false;
        for(int i=0;i<links.size();i++) if(!links.get(i).getQuestion().getId().equals(spec.entries().get(i).question().getId())
            || !Objects.equals(links.get(i).getPoints(),spec.entries().get(i).points()))return false;
        return true;
    }
    private List<QuizQuestion> links(Quiz quiz) {
        return em.createQuery("select i from QuizQuestion i join fetch i.question where i.quiz.id=:quiz order by i.position,i.id",QuizQuestion.class).setParameter("quiz",quiz.getId()).getResultList();
    }
}
