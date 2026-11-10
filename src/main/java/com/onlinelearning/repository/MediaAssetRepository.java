package com.onlinelearning.repository;
import com.onlinelearning.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MediaAsset a where a.id=:id")
    Optional<MediaAsset> findForUpdate(@Param("id") Long id);
}
