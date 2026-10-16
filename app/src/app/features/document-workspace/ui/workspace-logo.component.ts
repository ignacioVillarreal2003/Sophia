import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { WorkspaceLogoService } from '../data/workspace-logo.service';

/** A workspace's logo; falls back to a tinted tile with the workspace icon when it has none. */
@Component({
  selector: 'app-workspace-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    @if (src(); as source) {
      <img [src]="source" alt="" [width]="size()" [height]="size()" />
    } @else {
      <span class="wl__fallback" [style.--wl-color]="color() || 'var(--accent)'">
        <app-icon name="book" [size]="iconSize()" />
      </span>
    }
  `,
  host: {
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
  },
  styles: [
    `
      :host {
        display: inline-grid;
        flex-shrink: 0;
        border-radius: var(--radius-md);
        overflow: hidden;
      }
      img {
        display: block;
        width: 100%;
        height: 100%;
        object-fit: cover;
        background: var(--surface-sunken);
        box-shadow: inset 0 0 0 1px var(--border);
      }
      .wl__fallback {
        display: grid;
        place-items: center;
        width: 100%;
        height: 100%;
        border-radius: inherit;
        background: color-mix(in srgb, var(--wl-color) 16%, var(--surface));
        box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--wl-color) 28%, transparent);
        color: color-mix(in srgb, var(--wl-color) 70%, var(--text));
        transition: background var(--duration-base) var(--ease-standard);
      }
    `,
  ],
})
export class WorkspaceLogoComponent {
  private readonly logos = inject(WorkspaceLogoService);

  readonly logoUrl = input<string | null | undefined>(null);
  readonly color = input<string | null | undefined>(null);
  readonly size = input(40);
  readonly iconSize = input(18);

  readonly src = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const url = this.logoUrl();
      if (!url) {
        this.src.set(null);
        return;
      }
      const subscription = this.logos.resolve(url).subscribe((resolved) => this.src.set(resolved));
      onCleanup(() => subscription.unsubscribe());
    });
  }
}
