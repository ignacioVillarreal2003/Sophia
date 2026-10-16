package com.sophia.authentication.domain.entity;

import com.sophia.authentication.domain.entity.enums.Theme;
import com.sophia.authentication.domain.entity.enums.ThemeMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme", nullable = false, length = 20)
    private Theme theme;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false, length = 10)
    private ThemeMode themeMode;

    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    /**
     * Modules the user has taken off the home screen, held as a deny list so a
     * module added to the catalogue later shows up for existing users instead of
     * staying invisible until they opt in. Slugs are owned by the client: this
     * service has no catalogue of its own, so it validates their shape and count
     * but never their membership.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "hidden_modules", nullable = false)
    private List<String> hiddenModules;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserPreferences that = (UserPreferences) o;
        return userId != null && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public static UserPreferences create(User user, String photoUrl) {
        UserPreferences preferences = new UserPreferences();
        preferences.user = user;
        preferences.photoUrl = photoUrl;
        preferences.theme = Theme.CLAUDE;
        preferences.themeMode = ThemeMode.LIGHT;
        preferences.locale = "en-US";
        preferences.timezone = "UTC";
        preferences.hiddenModules = new ArrayList<>();
        return preferences;
    }

    public void updatePhoto(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    /**
     * A null list leaves the selection untouched; an empty one clears it. The two
     * have to stay distinguishable, which is why this is not folded into
     * {@link #updateSettings} alongside the ignore-null scalars.
     */
    public void updateHiddenModules(List<String> hiddenModules) {
        if (hiddenModules != null) {
            this.hiddenModules = new ArrayList<>(hiddenModules);
        }
    }

    public void updateSettings(Theme theme, ThemeMode themeMode, String locale, String timezone) {
        if (theme != null) {
            this.theme = theme;
        }
        if (themeMode != null) {
            this.themeMode = themeMode;
        }
        if (locale != null) {
            this.locale = locale;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
    }
}
