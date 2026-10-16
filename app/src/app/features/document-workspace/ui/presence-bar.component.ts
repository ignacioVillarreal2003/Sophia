import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { PresenceUser } from '@core/models/document-workspace';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { TooltipDirective } from '@shared/ui/tooltip/tooltip.directive';

/** Overlapping avatars of collaborators currently viewing the document. */
@Component({
  selector: 'app-presence-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AvatarComponent, TooltipDirective],
  template: `
    @if (users().length > 0) {
      <div class="pb">
        @for (u of visible(); track u.userId) {
          <span class="pb__ava" [style.--ring]="u.color" [appTooltip]="u.fullName">
            <app-avatar [name]="u.fullName" [photoUrl]="u.photoUrl" [size]="26" />
          </span>
        }
        @if (users().length > max) {
          <span class="pb__more">+{{ users().length - max }}</span>
        }
      </div>
    }
  `,
  styles: [
    `
      .pb {
        display: inline-flex;
        align-items: center;
      }
      .pb__ava {
        display: inline-flex;
        border-radius: var(--radius-pill);
        box-shadow: 0 0 0 2px var(--ring, var(--accent)), 0 0 0 4px var(--surface);
        margin-left: -8px;
        &:first-child { margin-left: 0; }
      }
      .pb__more {
        margin-left: 4px;
        font-size: 12px;
        color: var(--text-subtle);
        font-weight: 600;
      }
    `,
  ],
})
export class PresenceBarComponent {
  readonly users = input.required<PresenceUser[]>();
  readonly max = 4;

  visible(): PresenceUser[] {
    return this.users().slice(0, this.max);
  }
}
