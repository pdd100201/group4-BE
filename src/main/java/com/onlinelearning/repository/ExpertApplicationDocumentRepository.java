package com.onlinelearning.repository;

import com.onlinelearning.entity.ExpertApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpertApplicationDocumentRepository extends JpaRepository<ExpertApplicationDocument,Long> {
    interface DocumentInfo {
        Long getId(); String getOriginalName(); String getContentType(); long getSizeBytes();
    }
    List<DocumentInfo> findByApplicationIdOrderByIdAsc(Long applicationId);
}
