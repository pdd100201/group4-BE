package com.onlinelearning.service;

import com.onlinelearning.entity.*;
import com.onlinelearning.repository.*;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.Set;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class NotificationService {
 private final UserNotificationRepository notifications;
 private final UserRepository users;
 private final EntityManager em;
 public record View(Long id,String type,String category,String title,String message,String link,Instant createdAt,Instant readAt) {}
 public record Summary(long unreadCount,Long latestId) {}
 public Page<View> list(String email,boolean unreadOnly,String category,int page,int size){
  User actor=actor(email);
  if(page<0||page>100000||size<1||size>50||!Set.of("all","COURSE","PAYMENT","DISCUSSION","REVIEW","CERTIFICATE").contains(category))
   throw new ApiException(HttpStatus.BAD_REQUEST,"Bộ lọc thông báo không hợp lệ.");
  Specification<UserNotification> spec=(root,q,cb)->cb.equal(root.get("recipient").get("id"),actor.getId());
  if(unreadOnly)spec=spec.and((r,q,cb)->cb.isNull(r.get("readAt")));
  if(!category.equals("all"))spec=spec.and((r,q,cb)->cb.equal(r.get("category"),category));
  return notifications.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"))).map(this::view);
 }
 public Summary summary(String email){
  User actor=actor(email);
  Long latest=em.createQuery("select max(n.id) from UserNotification n where n.recipient.id=:id",Long.class).setParameter("id",actor.getId()).getSingleResult();
  return new Summary(notifications.countByRecipientIdAndReadAtIsNull(actor.getId()),latest);
 }
 @Transactional public void read(String email,Long id){
  User actor=actor(email);
  if(!notifications.existsByIdAndRecipientId(id,actor.getId()))throw new ApiException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo.");
  notifications.markRead(actor.getId(),id,Instant.now());
 }
 @Transactional public void readAll(String email,Long throughId){
  User actor=actor(email);
  if(throughId==null||throughId<1)throw new ApiException(HttpStatus.BAD_REQUEST,"Mốc thông báo không hợp lệ.");
  notifications.markAllRead(actor.getId(),throughId,Instant.now());
 }
 // The database unique key makes callback retries/concurrent delivery idempotent.
 // This write joins the business transaction; rollback removes its notifications.
 @Transactional(propagation=Propagation.MANDATORY)
 public void send(Long recipient,String key,String type,String category,String title,String message,String link){
  if(recipient==null)return;
  if(!link.startsWith("/")||link.startsWith("//"))throw new IllegalArgumentException("Internal links only");
  em.createNativeQuery("""
   INSERT INTO user_notifications (recipient_id,event_key,type,category,title,message,link,created_at,read_at)
   SELECT id,:eventKey,:type,:category,:title,:message,:link,:now,NULL FROM users
   WHERE id=:recipient AND enabled=true AND blocked=false
   ON DUPLICATE KEY UPDATE event_key=event_key
   """).setParameter("recipient",recipient).setParameter("eventKey",key).setParameter("type",type)
   .setParameter("category",category).setParameter("title",clip(title,200)).setParameter("message",clip(message,2000))
   .setParameter("link",link).setParameter("now",Instant.now()).executeUpdate();
 }
 private User actor(String email){return users.findByEmailIgnoreCase(email).filter(u->u.isEnabled()&&!u.isBlocked())
  .orElseThrow(()->new ApiException(HttpStatus.FORBIDDEN,"Tài khoản không khả dụng."));}
 private View view(UserNotification n){return new View(n.getId(),n.getType(),n.getCategory(),n.getTitle(),n.getMessage(),n.getLink(),n.getCreatedAt(),n.getReadAt());}
 private String clip(String value,int max){return value.length()<=max?value:value.substring(0,max-1)+"…";}
}