package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.WorkspaceAccessCache;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.dto.request.AddMemberRequest;
import com.sophia.documentworkspace.domain.entity.Workspace;
import com.sophia.documentworkspace.domain.entity.WorkspaceInvitation;
import com.sophia.documentworkspace.domain.entity.WorkspaceMember;
import com.sophia.documentworkspace.domain.entity.enums.InvitationStatus;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.entity.enums.WorkspaceRole;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ConflictException;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.infrastructure.client.UserDirectoryClient;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceInvitationRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceMemberRepository;
import com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkspaceInvitationServiceTest {

    @Mock WorkspaceInvitationRepository invitations;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock WorkspaceMemberRepository memberRepository;
    @Mock WorkspaceAuthorizationService authorization;
    @Mock WorkspaceAccessCache accessCache;
    @Mock UserDirectoryClient userDirectory;
    @Mock DomainEventPublisher eventPublisher;
    @Mock WorkspaceNotifier notifier;
    @Mock com.sophia.documentworkspace.infrastructure.repository.InvitationSearch search;

    WorkspaceInvitationService service;

    final UUID workspaceId = UUID.randomUUID();
    final UUID adminId = UUID.randomUUID();
    final UUID inviteeId = UUID.randomUUID();
    final AuthenticatedUser admin = new AuthenticatedUser(adminId, "admin@x.com", Set.of(), Set.of());
    final AuthenticatedUser invitee = new AuthenticatedUser(inviteeId, "ana@x.com", Set.of(), Set.of());
    Workspace workspace;

    @BeforeEach
    void setUp() {
        service = new WorkspaceInvitationService(invitations, workspaceRepository, memberRepository, authorization,
                accessCache, userDirectory, eventPublisher, notifier, search);
        workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(workspaceId);
        when(workspace.getName()).thenReturn("Team");
        when(workspace.isActive()).thenReturn(true);
        when(workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)).thenReturn(Optional.of(workspace));
        when(userDirectory.findByEmail("ana@x.com"))
                .thenReturn(Optional.of(new UserDirectoryClient.UserSummary(inviteeId, "ana@x.com", "Ana", null)));
        when(invitations.save(any(WorkspaceInvitation.class))).thenAnswer(a -> a.getArgument(0));
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(a -> a.getArgument(0));
    }

    private WorkspaceInvitation pending() {
        return WorkspaceInvitation.create(workspaceId, inviteeId, "ana@x.com", "Ana", WorkspaceRole.EDITOR,
                adminId, "admin@x.com");
    }

    @Test
    void inviting_creates_a_pending_invitation_without_adding_a_member() {
        var response = service.invite(admin, workspaceId, new AddMemberRequest("ana@x.com", WorkspaceRole.EDITOR));

        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(response.role()).isEqualTo(WorkspaceRole.EDITOR);
        verify(authorization).requirePermission(workspaceId, admin, WorkspacePermission.MANAGE_MEMBERS);
        verify(memberRepository, never()).save(any());
        verify(notifier).notify(any(), eq(inviteeId), eq(adminId), any(), any(), eq(workspaceId));
    }

    @Test
    void inviting_a_member_or_someone_already_invited_conflicts() {
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteeId)).thenReturn(true);
        assertThatThrownBy(() -> service.invite(admin, workspaceId, new AddMemberRequest("ana@x.com", WorkspaceRole.VIEWER)))
                .isInstanceOf(ConflictException.class);

        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, inviteeId)).thenReturn(false);
        when(invitations.findFirstByWorkspaceIdAndInviteeUserIdAndStatus(workspaceId, inviteeId, InvitationStatus.PENDING))
                .thenReturn(Optional.of(pending()));
        assertThatThrownBy(() -> service.invite(admin, workspaceId, new AddMemberRequest("ana@x.com", WorkspaceRole.VIEWER)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void an_expired_invitation_is_replaced_by_a_new_one() {
        WorkspaceInvitation old = pending();
        org.springframework.test.util.ReflectionTestUtils.setField(old, "expiresAt", Instant.now().minusSeconds(60));
        when(invitations.findFirstByWorkspaceIdAndInviteeUserIdAndStatus(workspaceId, inviteeId, InvitationStatus.PENDING))
                .thenReturn(Optional.of(old));

        var response = service.invite(admin, workspaceId, new AddMemberRequest("ana@x.com", WorkspaceRole.VIEWER));

        assertThat(old.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(response.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void accepting_makes_the_invitee_a_member_with_the_invited_role() {
        WorkspaceInvitation invitation = pending();
        UUID id = UUID.randomUUID();
        when(invitations.findByIdAndInviteeUserId(id, inviteeId)).thenReturn(Optional.of(invitation));

        var response = service.accept(invitee, id);

        assertThat(response.status()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(memberRepository).save(any(WorkspaceMember.class));
        verify(accessCache).evict(workspaceId, inviteeId);
    }

    @Test
    void declining_adds_nobody() {
        WorkspaceInvitation invitation = pending();
        UUID id = UUID.randomUUID();
        when(invitations.findByIdAndInviteeUserId(id, inviteeId)).thenReturn(Optional.of(invitation));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        service.decline(invitee, id);

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.DECLINED);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void somebody_elses_or_an_answered_or_an_expired_invitation_cannot_be_accepted() {
        UUID id = UUID.randomUUID();
        when(invitations.findByIdAndInviteeUserId(id, inviteeId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(invitee, id)).isInstanceOf(ResourceNotFoundException.class);

        WorkspaceInvitation answered = pending();
        answered.answer(InvitationStatus.DECLINED);
        when(invitations.findByIdAndInviteeUserId(id, inviteeId)).thenReturn(Optional.of(answered));
        assertThatThrownBy(() -> service.accept(invitee, id)).isInstanceOf(ConflictException.class);

        WorkspaceInvitation expired = pending();
        org.springframework.test.util.ReflectionTestUtils.setField(expired, "expiresAt", Instant.now().minusSeconds(60));
        when(invitations.findByIdAndInviteeUserId(id, inviteeId)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.accept(invitee, id)).isInstanceOf(BadRequestException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void an_admin_can_withdraw_an_open_invitation_but_not_an_answered_one() {
        WorkspaceInvitation open = pending();
        UUID id = UUID.randomUUID();
        when(invitations.findByIdAndWorkspaceId(id, workspaceId)).thenReturn(Optional.of(open));
        service.revoke(admin, workspaceId, id);
        assertThat(open.getStatus()).isEqualTo(InvitationStatus.REVOKED);

        assertThatThrownBy(() -> service.revoke(admin, workspaceId, id)).isInstanceOf(ConflictException.class);
    }
}
