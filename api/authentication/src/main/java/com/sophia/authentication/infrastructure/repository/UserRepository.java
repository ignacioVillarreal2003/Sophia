package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "directPermissions", "preferences", "oauthAccounts"})
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailWithAuthorities(@Param("email") String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions", "directPermissions", "preferences", "oauthAccounts"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithAuthorities(@Param("id") UUID id);

    long countByRoles_Id(UUID roleId);

    long countByDirectPermissions_Id(UUID permissionId);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts WHERE u.id = :id")
    int updateFailedLoginAttempts(@Param("id") UUID id, @Param("attempts") int attempts);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = :lockedUntil WHERE u.id = :id")
    int lockAccount(@Param("id") UUID id, @Param("lockedUntil") Instant lockedUntil);
}
