package com.sophia.authentication.infrastructure.repository.specification;

import com.sophia.authentication.domain.entity.Permission;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class PermissionSpecifications {

    private PermissionSpecifications() {
    }

    public static Specification<Permission> search(String term) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(term)) {
                return cb.conjunction();
            }
            String like = "%" + term.toLowerCase().trim() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like),
                    cb.like(cb.lower(root.get("category")), like));
        };
    }
}
