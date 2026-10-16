package com.sophia.authentication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "remember_me", nullable = false)
    private boolean rememberMe;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static UserSession create(User user, String ipAddress, String userAgent, String deviceName,
                                     boolean rememberMe, Instant lastActiveAt, Instant expiresAt) {
        UserSession session = new UserSession();
        session.user = user;
        session.ipAddress = ipAddress;
        session.userAgent = userAgent;
        session.deviceName = deviceName;
        session.rememberMe = rememberMe;
        session.lastActiveAt = lastActiveAt;
        session.expiresAt = expiresAt;
        return session;
    }

    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    public void touch(String ipAddress) {
        lastActiveAt = Instant.now();
        if (ipAddress != null) {
            this.ipAddress = ipAddress;
        }
    }

    public void revoke() {
        revokedAt = Instant.now();
    }
}
