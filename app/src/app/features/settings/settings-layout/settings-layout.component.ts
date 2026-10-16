import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {TranslationService} from '@core/services/i18n';
import {IconRailComponent, RailItem} from '@shared/ui/icon-rail/icon-rail.component';
import {IconName} from '@shared/ui/icon/icons';

interface SettingsNavItem {
  labelKey: string;
  icon: IconName;
  link: string;
}

const ITEMS: SettingsNavItem[] = [
  {labelKey: 'settings.profile.title', icon: 'user', link: 'profile'},
  {labelKey: 'settings.appearance.title', icon: 'sun', link: 'appearance'},
  {labelKey: 'settings.notifications.title', icon: 'bell', link: 'notifications'},
  {labelKey: 'settings.security.title', icon: 'shield', link: 'security'},
  {labelKey: 'settings.sessions.title', icon: 'monitor', link: 'sessions'},
  {labelKey: 'settings.activity.title', icon: 'history', link: 'activity'},
];

const PIN_STORAGE_KEY = 'sophia.settings.railPinned';

@Component({
  selector: 'app-settings-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, IconRailComponent],
  template: `
    <div class="set" [class.set--expanded]="expanded()">
      <app-icon-rail
        [items]="railItems()"
        [title]="title()"
        [expandLabel]="expandLabel()"
        [collapseLabel]="collapseLabel()"
        [storageKey]="railStorageKey"
        [(expanded)]="expanded"
      />

      <div class="set-content">
        <div class="app-container">
          <router-outlet/>
        </div>
      </div>
    </div>
  `,
  styleUrl: './settings-layout.component.scss',
})
export class SettingsLayoutComponent {
  private readonly i18n = inject(TranslationService);

  readonly railItems = computed<RailItem[]>(() =>
    ITEMS.map((item) => ({
      label: this.i18n.translate(item.labelKey),
      link: item.link,
      icon: item.icon,
    })),
  );

  readonly title = computed(() => this.i18n.translate('nav.settings'));
  readonly expandLabel = computed(() => this.i18n.translate('admin.rail.pin'));
  readonly collapseLabel = computed(() => this.i18n.translate('admin.rail.unpin'));

  readonly railStorageKey = PIN_STORAGE_KEY;
  readonly expanded = signal(localStorage.getItem(PIN_STORAGE_KEY) === 'true');
}
