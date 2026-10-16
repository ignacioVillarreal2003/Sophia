package com.sophia.documentworkspace.infrastructure.repository.specification;

import com.sophia.documentworkspace.domain.entity.AuditLog;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> search(UUID workspaceId, String action, UUID userId, String resourceType,
                                                  String resourceId, AuditLog.Result result, String requestId,
                                                  Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Expression<String> workspaceIdInDetails = cb.function(
                    "jsonb_extract_path_text", String.class, root.get("details"), cb.literal("workspaceId"));
            predicates.add(cb.equal(workspaceIdInDetails, workspaceId.toString()));
            if (StringUtils.hasText(action)) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (StringUtils.hasText(resourceType)) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            }
            if (StringUtils.hasText(resourceId)) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId));
            }
            if (result != null) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (StringUtils.hasText(requestId)) {
                predicates.add(cb.equal(root.get("requestId"), requestId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
