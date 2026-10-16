package com.sophia.authentication.domain.entity;

import com.sophia.authentication.domain.entity.enums.AuthenticationProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String password;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthenticationProvider authenticationProvider;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
    private UserPreferences preferences;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> directPermissions;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private Set<OAuthAccount> oauthAccounts;

    public static User create(String firstName, String lastName, String email, String password,
                              AuthenticationProvider authenticationProvider, boolean active, boolean emailVerified,
                              Set<Role> roles) {
        User user = new User();
        user.firstName = firstName;
        user.lastName = lastName;
        user.email = email;
        user.password = password;
        user.authenticationProvider = authenticationProvider;
        user.active = active;
        user.emailVerified = emailVerified;
        user.roles = roles;
        user.directPermissions = new HashSet<>();
        user.oauthAccounts = new HashSet<>();
        return user;
    }

    public String getFullName() {
        String first = firstName == null ? "" : firstName;
        String last = lastName == null ? "" : lastName;
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public Set<String> getEffectivePermissions() {
        return Stream.concat(
                        roles.stream().flatMap(role -> role.getPermissions().stream()),
                        directPermissions.stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }

    public boolean isGoogleLinked() {
        return oauthAccounts.stream()
                .anyMatch(account -> account.getProvider() == AuthenticationProvider.GOOGLE);
    }

    public String getGoogleEmail() {
        return oauthAccounts.stream()
                .filter(account -> account.getProvider() == AuthenticationProvider.GOOGLE)
                .map(OAuthAccount::getEmail)
                .findFirst()
                .orElse(null);
    }

    public void assignPreferences(UserPreferences preferences) {
        this.preferences = preferences;
    }

    public void recordFailedLoginAttempt() {
        this.failedLoginAttempts = this.failedLoginAttempts + 1;
    }

    public void lock(Instant until) {
        this.lockedUntil = until;
        this.failedLoginAttempts = 0;
    }

    public void unlock() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void unverifyEmail() {
        this.emailVerified = false;
    }

    public void changeEmail(String newEmail) {
        this.email = newEmail;
    }

    public void updateProfile(String firstName, String lastName) {
        if (firstName != null) {
            this.firstName = firstName;
        }
        if (lastName != null) {
            this.lastName = lastName;
        }
    }

    public void updateActiveStatus(boolean active) {
        this.active = active;
    }

    public void softDelete(String deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    public void updateRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public void updateDirectPermissions(Set<Permission> permissions) {
        this.directPermissions = permissions;
    }
}
