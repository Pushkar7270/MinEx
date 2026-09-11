package com.minex.backend.repo;

import com.minex.backend.domain.ExtractedContent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedContentRepository extends JpaRepository<ExtractedContent, UUID> {
    List<ExtractedContent> findByDocumentId(UUID documentId);
}
