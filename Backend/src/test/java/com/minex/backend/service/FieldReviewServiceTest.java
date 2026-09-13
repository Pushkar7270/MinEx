package com.minex.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.minex.backend.config.AppProps;
import com.minex.backend.domain.AppUser;
import com.minex.backend.domain.ExtractedField;
import com.minex.backend.domain.Role;
import com.minex.backend.repo.ApprovalRuleRepository;
import com.minex.backend.repo.CategoryRepository;
import com.minex.backend.repo.ExtractedFieldRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Guards around undoing an accidental rejection. */
class FieldReviewServiceTest {

    private final ExtractedFieldRepository fields = mock(ExtractedFieldRepository.class);
    private final FieldReviewService service = new FieldReviewService(
            fields, mock(ApprovalRuleRepository.class), mock(CategoryRepository.class),
            mock(DocumentService.class), mock(AuditService.class), new AppProps());

    private static AppUser user(UUID id) {
        Role role = new Role();
        role.setName("SUPERVISOR");
        role.setRank(30);
        AppUser u = new AppUser();
        u.setId(id);
        u.setEmail("user-" + id + "@minex.local");
        u.setRole(role);
        return u;
    }

    private static ExtractedField rejected(AppUser rejecter) {
        ExtractedField f = new ExtractedField();
        f.setId(UUID.randomUUID());
        f.setStatus("rejected");
        f.setVersion(1);
        f.setFieldName("Coal production");
        f.setReviewedBy(rejecter);
        return f;
    }

    @Test
    void rejecterCanRestoreRejection() {
        AppUser rejecter = user(UUID.randomUUID());
        ExtractedField f = rejected(rejecter);
        when(fields.findById(f.getId())).thenReturn(Optional.of(f));

        ExtractedField out = service.reopen(f.getId(), rejecter);

        assertEquals("pending_review", out.getStatus());
        verify(fields).save(f);
    }

    @Test
    void anotherReviewerCannotRestoreRejection() {
        ExtractedField f = rejected(user(UUID.randomUUID()));
        when(fields.findById(f.getId())).thenReturn(Optional.of(f));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reopen(f.getId(), user(UUID.randomUUID())));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void onlyRejectedFieldsCanBeRestored() {
        AppUser rejecter = user(UUID.randomUUID());
        ExtractedField f = rejected(rejecter);
        f.setStatus("pending_review");
        when(fields.findById(f.getId())).thenReturn(Optional.of(f));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reopen(f.getId(), rejecter));

        assertEquals(409, ex.getStatusCode().value());
    }
}
