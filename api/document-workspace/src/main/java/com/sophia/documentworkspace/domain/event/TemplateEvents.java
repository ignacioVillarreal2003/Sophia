package com.sophia.documentworkspace.domain.event;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;

import java.time.Instant;
import java.util.UUID;

/** Domain events of templates, keyed by template id. */
public final class TemplateEvents {

    static final String AGGREGATE_TEMPLATE = "template";

    private TemplateEvents() {
    }

    public record TemplateCreated(UUID eventId, UUID workspaceId, UUID actorId, Instant occurredAt,
                                  UUID templateId, ArtifactType targetType,
                                  String name) implements DomainEvent {

        public static TemplateCreated of(UUID workspaceId, UUID actorId, UUID templateId,
                                         ArtifactType targetType, String name) {
            return new TemplateCreated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    templateId, targetType, name);
        }

        @Override
        public String eventType() {
            return "template.created";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_TEMPLATE;
        }

        @Override
        public UUID aggregateId() {
            return templateId;
        }
    }

    public record TemplatePublished(UUID eventId, UUID workspaceId, UUID actorId,
                                    Instant occurredAt, UUID templateId,
                                    boolean published) implements DomainEvent {

        public static TemplatePublished of(UUID workspaceId, UUID actorId, UUID templateId,
                                           boolean published) {
            return new TemplatePublished(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    templateId, published);
        }

        @Override
        public String eventType() {
            return "template.published";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_TEMPLATE;
        }

        @Override
        public UUID aggregateId() {
            return templateId;
        }
    }

    public record TemplateInstantiated(UUID eventId, UUID workspaceId, UUID actorId,
                                       Instant occurredAt, UUID templateId,
                                       UUID newArtifactId) implements DomainEvent {

        public static TemplateInstantiated of(UUID workspaceId, UUID actorId, UUID templateId,
                                              UUID newArtifactId) {
            return new TemplateInstantiated(UUID.randomUUID(), workspaceId, actorId, Instant.now(),
                    templateId, newArtifactId);
        }

        @Override
        public String eventType() {
            return "template.instantiated";
        }

        @Override
        public String aggregateType() {
            return AGGREGATE_TEMPLATE;
        }

        @Override
        public UUID aggregateId() {
            return templateId;
        }
    }
}
