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
@Table(name = "documents")
public class Document {
    /** Assigned by DocumentService before persist (needed for the storage path). */
    @Id
    private UUID id;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    /** UPLOADED, PROCESSING, PROCESSED, FAILED, QUEUED_FOR_OCR */
    @Column(nullable = false)
    private String status = "UPLOADED";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
