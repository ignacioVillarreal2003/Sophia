package com.sophia.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_category_channel_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationCategoryChannelPreference {

    @EmbeddedId
    private NotificationCategoryChannelPreferenceId id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public static NotificationCategoryChannelPreference create(NotificationCategoryChannelPreferenceId id,
                                                                boolean enabled) {
        NotificationCategoryChannelPreference preference = new NotificationCategoryChannelPreference();
        preference.id = id;
        preference.enabled = enabled;
        return preference;
    }

    public void update(boolean enabled, UUID updatedBy) {
        this.enabled = enabled;
        this.updatedBy = updatedBy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        NotificationCategoryChannelPreference that = (NotificationCategoryChannelPreference) other;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
