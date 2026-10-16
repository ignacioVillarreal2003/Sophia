import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { environment } from '@env/environment';

@Component({
  selector: 'app-avatar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (resolvedPhotoUrl(); as src) {
      <img [src]="src" [alt]="name()" [width]="size()" [height]="size()" />
    } @else {
      <span class="avatar__initials" [style.background]="bg()">{{ initials() }}</span>
    }
  `,
  host: {
    class: 'avatar',
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
    '[style.font-size.px]': 'size() * 0.4',
  },
  styles: [
    `
      :host {
        display: inline-flex;
        border-radius: var(--radius-pill);
        overflow: hidden;
        flex-shrink: 0;
        user-select: none;
      }
      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
      .avatar__initials {
        width: 100%;
        height: 100%;
        display: grid;
        place-items: center;
        color: #fff;
        font-weight: 600;
        letter-spacing: 0;
      }
    `,
  ],
})
export class AvatarComponent {
  readonly name = input<string>('');
  readonly photoUrl = input<string | null | undefined>(null);
  readonly size = input<number>(32);

  readonly resolvedPhotoUrl = computed(() => {
    const url = this.photoUrl();
    if (!url) return null;
    return /^(https?:)?\/\//.test(url) ? url : `${environment.authenticationApiUrl}${url}`;
  });

  readonly initials = computed(() => {
    const parts = this.name().trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });

  readonly bg = computed(() => {
    const palette = ['#0066cc', '#5856d6', '#af52de', '#ff9500', '#34c759', '#00c7be', '#ff375f'];
    const name = this.name();
    let hash = 0;
    for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash);
    return palette[Math.abs(hash) % palette.length];
  });
}
