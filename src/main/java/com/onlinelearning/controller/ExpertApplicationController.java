package com.onlinelearning.controller;

import com.onlinelearning.service.ExpertApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1")
public class ExpertApplicationController {
    private final ExpertApplicationService service;
    public record Decision(@NotNull Boolean approve,@Size(max=2000) String reason) {}
    @GetMapping("/expert-applications/mine") public ResponseEntity<ExpertApplicationService.View> mine(Authentication actor){
        var view=service.mine(actor.getName());return view==null?ResponseEntity.noContent().build():ResponseEntity.ok(view);
    }
    @PostMapping(value="/expert-applications",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExpertApplicationService.View submit(Authentication actor,@Valid @RequestPart("details") ExpertApplicationService.Input details,
        @RequestPart(value="files",required=false) List<MultipartFile> files){return service.submit(actor.getName(),details,files);}
    @GetMapping("/admin/expert-applications") public Page<ExpertApplicationService.View> list(Authentication actor,
        @RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return service.list(actor.getName(),status,search,page);}
    @GetMapping("/admin/expert-applications/{id}") public ExpertApplicationService.View detail(Authentication actor,@PathVariable Long id){return service.detail(actor.getName(),id);}
    @PutMapping("/admin/expert-applications/{id}/review") public ExpertApplicationService.View review(Authentication actor,
        @PathVariable Long id,@Valid @RequestBody Decision decision){return service.review(actor.getName(),id,decision.approve(),decision.reason());}
    @GetMapping("/expert-applications/documents/{id}") public ResponseEntity<byte[]> download(Authentication actor,@PathVariable Long id){
        var doc=service.download(actor.getName(),id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(doc.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(doc.name(),StandardCharsets.UTF_8).build().toString())
            .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(doc.content());
    }
}
