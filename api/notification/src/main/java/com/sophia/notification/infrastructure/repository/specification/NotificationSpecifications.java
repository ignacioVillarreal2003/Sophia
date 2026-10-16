package com.sophia.notification.infrastructure.repository.specification;

import com.sophia.notification.domain.dto.request.NotificationSearchRequest;
import com.sophia.notification.domain.entity.Notification;
import com.sophia.notification.domain.entity.enums.NotificationSeverity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Inbox search predicates.
 *
 * <p>The filters are optional, and each one is only added when it carries a
 * value: a single query with {@code :param IS NULL OR ...} branches would send
 * untyped null parameters that PostgreSQL cannot plan.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> search(UUID recipientId, NotificationSearchRequest request,
                                                     Instant now) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("recipientId"), recipientId));
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.or(
                    builder.isNull(root.get("scheduledAt")),
                    builder.lessThanOrEqualTo(root.get("scheduledAt"), now)));
            predicates.add(builder.or(
                    builder.isNull(root.get("expiresAt")),
                    builder.greaterThan(root.get("expiresAt"), now)));

            if (request.unreadOnly()) {
                predicates.add(builder.isNull(root.get("readAt")));
            }
            if (request.category() != null) {
                predicates.add(builder.equal(root.get("category"), request.category()));
            }
            if (request.type() != null) {
                predicates.add(builder.equal(root.get("type"), request.type()));
            }
            if (request.normalizedSeverity() != null) {
                predicates.add(builder.equal(root.get("severity"),
                        NotificationSeverity.valueOf(request.normalizedSeverity())));
            }
            if (request.groupKey() != null) {
                predicates.add(builder.equal(root.get("groupKey"), request.groupKey()));
            }
            if (request.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), request.from()));
            }
            if (request.to() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), request.to()));
            }
            String pattern = request.searchPattern();
            if (pattern != null) {
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.and(
                                builder.isNotNull(root.get("body")),
                                builder.like(builder.lower(root.get("body")), pattern))));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
