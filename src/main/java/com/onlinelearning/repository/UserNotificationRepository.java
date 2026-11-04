package com.onlinelearning.repository;

import com.onlinelearning.entity.UserNotification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface UserNotificationRepository extends JpaRepository<UserNotification,Long>,JpaSpecificationExecutor<UserNotification> {
 long countByRecipientIdAndReadAtIsNull(Long id);
 @Modifying @Query("update UserNotification n set n.readAt=:now where n.recipient.id=:recipient and n.id=:id and n.readAt is null")
 int markRead(@Param("recipient") Long recipient,@Param("id") Long id,@Param("now") Instant now);
 @Modifying @Query("update UserNotification n set n.readAt=:now where n.recipient.id=:recipient and n.id<=:throughId and n.readAt is null")
 int markAllRead(@Param("recipient") Long recipient,@Param("throughId") Long throughId,@Param("now") Instant now);
 boolean existsByIdAndRecipientId(Long id,Long recipient);
}