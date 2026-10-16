package com.sophia.authentication.infrastructure.repository;

import com.sophia.authentication.domain.entity.ServiceClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ServiceClientRepository extends JpaRepository<ServiceClient, UUID>, JpaSpecificationExecutor<ServiceClient> {

    boolean existsByNameIgnoreCase(String name);
}
