package com.sophia.documentworkspace.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract for every domain event of this service. Concrete events are immutable
 * records (e.g. WorkspaceCreated, DocumentUpdated). Events are:
 * <ul>
 *   <li>persisted in the transactional outbox and relayed to Kafka for other
 *       microservices (notifications, analytics, email), and</li>
 *   <li>published in-process for local listeners (search indexing, audit)
 *       after the transaction commits.</li>
 * </ul>
 */
public interface DomainEvent {

    UUID eventId();

    /** Stable, versionable type discriminator, e.g. {@code workspace.created}. */
    String eventType();

    /** Aggregate kind, e.g. {@code workspace}, {@code artifact}, {@code comment}. */
    String aggregateType();

    UUID aggregateId();

    /** Workspace scope of the event; null only for cross-workspace events. */
    UUID workspaceId();

    /** User that triggered the event; null for system-triggered events. */
    UUID actorId();

    Instant occurredAt();
}
