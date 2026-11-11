package com.onlinelearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** UC28: Expert deletion, moderator deletion and retained-reference protection. */
@ExtendWith(MockitoExtension.class)
class DeleteCourseTest {
    private static final Set<String> passed = new HashSet<>();
    private static final String TITLE = "[DEMO-COURSE] Java cơ bản";
    private static final String[] EXPERT_REFERENCES = {"CourseRegistration", "Lesson", "Question", "Quiz"};
    private static final String[] MODERATOR_REFERENCES = {
        "select count(r) from CourseRegistration r where r.course.id=:id",
        "select count(a) from QuizAttempt a where a.quiz.course.id=:id",
        "select count(p) from StudentLessonProgress p where p.lesson.course.id=:id",
        "select count(a) from QuizAnswer a where a.question.course.id=:id",
        "select count(l) from QuizQuestion l where l.question.course.id=:id and l.quiz.course.id<>:id"
    };
    @Mock CourseRepository courses;
    @Mock UserRepository users;
    @Mock AuditLogRepository logs;
    @Mock EntityManager em;
    @Mock MediaService media;

    record DeleteCase(String group, String id, String type, String role, String email,
                      long actorId, boolean accountExists, long courseId, boolean courseExists,
                      CourseStatus state, String reason, String reference, String expected, String message) {
        @Override public String toString() { return group + " / " + id + " / " + expected; }
    }

    static Stream<DeleteCase> sheetCases() {
        List<DeleteCase> data = new ArrayList<>();
        for (int i=1;i<=12;i++) {
            CourseStatus state = switch(i) {
                case 2 -> CourseStatus.REJECTED; case 5 -> CourseStatus.PENDING_REVIEW;
                case 6 -> CourseStatus.PUBLISHED; case 7 -> CourseStatus.ARCHIVED;
                case 8 -> CourseStatus.APPROVED; default -> CourseStatus.DRAFT;
            };
            String ref = i>=9 ? EXPERT_REFERENCES[i-9] : null;
            String expected = i<=2 ? "SUCCESS" : i==3 ? "FORBIDDEN" : i==4 ? "NOT_FOUND" : "CONFLICT";
            String message = switch(expected) {
                case "FORBIDDEN" -> "Bạn chỉ được quản lý khóa học của mình.";
                case "NOT_FOUND" -> "Không tìm thấy khóa học.";
                case "CONFLICT" -> ref==null ? "Expert chỉ được xóa khóa học Nháp hoặc Bị từ chối."
                    : "Không thể xóa khóa học còn nội dung hoặc dữ liệu đăng ký liên quan.";
                default -> null;
            };
            data.add(new DeleteCase("EXPERT", id(i), i<=2?"N":"A", "EXPERT",
                i==3?"other.expert@example.com":"expert@onlinelearning.local", i==3?99:3,
                true, i==4?999999:1, i!=4, state, null, ref, expected, message));
        }
        for (int i=1;i<=15;i++) {
            String role=i==2?"ADMIN":i==3?"EXPERT":i==4?"STUDENT":"MANAGER";
            long actorId=switch(role){case "ADMIN"->1;case "EXPERT"->3;case "STUDENT"->4;default->2;};
            String email=i==5?"missing.moderator@example.invalid":role.toLowerCase(Locale.ROOT)+"@onlinelearning.local";
            String reason=switch(i){case 6->null;case 7->"";case 8->"   ";case 9->"X";case 10->"X".repeat(255);case 11->"X".repeat(256);default->"  Confirmed course violation.  ";};
            CourseStatus state=switch(i){case 2->CourseStatus.ARCHIVED;case 13->CourseStatus.PENDING_REVIEW;case 14->CourseStatus.APPROVED;case 15->CourseStatus.DRAFT;default->CourseStatus.PUBLISHED;};
            String expected=i>=3&&i<=5?"FORBIDDEN":i>=6&&i<=8||i==11?"BAD_REQUEST":i==12?"NOT_FOUND":i==13||i==14?"CONFLICT":"SUCCESS";
            String message=switch(expected){
                case "FORBIDDEN"->i==5?"Tài khoản không hợp lệ.":"Chỉ Admin/Manager được thực hiện thao tác này.";
                case "BAD_REQUEST"->"Lý do phải có từ 1 đến 255 ký tự.";
                case "NOT_FOUND"->"Không tìm thấy khóa học.";
                case "CONFLICT"->"Không thể xóa khóa học đang chờ duyệt hoặc ở trạng thái legacy.";
                default->null;
            };
            data.add(new DeleteCase("MODERATOR",id(i),i==9||i==10||i==11?"B":expected.equals("SUCCESS")?"N":"A",
                role,email,actorId,i!=5,i==12?999999:4,i!=12,state,reason,null,expected,message));
        }
        for(int i=0;i<MODERATOR_REFERENCES.length;i++) {
            data.add(new DeleteCase("RETENTION",id(i+1),"A","MANAGER","manager@onlinelearning.local",2,true,4,true,
                CourseStatus.PUBLISHED,"Confirmed course violation.",MODERATOR_REFERENCES[i],"CONFLICT",
                "Khóa học có dữ liệu cần lưu giữ, không thể xóa hẳn. Hãy ẩn khóa học để giữ lịch sử."));
        }
        return data.stream();
    }

    private static String id(int number){return "UTCID"+String.format("%02d",number);}
    @BeforeAll static void clearResults(){passed.clear();}

    @ParameterizedTest(name="{0}")
    @MethodSource("sheetCases")
    void deleteCourseMatchesSheet(DeleteCase tc) {
        boolean expert=tc.group().equals("EXPERT");
        User actor=user(tc.actorId(),tc.email(),tc.role());
        User owner=user(3,"expert@onlinelearning.local","EXPERT");
        Course course=new Course();course.setId(tc.courseId());course.setExpert(owner);course.setTitle(TITLE);
        course.setDescription("Existing course content.");course.setCategory("Programming");
        course.setPrice(new BigDecimal("299000"));course.setStatus(tc.state());
        MediaAsset asset=new MediaAsset();asset.setId(1001L);asset.setCourse(course);asset.setOwner(owner);
        if(!expert)when(users.findByEmailIgnoreCase(tc.email())).thenReturn(tc.accountExists()?Optional.of(actor):Optional.empty());
        boolean allowedRole=tc.accountExists()&&(tc.role().equals("MANAGER")||tc.role().equals("ADMIN"));
        boolean reasonValid=tc.reason()!=null&&!tc.reason().isBlank()&&tc.reason().length()<=255;
        boolean lookup=expert||allowedRole&&reasonValid;
        if(lookup)when(courses.findForUpdate(tc.courseId())).thenReturn(tc.courseExists()?Optional.of(course):Optional.empty());
        boolean checksReferences=tc.expected().equals("SUCCESS")||tc.reference()!=null;
        if(checksReferences)stubReferenceCounts(tc,expert);
        Query dml=null;
        if(tc.expected().equals("SUCCESS")) {
            dml=mock(Query.class,RETURNS_SELF);when(em.createQuery(anyString())).thenReturn(dml);
            if(expert){
                TypedQuery<MediaAsset> assets=mock(TypedQuery.class,RETURNS_SELF);
                when(em.createQuery("select a from MediaAsset a where a.course.id=:id",MediaAsset.class)).thenReturn(assets);
                when(assets.getResultList()).thenReturn(List.of(asset));
            }
        }
        if(tc.expected().equals("SUCCESS")) {
            assertDoesNotThrow(()->invoke(tc));
            verify(dml,times(expert?2:8)).executeUpdate();
            verify(em).createQuery("delete from Course c where c.id=:id");
            if(expert)assertNull(asset.getCourse());
            else verify(em).createQuery("update MediaAsset a set a.course=null where a.course.id=:id");
            verify(em).createQuery("delete from CoursePackage p where p.course.id=:id");
            verify(em).clear();verify(em,times(2)).flush();
            InOrder mutationOrder=inOrder(em);
            if(!expert)for(String query:List.of(
                "delete from QuizQuestion l where l.quiz.id in (select q.id from Quiz q where q.course.id=:id)",
                "delete from QuestionOption o where o.question.id in (select q.id from Question q where q.course.id=:id)",
                "delete from Quiz q where q.course.id=:id","delete from Question q where q.course.id=:id",
                "delete from Lesson l where l.course.id=:id"))mutationOrder.verify(em).createQuery(query);
            mutationOrder.verify(em).createQuery("delete from CoursePackage p where p.course.id=:id");
            if(!expert)mutationOrder.verify(em).createQuery("update MediaAsset a set a.course=null where a.course.id=:id");
            mutationOrder.verify(em).clear();mutationOrder.verify(em).createQuery("delete from Course c where c.id=:id");
            ArgumentCaptor<AuditLog> audit=ArgumentCaptor.forClass(AuditLog.class);verify(logs).save(audit.capture());
            assertEquals("COURSE_DELETED",audit.getValue().getAction());assertEquals("COURSE",audit.getValue().getTargetType());
            assertEquals(Long.toString(tc.courseId()),audit.getValue().getTargetId());
            assertSame(expert?owner:actor,audit.getValue().getActor());assertNotNull(audit.getValue().getOccurredAt());
            if(!expert)assertEquals(tc.reason().trim(),audit.getValue().getMetadata());
        } else {
            ApiException error=assertThrows(ApiException.class,()->invoke(tc));
            assertEquals(HttpStatus.valueOf(tc.expected()),error.status());assertEquals(tc.message(),error.getMessage());
            verifyNoInteractions(logs);verify(em,never()).createQuery(anyString());
            verify(em,never()).flush();verify(em,never()).clear();assertSame(course,asset.getCourse());
            if(!checksReferences)verifyNoInteractions(em);
        }
        assertEquals(TITLE,course.getTitle());assertEquals(tc.state(),course.getStatus());
        assertEquals("Existing course content.",course.getDescription());assertEquals("Programming",course.getCategory());
        assertEquals(new BigDecimal("299000"),course.getPrice());assertSame(owner,course.getExpert());
        if(lookup)verify(courses).findForUpdate(tc.courseId());else verifyNoInteractions(courses);
        verify(courses,never()).save(any(Course.class));verify(courses,never()).delete(any(Course.class));
        if(expert)verifyNoInteractions(users);verifyNoInteractions(media);
        passed.add(tc.group()+"/"+tc.id());
    }

    private void stubReferenceCounts(DeleteCase tc,boolean expert) {
        when(em.createQuery(anyString(),eq(Long.class))).thenAnswer(invocation->{
            String query=invocation.getArgument(0);
            TypedQuery<Long> count=mock(TypedQuery.class,RETURNS_SELF);
            boolean retained=tc.reference()!=null&&(expert?query.contains("from "+tc.reference()+" e "):query.equals(tc.reference()));
            when(count.getSingleResult()).thenReturn(retained?1L:0L);
            return count;
        });
    }

    private void invoke(DeleteCase tc){
        if(tc.group().equals("EXPERT"))new ExpertCourseService(courses,users,logs,em,media,org.mockito.Mockito.mock(com.onlinelearning.repository.CoursePackageRepository.class)).delete(tc.courseId(),tc.email());
        else new CourseModerationService(org.mockito.Mockito.mock(NotificationEvents.class),courses,users,logs,em).delete(tc.courseId(),tc.email(),tc.reason());
    }

    private User user(long id,String email,String role){
        User user=new User();user.setId(id);user.setEmail(email);user.setEnabled(true);user.setBlocked(false);
        Role assigned=new Role();assigned.setCode(role);user.getRoles().add(assigned);return user;
    }

    @AfterAll static void exportSheetCases()throws Exception{
        List<Map<String,Object>> result=sheetCases().map(tc->{Map<String,Object> row=new LinkedHashMap<>();
            row.put("case",tc);row.put("passed",passed.contains(tc.group()+"/"+tc.id()));return row;}).toList();
        Files.createDirectories(Path.of("target"));new ObjectMapper().writerWithDefaultPrettyPrinter()
            .writeValue(Path.of("target/delete-course-sheet-cases.json").toFile(),result);
    }
}
