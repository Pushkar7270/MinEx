package com.minex.backend.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Configurable approval graph — who may approve whose work (PRD §2). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "approval_rules")
public class ApprovalRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role; // submitter role

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "can_approve_role_id", nullable = false)
    private Role canApproveRole; // approver role

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category; // NULL = all categories

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
