package com.minex.backend.repo;

import com.minex.backend.domain.ApprovalRule;
import com.minex.backend.domain.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRuleRepository extends JpaRepository<ApprovalRule, UUID> {
    List<ApprovalRule> findByRoleAndCanApproveRole(Role role, Role canApproveRole);
}
