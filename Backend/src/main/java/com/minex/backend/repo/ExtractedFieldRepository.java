package com.minex.backend.repo;

import com.minex.backend.domain.Category;
import com.minex.backend.domain.ExtractedField;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {
    Page<ExtractedField> findByStatus(String status, Pageable pageable);
    Page<ExtractedField> findByNeedsReviewTrue(Pageable pageable);
    List<ExtractedField> findByDocumentId(UUID documentId);
    Page<ExtractedField> findByDocumentIdAndNeedsReviewTrue(UUID documentId, Pageable pageable);
    List<ExtractedField> findByCategoryAndFieldNameIgnoreCaseOrderByPeriodAsc(Category category, String fieldName);
    List<ExtractedField> findByCategoryAndStatusInOrderByPeriodAsc(Category category, Collection<String> statuses);
    List<ExtractedField> findByCategoryAndStatusIn(Category category, Collection<String> statuses);

    /**
     * Counts figures still awaiting a decision after collapsing each
     * document+field+period to its newest version — so superseded drafts
     * (e.g. version 1 after a correction produced version 2) are not counted.
     * Uses Postgres DISTINCT ON instead of loading the whole table.
     */
    @Query(value = """
            SELECT count(*) FROM (
                SELECT DISTINCT ON (document_id, field_name, period) status
                FROM extracted_fields
                ORDER BY document_id, field_name, period, version DESC
            ) latest
            WHERE latest.status = 'pending_review'
            """, nativeQuery = true)
    long countLatestPending();
}
