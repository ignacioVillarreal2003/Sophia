import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AccessRequest, DocumentWorkspaceMember, DocumentWorkspaceRole } from '@core/models/document-workspace';
import { AppError } from '@core/models/api.model';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { WorkspaceRequestsService } from '../data/workspace-extras.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { BadgeComponent } from '@shared/ui/badge/badge.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';

const ALL_ROLES: DocumentWorkspaceRole[] = ['ADMIN', 'EDITOR', 'VIEWER'];
const SWAPPABLE_ROLES: DocumentWorkspaceRole[] = ['EDITOR', 'VIEWER'];

/**
 * Members of a workspace. Permissions mirror the API: only ADMINs add members; the owner can give
 * anyone any role and suspend/reactivate; other admins only toggle EDITOR <-> VIEWER.
 */
@Component({
  selector: 'app-members',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    AvatarComponent,
    BadgeComponent,
    IconComponent,
    SpinnerComponent,
    PageHeaderComponent,
    TextFieldComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './members.component.html',
  styleUrl: './members.component.scss',
})
export class MembersComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(DocumentWorkspaceService);
  private readonly requestsService = inject(WorkspaceRequestsService);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly i18n = inject(TranslationService);

  /** Bound from the route param. */
  readonly documentWorkspaceId = input.required<string>();

  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly loading = signal(true);
  readonly adding = signal(false);
  readonly roles = ALL_ROLES;

  private readonly workspace = computed(() => this.service.active());
  readonly ownerId = computed(() => this.workspace()?.ownerId ?? null);
  readonly isOwner = computed(() => !!this.ownerId() && this.ownerId() === this.auth.user()?.id);
  readonly isAdmin = computed(() => this.isOwner() || this.workspace()?.myRole === 'ADMIN');

  readonly addForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role: ['EDITOR' as DocumentWorkspaceRole, [Validators.required]],
  });

  /** People who asked to join (admins only), oldest first. */
  readonly requests = signal<AccessRequest[]>([]);
  readonly requestRoles = signal<Record<string, DocumentWorkspaceRole>>({});
  readonly busyRequest = signal<string | null>(null);
  private requestsLoaded = false;

  constructor() {
    // The role (and so admin-ness) arrives with the workspace, which may load after this page opens.
    effect(() => {
      if (this.isAdmin() && !untracked(() => this.requestsLoaded)) {
        this.requestsLoaded = true;
        untracked(() => this.loadRequests());
      }
    });
  }

  readonly selectedRole = toSignal(this.addForm.controls.role.valueChanges, {
    initialValue: this.addForm.controls.role.value,
  });

  ngOnInit(): void {
    this.reload();
  }

  private loadRequests(): void {
    this.requestsService.pendingAccess(this.documentWorkspaceId()).subscribe({
      next: (list) => this.requests.set(list),
      error: () => undefined,
    });
  }

  requestRole(request: AccessRequest): DocumentWorkspaceRole {
    return this.requestRoles()[request.id] ?? 'EDITOR';
  }

  setRequestRole(request: AccessRequest, role: string): void {
    this.requestRoles.update((map) => ({ ...map, [request.id]: role as DocumentWorkspaceRole }));
  }

  approveRequest(request: AccessRequest): void {
    this.busyRequest.set(request.id);
    this.requestsService.approveAccess(this.documentWorkspaceId(), request.id, this.requestRole(request)).subscribe({
      next: () => {
        this.busyRequest.set(null);
        this.requests.update((list) => list.filter((r) => r.id !== request.id));
        this.toast.success(this.i18n.translate('workspaces.access.approved', { email: request.email }));
        this.reload();
      },
      error: (err: AppError) => {
        this.busyRequest.set(null);
        this.toast.error(err.message);
        this.loadRequests();
      },
    });
  }

  denyRequest(request: AccessRequest): void {
    this.busyRequest.set(request.id);
    this.requestsService.denyAccess(this.documentWorkspaceId(), request.id).subscribe({
      next: () => {
        this.busyRequest.set(null);
        this.requests.update((list) => list.filter((r) => r.id !== request.id));
      },
      error: (err: AppError) => {
        this.busyRequest.set(null);
        this.toast.error(err.message);
        this.loadRequests();
      },
    });
  }

  roleLabel(role: DocumentWorkspaceRole): string {
    return this.i18n.translate(`documentWorkspace.role.${role}`);
  }

  roleDescription(role: string): string {
    return this.i18n.translate(`documentWorkspace.members.roleDesc.${role}`);
  }

  setRole(role: string): void {
    this.addForm.controls.role.setValue(role as DocumentWorkspaceRole);
  }

  emailInvalid(): boolean {
    const control = this.addForm.controls.email;
    return control.invalid && control.touched;
  }

  isOwnerMember(member: DocumentWorkspaceMember): boolean {
    return member.userId === this.ownerId();
  }

  private isMe(member: DocumentWorkspaceMember): boolean {
    return member.userId === this.auth.user()?.id;
  }

  /** Roles the current user may assign to this member; empty when the role is read-only for them. */
  assignableRoles(member: DocumentWorkspaceMember): DocumentWorkspaceRole[] {
    if (this.isOwnerMember(member) || this.isMe(member)) return [];
    if (this.isOwner()) return ALL_ROLES;
    if (this.isAdmin() && SWAPPABLE_ROLES.includes(member.role)) return SWAPPABLE_ROLES;
    return [];
  }

  canRemove(member: DocumentWorkspaceMember): boolean {
    if (this.isOwnerMember(member) || this.isMe(member)) return false;
    return this.isOwner() || (this.isAdmin() && member.role !== 'ADMIN');
  }

  canSuspend(member: DocumentWorkspaceMember): boolean {
    return this.isOwner() && !this.isOwnerMember(member) && !this.isMe(member);
  }

  private reload(): void {
    this.loading.set(true);
    this.service.members(this.documentWorkspaceId()).subscribe({
      next: (list) => {
        this.members.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private replace(updated: DocumentWorkspaceMember): void {
    this.members.update((list) => list.map((m) => (m.id === updated.id ? updated : m)));
  }

  add(): void {
    if (this.addForm.invalid) {
      this.addForm.markAllAsTouched();
      return;
    }
    this.adding.set(true);
    this.service.addMember(this.documentWorkspaceId(), this.addForm.getRawValue()).subscribe({
      next: (member) => {
        this.members.update((list) => [...list, member]);
        this.addForm.reset({ email: '', role: 'EDITOR' });
        this.adding.set(false);
        this.toast.success(this.i18n.translate('documentWorkspace.toast.memberAdded'));
      },
      error: (err: AppError) => {
        this.adding.set(false);
        this.toast.error(err.message);
      },
    });
  }

  changeRole(member: DocumentWorkspaceMember, role: string): void {
    this.service
      .updateMemberRole(this.documentWorkspaceId(), member.id, role as DocumentWorkspaceRole)
      .subscribe({
        next: (updated) => this.replace(updated),
        error: (err: AppError) => {
          this.toast.error(err.message);
          this.members.update((list) => [...list]);
        },
      });
  }

  toggleSuspended(member: DocumentWorkspaceMember): void {
    const request =
      member.status === 'SUSPENDED'
        ? this.service.reactivateMember(this.documentWorkspaceId(), member.id)
        : this.service.suspendMember(this.documentWorkspaceId(), member.id);
    request.subscribe({
      next: (updated) => this.replace(updated),
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  async removeMember(member: DocumentWorkspaceMember): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('documentWorkspace.confirmRemoveMember.title'),
      message: this.i18n.translate('documentWorkspace.confirmRemoveMember.message', { name: member.fullName || member.email }),
      confirmLabel: this.i18n.translate('documentWorkspace.confirmRemoveMember.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.service.removeMember(this.documentWorkspaceId(), member.id).subscribe({
      next: () => this.members.update((list) => list.filter((m) => m.id !== member.id)),
      error: (err: AppError) => this.toast.error(err.message),
    });
  }
}
