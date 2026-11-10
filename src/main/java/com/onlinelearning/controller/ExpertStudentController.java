package com.onlinelearning.controller;
import com.onlinelearning.service.ExpertStudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/expert/courses/{courseId}/students")
public class ExpertStudentController {
 private final ExpertStudentService service;
 @GetMapping public ExpertStudentService.StudentPage list(@PathVariable Long courseId,Authentication actor,
   @RequestParam(defaultValue="") String search,@RequestParam(defaultValue="") String access,@RequestParam(defaultValue="") String learning,
   @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
  return service.list(courseId,actor.getName(),search,access,learning,page,size);
 }
 @GetMapping("/{studentId}") public ExpertStudentService.Detail detail(@PathVariable Long courseId,@PathVariable Long studentId,Authentication actor) {
  return service.detail(courseId,studentId,actor.getName());
 }
}
