package com.sophia.ai.infrastructure.repository;

import com.sophia.ai.domain.entity.AiSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSettingsRepository extends JpaRepository<AiSettings, Short> {
}
