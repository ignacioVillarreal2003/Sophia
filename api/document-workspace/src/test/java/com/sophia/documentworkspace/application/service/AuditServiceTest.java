package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.AuditLogMapper;
import org.mapstruct.factory.Mappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.entity.AuditLog;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.infrastructure.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditLogPersister auditLogPersister;

    @Mock
    private WorkspaceAuthorizationService workspaceAuthorization;

    @Test
    void aDomainEventsBusinessFieldsBecomeTheAuditDetails() {
        AuditService service = new AuditService(auditLogRepository, auditLogPersister, workspaceAuthorization,
                Mappers.getMapper(AuditLogMapper.class), new ObjectMapper().registerModule(new JavaTimeModule()));
        UUID workspaceId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        ArtifactEvents.ArtifactCreated event = ArtifactEvents.ArtifactCreated.of(
                workspaceId, UUID.randomUUID(), artifactId, ArtifactType.MARKDOWN_DOCUMENT, "Roadmap", parentId);

        service.onDomainEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogPersister).persist(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("artifact.created");
        assertThat(saved.getResult()).isEqualTo(AuditLog.Result.SUCCESS);
        assertThat(saved.getDetails())
                .containsEntry("artifactId", artifactId.toString())
                .containsEntry("name", "Roadmap")
                .containsEntry("parentId", parentId.toString())
                .containsEntry("type", "MARKDOWN_DOCUMENT")
                .containsEntry("workspaceId", workspaceId)
                .doesNotContainKeys("eventId", "actorId", "occurredAt");
    }
}
