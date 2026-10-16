import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';

/** A page button (0-based `index`) or a collapsed gap between two buttons. */
type PageItem = { readonly index: number } | { readonly gap: true };

@Component({
  selector: 'app-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  template: `
    @if (totalPages() > 1) {
      <nav class="pg" aria-label="Pagination">
        <button
          type="button"
          class="pg__btn pg__btn--nav"
          [disabled]="first()"
          aria-label="Previous page"
          (click)="pageChange.emit(page() - 1)"
        >
          <app-icon name="chevron-left" [size]="16" />
        </button>

        <div class="pg__pages">
          @for (item of items(); track $index) {
            @if ('index' in item) {
              <button
                type="button"
                class="pg__btn pg__btn--page"
                [class.pg__btn--active]="item.index === page()"
                [attr.aria-current]="item.index === page() ? 'page' : null"
                (click)="item.index !== page() && pageChange.emit(item.index)"
              >
                {{ item.index + 1 }}
              </button>
            } @else {
              <span class="pg__gap" aria-hidden="true">…</span>
            }
          }
        </div>

        <span class="pg__status">{{ 'pagination.status' | t: { page: page() + 1, total: totalPages() } }}</span>

        <button
          type="button"
          class="pg__btn pg__btn--nav"
          [disabled]="last()"
          aria-label="Next page"
          (click)="pageChange.emit(page() + 1)"
        >
          <app-icon name="chevron-right" [size]="16" />
        </button>
      </nav>
    }
  `,
  styles: [
    `
      .pg {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--space-xs);
        /* Only space above: the page container already pads the bottom. */
        padding-top: var(--space-xl);
      }
      .pg__pages {
        display: flex;
        align-items: center;
        gap: 4px;
      }
      .pg__status {
        display: none;
        padding-inline: var(--space-sm);
        font-size: var(--type-caption-size);
        font-weight: 600;
        color: var(--text);
      }
      .pg__gap {
        min-width: 20px;
        text-align: center;
        color: var(--text-subtle);
        user-select: none;
      }
      .pg__btn {
        display: grid;
        place-items: center;
        min-width: 36px;
        height: 36px;
        padding-inline: 8px;
        border-radius: var(--radius-md);
        border: 1px solid transparent;
        background: transparent;
        color: var(--text-subtle);
        font-family: var(--font-text);
        font-size: 14px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        cursor: pointer;
        transition:
          background var(--duration-fast) var(--ease-standard),
          color var(--duration-fast) var(--ease-standard),
          border-color var(--duration-fast) var(--ease-standard);

        &:hover:not(:disabled):not(.pg__btn--active) {
          background: var(--surface-muted);
          color: var(--text);
        }
        &:focus-visible {
          outline: 2px solid var(--accent-focus);
          outline-offset: 2px;
        }
        &:disabled {
          opacity: 0.35;
          cursor: not-allowed;
        }
      }
      .pg__btn--nav {
        border-color: var(--border);
        background: var(--surface);
        color: var(--text);

        &:hover:not(:disabled) {
          border-color: var(--border-strong);
        }
      }
      .pg__btn--active {
        background: var(--text);
        color: var(--surface);
        cursor: default;
      }

      /* Phones: the numbered strip gets crowded, so fall back to "Page x of y". */
      @media (max-width: 520px) {
        .pg__pages {
          display: none;
        }
        .pg__status {
          display: inline;
        }
      }
    `,
  ],
})
export class PaginationComponent {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly first = input.required<boolean>();
  readonly last = input.required<boolean>();
  readonly pageChange = output<number>();

  /** First, last and the current page with its neighbours; everything else collapses to a gap. */
  readonly items = computed<PageItem[]>(() => {
    const total = this.totalPages();
    const current = this.page();
    const keep = new Set<number>([0, total - 1, current - 1, current, current + 1]);
    const result: PageItem[] = [];
    let previous = -1;
    for (const index of [...keep].filter((i) => i >= 0 && i < total).sort((a, b) => a - b)) {
      if (index - previous === 2) {
        // A single hidden page is cheaper to show than an ellipsis.
        result.push({ index: previous + 1 });
      } else if (index - previous > 2) {
        result.push({ gap: true });
      }
      result.push({ index });
      previous = index;
    }
    return result;
  });
}
