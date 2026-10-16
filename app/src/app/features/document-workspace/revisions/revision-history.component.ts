import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { DocumentVersion } from '@core/models/document-workspace';
import { ToastService } from '@core/services/toast.service';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { DocumentContentService } from '../data/document-content.service';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';

interface DiffLine {
  readonly kind: 'add' | 'remove' | 'same';
  readonly text: string;
}

/** Line diff (longest common subsequence), bounded so huge documents stay responsive. */
export function diffLines(before: string, after: string, limit = 2000): DiffLine[] {
  const a = before.split('\n').slice(0, limit);
  const b = after.split('\n').slice(0, limit);
  const table: number[][] = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0));
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      table[i][j] = a[i] === b[j] ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1]);
    }
  }
  const out: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      out.push({ kind: 'same', text: a[i] });
      i++;
      j++;
    } else if (table[i + 1][j] >= table[i][j + 1]) {
      out.push({ kind: 'remove', text: a[i++] });
    } else {
      out.push({ kind: 'add', text: b[j++] });
    }
  }
  while (i < a.length) out.push({ kind: 'remove', text: a[i++] });
  while (j < b.length) out.push({ kind: 'add', text: b[j++] });
  return out;
}

/** Version history of a document: browse snapshots, compare them with the current text, name and restore. */
@Component({
  selector: 'app-revision-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SpinnerComponent, EmptyStateComponent, RelativeTimePipe, TranslatePipe],
  template: `
    <div class="rh">
      <header class="rh__head">
        <h3 class="rh__title"><app-icon name="history" [size]="16" /> {{ 'revisions.title' | t }}</h3>
        <app-button variant="text" size="sm" icon="x" iconOnly (click)="close.emit()" />
      </header>

      <div class="rh__list">
        @if (loading()) {
          <div class="rh__loading"><app-spinner [size]="20" /></div>
        } @else if (versions().length === 0) {
          <app-empty-state icon="history" [title]="'revisions.empty' | t" />
        } @else {
          @for (v of versions(); track v.id; let first = $first) {
            <div class="rh__item" [class.is-open]="selected()?.id === v.id">
              <button type="button" class="rh__item-main" (click)="toggle(v)">
                <span class="rh__item-title">
                  {{ v.name || versionLabel(v.version) }}
                  @if (first) { <span class="rh__badge">{{ 'revisions.latest' | t }}</span> }
                  @if (v.name) { <app-icon name="star-filled" [size]="12" /> }
                </span>
                <span class="rh__item-meta">{{ v.savedAt | relativeTime }} · {{ 'revisions.size' | t: { count: v.sizeChars } }}</span>
              </button>
              @if (selected()?.id === v.id) {
                <div class="rh__detail">
                  @if (detailLoading()) {
                    <app-spinner [size]="16" />
                  } @else {
                    <div class="rh__actions">
                      <app-button variant="secondary" size="sm" (click)="restore(v)">{{ 'revisions.restore' | t }}</app-button>
                      <app-button variant="text" size="sm" (click)="rename(v)">{{ 'revisions.name' | t }}</app-button>
                    </div>
                    @if (diff().length === 0) {
                      <p class="rh__same">{{ 'revisions.identical' | t }}</p>
                    } @else {
                      <p class="rh__legend">{{ 'revisions.diffHint' | t }}</p>
                      <pre class="rh__diff">@for (line of diff(); track $index) {<span class="rh__line rh__line--{{ line.kind }}">{{ line.kind === 'add' ? '+ ' : line.kind === 'remove' ? '- ' : '  ' }}{{ line.text }}
</span>}</pre>
                    }
                  }
                </div>
              }
            </div>
          }
        }
      </div>
    </div>
  `,
  styleUrl: './revision-history.component.scss',
})
export class RevisionHistoryComponent implements OnInit {
  private readonly service = inject(DocumentContentService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly documentWorkspaceId = input.required<string>();
  readonly documentId = input.required<string>();
  /** Current text, to show what each version differs by. */
  readonly currentContent = input('');
  readonly close = output<void>();
  readonly restored = output<void>();

  readonly versions = signal<DocumentVersion[]>([]);
  readonly loading = signal(true);
  readonly selected = signal<DocumentVersion | null>(null);
  readonly detailLoading = signal(false);
  readonly diff = signal<DiffLine[]>([]);

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.service.versions(this.documentWorkspaceId(), this.documentId()).subscribe({
      next: (list) => {
        this.versions.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  versionLabel(version: number): string {
    return this.i18n.translate('revisions.versionLabel', { seq: version });
  }

  toggle(version: DocumentVersion): void {
    if (this.selected()?.id === version.id) {
      this.selected.set(null);
      return;
    }
    this.selected.set(version);
    this.detailLoading.set(true);
    this.service.version(this.documentWorkspaceId(), this.documentId(), version.id).subscribe({
      next: (full) => {
        const lines = diffLines(full.content ?? '', this.currentContent());
        this.diff.set(lines.some((l) => l.kind !== 'same') ? lines.filter((l, i, all) => l.kind !== 'same' || this.nearChange(all, i)) : []);
        this.detailLoading.set(false);
      },
      error: () => this.detailLoading.set(false),
    });
  }

  /** Keeps two lines of context around every change. */
  private nearChange(lines: DiffLine[], index: number): boolean {
    for (let d = 1; d <= 2; d++) {
      if (lines[index - d]?.kind && lines[index - d].kind !== 'same') return true;
      if (lines[index + d]?.kind && lines[index + d].kind !== 'same') return true;
    }
    return false;
  }

  async restore(version: DocumentVersion): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('revisions.confirmRestore.title'),
      message: this.i18n.translate('revisions.confirmRestore.message', {
        name: version.name || this.versionLabel(version.version),
      }),
      confirmLabel: this.i18n.translate('revisions.restore'),
    });
    if (!ok) return;
    this.service.restore(this.documentWorkspaceId(), this.documentId(), version.id).subscribe({
      next: () => this.restored.emit(),
      error: (err) => this.toast.error(err.message),
    });
  }

  rename(version: DocumentVersion): void {
    const name = window.prompt(this.i18n.translate('revisions.name.prompt'), version.name ?? '');
    if (name === null) return;
    this.service.nameVersion(this.documentWorkspaceId(), this.documentId(), version.id, name).subscribe({
      next: (updated) => this.versions.update((list) => list.map((v) => (v.id === updated.id ? updated : v))),
      error: (err) => this.toast.error(err.message),
    });
  }
}
