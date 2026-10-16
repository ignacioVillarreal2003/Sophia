package com.sophia.authentication.infrastructure.repository.specification;

import com.sophia.authentication.domain.entity.Role;
import com.sophia.authentication.domain.entity.User;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> search(String term) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(term)) {
                return cb.conjunction();
            }
            String like = "%" + term.toLowerCase().trim() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), like),
                    cb.like(cb.lower(root.get("lastName")), like),
                    cb.like(cb.lower(root.get("email")), like));
        };
    }

    public static Specification<User> hasActive(Boolean active) {
        return (root, query, cb) ->
                active == null ? cb.conjunction() : cb.equal(root.get("active"), active);
    }

    public static Specification<User> hasEmailVerified(Boolean emailVerified) {
        return (root, query, cb) ->
                emailVerified == null ? cb.conjunction() : cb.equal(root.get("emailVerified"), emailVerified);
    }

    public static Specification<User> hasRole(String roleName) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(roleName)) {
                return cb.conjunction();
            }
            query.distinct(true);
            Join<User, Role> roles = root.join("roles");
            return cb.equal(roles.get("name"), roleName);
        };
    }
}
