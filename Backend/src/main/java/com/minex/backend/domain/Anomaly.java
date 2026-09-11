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
@Table(name = "anomalies")
public class Anomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extracted_field_id", nullable = false)
    private ExtractedField extractedField;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /** open, acknowledged, resolved */
    @Column(nullable = false)
    private String status = "open";

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;
}
