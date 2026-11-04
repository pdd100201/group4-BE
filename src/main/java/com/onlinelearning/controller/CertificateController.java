package com.onlinelearning.controller;

import com.onlinelearning.service.CertificateService;
import com.onlinelearning.service.CertificateDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class CertificateController {
 private final CertificateService certificates;
 private final CertificateDocumentService documents;
 public record PolicyRequest(@NotNull Boolean enabled,@NotBlank @Size(max=160) String issuerName,
  @NotNull @Size(max=1000) List<@NotNull @Positive Long> quizIds,@NotNull @Min(0) Integer expectedVersion) {}
 public record RevokeRequest(@NotBlank @Size(max=500) String reason) {}
 @GetMapping("/api/v1/student/certificates")
 public Page<CertificateService.View> mine(Authentication a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="all") String status,@RequestParam(defaultValue="0") int page){return certificates.mine(a.getName(),search,status,page);}
 @GetMapping("/api/v1/student/certificates/courses")
 public Page<CertificateService.Eligibility> courses(Authentication a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return certificates.myCourses(a.getName(),search,page);}
 @PostMapping("/api/v1/student/certificates/courses/{courseId}/check")
 public CertificateService.Eligibility check(Authentication a,@PathVariable Long courseId){return certificates.checkAndIssue(a.getName(),courseId);}
 @GetMapping("/api/v1/student/certificates/{id}")
 public CertificateService.View detail(Authentication a,@PathVariable Long id){return certificates.mineDetail(a.getName(),id);}
 @GetMapping("/api/v1/student/certificates/{id}/pdf")
 public ResponseEntity<byte[]> pdf(Authentication a,@PathVariable Long id){return pdfResponse(certificates.mineDetail(a.getName(),id));}
 @GetMapping("/api/v1/public/certificates/{code}")
 public ResponseEntity<CertificateService.PublicView> verify(@PathVariable String code){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(certificates.verify(code));}
 @GetMapping({"/api/v1/admin/certificates","/api/v1/manager/certificates"})
 public Page<CertificateService.View> list(Authentication a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="all") String status,@RequestParam(required=false) Long courseId,@RequestParam(defaultValue="0") int page){return certificates.management(a.getName(),search,status,courseId,page);}
 @GetMapping({"/api/v1/admin/certificates/courses","/api/v1/manager/certificates/courses"})
 public Page<CertificateService.CourseOption> options(Authentication a,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page){return certificates.courseOptions(a.getName(),search,page);}
 @GetMapping({"/api/v1/admin/certificates/courses/{courseId}/policy","/api/v1/manager/certificates/courses/{courseId}/policy"})
 public CertificateService.PolicyOptions policy(Authentication a,@PathVariable Long courseId){return certificates.policy(a.getName(),courseId);}
 @PostMapping({"/api/v1/admin/certificates/courses/{courseId}/policy","/api/v1/manager/certificates/courses/{courseId}/policy"})
 public CertificateService.PolicyView save(Authentication a,@PathVariable Long courseId,@Valid @RequestBody PolicyRequest request){return certificates.savePolicy(a.getName(),courseId,request.enabled(),request.issuerName(),request.quizIds(),request.expectedVersion());}
 @GetMapping({"/api/v1/admin/certificates/{id}","/api/v1/manager/certificates/{id}"})
 public CertificateService.View managementDetail(Authentication a,@PathVariable Long id){return certificates.managementDetail(a.getName(),id);}
 @GetMapping({"/api/v1/admin/certificates/{id}/audit","/api/v1/manager/certificates/{id}/audit"})
 public Page<CertificateService.AuditView> history(Authentication a,@PathVariable Long id,@RequestParam(defaultValue="0") int page){return certificates.history(a.getName(),id,page);}
 @PostMapping({"/api/v1/admin/certificates/{id}/revoke","/api/v1/manager/certificates/{id}/revoke"})
 public CertificateService.View revoke(Authentication a,@PathVariable Long id,@Valid @RequestBody RevokeRequest request){return certificates.revoke(a.getName(),id,request.reason());}
 @GetMapping({"/api/v1/admin/certificates/{id}/pdf","/api/v1/manager/certificates/{id}/pdf"})
 public ResponseEntity<byte[]> managementPdf(Authentication a,@PathVariable Long id){return pdfResponse(certificates.managementDetail(a.getName(),id));}
 private ResponseEntity<byte[]> pdfResponse(CertificateService.View certificate){
  return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
   .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"certificate-"+certificate.code()+".pdf\"")
   .header("X-Content-Type-Options","nosniff").body(documents.pdf(certificate));
 }
}