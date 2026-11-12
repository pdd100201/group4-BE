package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.entity.Course.ReviewField;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.CourseRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class CourseReviewChangesService {
    private final EntityManager em;
    private final CourseRepository courses;
    public record Change(String key, String label, String type, String before, String after) {}
    public record Comparison(boolean baselineAvailable, List<Change> changes) {}

    // Caller holds the course write lock. Later draft edits retain this original baseline.
    public void capturePublished(Course course) {
        if (course.getStatus()==CourseStatus.PUBLISHED) course.setReviewBaseline(snapshot(course));
    }

    public Comparison compare(Long id) {
        Course course=courses.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy khóa học."));
        var old=course.getReviewBaseline();
        if(old==null) return new Comparison(false,List.of());
        var current=snapshot(course);
        Set<String> keys=new LinkedHashSet<>(old.keySet());keys.addAll(current.keySet());
        List<Change> changes=new ArrayList<>();
        for(String key:keys) {
            var before=old.get(key);var after=current.get(key);
            String from=before==null?null:before.value(),to=after==null?null:after.value();
            if(!Objects.equals(from,to)) {
                var field=after==null?before:after;
                changes.add(new Change(key,field.label(),field.type(),from,to));
            }
        }
        return new Comparison(true,List.copyOf(changes));
    }

    public Map<String,ReviewField> snapshot(Course c) {
        Map<String,ReviewField> data=new LinkedHashMap<>();
        field(data,"course.title","Tên khóa học",c.getTitle());
        field(data,"course.description","Mô tả khóa học",c.getDescription());
        field(data,"course.category","Danh mục",c.getCategory());
        field(data,"course.price","Giá cơ sở",c.getPrice());
        data.put("course.thumbnail",new ReviewField("Ảnh bìa","image",c.getThumbnail()==null?null:c.getThumbnail().getSecureUrl()));
        for(var p:em.createQuery("select p from CoursePackage p where p.course.id=:id order by p.duration",CoursePackage.class).setParameter("id",c.getId()).getResultList()) {
            String key="package."+p.getDuration()+".",label="Gói "+switch(p.getDuration()){case ONE_MONTH->"1 tháng";case ONE_YEAR->"1 năm";case LIFETIME->"vĩnh viễn";};
            field(data,key+"price",label+" · Giá",p.getPrice());
            field(data,key+"discount",label+" · Giảm giá (%)",p.getDiscountPercent());
            field(data,key+"active",label+" · Mở đăng ký",p.isActive());
        }
        for(var l:em.createQuery("select l from Lesson l where l.course.id=:id order by l.id",Lesson.class).setParameter("id",c.getId()).getResultList()) {
            String key="lesson."+l.getId()+".",label="Bài học #"+l.getId();
            field(data,key+"title",label+" · Tên",l.getTitle());
            field(data,key+"content",label+" · Nội dung",l.getContent());
            field(data,key+"position",label+" · Thứ tự",l.getPosition());
            field(data,key+"active",label+" · Hiển thị",l.isActive());
            data.put(key+"video",new ReviewField(label+" · Video","video",l.getVideo()==null?null:l.getVideo().getId().toString()));
        }
        for(var q:em.createQuery("select q from Question q where q.course.id=:id order by q.id",Question.class).setParameter("id",c.getId()).getResultList()) {
            String key="question."+q.getId()+".",label="Câu hỏi #"+q.getId();
            field(data,key+"content",label+" · Nội dung",q.getContent());
            field(data,key+"type",label+" · Loại",q.getType());
            field(data,key+"explanation",label+" · Giải thích",q.getExplanation());
            field(data,key+"active",label+" · Hoạt động",q.isActive());
            field(data,key+"lesson",label+" · Các bài học",q.getLessons().stream().sorted(Comparator.comparing(Lesson::getId)).map(l->"#"+l.getId()+" "+l.getTitle()).toList());
            field(data,key+"status",label+" · Trạng thái",q.getStatus());
            field(data,key+"questionType",label+" · Kiểu câu hỏi",q.getQuestionType());
            data.put(key+"image",new ReviewField(label+" · Hình ảnh","image",q.getImage()==null?null:q.getImage().getSecureUrl()));
            for(var o:q.getOptions().stream().sorted(Comparator.comparing(QuestionOption::getId)).toList()) {
                field(data,key+"option."+o.getId()+".content",label+" · Đáp án #"+o.getId(),o.getContent());
                field(data,key+"option."+o.getId()+".correct",label+" · Đáp án đúng #"+o.getId(),o.isCorrect());
            }
        }
        for(var q:em.createQuery("select q from Quiz q where q.course.id=:id and q.lesson is null order by q.id",Quiz.class).setParameter("id",c.getId()).getResultList()) {
            String key="quiz."+q.getId()+".",label="Quiz #"+q.getId();
            field(data,key+"title",label+" · Tên",q.getTitle());
            field(data,key+"duration",label+" · Thời lượng (phút)",q.getDurationMinutes());
            field(data,key+"passRate",label+" · Tỷ lệ đạt (%)",q.getPassRate());
            field(data,key+"maxAttempts",label+" · Số lần làm",q.getMaxAttempts());
            field(data,key+"active",label+" · Hoạt động",q.isActive());
            field(data,key+"lesson",label+" · Bài học",q.getLesson()==null?null:q.getLesson().getId());
            for(var link:em.createQuery("select l from QuizQuestion l where l.quiz.id=:id order by l.id",QuizQuestion.class).setParameter("id",q.getId()).getResultList()) {
                String item=key+"question."+link.getQuestion().getId()+".";
                field(data,item+"position",label+" · Thứ tự câu #"+link.getQuestion().getId(),link.getPosition());
                field(data,item+"points",label+" · Điểm câu #"+link.getQuestion().getId(),link.getPoints());
            }
        }
        return data;
    }

    private void field(Map<String,ReviewField> fields,String key,String label,Object value) {
        String text=value==null?null:value instanceof BigDecimal number?number.stripTrailingZeros().toPlainString():value instanceof Boolean flag?(flag?"Có":"Không"):value.toString();
        fields.put(key,new ReviewField(label,"text",text));
    }
}
