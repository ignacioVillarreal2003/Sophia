package com.sophia.authentication.domain.entity;

import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "oauth_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "ux_oauth_accounts_provider_user", columnNames = {"provider", "provider_user_id"}),
        @UniqueConstraint(name = "ux_oauth_accounts_user_provider", columnNames = {"user_id", "provider"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthenticationProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    public static OAuthAccount create(User user, AuthenticationProvider provider, String providerUserId,
                                      String email, String name) {
        OAuthAccount account = new OAuthAccount();
        account.user = user;
        account.provider = provider;
        account.providerUserId = providerUserId;
        account.email = email;
        account.name = name;
        account.linkedAt = Instant.now();
        return account;
    }

    public void refreshProviderData(String email, String name) {
        this.email = email;
        this.name = name;
    }
}
