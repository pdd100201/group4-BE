package com.onlinelearning.controller;

import com.onlinelearning.dto.quiz.*;
import com.onlinelearning.service.ExpertQuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/quizzes")
public class ExpertQuizListController {
    private final ExpertQuizService quizzes;
    public record QuizPage(List<QuizListItem> content, long totalElements, int totalPages, int number) {}
    @GetMapping public QuizPage list(Authentication actor, @RequestParam(required=false) Long courseId,
        @RequestParam(defaultValue="") String search, @RequestParam(defaultValue="") String category, @RequestParam(required=false) Boolean active,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        var result = quizzes.search(actor.getName(), courseId, search, category, active, page, size);
        return new QuizPage(result.getContent(), result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }
}
