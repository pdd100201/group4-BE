package com.onlinelearning.controller;

import com.onlinelearning.dto.quiz.StudentQuizDtos.*;
import com.onlinelearning.service.StudentQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/student")
public class StudentQuizController {
    private final StudentQuizService quizzes;
    @GetMapping("/quizzes/{id}") public Info info(@PathVariable Long id,Authentication actor){return quizzes.info(id,actor.getName());}
    @PostMapping("/quizzes/{id}/attempts") public Attempt start(@PathVariable Long id,Authentication actor){return quizzes.start(id,actor.getName());}
    @GetMapping("/quiz-attempts/{id}") public Attempt attempt(@PathVariable Long id,Authentication actor){return quizzes.read(id,actor.getName());}
    @PutMapping("/quiz-attempts/{id}/answers") public Attempt answer(@PathVariable Long id,Authentication actor,@Valid @RequestBody Answer answer){return quizzes.save(id,actor.getName(),answer);}
    @PostMapping("/quiz-attempts/{id}/submit") public Result submit(@PathVariable Long id,Authentication actor,@Valid @RequestBody Submission body){return quizzes.submit(id,actor.getName(),body);}
    @GetMapping("/quiz-attempts/{id}/result") public Result result(@PathVariable Long id,Authentication actor){return quizzes.review(id,actor.getName());}
}
