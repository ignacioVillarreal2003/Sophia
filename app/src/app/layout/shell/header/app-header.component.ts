import {ChangeDetectionStrategy, Component, computed, inject} from '@angular/core';
import {Router, RouterLink, RouterLinkActive} from '@angular/router';
import {CdkMenu, CdkMenuItem, CdkMenuTrigger} from '@angular/cdk/menu';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ADMIN_PERMISSIONS} from '@core/models/admin';
import {NotificationService} from '@features/notifications/data/notification.service';
import {AvatarComponent} from '@shared/ui/avatar/avatar.component';
import {IconComponent} from '@shared/ui/icon/icon.component';

@Component({
  selector: 'app-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    RouterLinkActive,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    AvatarComponent,
    IconComponent,
    TranslatePipe,
  ],
  template: `
    <header class="hdr">
      <a routerLink="/" class="hdr__brand">
        <img src="/sophia.png" alt="Sophia" class="hdr__brand-mark"/>
        <span class="hdr__brand-word">Sophia</span>
      </a>

      <div class="hdr__spacer"></div>

      <button
        class="hdr__icon-btn"
        routerLink="/"
        routerLinkActive="hdr__icon-btn--active"
        [routerLinkActiveOptions]="{ exact: true }"
        [attr.aria-label]="'nav.dashboard' | t"
        [title]="'nav.dashboard' | t"
      >
        <app-icon name="home" [size]="18"/>
      </button>

      <button
        class="hdr__icon-btn hdr__icon-btn--badge"
        routerLink="/notifications"
        [attr.aria-label]="'nav.notifications' | t"
        [title]="'nav.notifications' | t"
      >
        <app-icon name="bell" [size]="18"/>
        @if (unread() > 0) {
          <span class="hdr__badge">{{ unread() > 9 ? '9+' : unread() }}</span>
        }
      </button>

      @if (showAdmin()) {
        <button
          class="hdr__icon-btn"
          routerLink="/admin"
          routerLinkActive="hdr__icon-btn--active"
          [attr.aria-label]="'nav.admin' | t"
          [title]="'nav.admin' | t"
        >
          <app-icon name="shield" [size]="18"/>
        </button>
      }

      <button class="hdr__user" [cdkMenuTriggerFor]="userMenu" [attr.aria-label]="'nav.account' | t">
        <app-avatar [name]="user()?.fullName ?? ''" [photoUrl]="user()?.photoUrl" [size]="28"/>
      </button>
    </header>

    <ng-template #userMenu>
      <div class="menu" cdkMenu>
        <div class="menu__label">{{ user()?.email }}</div>
        <button class="menu__item" cdkMenuItem routerLink="/settings">
          <app-icon name="settings" [size]="16"/>
          {{ 'nav.settings' | t }}
        </button>
        <div class="menu__divider"></div>
        <button class="menu__item menu__item--danger" cdkMenuItem (click)="signOut()">
          <app-icon name="logout" [size]="16"/>
          {{ 'nav.signOut' | t }}
        </button>
      </div>
    </ng-template>
  `,
  styleUrl: './app-header.component.scss',
})
export class AppHeaderComponent {
  private readonly auth = inject(AuthenticationService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  readonly user = this.auth.user;
  readonly unread = this.notifications.unreadCount;
  readonly showAdmin = computed(() => {
    const perms = this.auth.permissions();
    return ADMIN_PERMISSIONS.some((p) => perms.has(p));
  });

  signOut(): void {
    this.auth.logout().subscribe(() => this.router.navigate(['/authentication/login']));
  }
}
