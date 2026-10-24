import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, model, signal } from '@angular/core';
import { Artifact } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { artifactIcon } from '@shared/utils/artifact-icon';
import { ArtifactService } from '../../document-workspace/data/artifact.service';

/**
 * Chooses the workspace documents a generation works from. Only documents the person can open are listed, and the
 * AI service reads them again with the person's own token, so picking one grants nothing extra.
 */
@Component({
  selector: 'app-ai-sources',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  template: `
    <div class="src">
      <div class="src__head">
        <span class="src__label">{{ label() || ('ai.sources.label' | t) }}</span>
        <span class="src__count">{{ 'ai.sources.count' | t: { count: selected().length, max: max() } }}</span>
      </div>

      @if (chosen().length > 0) {
        <div class="src__chosen">
          @for (doc of chosen(); track doc.id) {
            <span class="src__tag">
              <app-icon [name]="icon(doc)" [size]="13" />
              <span class="src__tag-name">{{ doc.name }}</span>
              <button type="button" class="src__remove" [attr.aria-label]="'ai.sources.remove' | t" (click)="toggle(doc)">
                <app-icon name="x" [size]="12" />
              </button>
            </span>
          }
        </div>
      }

      <div class="src__search">
        <app-icon name="search" [size]="14" />
        <input
          type="search"
          [placeholder]="'ai.sources.search' | t"
          [value]="query()"
          (input)="onSearch($any($event.target).value)"
        />
      </div>

      <div class="src__list" role="listbox" aria-multiselectable="true" [attr.aria-label]="'ai.sources.label' | t">
        @if (loading()) {
          <p class="src__note">{{ 'ai.sources.loading' | t }}</p>
        } @else if (documents().length === 0) {
          <p class="src__note">{{ 'ai.sources.empty' | t }}</p>
        } @else {
          @for (doc of documents(); track doc.id) {
            <button
              type="button"
              role="option"
              class="src__item"
              [class.src__item--on]="isSelected(doc)"
              [attr.aria-selected]="isSelected(doc)"
              [disabled]="!isSelected(doc) && selected().length >= max()"
              (click)="toggle(doc)"
            >
              <app-icon [name]="icon(doc)" [size]="15" />
              <span class="src__name">{{ doc.name }}</span>
              @if (isSelected(doc)) {
                <app-icon name="check" [size]="14" />
              }
            </button>
          }
        }
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }
    .src { display: flex; flex-direction: column; gap: 8px; }
    .src__head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
    .src__label { font-size: var(--type-caption-size); font-weight: 600; color: var(--text); }
    .src__count { font-size: var(--type-fine-size); color: var(--text-subtle); }
    .src__chosen { display: flex; flex-wrap: wrap; gap: 6px; }
    .src__tag {
      display: inline-flex; align-items: center; gap: 6px; max-width: 100%;
      padding: 4px 6px 4px 10px; font-size: var(--type-fine-size); color: var(--text);
      background: color-mix(in srgb, var(--accent) 12%, var(--surface));
      border: 1px solid color-mix(in srgb, var(--accent) 40%, var(--border));
      border-radius: var(--radius-full);
    }
    .src__tag-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 220px; }
    .src__remove {
      display: grid; place-items: center; width: 18px; height: 18px; padding: 0; color: var(--text-muted);
      background: transparent; border: 0; border-radius: 50%; cursor: pointer;
      &:hover { background: var(--surface-muted); color: var(--text); }
      &:focus-visible { outline: 2px solid var(--accent-focus); }
    }
    .src__search {
      display: flex; align-items: center; gap: 8px; padding: 0 10px; color: var(--text-subtle);
      background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md);
      &:focus-within { outline: 2px solid var(--accent-focus); outline-offset: 1px; }
      input {
        flex: 1; min-width: 0; padding: 8px 0; font: inherit; font-size: var(--type-caption-size);
        color: var(--text); background: transparent; border: 0; outline: 0;
      }
    }
    .src__list {
      display: flex; flex-direction: column; max-height: 220px; overflow: auto; padding: 4px;
      background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md);
    }
    .src__note { margin: 0; padding: 14px; text-align: center; font-size: var(--type-caption-size); color: var(--text-subtle); }
    .src__item {
      display: flex; align-items: center; gap: 10px; padding: 7px 10px; font: inherit;
      font-size: var(--type-caption-size); color: var(--text); text-align: left; background: transparent;
      border: 0; border-radius: var(--radius-sm); cursor: pointer;
      &:hover:not(:disabled) { background: var(--surface-muted); }
      &:focus-visible { outline: 2px solid var(--accent-focus); outline-offset: -2px; }
      &:disabled { opacity: 0.45; cursor: not-allowed; }
      &--on { background: color-mix(in srgb, var(--accent) 10%, transparent); }
    }
    .src__name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  `,
})
export class SourcePickerComponent implements OnInit {
  private readonly artifacts = inject(ArtifactService);

  readonly workspaceId = input.required<string>();
  readonly max = input(12);
  readonly label = input('');
  /** Ids of the chosen documents, in the order they were picked. */
  readonly selected = model<string[]>([]);

  readonly documents = signal<Artifact[]>([]);
  readonly loading = signal(true);
  readonly query = signal('');
  icon(doc: Artifact) {
    return artifactIcon(doc.type);
  }

  /** Chosen documents that are not in the current search results are still shown, as tags. */
  private readonly known = signal(new Map<string, Artifact>());
  readonly chosen = computed(() =>
    this.selected().map((id) => this.known().get(id)).filter((doc): doc is Artifact => !!doc),
  );

  private timer: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.load();
  }

  onSearch(value: string): void {
    this.query.set(value);
    if (this.timer) {
      clearTimeout(this.timer);
    }
    this.timer = setTimeout(() => this.load(), 250);
  }

  isSelected(doc: Artifact): boolean {
    return this.selected().includes(doc.id);
  }

  toggle(doc: Artifact): void {
    this.selected.update((ids) => {
      if (ids.includes(doc.id)) {
        return ids.filter((id) => id !== doc.id);
      }
      return ids.length >= this.max() ? ids : [...ids, doc.id];
    });
  }

  private load(): void {
    this.loading.set(true);
    this.artifacts.documents(this.workspaceId(), this.query().trim(), 60, true).subscribe({
      next: (docs) => {
        this.documents.set(docs);
        this.known.update((map) => {
          const next = new Map(map);
          docs.forEach((doc) => next.set(doc.id, doc));
          return next;
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
