import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { AccessMode, AclEntry, Artifact, DocumentWorkspaceMember, KIND_BY_TYPE } from '@core/models/document-workspace';
import { ToastService } from '@core/services/toast.service';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { PermissionService } from '../data/permission.service';
import { ArtifactService } from '../data/artifact.service';
import { PublicLink, PublicLinkService } from '../data/public-link.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { BadgeComponent } from '@shared/ui/badge/badge.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { SelectFieldComponent, SelectOption } from '@shared/ui/form-field/select-field.component';

export interface ShareDialogData {
  documentWorkspaceId: string;
  artifactId: string;
}

const ALL_MODES: AccessMode[] = ['READ', 'WRITE', 'COMMENT', 'DELETE', 'SHARE'];

@Component({
  selector: 'app-share-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    ButtonComponent,
    AvatarComponent,
    BadgeComponent,
    IconComponent,
    SpinnerComponent,
    SelectFieldComponent,
    TranslatePipe,
  ],
  templateUrl: './share-dialog.component.html',
  styleUrl: './share-dialog.component.scss',
})
export class ShareDialogComponent implements OnInit {
  readonly ref = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<ShareDialogData>(DIALOG_DATA);
  private readonly permissions = inject(PermissionService);
  private readonly artifacts = inject(ArtifactService);
  private readonly publicLinks = inject(PublicLinkService);
  private readonly documentWorkspaces = inject(DocumentWorkspaceService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly allModes = ALL_MODES;

  modeLabel(mode: AccessMode): string {
    return this.i18n.translate(`share.mode.${mode}`);
  }

  readonly entries = signal<AclEntry[]>([]);
  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly loading = signal(true);
  readonly granting = signal(false);

  // ---- public links ----
  readonly artifact = signal<Artifact | null>(null);
  readonly links = signal<PublicLink[]>([]);
  readonly creatingLink = signal(false);
  readonly expiresInDays = signal<number | null>(7);
  /** The full URL of a link created in this session; the API never returns its token again. */
  readonly freshUrl = signal<string | null>(null);
  readonly copied = signal(false);
  readonly expiryOptions = [
    { value: 1, key: 'share.public.expiry.1' },
    { value: 7, key: 'share.public.expiry.7' },
    { value: 30, key: 'share.public.expiry.30' },
    { value: 0, key: 'share.public.expiry.never' },
  ];

  /** Whether the workspace allows public links at all (a workspace setting). */
  readonly publicEnabled = computed(() => this.documentWorkspaces.active()?.publicSharingEnabled === true);
  readonly publicEligible = computed(() => {
    const a = this.artifact();
    if (!a) return false;
    const kind = KIND_BY_TYPE[a.type];
    return kind === 'DOCUMENT';
  });

  readonly selectedUser = signal('');
  readonly selectedModes = signal<Set<AccessMode>>(new Set(['READ']));

  readonly memberOptions = computed<SelectOption[]>(() =>
    this.members().map((m) => ({ value: m.userId, label: `${m.fullName} · ${m.email}` })),
  );

  ngOnInit(): void {
    const ws = this.data.documentWorkspaceId;
    if (!this.documentWorkspaces.active() || this.documentWorkspaces.active()?.id !== ws) {
      this.documentWorkspaces.get(ws).subscribe();
    }
    this.artifacts.get(ws, this.data.artifactId).subscribe({ next: (a) => this.artifact.set(a) });
    this.loadLinks();
    this.permissions.list(this.data.documentWorkspaceId, this.data.artifactId).subscribe({
      next: (list) => {
        this.entries.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.documentWorkspaces.members(this.data.documentWorkspaceId).subscribe({
      next: (list) => this.members.set(list),
      error: () => this.members.set([]),
    });
  }

  private loadLinks(): void {
    this.publicLinks.list(this.data.documentWorkspaceId, this.data.artifactId).subscribe({
      next: (list) => this.links.set(list),
      error: () => this.links.set([]),
    });
  }

  setExpiry(value: string): void {
    this.expiresInDays.set(Number(value) || null);
  }

  createLink(): void {
    this.creatingLink.set(true);
    this.publicLinks.create(this.data.documentWorkspaceId, this.data.artifactId, this.expiresInDays()).subscribe({
      next: (link) => {
        this.creatingLink.set(false);
        this.freshUrl.set(`${location.origin}/p/${link.token}`);
        this.copied.set(false);
        this.loadLinks();
      },
      error: (err) => {
        this.creatingLink.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async copyFresh(): Promise<void> {
    const url = this.freshUrl();
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      this.copied.set(true);
    } catch {
      this.toast.error(this.i18n.translate('share.public.copyFailed'));
    }
  }

  revokeLink(link: PublicLink): void {
    this.publicLinks.revoke(this.data.documentWorkspaceId, this.data.artifactId, link.id).subscribe({
      next: () => {
        this.links.update((list) => list.filter((l) => l.id !== link.id));
        this.toast.success(this.i18n.translate('share.public.revoked'));
      },
      error: (err) => this.toast.error(err.message),
    });
  }

  toggleMode(mode: AccessMode): void {
    this.selectedModes.update((set) => {
      const next = new Set(set);
      next.has(mode) ? next.delete(mode) : next.add(mode);
      if (next.size === 0) next.add('READ');
      return next;
    });
  }

  hasMode(mode: AccessMode): boolean {
    return this.selectedModes().has(mode);
  }

  grant(): void {
    if (!this.selectedUser()) {
      this.toast.warning(this.i18n.translate('share.selectMemberWarning'));
      return;
    }
    this.granting.set(true);
    this.permissions
      .grant(this.data.documentWorkspaceId, this.data.artifactId, {
        subjectUserId: this.selectedUser(),
        modes: [...this.selectedModes()],
      })
      .subscribe({
        next: (entry) => {
          this.entries.update((list) => [entry, ...list]);
          this.selectedUser.set('');
          this.selectedModes.set(new Set(['READ']));
          this.granting.set(false);
          this.toast.success(this.i18n.translate('share.toast.granted'));
        },
        error: (err) => {
          this.granting.set(false);
          this.toast.error(err.message);
        },
      });
  }

  revoke(entry: AclEntry): void {
    this.permissions.revoke(this.data.documentWorkspaceId, this.data.artifactId, entry.id).subscribe({
      next: () => this.entries.update((list) => list.filter((e) => e.id !== entry.id)),
      error: (err) => this.toast.error(err.message),
    });
  }

  nameOf(entry: AclEntry): string {
    const member = this.members().find((m) => m.userId === entry.subjectUserId);
    return member?.fullName ?? member?.email ?? this.i18n.translate('share.fallbackUser');
  }
}
