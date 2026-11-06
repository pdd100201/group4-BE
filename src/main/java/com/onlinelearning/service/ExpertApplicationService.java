package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ExpertApplicationService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final ExpertApplicationRepository applications;
    private final ExpertApplicationDocumentRepository documents;
    private final AuditLogRepository audit;
    private final NotificationService notifications;
    private final EntityManager em;

    public record Input(@NotBlank @Size(min=3,max=200) String specialty,
        @Min(0) @Max(60) int experienceYears,
        @NotBlank @Size(min=20,max=4000) String experience,
        @NotBlank @Size(min=20,max=4000) String introduction,
        @Size(max=500) String portfolioUrl) {}
    public record DocumentView(Long id,String name,String contentType,long sizeBytes) {}
    public record View(Long id,Long applicantId,String applicantName,String applicantEmail,String applicantPhone,
        String specialty,int experienceYears,String experience,String introduction,String portfolioUrl,
        ExpertApplicationStatus status,Instant submittedAt,Instant reviewedAt,String reviewerName,String reviewReason,
        List<DocumentView> documents) {}
    public record Download(String name,String contentType,byte[] content) {}

    public View mine(String email) {
        var actor=actor(email);
        return applications.findFirstByApplicantIdOrderByIdDesc(actor.getId()).map(a->view(a,true)).orElse(null);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public View submit(String email,Input input,List<MultipartFile> files) {
        var actor=actor(email);em.lock(actor,LockModeType.PESSIMISTIC_WRITE);em.refresh(actor);
        if (!actor.isEnabled()||actor.isBlocked()||!has(actor,"STUDENT")||has(actor,"EXPERT")||has(actor,"ADMIN")||has(actor,"MANAGER"))
            throw forbidden("Chỉ học viên chưa có quyền Expert được gửi hồ sơ.");
        var latest=applications.findFirstByApplicantIdOrderByIdDesc(actor.getId());
        if (latest.isPresent() && latest.get().getStatus()!=ExpertApplicationStatus.REJECTED)
            throw conflict("Bạn đã có hồ sơ chờ duyệt hoặc đã được duyệt.");
        var a=new ExpertApplication();a.setApplicant(actor);
        a.setSpecialty(text(input.specialty(),3,200));a.setExperienceYears(input.experienceYears());
        if(input.experienceYears()<0||input.experienceYears()>60)throw bad("Số năm kinh nghiệm phải từ 0 đến 60.");
        a.setExperience(text(input.experience(),20,4000));a.setIntroduction(text(input.introduction(),20,4000));
        a.setPortfolioUrl(url(input.portfolioUrl()));
        List<MultipartFile> attachments=files==null?List.of():files;
        if(attachments.size()>3)throw bad("Đính kèm tối đa 3 tệp minh chứng.");
        // Validate and read every file before persisting the application.
        var prepared=new ArrayList<ExpertApplicationDocument>();
        for(var file:attachments) prepared.add(document(file,a));
        applications.saveAndFlush(a);documents.saveAll(prepared);documents.flush();
        record(actor,a,"EXPERT_APPLICATION_SUBMITTED");
        for(var admin:em.createQuery("select distinct u from User u join u.roles r where r.code='ADMIN' and u.enabled=true and u.blocked=false",User.class).getResultList())
            notifications.send(admin.getId(),"expert-submitted:"+a.getId(),"EXPERT_APPLICATION_SUBMITTED","REVIEW",
                "Có hồ sơ giảng viên cần duyệt",actor.getFullName()+" đăng ký giảng dạy: "+a.getSpecialty(),"/admin/expert-applications/"+a.getId());
        return view(a,true);
    }

    public Page<View> list(String email,String state,String search,int page) {
        admin(email);
        if(page<0||page>100000||search==null||search.length()>200)throw bad("Bộ lọc không hợp lệ.");
        ExpertApplicationStatus status=null;
        if(!"ALL".equals(state))try{status=ExpertApplicationStatus.valueOf(state);}catch(IllegalArgumentException e){throw bad("Trạng thái không hợp lệ.");}
        ExpertApplicationStatus filter=status;
        String term=search.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");
        Specification<ExpertApplication> spec=(root,q,cb)->{
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(filter!=null)predicates.add(cb.equal(root.get("status"),filter));
            if(!term.isEmpty())predicates.add(cb.or(cb.like(cb.lower(root.get("applicant").get("fullName")),"%"+term+"%",'!'),
                cb.like(cb.lower(root.get("applicant").get("email")),"%"+term+"%",'!'),cb.like(cb.lower(root.get("specialty")),"%"+term+"%",'!')));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return applications.findAll(spec,PageRequest.of(page,20,Sort.by(Sort.Direction.DESC,"id"))).map(a->view(a,false));
    }
    public View detail(String email,Long id) {
        admin(email);return view(applications.findById(id).orElseThrow(this::missing),true);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public View review(String email,Long id,boolean approve,String reason) {
        var reviewer=admin(email);
        var a=applications.findForUpdate(id).orElseThrow(this::missing);
        if(a.getStatus()!=ExpertApplicationStatus.PENDING)throw conflict("Hồ sơ đã được xử lý. Hãy tải lại trang.");
        if(reviewer.getId().equals(a.getApplicant().getId()))throw forbidden("Không thể tự duyệt hồ sơ của mình.");
        String note=reason==null?"":reason.strip();
        if((!approve&&note.isBlank())||note.length()>2000)throw bad("Khi từ chối cần lý do, tối đa 2.000 ký tự.");
        var applicant=a.getApplicant();em.lock(applicant,LockModeType.PESSIMISTIC_WRITE);em.refresh(applicant);
        if(approve){
            if(!applicant.isEnabled()||applicant.isBlocked()||!has(applicant,"STUDENT")||has(applicant,"ADMIN")||has(applicant,"MANAGER"))
                throw conflict("Tài khoản không còn đủ điều kiện để cấp quyền Expert.");
            applicant.getRoles().add(roles.findByCode("EXPERT").orElseThrow(()->conflict("Chưa cấu hình vai trò Expert.")));
            applicant.getRoles().add(roles.findByCode("STUDENT").orElseThrow());
        }
        a.setStatus(approve?ExpertApplicationStatus.APPROVED:ExpertApplicationStatus.REJECTED);
        a.setReviewedBy(reviewer);a.setReviewedAt(Instant.now());a.setReviewReason(note.isEmpty()?null:note);
        applications.flush();record(reviewer,a,approve?"EXPERT_APPLICATION_APPROVED":"EXPERT_APPLICATION_REJECTED");
        notifications.send(applicant.getId(),"expert-reviewed:"+a.getId(),"EXPERT_APPLICATION_REVIEWED","REVIEW",
            approve?"Hồ sơ giảng viên đã được duyệt":"Hồ sơ giảng viên bị từ chối",
            approve?"Bạn đã được cấp quyền Expert và vẫn giữ quyền học viên. Mở hồ sơ để vào không gian giảng dạy.":"Lý do: "+note,
            "/profile?section=expert");
        return view(a,true);
    }

    public Download download(String email,Long id) {
        var actor=actor(email);var doc=documents.findById(id).orElseThrow(this::missing);
        if(!has(actor,"ADMIN")&&!doc.getApplication().getApplicant().getId().equals(actor.getId()))throw missing();
        return new Download(doc.getOriginalName(),doc.getContentType(),doc.getContent());
    }
    private ExpertApplicationDocument document(MultipartFile file,ExpertApplication application) {
        if(file.isEmpty()||file.getSize()>5L*1024*1024)throw bad("Mỗi tệp minh chứng phải có dữ liệu và không quá 5 MB.");
        try {
            byte[] bytes=file.getBytes();String type=null;
            if(bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))type="application/pdf";
            else if(bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))type="image/png";
            else if(bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)type="image/jpeg";
            else if(bytes.length>=12&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))type="image/webp";
            if(type==null)throw bad("Minh chứng chỉ hỗ trợ PDF, JPG, PNG hoặc WebP.");
            String name=Optional.ofNullable(file.getOriginalFilename()).orElse("minh-chung").replace('\\','/');
            name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]","");
            if(name.isBlank())name="minh-chung";if(name.length()>200)name=name.substring(name.length()-200);
            var doc=new ExpertApplicationDocument();doc.setApplication(application);doc.setOriginalName(name);
            doc.setContentType(type);doc.setSizeBytes(bytes.length);doc.setContent(bytes);return doc;
        }catch(IOException e){throw bad("Không đọc được tệp minh chứng.");}
    }
    private View view(ExpertApplication a,boolean includeDocuments) {
        var u=a.getApplicant();
        var files=includeDocuments?documents.findByApplicationIdOrderByIdAsc(a.getId()).stream()
            .map(d->new DocumentView(d.getId(),d.getOriginalName(),d.getContentType(),d.getSizeBytes())).toList():List.<DocumentView>of();
        return new View(a.getId(),u.getId(),u.getFullName(),u.getEmail(),u.getPhone(),a.getSpecialty(),a.getExperienceYears(),
            a.getExperience(),a.getIntroduction(),a.getPortfolioUrl(),a.getStatus(),a.getSubmittedAt(),a.getReviewedAt(),
            a.getReviewedBy()==null?null:a.getReviewedBy().getFullName(),a.getReviewReason(),files);
    }
    private User actor(String email){return users.findByEmailIgnoreCase(email).filter(u->u.isEnabled()&&!u.isBlocked()).orElseThrow(()->forbidden("Tài khoản không khả dụng."));}
    private User admin(String email){var u=actor(email);if(!has(u,"ADMIN"))throw forbidden("Chỉ Admin được duyệt hồ sơ giảng viên.");return u;}
    private boolean has(User user,String code){return user.getRoles().stream().anyMatch(r->code.equals(r.getCode()));}
    private String text(String value,int min,int max){if(value==null||value.strip().length()<min||value.strip().length()>max)throw bad("Thông tin hồ sơ chưa đủ hoặc vượt giới hạn ký tự.");return value.strip();}
    private String url(String value){if(value==null||value.isBlank())return null;if(value.length()>500)throw bad("Liên kết quá dài.");try{var uri=URI.create(value.strip());if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();return uri.toString();}catch(IllegalArgumentException e){throw bad("Liên kết hồ sơ phải là URL HTTPS hợp lệ.");}}
    private void record(User actor,ExpertApplication application,String action){var log=new AuditLog();log.setActor(actor);log.setAction(action);log.setTargetType("EXPERT_APPLICATION");log.setTargetId(application.getId().toString());log.setOccurredAt(Instant.now());audit.save(log);}
    private ApiException bad(String message){return new ApiException(HttpStatus.BAD_REQUEST,message);}
    private ApiException conflict(String message){return new ApiException(HttpStatus.CONFLICT,message);}
    private ApiException forbidden(String message){return new ApiException(HttpStatus.FORBIDDEN,message);}
    private ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy hồ sơ hoặc minh chứng.");}
}
