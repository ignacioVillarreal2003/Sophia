package com.sophia.documentworkspace.infrastructure.repository.specification;

import com.sophia.documentworkspace.domain.dto.request.WorkspaceListFilter;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceLabelLink;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.MemberStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WorkspaceSpecifications {

    private WorkspaceSpecifications() {
    }

    /**
     * The workspaces a member belongs to, narrowed by the list filter and already ordered: pinned ones
     * first, then by the chosen key. The ordering lives here (not in the Pageable) because it reaches
     * into the member row.
     */
    public static Specification<Workspace> forMember(UUID userId, MemberStatus memberStatus,
                                                      WorkspaceListFilter filter) {
        return (root, query, cb) -> {
            Root<WorkspaceMember> member = query.from(WorkspaceMember.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(member.get("workspace"), root));
            predicates.add(cb.equal(member.get("userId"), userId));
            predicates.add(cb.equal(member.get("status"), memberStatus));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (StringUtils.hasText(filter.search())) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + filter.search().toLowerCase() + "%"));
            }
            if (filter.role() != null) {
                predicates.add(cb.equal(member.get("role"), filter.role()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.ownership() == WorkspaceListFilter.Ownership.MINE) {
                predicates.add(cb.equal(root.get("ownerId"), userId));
            } else if (filter.ownership() == WorkspaceListFilter.Ownership.SHARED) {
                predicates.add(cb.notEqual(root.get("ownerId"), userId));
            }
            if (filter.pinnedOnly()) {
                predicates.add(cb.isNotNull(member.get("pinnedAt")));
            }
            if (filter.createdFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.createdFrom()));
            }
            if (filter.createdTo() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), filter.createdTo()));
            }
            if (filter.activeFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("lastActivityAt"), filter.activeFrom()));
            }
            if (filter.activeTo() != null) {
                predicates.add(cb.lessThan(root.get("lastActivityAt"), filter.activeTo()));
            }
            if (filter.labelId() != null) {
                Subquery<UUID> labelled = query.subquery(UUID.class);
                Root<WorkspaceLabelLink> link = labelled.from(WorkspaceLabelLink.class);
                labelled.select(link.get("id")).where(
                        cb.equal(link.get("labelId"), filter.labelId()),
                        cb.equal(link.get("workspaceId"), root.get("id")));
                predicates.add(cb.exists(labelled));
            }

            // Ordering only applies to the content query, never to the count query.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(orders(cb, root, member, filter));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static List<Order> orders(CriteriaBuilder cb, Root<Workspace> root, Root<WorkspaceMember> member,
                                      WorkspaceListFilter filter) {
        List<Order> orders = new ArrayList<>();
        // Pinned first, whatever the key.
        Expression<Integer> unpinned = cb.<Integer>selectCase()
                .when(cb.isNull(member.get("pinnedAt")), 1).otherwise(0);
        orders.add(cb.asc(unpinned));
        orders.add(cb.desc(member.get("pinnedAt")));

        WorkspaceListFilter.SortKey key = filter.sort() == null ? WorkspaceListFilter.SortKey.NAME : filter.sort();
        boolean desc = filter.descending();
        switch (key) {
            case CREATED -> orders.add(desc ? cb.desc(root.get("createdAt")) : cb.asc(root.get("createdAt")));
            case ACTIVITY -> orders.add(desc ? cb.desc(root.get("lastActivityAt")) : cb.asc(root.get("lastActivityAt")));
            case ACCESSED -> {
                Expression<Integer> neverOpened = cb.<Integer>selectCase()
                        .when(cb.isNull(member.get("lastAccessedAt")), 1).otherwise(0);
                orders.add(cb.asc(neverOpened));
                orders.add(desc ? cb.desc(member.get("lastAccessedAt")) : cb.asc(member.get("lastAccessedAt")));
            }
            default -> orders.add(desc ? cb.desc(cb.lower(root.get("name"))) : cb.asc(cb.lower(root.get("name"))));
        }
        orders.add(cb.asc(root.get("id")));
        return orders;
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
