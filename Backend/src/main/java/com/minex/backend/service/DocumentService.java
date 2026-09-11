package com.minex.backend.service;

import com.minex.backend.config.AppProps;
import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.Document;
import com.minex.backend.repo.DocumentRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * §4.1 Ingestion: validates uploads, stores raw bytes in MinIO at
 * {@code documents/{year}/{doc_id}/original.{ext}}, creates one
 * {@code documents} row per file (ZIPs unpack to rows sharing a batch_id).
 */
@Service
public class DocumentService {

    static final int MAX_ZIP_ENTRIES = 50;

    private static final Map<String, String> EXT_TO_MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("csv", "text/csv"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("zip", "application/zip"));

    private final DocumentRepository documents;
    private final StorageService storage;
    private final AuditService audit;
    private final AppProps props;
    private final ApplicationEventPublisher events;

    public DocumentService(DocumentRepository documents, StorageService storage, AuditService audit,
                           AppProps props, ApplicationEventPublisher events) {
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
        this.props = props;
        this.events = events;
    }

    @Transactional
    public List<Document> upload(MultipartFile file, AppUser uploader) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Upload must include a filename");
        }
        byte[] bytes = file.getBytes();
        checkSize(bytes.length, filename);
        String mime = mimeFor(filename);
        checkAllowed(mime, filename);

        List<Document> stored;
        if ("application/zip".equals(mime)) {
            stored = unpackZip(filename, bytes, uploader);
        } else {
            stored = List.of(storeOne(filename, mime, bytes, uploader, null));
        }
        stored.forEach(doc -> events.publishEvent(new DocumentUploadedEvent(doc.getId())));
        return stored;
    }

    @Transactional(readOnly = true)
    public Page<Document> list(AppUser viewer, Pageable pageable) {
        if ("DATA_CORRECTOR".equals(viewer.getRole().getName())) {
            return documents.findByUploadedBy(viewer, pageable);
        }
        return documents.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Document get(UUID id, AppUser viewer) {        Document doc = documents.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if ("DATA_CORRECTOR".equals(viewer.getRole().getName())
                && (doc.getUploadedBy() == null || !doc.getUploadedBy().getId().equals(viewer.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted for your role");
        }
        return doc;
    }

    /** Re-queues a document for extraction (listener fires after this transaction commits). */
    @Transactional
    public Document reprocess(UUID id, AppUser viewer) {
        Document doc = get(id, viewer);
        events.publishEvent(new DocumentUploadedEvent(doc.getId()));
        return doc;
    }
    // ---------- internals ----------

    private List<Document> unpackZip(String filename, byte[] bytes, AppUser uploader) throws IOException {
        UUID batchId = UUID.randomUUID();
        List<Document> stored = new ArrayList<>();
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++entries > MAX_ZIP_ENTRIES) {
                    throw new IllegalArgumentException("ZIP exceeds max of " + MAX_ZIP_ENTRIES + " files");
                }
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new IllegalArgumentException("ZIP entry has unsafe path: " + name);
                }
                String base = name.substring(name.lastIndexOf('/') + 1);
                byte[] content = readLimited(zip, props.getFiles().getMaxSizeBytes(), base);
                String mime = mimeFor(base);
                checkAllowed(mime, base);
                if ("application/zip".equals(mime)) {
                    // No nested unpacking: store nested archives as a single document row.
                    stored.add(storeOne(base, mime, content, uploader, batchId));
                } else {
                    stored.add(storeOne(base, mime, content, uploader, batchId));
                }
            }
        }
        if (stored.isEmpty()) {
            throw new IllegalArgumentException("ZIP archive '" + filename + "' contains no files");
        }
        return stored;
    }

    private Document storeOne(String filename, String mime, byte[] bytes, AppUser uploader, UUID batchId) {
        UUID id = UUID.randomUUID();
        String ext = extensionOf(filename);
        String key = "documents/" + LocalDate.now().getYear() + "/" + id + "/original." + ext;
        storage.put(key, bytes, mime);

        Document doc = new Document();
        doc.setId(id);
        doc.setBatchId(batchId);
        doc.setOriginalFilename(filename);
        doc.setMimeType(mime);
        doc.setStoragePath(key);
        doc.setUploadedBy(uploader);
        doc.setStatus("UPLOADED");
        doc.setCreatedAt(OffsetDateTime.now());
        documents.save(doc);

        audit.logAs(uploader, "DOCUMENT_UPLOADED", "document", id, null,
                "{\"filename\":\"" + escape(filename) + "\",\"mime\":\"" + mime + "\",\"bytes\":" + bytes.length + "}");
        return doc;
    }

    private void checkSize(long bytes, String filename) {
        if (bytes > props.getFiles().getMaxSizeBytes()) {
            throw new IllegalArgumentException(
                    "File '" + filename + "' exceeds max size of " + props.getFiles().getMaxSizeBytes() + " bytes");
        }
    }

    private void checkAllowed(String mime, String filename) {
        Set<String> allowed = Arrays.stream(props.getFiles().getAllowedMimeTypes().split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!allowed.contains(mime.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("File type not allowed for '" + filename + "': " + mime);
        }
    }

    static String mimeFor(String filename) {
        String ext = extensionOf(filename);
        return EXT_TO_MIME.getOrDefault(ext, "application/octet-stream");
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "bin";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readLimited(InputStream in, long maxBytes, String name) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new IllegalArgumentException("ZIP entry '" + name + "' exceeds max file size");
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
