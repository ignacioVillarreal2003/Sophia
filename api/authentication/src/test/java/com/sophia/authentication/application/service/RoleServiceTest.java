package com.sophia.authentication.application.service;

import com.sophia.authentication.application.mapper.RoleMapper;
import com.sophia.authentication.application.service.AuditService;
import com.sophia.authentication.application.service.RoleService;
import com.sophia.authentication.domain.dto.request.RoleRequest;
import com.sophia.authentication.domain.dto.response.BulkOperationResponse;
import com.sophia.authentication.domain.dto.response.PageResponse;
import com.sophia.authentication.domain.dto.response.RoleResponse;
import com.sophia.authentication.domain.entity.Permission;
import com.sophia.authentication.domain.entity.Role;
import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.DuplicateResourceException;
import com.sophia.authentication.domain.exception.OperationNotAllowedException;
import com.sophia.authentication.infrastructure.repository.PermissionRepository;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.support.TestFixtures;
import com.sophia.authentication.support.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private RoleService service;

    @BeforeEach
    void setUp() {
        service = new RoleService(roleRepository, permissionRepository, userRepository,
                Mappers.getMapper(RoleMapper.class), auditService);
    }

    @Test
    @DisplayName("list uses the summary mapping without permissions")
    void listUsesSummaryMapping() {
        Role role = TestFixtures.role("SUPPORT_AGENT", "USER_READ");
        Pageable pageable = PageRequest.of(0, 20);
        when(roleRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(role), pageable, 1));

        PageResponse<RoleResponse> page = service.list(null, pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().name()).isEqualTo("SUPPORT_AGENT");
        assertThat(page.content().getFirst().permissions()).isEmpty();
    }

    @Test
    @DisplayName("get returns the role with its permissions")
    void getReturnsPermissions() {
        Role role = TestFixtures.role("SUPPORT_AGENT", "USER_READ");
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        RoleResponse response = service.get(role.getId());

        assertThat(response.permissions()).containsExactly("USER_READ");
    }

    @Test
    @DisplayName("creates a role resolving its permissions")
    void createsRole() {
        Permission permission = TestIds.withRandomId(Permission.create("USER_READ", null, null));
        when(roleRepository.existsByNameIgnoreCase("SUPPORT_AGENT")).thenReturn(false);
        when(permissionRepository.findByNameIn(Set.of("USER_READ"))).thenReturn(List.of(permission));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation ->
                TestIds.withRandomId(invocation.getArgument(0)));

        RoleResponse response = service.create(
                new RoleRequest("SUPPORT_AGENT", "Support staff", Set.of("USER_READ")));

        assertThat(response.name()).isEqualTo("SUPPORT_AGENT");
        assertThat(response.system()).isFalse();
        assertThat(response.permissions()).containsExactly("USER_READ");
    }

    @Test
    @DisplayName("rejects duplicate role names")
    void rejectsDuplicateName() {
        when(roleRepository.existsByNameIgnoreCase("SUPPORT_AGENT")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new RoleRequest("SUPPORT_AGENT", null, Set.of())))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("rejects unknown permissions")
    void rejectsUnknownPermissions() {
        when(roleRepository.existsByNameIgnoreCase("SUPPORT_AGENT")).thenReturn(false);
        when(permissionRepository.findByNameIn(Set.of("NOPE"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new RoleRequest("SUPPORT_AGENT", null, Set.of("NOPE"))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("system roles cannot be renamed or deleted")
    void protectsSystemRoles() {
        Role systemRole = TestFixtures.role("ADMIN");
        when(roleRepository.findById(systemRole.getId())).thenReturn(Optional.of(systemRole));

        assertThatThrownBy(() -> service.update(systemRole.getId(),
                new RoleRequest("RENAMED", null, Set.of())))
                .isInstanceOf(OperationNotAllowedException.class);
        assertThatThrownBy(() -> service.delete(systemRole.getId()))
                .isInstanceOf(OperationNotAllowedException.class);
        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    @DisplayName("a role assigned to users cannot be deleted")
    void protectsAssignedRoles() {
        Role role = TestIds.withRandomId(Role.create("SUPPORT_AGENT", null, false, Set.of()));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));
        when(userRepository.countByRoles_Id(role.getId())).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(role.getId()))
                .isInstanceOf(OperationNotAllowedException.class);
    }

    @Test
    @DisplayName("bulk delete reports per-id failures without aborting the batch")
    void bulkDeleteReportsFailures() {
        Role deletable = TestIds.withRandomId(Role.create("DELETABLE", null, false, Set.of()));
        Role systemRole = TestFixtures.role("ADMIN");
        when(roleRepository.findById(deletable.getId())).thenReturn(Optional.of(deletable));
        when(roleRepository.findById(systemRole.getId())).thenReturn(Optional.of(systemRole));
        when(userRepository.countByRoles_Id(deletable.getId())).thenReturn(0L);

        BulkOperationResponse response = service.bulkDelete(List.of(deletable.getId(), systemRole.getId()));

        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failures()).hasSize(1);
        verify(roleRepository).delete(deletable);
    }
}
