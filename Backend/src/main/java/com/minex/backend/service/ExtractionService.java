package com.minex.backend.service;

import com.minex.backend.config.AppProps;
import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.Document;
import com.minex.backend.domain.ExtractedContent;
import com.minex.backend.domain.ExtractedField;
import com.minex.backend.repo.DocumentRepository;
import com.minex.backend.repo.ExtractedContentRepository;
import com.minex.backend.repo.ExtractedFieldRepository;
import com.minex.backend.service.extraction.BoilerplateClassifier;
import com.minex.backend.service.extraction.CandidateFigure;
import com.minex.backend.service.extraction.CategorizerService;
import com.minex.backend.service.extraction.FigureExtractor;
import com.minex.backend.service.extraction.OfficeExtractor;
import com.minex.backend.service.extraction.PageBlock;
import com.minex.backend.service.extraction.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * §4.2 async OCR & extraction pipeline (idempotent: re-running on the same
 * document wipes its derived rows first, so no duplicates accumulate).
 */
@Service
public class ExtractionService {

    private final DocumentRepository documents;
    private final ExtractedContentRepository content;
    private final ExtractedFieldRepository fields;
    private final StorageService storage;
    private final AuditService audit;
    private final AppProps props;
    private final PdfTextExtractor pdf;
    private final OfficeExtractor office;
    private final FigureExtractor figures;
    private final BoilerplateClassifier boilerplate;
    private final CategorizerService categorizer;
    private final ValidationService validation;

    public ExtractionService(DocumentRepository documents, ExtractedContentRepository content,
                             ExtractedFieldRepository fields, StorageService storage,
                             AuditService audit, AppProps props, PdfTextExtractor pdf,
                             OfficeExtractor office, FigureExtractor figures,
                             BoilerplateClassifier boilerplate, CategorizerService categorizer,
                             ValidationService validation) {
        this.documents = documents;
        this.content = content;
        this.fields = fields;
        this.storage = storage;
        this.audit = audit;
        this.props = props;
        this.pdf = pdf;
        this.office = office;
        this.figures = figures;
        this.boilerplate = boilerplate;
        this.categorizer = categorizer;
        this.validation = validation;
    }

    // AFTER_COMMIT: the uploader transaction must be committed first,
    // otherwise this thread cannot see the new documents row yet.
    // REQUIRES_NEW: this runs in a background thread with no transaction.
    @Async("ingestionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpload(DocumentUploadedEvent event) {
        process(event.documentId());
    }

    /** Synchronous entry point (used by tests / reprocessing). */
    public void process(UUID documentId) {
        Document doc = documents.findById(documentId).orElse(null);
        if (doc == null) return;
        AppUser actor = doc.getUploadedBy();
        setStatus(doc, "PROCESSING", actor, null);
        try {
            // Idempotency: clear previously derived rows before re-deriving.
            fields.findByDocumentId(documentId).forEach(fields::delete);
            content.findByDocumentId(documentId).forEach(content::delete);

            byte[] bytes = storage.get(doc.getStoragePath());
            List<PageBlock> blocks = extractBlocks(doc, bytes);
            if (blocks == null) {
                // Scanned/image input: OCR stub state per MVP scope.
                setStatus(doc, "QUEUED_FOR_OCR", actor, "{\"reason\":\"no text layer; OCR pending\"}");
                return;
            }

            Map<String, Integer> firstFreq = new HashMap<>();
            Map<String, Integer> lastFreq = new HashMap<>();
            boilerplate.accumulateFrequencies(blocks, firstFreq, lastFreq);

            List<PageBlock> clean = new ArrayList<>();
            for (PageBlock block : blocks) {
                boolean isBoiler = boilerplate.isBoilerplate(block.text(), firstFreq, lastFreq);
                persistContent(doc, block, isBoiler);
                if (!isBoiler) clean.add(block);
            }

            String period = figures.detectPeriod(clean);
            List<ExtractedField> saved = new ArrayList<>();
            for (CandidateFigure fig : figures.extract(clean)) {
                saved.add(persistField(doc, fig, period));
            }
            validation.validate(doc, saved, actor);
            setStatus(doc, "PROCESSED", actor,
                    "{\"blocks\":" + blocks.size() + ",\"fields\":" + saved.size() + "}");
        } catch (Exception ex) {
            setStatus(doc, "FAILED", actor, "{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }

    /**
     * Routes by detected type (PRD §4.2). Returns null when there is no
     * machine-readable text layer (scanned PDF / image → OCR queue stub).
     */
    private List<PageBlock> extractBlocks(Document doc, byte[] bytes) throws Exception {
        return switch (doc.getMimeType()) {
            case "application/pdf" -> {
                var result = pdf.extract(bytes);
                yield result.scanned() ? null : result.blocks();
            }
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.ms-excel" ->
                    office.extractSpreadsheet(bytes, doc.getOriginalFilename());
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    office.extractDocx(bytes, doc.getOriginalFilename());
            case "text/csv" -> List.of(new PageBlock(null, "text",
                    new String(bytes, StandardCharsets.UTF_8).strip(), 0.9));
            case "image/jpeg", "image/png" -> null; // OCR queue stub
            default -> throw new IllegalArgumentException("Unsupported type: " + doc.getMimeType());
        };
    }

    private void persistContent(Document doc, PageBlock block, boolean isBoiler) {
        ExtractedContent row = new ExtractedContent();
        row.setDocument(doc);
        row.setPageNumber(block.pageNumber());
        row.setBlockType(block.blockType());
        row.setRawText(block.text());
        row.setConfidenceScore(block.confidence());
        row.setBoilerplate(isBoiler);
        row.setCreatedAt(OffsetDateTime.now());
        content.save(row);
    }

    private ExtractedField persistField(Document doc, CandidateFigure fig, String period) {
        var category = categorizer.categorize(fig.fieldName() + " " + fig.context());
        double threshold = props.getExtraction().getConfidenceThreshold();
        ExtractedField row = new ExtractedField();
        row.setDocument(doc);
        row.setCategory(category.orElse(null));
        row.setPeriod(period);
        row.setFieldName(fig.fieldName());
        row.setFieldValue(fig.value());
        row.setUnit(fig.normalizedUnit());
        row.setConfidenceScore(fig.confidence());
        row.setNeedsReview(fig.confidence() < threshold || category.isEmpty());
        row.setStatus("pending_review");
        row.setVersion(1);
        row.setCreatedBy(null); // machine-extracted; human corrections set created_by
        row.setCreatedAt(OffsetDateTime.now());
        return fields.save(row);
    }

    private void setStatus(Document doc, String status, AppUser actor, String detailJson) {
        String old = "{\"status\":\"" + doc.getStatus() + "\"}";
        doc.setStatus(status);
        documents.save(doc);
        String updated = "{\"status\":\"" + status + "\""
                + (detailJson == null ? "" : ",\"detail\":" + detailJson) + "}";
        if (actor != null) {
            audit.logAs(actor, "DOCUMENT_" + status, "document", doc.getId(), old, updated);
        }
    }
}
