package com.onlinelearning.controller;
import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.service.ExpertQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses/{courseId}/quizzes")
public class ExpertQuizController {
    private final ExpertQuizService quizzes;
    @GetMapping public List<QuizResponse> list(@PathVariable Long courseId, Authentication a) { return quizzes.list(courseId, a.getName()); }
    @GetMapping("/{id}") public QuizResponse get(@PathVariable Long courseId, @PathVariable Long id, Authentication a) { return quizzes.get(courseId, id, a.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public QuizResponse create(@PathVariable Long courseId, @Valid @RequestBody QuizRequest r, Authentication a) { return quizzes.create(courseId, a.getName(), r); }
    @PutMapping("/{id}") public QuizResponse update(@PathVariable Long courseId, @PathVariable Long id, @Valid @RequestBody QuizRequest r, Authentication a) { return quizzes.update(courseId, id, a.getName(), r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long courseId, @PathVariable Long id, Authentication a) { quizzes.delete(courseId, id, a.getName()); }
}
