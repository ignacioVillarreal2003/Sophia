package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByName(String name);

    List<Role> findByNameIn(Set<String> names);

    boolean existsByNameIgnoreCase(String name);
}
