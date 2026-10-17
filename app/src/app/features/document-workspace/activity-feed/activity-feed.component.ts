import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { WorkspaceActivityItem } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { CheckboxComponent } from '@shared/ui/checkbox/checkbox.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { IconName } from '@shared/ui/icon/icons';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { WorkspaceActivityService } from '../data/workspace-extras.service';
import { WorkspaceDatePipe } from '../ui/workspace-date.pipe';

type Tone = 'success' | 'accent' | 'warning' | 'danger' | 'neutral';

const VISUALS: Record<string, { icon: IconName; tone: Tone }> = {
  'artifact.created': { icon: 'plus-circle', tone: 'success' },
  'artifact.updated': { icon: 'edit', tone: 'accent' },
  'artifact.moved': { icon: 'folder', tone: 'accent' },
  'artifact.duplicated': { icon: 'copy', tone: 'accent' },
  'artifact.trashed': { icon: 'trash', tone: 'warning' },
  'artifact.restored': { icon: 'history', tone: 'success' },
  'document.content-updated': { icon: 'edit', tone: 'accent' },
  'document.version-restored': { icon: 'history', tone: 'accent' },
  'file.uploaded': { icon: 'upload', tone: 'success' },
  'file.version-added': { icon: 'upload', tone: 'accent' },
  'comment.created': { icon: 'message-circle', tone: 'success' },
  'comment.resolved': { icon: 'check-circle', tone: 'success' },
  'workspace.updated': { icon: 'settings', tone: 'accent' },
  'workspace.ownership-transferred': { icon: 'swap', tone: 'warning' },
  'workspace-member.added': { icon: 'users', tone: 'success' },
  'workspace-member.removed': { icon: 'users', tone: 'danger' },
  'workspace-member.role-changed': { icon: 'shield', tone: 'accent' },
};

interface Row {
  readonly item: WorkspaceActivityItem;
  readonly actor: string;
  readonly label: string;
  readonly visual: { icon: IconName; tone: Tone };
}

/** What other people did lately in any workspace the user belongs to, newest first. */
@Component({
  selector: 'app-activity-feed',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AvatarComponent,
    CheckboxComponent,
    EmptyStateComponent,
    IconComponent,
    PageHeaderComponent,
    SpinnerComponent,
    RelativeTimePipe,
    WorkspaceDatePipe,
    TranslatePipe,
  ],
  template: `
    <div class="act app-container">
      <app-page-header [title]="'nav.workspaceActivity' | t" [subtitle]="'workspaces.activity.subtitle' | t" />

      <div class="feed__options">
        <app-checkbox [formControl]="includeOwn" [label]="'workspaces.activity.includeOwn' | t" />
      </div>

      @if (loading()) {
        <div class="act__loading"><app-spinner /></div>
      } @else if (rows().length === 0) {
        <app-empty-state icon="clock" [title]="'workspaces.activity.empty' | t" [description]="'workspaces.activity.emptyHint' | t" />
      } @else {
        <ul class="act__list">
          @for (row of rows(); track row.item.id) {
            <li class="act__row">
              <span class="act__icon act__icon--{{ row.visual.tone }}"><app-icon [name]="row.visual.icon" [size]="18" /></span>
              <div class="act__text">
                <span class="act__label">{{ row.label }}</span>
                <span class="act__detail">
                  @if (row.item.resourceName) {
                    {{ row.item.resourceName }} ·
                  }
                  <a class="feed__ws" [routerLink]="['/document-workspaces', row.item.workspaceId]">{{ row.item.workspaceName }}</a>
                </span>
              </div>
              <div class="act__who">
                <app-avatar [name]="row.actor" [size]="24" />
                <span class="act__actor">{{ row.actor }}</span>
              </div>
              <time class="act__time" [attr.datetime]="row.item.occurredAt" [title]="row.item.occurredAt | wsDate">
                {{ row.item.occurredAt | relativeTime }}
              </time>
            </li>
          }
        </ul>
      }
    </div>
  `,
  styleUrl: '../activity/workspace-activity.component.scss',
  styles: [
    `
      .feed__options { margin-bottom: var(--space-md); }
      .feed__ws { color: var(--accent); text-decoration: none; font-weight: 600; }
      .feed__ws:hover { text-decoration: underline; }
    `,
  ],
})
export class ActivityFeedComponent implements OnInit {
  private readonly activity = inject(WorkspaceActivityService);
  private readonly i18n = inject(TranslationService);

  readonly includeOwn = new FormControl(false, { nonNullable: true });
  readonly items = signal<WorkspaceActivityItem[]>([]);
  readonly loading = signal(true);

  readonly rows = computed<Row[]>(() =>
    this.items().map((item) => {
      const key = `admin.audit.action.${item.action}`;
      const translated = this.i18n.translate(key);
      return {
        item,
        actor: item.actorName || item.actorEmail || this.i18n.translate('activity.system'),
        label: translated !== key ? translated : item.action,
        visual: VISUALS[item.action] ?? { icon: 'clock', tone: 'neutral' },
      };
    }),
  );

  ngOnInit(): void {
    this.load();
    this.includeOwn.valueChanges.subscribe(() => this.load());
  }

  private load(): void {
    this.loading.set(true);
    this.activity.recent(50, this.includeOwn.value).subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
