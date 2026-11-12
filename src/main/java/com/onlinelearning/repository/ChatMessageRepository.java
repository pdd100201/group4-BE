package com.onlinelearning.repository;

import com.onlinelearning.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    Optional<ChatMessage> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);
    Optional<ChatMessage> findByAttachmentId(Long attachmentId);

    @EntityGraph(attributePaths = {"sender", "recipient", "course"})
    @Query("""
        select m from ChatMessage m where
        m.course is not null and
        ((m.sender.id = :me and m.recipient.id = :peer) or (m.sender.id = :peer and m.recipient.id = :me))
        and (:before is null or m.id < :before) order by m.id desc
        """)
    List<ChatMessage> privateHistory(@Param("me") Long me, @Param("peer") Long peer,
                                    @Param("before") Long before, Pageable pageable);
    @EntityGraph(attributePaths = {"sender", "recipient", "course"})
    @Query("""
        select m from ChatMessage m where
        m.course is not null and
        ((m.sender.id = :me and m.recipient.id = :peer) or (m.sender.id = :peer and m.recipient.id = :me))
        and lower(m.content) like lower(:pattern) escape '!'
        and (:before is null or m.id < :before) order by m.id desc
        """)
    List<ChatMessage> searchPrivate(Long me, Long peer, String pattern, Long before, Pageable pageable);
}
