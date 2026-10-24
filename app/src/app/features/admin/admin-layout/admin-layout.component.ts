import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {TranslationService} from '@core/services/i18n';
import {IconRailComponent, RailItem} from '@shared/ui/icon-rail/icon-rail.component';
import {IconName} from '@shared/ui/icon/icons';

interface AdminTab {
  labelKey: string;
  link: string;
  icon: IconName;
  /** The tab shows when the user holds any one of these. */
  permissions: string[];
}

const TABS: AdminTab[] = [
  {labelKey: 'admin.tab.users', link: 'users', icon: 'users', permissions: ['USER_READ']},
  {labelKey: 'admin.tab.workspaces', link: 'workspaces', icon: 'grid', permissions: ['WORKSPACE_MANAGE']},
  {labelKey: 'admin.tab.ai', link: 'ai', icon: 'sparkles', permissions: ['WORKSPACE_MANAGE']},
  {labelKey: 'admin.tab.roles', link: 'roles', icon: 'shield', permissions: ['ROLE_READ']},
  {labelKey: 'admin.tab.permissions', link: 'permissions', icon: 'lock', permissions: ['PERMISSION_READ']},
  {
    labelKey: 'admin.tab.notifications',
    link: 'notifications',
    icon: 'bell',
    permissions: [
      'NOTIFICATION_CATEGORY_READ',
      'NOTIFICATION_AUDIT_READ',
      'NOTIFICATION_DEAD_LETTER_REPLAY',
    ],
  },
  {labelKey: 'admin.tab.audit', link: 'audit', icon: 'history', permissions: ['AUDIT_READ']},
  {
    labelKey: 'admin.tab.serviceClients',
    link: 'service-clients',
    icon: 'command',
    permissions: ['SERVICE_CLIENT_MANAGE'],
  },
];

const PIN_STORAGE_KEY = 'sophia.admin.railPinned';

@Component({
  selector: 'app-admin-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, IconRailComponent],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent {
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);

  readonly railItems = computed<RailItem[]>(() => {
    const perms = this.auth.permissions();
    return TABS.filter((tab) => tab.permissions.some((permission) => perms.has(permission))).map((tab) => ({
      label: this.i18n.translate(tab.labelKey),
      link: tab.link,
      icon: tab.icon,
    }));
  });

  readonly title = computed(() => this.i18n.translate('nav.admin'));
  readonly expandLabel = computed(() => this.i18n.translate('admin.rail.pin'));
  readonly collapseLabel = computed(() => this.i18n.translate('admin.rail.unpin'));

  readonly railStorageKey = PIN_STORAGE_KEY;
  readonly expanded = signal(localStorage.getItem(PIN_STORAGE_KEY) === 'true');
}
