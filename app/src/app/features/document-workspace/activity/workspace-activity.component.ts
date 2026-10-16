import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DocumentWorkspaceMember, WorkspaceAuditEntry } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { IconName } from '@shared/ui/icon/icons';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { WorkspaceDatePipe } from '../ui/workspace-date.pipe';

type Tone = 'success' | 'accent' | 'warning' | 'danger' | 'neutral';

interface Visual {
  readonly icon: IconName;
  readonly tone: Tone;
}

const VISUALS: Record<string, Visual> = {
  'workspace.created': { icon: 'plus-circle', tone: 'success' },
  'workspace.updated': { icon: 'edit', tone: 'accent' },
  'workspace.settings-updated': { icon: 'settings', tone: 'accent' },
  'workspace.suspended': { icon: 'lock', tone: 'warning' },
  'workspace.reactivated': { icon: 'check-circle', tone: 'success' },
  'workspace.restored': { icon: 'history', tone: 'success' },
  'workspace.deleted': { icon: 'trash', tone: 'danger' },
  'workspace.purged': { icon: 'trash', tone: 'danger' },
  'workspace.status-changed': { icon: 'lock', tone: 'warning' },
  'workspace.limits-updated': { icon: 'database', tone: 'accent' },
  'workspace.ownership-transferred': { icon: 'users', tone: 'warning' },
  'workspace-member.added': { icon: 'users', tone: 'success' },
  'workspace-member.removed': { icon: 'users', tone: 'danger' },
  'workspace-member.role-changed': { icon: 'shield', tone: 'accent' },
  'artifact.created': { icon: 'plus-circle', tone: 'success' },
  'artifact.updated': { icon: 'edit', tone: 'accent' },
  'artifact.moved': { icon: 'folder', tone: 'accent' },
  'artifact.duplicated': { icon: 'copy', tone: 'accent' },
  'artifact.trashed': { icon: 'trash', tone: 'warning' },
  'artifact.restored': { icon: 'history', tone: 'success' },
  'artifact.purged': { icon: 'trash', tone: 'danger' },
  'document.content-updated': { icon: 'edit', tone: 'accent' },
  'file.uploaded': { icon: 'upload', tone: 'success' },
  'comment.created': { icon: 'message-circle', tone: 'success' },
  'comment.deleted': { icon: 'message-circle', tone: 'danger' },
  'resource-permission.acl-granted': { icon: 'share', tone: 'success' },
  'resource-permission.acl-revoked': { icon: 'share', tone: 'danger' },
  'resource-permission.relabelled': { icon: 'shield', tone: 'warning' },
};

const FALLBACK: Visual = { icon: 'clock', tone: 'neutral' };

interface Row {
  readonly id: string;
  readonly actor: string;
  readonly photoUrl: string | null;
  readonly label: string;
  readonly detail: string;
  readonly visual: Visual;
  readonly createdAt: string;
  readonly failed: boolean;
}

interface Day {
  readonly key: string;
  readonly date: Date;
  readonly rows: Row[];
}

/** Activity feed of one workspace: who did what, newest first, grouped by day. */
@Component({
  selector: 'app-workspace-activity',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WorkspaceDatePipe,
    AvatarComponent,
    EmptyStateComponent,
    IconComponent,
    PageHeaderComponent,
    PaginationComponent,
    SpinnerComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './workspace-activity.component.html',
  styleUrl: './workspace-activity.component.scss',
})
export class WorkspaceActivityComponent implements OnInit {
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly i18n = inject(TranslationService);

  /** Route param. */
  readonly documentWorkspaceId = input.required<string>();

  readonly entries = signal<WorkspaceAuditEntry[]>([]);
  readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly loading = signal(true);
  readonly forbidden = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly first = signal(true);
  readonly last = signal(true);

  readonly days = computed<Day[]>(() => {
    const byDay = new Map<string, Day>();
    for (const entry of this.entries()) {
      const date = new Date(entry.createdAt);
      const key = `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;
      let day = byDay.get(key);
      if (!day) {
        day = { key, date, rows: [] };
        byDay.set(key, day);
      }
      day.rows.push(this.toRow(entry));
    }
    return [...byDay.values()];
  });

  ngOnInit(): void {
    this.workspaces.members(this.documentWorkspaceId()).subscribe({ next: (list) => this.members.set(list) });
    this.load();
  }

  onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.workspaces.audit(this.documentWorkspaceId(), { page: this.page(), size: 20, sort: 'createdAt,desc' }).subscribe({
      next: (result) => {
        this.entries.set(result.content);
        this.totalPages.set(result.totalPages);
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: (err) => {
        this.forbidden.set(err?.status === 403);
        this.loading.set(false);
      },
    });
  }

  /** "Today" / "Yesterday" for the two most recent days; the full date otherwise. */
  dayLabel(day: Day): string | null {
    const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    const diff = Math.round((startOfDay(new Date()) - startOfDay(day.date)) / 86_400_000);
    if (diff === 0) return this.i18n.translate('activity.today');
    if (diff === 1) return this.i18n.translate('activity.yesterday');
    return null;
  }

  private toRow(entry: WorkspaceAuditEntry): Row {
    const member = this.members().find((m) => m.userId === entry.userId);
    const actor = member?.fullName || member?.email || entry.userEmail || this.i18n.translate('activity.system');
    const key = `admin.audit.action.${entry.action}`;
    const translated = this.i18n.translate(key);
    const details = entry.details ?? {};
    const name = typeof details['name'] === 'string' ? (details['name'] as string) : '';
    const type = typeof details['type'] === 'string' ? (details['type'] as string) : '';
    return {
      id: entry.id,
      actor,
      photoUrl: member?.photoUrl ?? null,
      label: translated !== key ? translated : entry.action,
      detail: [name, type ? this.typeLabel(type) : ''].filter(Boolean).join(' · '),
      visual: VISUALS[entry.action] ?? FALLBACK,
      createdAt: entry.createdAt,
      failed: entry.result === 'FAILURE',
    };
  }

  private typeLabel(type: string): string {
    const key = `newItem.type.${type}`;
    const label = this.i18n.translate(key);
    return label !== key ? label : type.toLowerCase().replace(/_/g, ' ');
  }
}
