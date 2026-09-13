package com.minex.backend.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "extracted_fields")
public class ExtractedField {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Fiscal year/quarter, e.g. "2024-25". */
    private String period;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "field_value")
    private Double fieldValue;

    @Column(name = "field_text", columnDefinition = "TEXT")
    private String fieldText;

    private String unit;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore = 1.0;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview = false;

    /** draft, pending_review, approved, rejected, published */
    @Column(nullable = false)
    private String status = "pending_review";

    @Column(nullable = false)
    private int version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
