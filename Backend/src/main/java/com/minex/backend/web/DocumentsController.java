package com.minex.backend.web;

import com.minex.backend.domain.Document;
import com.minex.backend.service.CurrentUserService;
import com.minex.backend.service.DocumentService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** §4.1 Ingestion API + document reads. */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentsController {
    private final DocumentService docs;
    private final CurrentUserService currentUser;

    public DocumentsController(DocumentService docs, CurrentUserService currentUser) {
        this.docs = docs;
        this.currentUser = currentUser;
    }

    public record DocResponse(UUID id, UUID batchId, String originalFilename,
                              String mimeType, String status, String createdAt) {
        static DocResponse of(Document d) {
            return new DocResponse(d.getId(), d.getBatchId(), d.getOriginalFilename(),
                    d.getMimeType(), d.getStatus(),
                    d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbac.canCorrect()")
    public List<DocResponse> upload(@RequestPart("file") MultipartFile file) throws IOException {
        var uploader = currentUser.requireCurrentUser();
        return docs.upload(file, uploader).stream().map(DocResponse::of).toList();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<DocResponse> list(Pageable pageable) {
        return docs.list(currentUser.requireCurrentUser(), pageable).map(DocResponse::of);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public DocResponse get(@PathVariable UUID id) {
        return DocResponse.of(docs.get(id, currentUser.requireCurrentUser()));
    }

    @PostMapping("/{id}/reprocess")
    @PreAuthorize("@rbac.canPublish()")
    public DocResponse reprocess(@PathVariable UUID id) {
        return DocResponse.of(docs.reprocess(id, currentUser.requireCurrentUser()));
    }
}
