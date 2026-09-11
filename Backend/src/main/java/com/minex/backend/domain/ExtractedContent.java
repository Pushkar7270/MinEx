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
@Table(name = "extracted_content")
public class ExtractedContent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "page_number")
    private Integer pageNumber;

    /** text, table, figure, header, footer */
    @Column(name = "block_type", nullable = false)
    private String blockType = "text";

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "confidence_score", nullable = false)
    private double confidenceScore = 1.0;

    @Column(name = "is_boilerplate", nullable = false)
    private boolean boilerplate = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
