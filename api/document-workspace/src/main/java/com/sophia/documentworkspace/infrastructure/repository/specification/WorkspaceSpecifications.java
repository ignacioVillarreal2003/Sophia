package com.sophia.documentworkspace.infrastructure.repository.specification;

import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WorkspaceSpecifications {

    private WorkspaceSpecifications() {
    }

    public static Specification<Workspace> forMember(UUID userId, MemberStatus memberStatus, String search,
                                                      WorkspaceRole role, WorkspaceStatus status) {
        return (root, query, cb) -> {
            Root<WorkspaceMember> member = query.from(WorkspaceMember.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(member.get("workspace"), root));
            predicates.add(cb.equal(member.get("userId"), userId));
            predicates.add(cb.equal(member.get("status"), memberStatus));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (StringUtils.hasText(search)) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }
            if (role != null) {
                predicates.add(cb.equal(member.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Every live workspace, for platform administrators (membership is irrelevant). */
    public static Specification<Workspace> all(String search, WorkspaceStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (StringUtils.hasText(search)) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
