package com.onlinelearning.repository;
import com.onlinelearning.entity.ChatAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ChatAttachmentRepository extends JpaRepository<ChatAttachment, Long> {
    List<ChatAttachment> findByCloudPublicIdIsNullAndIdGreaterThanOrderByIdAsc(Long after, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ChatAttachment a where a.id = :id")
    Optional<ChatAttachment> findForUpdate(Long id);
}
