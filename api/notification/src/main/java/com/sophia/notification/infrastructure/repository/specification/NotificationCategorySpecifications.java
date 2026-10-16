package com.sophia.notification.infrastructure.repository.specification;

import com.sophia.notification.domain.entity.NotificationCategory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Category catalogue search. The free-text filter is optional and is left out
 * of the query when absent, instead of being compared against a null parameter.
 */
public final class NotificationCategorySpecifications {

    private NotificationCategorySpecifications() {
    }

    public static Specification<NotificationCategory> search(String search) {
        return (root, query, builder) -> {
            if (search == null || search.isBlank()) {
                return builder.conjunction();
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            Predicate description = builder.and(
                    builder.isNotNull(root.get("description")),
                    builder.like(builder.lower(root.get("description")), pattern));
            return builder.or(
                    builder.like(builder.lower(root.get("code")), pattern),
                    builder.like(builder.lower(root.get("name")), pattern),
                    description);
        };
    }
}
