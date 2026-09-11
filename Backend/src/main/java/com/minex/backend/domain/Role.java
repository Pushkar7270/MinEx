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

    /** Discord-style display color, e.g. "#e06c75". */
    @Column(nullable = false)
    private String color = "#8b6cc1";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
