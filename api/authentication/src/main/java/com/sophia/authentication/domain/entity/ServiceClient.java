package com.sophia.authentication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_clients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceClient extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "secret_hash", nullable = false, length = 100)
    private String secretHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scopes", nullable = false)
    private List<String> scopes;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public static ServiceClient create(String name, String description, String secretHash, List<String> scopes,
                                       boolean active) {
        ServiceClient client = new ServiceClient();
        client.name = name;
        client.description = description;
        client.secretHash = secretHash;
        client.scopes = scopes != null ? scopes : new ArrayList<>();
        client.active = active;
        return client;
    }

    public void recordUsage() {
        this.lastUsedAt = Instant.now();
    }

    public void update(String name, String description, List<String> scopes, Boolean active) {
        this.name = name;
        this.description = description;
        this.scopes = scopes;
        if (active != null) {
            this.active = active;
        }
    }

    public void rotateSecret(String secretHash) {
        this.secretHash = secretHash;
    }
}
