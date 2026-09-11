package com.minex.backend.repo;

import com.minex.backend.domain.Category;
import com.minex.backend.domain.ExtractedField;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {
    Page<ExtractedField> findByStatus(String status, Pageable pageable);
    Page<ExtractedField> findByNeedsReviewTrue(Pageable pageable);
    List<ExtractedField> findByDocumentId(UUID documentId);
    Page<ExtractedField> findByDocumentIdAndNeedsReviewTrue(UUID documentId, Pageable pageable);
    List<ExtractedField> findByCategoryAndFieldNameIgnoreCaseOrderByPeriodAsc(Category category, String fieldName);
    List<ExtractedField> findByCategoryAndStatusInOrderByPeriodAsc(Category category, Collection<String> statuses);
    List<ExtractedField> findByCategoryAndStatusIn(Category category, Collection<String> statuses);
}
