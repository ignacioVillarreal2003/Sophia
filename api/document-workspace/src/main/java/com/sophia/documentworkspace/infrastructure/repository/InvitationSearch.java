package com.sophia.documentworkspace.infrastructure.repository;

import com.sophia.documentworkspace.domain.entity.enums.InvitationStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Searches of the invitations a person has received and not answered. The filters are optional and combine, so the
 * query text is built from the ones given; everything the caller typed travels as a parameter.
 */
@Repository
public class InvitationSearch {

    /** What narrows a listing; a null field does not narrow it. */
    public record Filter(String text, WorkspaceRole role, String invitedBy, Instant from, Instant to) {
    }

    public enum Order { CREATED, NAME, EXPIRES }

    @PersistenceContext
    private EntityManager em;

    private static final String BASE = """
            from WorkspaceInvitation i, Workspace w
            where i.inviteeUserId = :userId and i.status = :pending and i.expiresAt > :now
              and w.id = i.workspaceId and w.deletedAt is null
            """;

    /** The open invitations (invitation, workspace) of the person, filtered and ordered. */
    public Page<Object[]> open(UUID userId, Instant now, Filter filter, Order order, boolean desc, Pageable pageable) {
        Map<String, Object> params = base(userId, now);
        StringBuilder where = new StringBuilder(BASE);
        text(where, params, "lower(w.name)", filter.text());
        if (filter.role() != null) {
            where.append(" and i.role = :role");
            params.put("role", filter.role());
        }
        if (filter.invitedBy() != null && !filter.invitedBy().isBlank()) {
            where.append(" and lower(i.invitedByEmail) = :invitedBy");
            params.put("invitedBy", filter.invitedBy().trim().toLowerCase());
        }
        if (filter.from() != null) {
            where.append(" and i.createdAt >= :from");
            params.put("from", filter.from());
        }
        if (filter.to() != null) {
            where.append(" and i.createdAt < :to");
            params.put("to", filter.to());
        }

        String direction = desc ? "desc" : "asc";
        String orderBy = switch (order) {
            case NAME -> " order by lower(w.name) " + direction + ", i.createdAt desc, i.id";
            case EXPIRES -> " order by i.expiresAt " + direction + ", i.id";
            case CREATED -> " order by i.createdAt " + direction + ", i.id";
        };
        Query select = em.createQuery("select i, w " + where + orderBy);
        Query count = em.createQuery("select count(i) " + where);
        params.forEach((name, value) -> {
            select.setParameter(name, value);
            count.setParameter(name, value);
        });
        long total = (Long) count.getSingleResult();
        List<Object[]> content = new ArrayList<>();
        if (total > 0) {
            select.setFirstResult((int) pageable.getOffset()).setMaxResults(pageable.getPageSize());
            content = select.getResultList();
        }
        return new PageImpl<>(content, pageable, total);
    }

    /** The people who invited the person, by address, for a picker. */
    @SuppressWarnings("unchecked")
    public List<String> inviters(UUID userId, Instant now, String text, int limit) {
        Map<String, Object> params = base(userId, now);
        StringBuilder where = new StringBuilder(BASE);
        where.append(" and i.invitedByEmail is not null");
        text(where, params, "lower(i.invitedByEmail)", text);
        Query query = em.createQuery("select distinct i.invitedByEmail " + where + " order by i.invitedByEmail");
        params.forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList();
    }

    private static Map<String, Object> base(UUID userId, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("now", now);
        params.put("pending", InvitationStatus.PENDING);
        return params;
    }

    private static void text(StringBuilder where, Map<String, Object> params, String column, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String escaped = text.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        where.append(" and ").append(column).append(" like :text escape '\\'");
        params.put("text", "%" + escaped + "%");
    }
}
