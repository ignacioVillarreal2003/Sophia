package com.sophia.notification.application.service;

import com.sophia.notification.application.mapper.NotificationDispatchMapper;
import com.sophia.notification.application.mapper.NotificationMapper;
import com.sophia.notification.domain.dto.request.NotificationSearchRequest;
import com.sophia.notification.domain.entity.Notification;
import com.sophia.notification.domain.entity.NotificationAttributes;
import com.sophia.notification.domain.exception.BadRequestException;
import com.sophia.notification.domain.exception.ResourceNotFoundException;
import com.sophia.notification.infrastructure.repository.NotificationDispatchRepository;
import com.sophia.notification.infrastructure.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.domain.PageRequest.of;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.domain.Sort.by;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationDispatchRepository dispatchRepository;
    @Mock
    private AuditService auditService;

    private NotificationInboxService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void emptyInboxByDefault() {
        service = new NotificationInboxService(notificationRepository, dispatchRepository,
                Mappers.getMapper(NotificationMapper.class), Mappers.getMapper(NotificationDispatchMapper.class),
                auditService);
        Mockito.lenient()
                .when(notificationRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());
    }

    @Test
    void anExpiredNotificationCannotBeRead() {
        when(notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(notificationId, userId))
                .thenReturn(Optional.of(notification(null, Instant.now().minusSeconds(60))));

        assertThatThrownBy(() -> service.markRead(userId, notificationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aNotificationScheduledForLaterCannotBeRead() {
        when(notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(notificationId, userId))
                .thenReturn(Optional.of(notification(Instant.now().plus(Duration.ofHours(1)), null)));

        assertThatThrownBy(() -> service.markRead(userId, notificationId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aLiveNotificationIsReadable() {
        when(notificationRepository.findByIdAndRecipientIdAndDeletedAtIsNull(notificationId, userId))
                .thenReturn(Optional.of(notification(null, Instant.now().plusSeconds(60))));

        assertThat(service.markRead(userId, notificationId).read()).isTrue();
    }

    @Test
    void anInboxWithoutFiltersIsSearchable() {
        service.search(userId, request(null, null), of(0, 20));

        verify(notificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void theDefaultOrderIsNewestFirst() {
        service.search(userId, request(null, null), of(0, 20));

        assertThat(capturedPageable().getSort().getOrderFor("createdAt"))
                .isNotNull()
                .satisfies(order -> assertThat(order.isDescending()).isTrue());
    }

    @Test
    void aSortRequestedByTheClientIsHonoured() {
        service.search(userId, request(null, null), of(0, 20, by(ASC, "priority")));

        assertThat(capturedPageable().getSort().getOrderFor("priority"))
                .isNotNull()
                .satisfies(order -> assertThat(order.isAscending()).isTrue());
    }

    @Test
    void sortingByAnArbitraryFieldIsRejected() {
        assertThatThrownBy(() -> service.search(userId, request(null, null), of(0, 20, by("recipientId"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("recipientId");
    }

    @Test
    void anUnknownSeverityIsRejected() {
        assertThatThrownBy(() -> service.search(userId, request("NUCLEAR", null), of(0, 20)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("NUCLEAR");
    }

    @Test
    void anInvertedDateRangeIsRejected() {
        NotificationSearchRequest request = new NotificationSearchRequest(
                false, null, null, null, null, Instant.now(), Instant.now().minusSeconds(60), null);

        assertThatThrownBy(() -> service.search(userId, request, of(0, 20)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("from");
    }

    private NotificationSearchRequest request(String severity, String search) {
        return new NotificationSearchRequest(false, null, null, severity, null, null, null, search);
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    private Notification notification(Instant scheduledAt, Instant expiresAt) {
        return Notification.of(userId, null, "SYSTEM", "SYSTEM", "Title", null, Map.of(),
                new NotificationAttributes(null, null, null, null, null, expiresAt), scheduledAt);
    }
}
