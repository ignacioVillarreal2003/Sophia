import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { WorkspaceLogoService } from '../data/workspace-logo.service';
import { PATTERN_SVG_ATTRS, patternFor } from '../workspace-patterns';

/** A workspace's logo; without one, its pattern: an abstract mark in one ink, picked for it. */
@Component({
  selector: 'app-workspace-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (src(); as source) {
      <img [src]="source" alt="" [width]="size()" [height]="size()" />
    } @else {
      <span class="wl__mark" [innerHTML]="mark()"></span>
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
        --wl-bg: var(--surface-sunken);
      }
      img {
        display: block;
        width: 100%;
        height: 100%;
        object-fit: cover;
        background: var(--surface-sunken);
        box-shadow: inset 0 0 0 1px var(--border);
      }
      .wl__mark {
        display: block;
        width: 100%;
        height: 100%;
        background: var(--surface-sunken);
        box-shadow: inset 0 0 0 1px var(--border);
        border-radius: inherit;
        color: var(--text);
      }
      .wl__mark ::ng-deep svg {
        display: block;
        width: 100%;
        height: 100%;
      }
    `,
  ],
})
export class WorkspaceLogoComponent {
  private readonly logos = inject(WorkspaceLogoService);
  private readonly sanitizer = inject(DomSanitizer);

  readonly logoUrl = input<string | null | undefined>(null);
  /** The pattern stored for the workspace (its id); older workspaces stored a colour, which is read as "pick one". */
  readonly pattern = input<string | null | undefined>(null);
  /** What the pick is made from when there is no stored pattern: the workspace's id. */
  readonly seed = input('');
  readonly size = input(40);

  readonly src = signal<string | null>(null);

  readonly mark = computed<SafeHtml>(() => {
    const { svg } = patternFor(this.pattern(), this.seed());
    return this.sanitizer.bypassSecurityTrustHtml(`<svg viewBox="0 0 48 48" ${PATTERN_SVG_ATTRS}>${svg}</svg>`);
  });

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
