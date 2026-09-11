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
@Table(name = "roles")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name; // DATA_CORRECTOR / SUB_SUPERVISOR / SUPERVISOR / MANAGER / ADMIN

    @Column(nullable = false, unique = true)
    private Integer rank;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
