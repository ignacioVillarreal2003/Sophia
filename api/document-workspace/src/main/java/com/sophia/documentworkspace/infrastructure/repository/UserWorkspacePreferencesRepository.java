package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.UserWorkspacePreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserWorkspacePreferencesRepository extends JpaRepository<UserWorkspacePreferences, UUID> {
}
