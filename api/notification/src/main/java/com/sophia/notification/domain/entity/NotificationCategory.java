package com.sophia.notification.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "notification_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationCategory {

    @Id
    @Column(name = "code", length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_by")
    private UUID createdBy;

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

    public static NotificationCategory create(String code, String name, String description, boolean mandatory,
                                               UUID createdBy) {
        return create(code, name, description, mandatory, false, createdBy);
    }

    public static NotificationCategory create(String code, String name, String description, boolean mandatory,
                                               boolean system, UUID createdBy) {
        NotificationCategory category = new NotificationCategory();
        category.code = code;
        category.name = name;
        category.description = description;
        category.mandatory = mandatory;
        category.system = system;
        category.createdBy = createdBy;
        category.updatedBy = createdBy;
        return category;
    }

    public void update(String name, String description, Boolean mandatory, UUID updatedBy) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (mandatory != null) {
            this.mandatory = mandatory;
        }
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
        NotificationCategory that = (NotificationCategory) other;
        return code != null && Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
