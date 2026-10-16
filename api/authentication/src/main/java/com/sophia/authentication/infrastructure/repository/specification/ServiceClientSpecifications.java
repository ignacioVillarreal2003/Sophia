package com.sophia.authentication.infrastructure.repository.specification;

import com.sophia.authentication.domain.entity.ServiceClient;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class ServiceClientSpecifications {

    private ServiceClientSpecifications() {
    }

    public static Specification<ServiceClient> search(String term) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(term)) {
                return cb.conjunction();
            }
            String like = "%" + term.toLowerCase().trim() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("description")), like));
        };
    }
}
