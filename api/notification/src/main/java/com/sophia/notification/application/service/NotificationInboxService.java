package com.sophia.notification.application.service;

import com.sophia.notification.application.mapper.NotificationDispatchMapper;
import com.sophia.notification.application.mapper.NotificationMapper;
import com.sophia.notification.application.security.AuthenticatedUser;
import com.sophia.notification.domain.dto.request.NotificationSearchRequest;
import com.sophia.notification.domain.dto.response.NotificationDispatchResponse;
import com.sophia.notification.domain.dto.response.NotificationGroupResponse;
import com.sophia.notification.domain.dto.response.NotificationResponse;
import com.sophia.notification.domain.dto.response.PageResponse;
import com.sophia.notification.domain.entity.AuditLog;
import com.sophia.notification.domain.entity.Notification;
import com.sophia.notification.domain.entity.enums.NotificationSeverity;
import com.sophia.notification.domain.exception.BadRequestException;
import com.sophia.notification.domain.exception.ResourceNotFoundException;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import com.sophia.notification.infrastructure.repository.NotificationRepository;
import com.sophia.notification.infrastructure.repository.specification.NotificationSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationInboxService {

    private static final Set<String> SORTABLE =
            Set.of("createdAt", "readAt", "priority", "severity", "category", "type", "title");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final int MAX_GROUPS = 200;

    private final NotificationRepository notificationRepository;
    private final NotificationDispatchRepository dispatchRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationDispatchMapper notificationDispatchMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(UUID recipientId, NotificationSearchRequest request,
                                                     Pageable pageable) {
        validateSeverity(request.normalizedSeverity());
        validateRange(request.from(), request.to());
        Page<Notification> page = notificationRepository.findAll(
                NotificationSpecifications.search(recipientId, request, Instant.now()),
                applySort(pageable));
        return PageResponse.from(page, notificationMapper::toResponse);
    }

    /** Reads another user's inbox on their behalf; the access itself is auditable. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> searchUnrestricted(UUID recipientId,
                                                                 NotificationSearchRequest request,
                                                                 Pageable pageable,
                                                                 AuthenticatedUser actor) {
        PageResponse<NotificationResponse> page = search(recipientId, request, pageable);
        auditService.record(actor.id(), actor.email(), AuditService.READ_OTHER_INBOX,
                AuditService.RESOURCE_TYPE_USER, recipientId.toString(), AuditLog.Result.SUCCESS,
                AuditService.details("category", request.category(), "type", request.type(),
                        "search", request.search()));
        return page;
    }

    @Transactional(readOnly = true)
    public List<NotificationGroupResponse> groups(UUID recipientId, boolean unreadOnly) {
        return notificationRepository.findGroups(recipientId, Instant.now(), unreadOnly,
                PageRequest.of(0, MAX_GROUPS));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notificationRepository.countUnread(recipientId, Instant.now());
    }

    @Transactional
    public NotificationResponse markRead(UUID recipientId, UUID notificationId) {
        Notification notification = visible(recipientId, notificationId);
        notification.markRead();
        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID recipientId) {
        return notificationRepository.markAllRead(recipientId, Instant.now());
    }

    @Transactional
    public int markGroupRead(UUID recipientId, String groupKey) {
        return notificationRepository.markGroupRead(recipientId, groupKey, Instant.now());
    }

    @Transactional
    public void delete(UUID recipientId, UUID notificationId) {
        visible(recipientId, notificationId).softDelete();
    }

    @Transactional(readOnly = true)
    public List<NotificationDispatchResponse> listDispatches(UUID recipientId, UUID notificationId) {
        Notification notification = visible(recipientId, notificationId);
        return dispatchRepository.findAllByNotificationIdOrderByCreatedAtDesc(notification.getId()).stream()
                .map(notificationDispatchMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationDispatchResponse> listDispatchesByCorrelation(String correlationId) {
        return dispatchRepository.findAllByCorrelationIdOrderByCreatedAtDesc(correlationId).stream()
                .map(notificationDispatchMapper::toResponse)
                .toList();
    }

    private Notification visible(UUID recipientId, UUID notificationId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientIdAndDeletedAtIsNull(notificationId, recipientId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (!notification.isVisibleAt(Instant.now())) {
            throw new ResourceNotFoundException("Notification", notificationId);
        }
        return notification;
    }

    private Pageable applySort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException(
                        "Notifications cannot be sorted by '%s'; sortable fields are %s"
                                .formatted(order.getProperty(), SORTABLE));
            }
        }
        return pageable;
    }

    private void validateSeverity(String severity) {
        if (severity != null && !NotificationSeverity.ALL.contains(severity)) {
            throw new BadRequestException("Unknown severity '%s'; expected one of %s"
                    .formatted(severity, NotificationSeverity.ALL));
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("The 'from' instant cannot be after the 'to' instant");
        }
    }
}
