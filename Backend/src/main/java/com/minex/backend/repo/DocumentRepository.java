package com.minex.backend.repo;

import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.Document;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Page<Document> findByStatus(String status, Pageable pageable);
    Page<Document> findByUploadedBy(AppUser uploadedBy, Pageable pageable);
    long countByStatus(String status);
}
