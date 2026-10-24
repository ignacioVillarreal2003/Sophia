import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { DocumentVersion, DocumentWorkspaceMember } from '@core/models/document-workspace';
import { ToastService } from '@core/services/toast.service';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { DocumentContentService } from '../data/document-content.service';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';

/** Version history of a document: browse the saved versions, add a note to one, restore one. */
@Component({
  selector: 'app-revision-history',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AvatarComponent, ButtonComponent, IconComponent, SpinnerComponent, EmptyStateComponent, RelativeTimePipe, TranslatePipe],
  template: `
    <div class="rh">
      @if (closable()) {
        <header class="rh__head">
          <h3 class="rh__title"><app-icon name="history" [size]="16" /> {{ 'revisions.title' | t }}</h3>
          <app-button variant="text" size="sm" icon="x" iconOnly (click)="close.emit()" />
        </header>
      }

      @if (loading()) {
        <div class="rh__loading"><app-spinner [size]="20" /></div>
      } @else if (versions().length === 0) {
        <app-empty-state icon="history" [title]="'revisions.empty' | t" />
      } @else {
        <div class="rh__table">
          <div class="rh__thead" aria-hidden="true">
            <span>{{ 'revisions.col.version' | t }}</span>
            <span class="rh__c-by">{{ 'revisions.col.by' | t }}</span>
            <span>{{ 'revisions.col.saved' | t }}</span>
            <span class="rh__c-size">{{ 'revisions.col.size' | t }}</span>
            <span></span>
          </div>

          @for (v of versions(); track v.id; let first = $first) {
            <div class="rh__group" [class.is-open]="selected()?.id === v.id">
              <div class="rh__row" tabindex="0" role="button" (click)="toggle(v)" (keydown.enter)="toggle(v)">
                <span class="rh__version">
                  <span class="rh__vnum">{{ versionLabel(v.version) }}</span>
                  @if (v.name) {
                    <span class="rh__vname">{{ v.name }}</span>
                  }
                  @if (first) { <span class="rh__badge">{{ 'revisions.latest' | t }}</span> }
                </span>
                <span class="rh__by rh__c-by">
                  <app-avatar [name]="authorName(v)" [photoUrl]="authorPhoto(v)" [size]="22" />
                  <span class="rh__by-name">{{ authorName(v) }}</span>
                </span>
                <span class="rh__when" [title]="v.savedAt">{{ v.savedAt | relativeTime }}</span>
                <span class="rh__size rh__c-size">{{ 'revisions.size' | t: { count: v.sizeChars } }}</span>
                <span class="rh__chev"><app-icon [name]="selected()?.id === v.id ? 'chevron-down' : 'chevron-right'" [size]="16" /></span>
              </div>

              @if (selected()?.id === v.id) {
                <div class="rh__detail">
                  <label class="rh__label" [attr.for]="'rh-note-' + v.id">{{ 'revisions.note' | t }}</label>
                  <div class="rh__note">
                    <input
                      class="rh__input"
                      type="text"
                      maxlength="120"
                      [id]="'rh-note-' + v.id"
                      [value]="note()"
                      [placeholder]="'revisions.note.placeholder' | t"
                      (input)="note.set($any($event.target).value)"
                      (keydown.enter)="saveNote(v)"
                    />
                    <button type="button" class="rh__btn rh__btn--dark" [disabled]="note().trim() === (v.name ?? '')" (click)="saveNote(v)">
                      {{ 'revisions.note.save' | t }}
                    </button>
                    @if (!first) {
                      <button type="button" class="rh__btn" (click)="restore(v)">{{ 'revisions.restore' | t }}</button>
                    }
                  </div>
                </div>
              }
            </div>
          }
        </div>
      }
    </div>
  `,
  styleUrl: './revision-history.component.scss',
})
export class RevisionHistoryComponent implements OnInit {
  private readonly service = inject(DocumentContentService);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly documentWorkspaceId = input.required<string>();
  readonly documentId = input.required<string>();
  /** A close button in the header, for when it is a side panel. */
  readonly closable = input(true);
  readonly close = output<void>();
  readonly restored = output<void>();

  readonly versions = signal<DocumentVersion[]>([]);
  private readonly members = signal<DocumentWorkspaceMember[]>([]);
  readonly loading = signal(true);
  readonly selected = signal<DocumentVersion | null>(null);
  /** The note being written for the open version. */
  readonly note = signal('');

  ngOnInit(): void {
    this.workspaces.members(this.documentWorkspaceId()).subscribe({ next: (list) => this.members.set(list) });
    this.reload();
  }

  authorName(version: DocumentVersion): string {
    const m = this.members().find((x) => x.userId === version.createdBy);
    return m?.fullName || m?.email || this.i18n.translate('share.fallbackUser');
  }

  authorPhoto(version: DocumentVersion): string | null {
    return this.members().find((x) => x.userId === version.createdBy)?.photoUrl ?? null;
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
    this.note.set(version.name ?? '');
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

  saveNote(version: DocumentVersion): void {
    const note = this.note().trim();
    if (note === (version.name ?? '')) return;
    this.service.nameVersion(this.documentWorkspaceId(), this.documentId(), version.id, note).subscribe({
      next: (updated) => {
        this.versions.update((list) => list.map((v) => (v.id === updated.id ? updated : v)));
        this.selected.set(updated);
        this.toast.success(this.i18n.translate('revisions.note.saved'));
      },
      error: (err) => this.toast.error(err.message),
    });
  }
}
