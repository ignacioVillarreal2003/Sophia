package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.WorkspaceLimits;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WorkspaceLimitsRepository extends JpaRepository<WorkspaceLimits, UUID> {
}
