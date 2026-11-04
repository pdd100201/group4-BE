package com.onlinelearning.controller;
import com.onlinelearning.service.LessonDiscussionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/discussions")
public class LessonDiscussionController {
 private final LessonDiscussionService service;
 public record QuestionRequest(@NotNull @Positive Long lessonId,@NotBlank @Size(max=200) String title,@NotBlank @Size(max=4000) String content) {}
 public record AnswerRequest(@NotBlank @Size(max=4000) String content) {}
 public record ResolveRequest(@Positive Long answerId) {}
 public record VisibilityRequest(@NotNull Boolean hidden) {}
 @GetMapping public Page<LessonDiscussionService.QuestionView> list(Authentication actor,@RequestParam(required=false) Long lessonId,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="all") String state,@RequestParam(defaultValue="0") int page){return service.list(actor.getName(),lessonId,search,state,page);}
 @GetMapping("/{id}") public LessonDiscussionService.Detail detail(Authentication actor,@PathVariable Long id,@RequestParam(defaultValue="0") int page){return service.detail(actor.getName(),id,page);}
 @PostMapping public LessonDiscussionService.QuestionView create(Authentication actor,@Valid @RequestBody QuestionRequest body){return service.create(actor.getName(),body.lessonId(),body.title(),body.content());}
 @PostMapping("/{id}/answers") public LessonDiscussionService.AnswerView reply(Authentication actor,@PathVariable Long id,@Valid @RequestBody AnswerRequest body){return service.reply(actor.getName(),id,body.content());}
 @PutMapping("/{id}/resolution") public void resolve(Authentication actor,@PathVariable Long id,@Valid @RequestBody ResolveRequest body){service.resolve(actor.getName(),id,body.answerId());}
 @PutMapping("/{id}/visibility") public void hide(Authentication actor,@PathVariable Long id,@Valid @RequestBody VisibilityRequest body){service.hideQuestion(actor.getName(),id,body.hidden());}
 @PutMapping("/{id}/answers/{answerId}/visibility") public void hideAnswer(Authentication actor,@PathVariable Long id,@PathVariable Long answerId,@Valid @RequestBody VisibilityRequest body){service.hideAnswer(actor.getName(),id,answerId,body.hidden());}
}