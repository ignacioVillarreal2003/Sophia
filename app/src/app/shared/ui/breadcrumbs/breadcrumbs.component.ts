import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '../icon/icon.component';

export interface Crumb {
  id: string | null;
  label: string;
  link?: string | unknown[];
}

@Component({
  selector: 'app-breadcrumbs',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  template: `
    <nav class="crumbs" [attr.aria-label]="'a11y.breadcrumbNav' | t">
      @for (crumb of items(); track crumb.id; let last = $last) {
        <button
          class="crumbs__item"
          [class.crumbs__item--current]="last"
          [class.crumbs__item--drop]="dropId() === crumb.id && !last"
          [disabled]="last"
          (click)="navigate.emit(crumb)"
          (dragover)="onDragOver($event, crumb, last)"
          (dragleave)="onDragLeave(crumb)"
          (drop)="onDrop($event, crumb, last)"
        >
          {{ crumb.label }}
        </button>
        @if (!last) {
          <app-icon class="crumbs__sep" name="chevron-right" [size]="14" />
        }
      }
    </nav>
  `,
  styles: [
    `
      .crumbs {
        display: flex;
        align-items: center;
        gap: 2px;
        flex-wrap: wrap;
        min-width: 0;
      }
      .crumbs__item {
        padding: 3px 8px;
        border-radius: var(--radius-sm);
        font-size: var(--type-caption-size);
        letter-spacing: var(--type-caption-ls);
        color: var(--text-muted);
        max-width: 220px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        &:hover:not(:disabled) {
          background: var(--surface-sunken);
          color: var(--text);
        }
        &--drop {
          background: color-mix(in srgb, var(--accent) 16%, var(--surface));
          color: var(--text);
          box-shadow: inset 0 0 0 2px var(--accent);
        }
        &--current {
          color: var(--text);
          font-weight: 600;
          cursor: default;
        }
      }
      .crumbs__sep {
        color: var(--text-disabled);
      }
    `,
  ],
})
export class BreadcrumbsComponent {
  readonly items = input.required<Crumb[]>();
  readonly navigate = output<Crumb>();

  /** When true, earlier crumbs accept dropped items (emits `crumbDrop`). */
  readonly droppable = input(false);
  readonly crumbDrop = output<Crumb>();
  protected readonly dropId = signal<string | null | undefined>(undefined);

  protected onDragOver(event: DragEvent, crumb: Crumb, last: boolean): void {
    if (!this.droppable() || last) return;
    event.preventDefault();
    this.dropId.set(crumb.id);
  }

  protected onDragLeave(crumb: Crumb): void {
    if (this.dropId() === crumb.id) this.dropId.set(undefined);
  }

  protected onDrop(event: DragEvent, crumb: Crumb, last: boolean): void {
    this.dropId.set(undefined);
    if (!this.droppable() || last) return;
    event.preventDefault();
    this.crumbDrop.emit(crumb);
  }
}
