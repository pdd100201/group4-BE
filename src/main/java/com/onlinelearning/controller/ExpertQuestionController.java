package com.onlinelearning.controller;

import com.onlinelearning.dto.question.*;
import com.onlinelearning.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses/{courseId}/questions")
public class ExpertQuestionController {
    private final ExpertQuestionService questions;
    private final QuestionImportService imports;
    public record QuestionPage(List<QuestionResponse> content, long totalElements, int totalPages, int number) {}
    public record StatusRequest(@NotNull Boolean active) {}
    @GetMapping public QuestionPage list(@PathVariable Long courseId, Authentication actor,
        @RequestParam(defaultValue="") String search, @RequestParam(defaultValue="") String type,
        @RequestParam(required=false) Boolean active, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        var result=questions.list(courseId,actor.getName(),search,type,active,page,size);
        return new QuestionPage(result.getContent(),result.getTotalElements(),result.getTotalPages(),result.getNumber());
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public QuestionResponse create(@PathVariable Long courseId, Authentication actor, @Valid @RequestBody QuestionRequest request) { return questions.create(courseId,actor.getName(),request); }
    @PutMapping("/{id}") public QuestionResponse update(@PathVariable Long courseId,@PathVariable Long id,Authentication actor,@Valid @RequestBody QuestionRequest request) { return questions.update(courseId,id,actor.getName(),request); }
    @PatchMapping("/{id}/status") public QuestionResponse status(@PathVariable Long courseId,@PathVariable Long id,Authentication actor,@Valid @RequestBody StatusRequest request) { return questions.status(courseId,id,actor.getName(),request.active()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long courseId,@PathVariable Long id,Authentication actor) { questions.delete(courseId,id,actor.getName()); }
    @GetMapping("/template") public ResponseEntity<byte[]> template(@PathVariable Long courseId,Authentication actor) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=question-template.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(imports.template(courseId,actor.getName()));
    }
    @PostMapping(value="/import",consumes="multipart/form-data") public QuestionImportService.ImportResult importFile(@PathVariable Long courseId,Authentication actor,@RequestParam MultipartFile file) { return imports.importFile(courseId,actor.getName(),file); }
}
