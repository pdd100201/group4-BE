package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseRequest;
import com.onlinelearning.dto.question.QuestionRequest;
import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.entity.*;
import com.onlinelearning.exception.ApiException;
import com.onlinelearning.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @Transactional @AutoConfigureMockMvc
class ExpertQuestionIntegrationTests {
    @Autowired ExpertQuestionService questions;
    @Autowired ExpertCourseService courses;
    @Autowired ExpertQuizService quizzes;
    @Autowired QuestionImportService imports;
    @Autowired QuestionRepository repository;
    @Autowired UserRepository users;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    static final String OWNER="expert@onlinelearning.local";
    Long course() { return courses.create(OWNER,new CourseRequest("Question course","Description","Java",BigDecimal.ZERO)).id(); }
    QuestionRequest input(String content) { return new QuestionRequest(content,"SINGLE_CHOICE","Explanation",true,null,List.of(
        new QuestionRequest.Option("A",true),new QuestionRequest.Option("B",false),new QuestionRequest.Option("C",false),new QuestionRequest.Option("D",false))); }

    @Test void crudStatusFiltersAndPagination() {
        Long id=course(); var created=questions.create(id,OWNER,input("Java question"));
        assertEquals(4,created.options().size()); assertTrue(created.options().getFirst().correct());
        assertEquals(1,questions.list(id,OWNER,"Java","SINGLE_CHOICE",true,0,1).getTotalElements());
        assertEquals("Updated",questions.update(id,created.id(),OWNER,input("Updated")).content());
        questions.status(id,created.id(),OWNER,false);
        assertEquals(0,questions.list(id,OWNER,"","",true,0,10).getTotalElements());
        questions.delete(id,created.id(),OWNER); assertFalse(repository.existsById(created.id()));
    }
    @Test void ownerStateAndRoleBoundaries() throws Exception {
        Long id=course(); var q=questions.create(id,OWNER,input("Owned"));
        assertEquals(403,assertThrows(ApiException.class,()->questions.update(id,q.id(),"student@onlinelearning.local",input("No"))).status().value());
        em.find(Course.class,id).setStatus(CourseStatus.PUBLISHED);
        assertEquals(409,assertThrows(ApiException.class,()->questions.create(id,OWNER,input("No"))).status().value());
        assertEquals(1,questions.list(id,OWNER,"","",null,0,10).getTotalElements());
        for(String role:List.of("STUDENT","MANAGER","ADMIN")) mvc.perform(get("/api/v1/expert/courses/"+id+"/questions").with(user("other@test.local").roles(role))).andExpect(status().isForbidden());
    }
    @Test void validationAndMultipleAnswers() {
        Long id=course(); var options=List.of(new QuestionRequest.Option("A",true),new QuestionRequest.Option("B",true),new QuestionRequest.Option("C",false),new QuestionRequest.Option("D",false));
        assertThrows(ApiException.class,()->questions.create(id,OWNER,new QuestionRequest("Q","SINGLE_CHOICE","",true,null,options)));
        assertEquals("MULTIPLE_CHOICE",questions.create(id,OWNER,new QuestionRequest("Q","MULTIPLE_CHOICE","",true,null,options)).type());
        assertThrows(ApiException.class,()->questions.list(id,OWNER,"","",null,-1,10));
    }
    @Test void linkedQuizAndRetainedAttemptsPreventUnsafeChanges() {
        Long id=course(); var q=questions.create(id,OWNER,input("Retained"));
        var quiz=quizzes.create(id,OWNER,new QuizRequest("Quiz",10,70,3,true,null,List.of(new QuizQuestionItem(q.id(),1,1))));
        assertThrows(ApiException.class,()->questions.delete(id,q.id(),OWNER));
        assertThrows(ApiException.class,()->questions.status(id,q.id(),OWNER,false));
        var attempt=new QuizAttempt();attempt.setQuiz(em.find(Quiz.class,quiz.id()));attempt.setStudent(users.findByEmailIgnoreCase("student@onlinelearning.local").orElseThrow());em.persist(attempt);em.flush();
        assertThrows(ApiException.class,()->questions.update(id,q.id(),OWNER,input("Changed")));
    }
    @Test void importReportsInvalidRowsAndSavesValidRows() throws Exception {
        Long id=course(); byte[] template=imports.template(id,OWNER); byte[] file;
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(template));var out=new ByteArrayOutputStream()) {
            var row=book.getSheetAt(0).createRow(2);row.createCell(0).setCellValue("Invalid row");book.write(out);file=out.toByteArray();
        }
        var result=imports.importFile(id,OWNER,new MockMultipartFile("file","questions.xlsx","application/octet-stream",file));
        assertEquals(1,result.imported());assertEquals(1,result.errors().size());assertEquals(3,result.errors().getFirst().row());
        assertEquals(1,questions.list(id,OWNER,"","",null,0,10).getTotalElements());
        assertThrows(ApiException.class,()->imports.importFile(id,OWNER,new MockMultipartFile("file","bad.xlsx","application/octet-stream",new byte[]{1,2,3})));
    }
}
