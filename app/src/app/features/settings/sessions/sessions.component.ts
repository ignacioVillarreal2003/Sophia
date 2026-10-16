import {ChangeDetectionStrategy, Component, OnInit, computed, inject, signal} from '@angular/core';
import {Router} from '@angular/router';
import {Session} from '@core/models/authentication';
import {AppError} from '@core/models/api.model';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {CardComponent} from '@shared/ui/card/card.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {EmptyStateComponent} from '@shared/ui/empty-state/empty-state.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';
import {deviceLabel} from '@shared/utils/device-label';
import {IconName} from '@shared/ui/icon/icons';

const MOBILE_UA_PATTERN = /Mobile|Android|iPhone|iPad/i;

@Component({
  selector: 'app-sessions',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CardComponent,
    ButtonComponent,
    IconComponent,
    BadgeComponent,
    SpinnerComponent,
    EmptyStateComponent,
    PageHeaderComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './sessions.component.html',
  styleUrl: './sessions.component.scss',
})
export class SessionsComponent implements OnInit {
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);

  readonly sessions = signal<Session[]>([]);
  readonly loading = signal(true);
  readonly revokingId = signal<string | null>(null);
  readonly revokingOthers = signal(false);
  readonly loggingOutAll = signal(false);

  readonly hasOtherSessions = computed(() => this.sessions().some((s) => !s.current));

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.auth.listSessions().subscribe({
      next: (sessions) => {
        this.sessions.set(sessions);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  deviceIcon(session: Session): IconName {
    return session.userAgent && MOBILE_UA_PATTERN.test(session.userAgent) ? 'smartphone' : 'monitor';
  }

  deviceLabelOf(session: Session): string {
    return deviceLabel(session.userAgent, this.i18n.translate);
  }

  async revoke(session: Session): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('settings.sessions.confirmRevoke.title'),
      message: this.i18n.translate('settings.sessions.confirmRevoke.message', {
        device: this.deviceLabelOf(session),
      }),
      confirmLabel: this.i18n.translate('settings.sessions.confirmRevoke.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.revokingId.set(session.id);
    this.auth.revokeSession(session.id).subscribe({
      next: () => {
        this.revokingId.set(null);
        this.sessions.update((list) => list.filter((s) => s.id !== session.id));
      },
      error: (err: AppError) => {
        this.revokingId.set(null);
        this.toast.error(err.message);
      },
    });
  }

  async revokeOthers(): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('settings.sessions.confirmRevokeOthers.title'),
      message: this.i18n.translate('settings.sessions.confirmRevokeOthers.message'),
      confirmLabel: this.i18n.translate('settings.sessions.confirmRevokeOthers.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.revokingOthers.set(true);
    this.auth.revokeOtherSessions().subscribe({
      next: () => {
        this.revokingOthers.set(false);
        this.sessions.update((list) => list.filter((s) => s.current));
        this.toast.success(this.i18n.translate('settings.sessions.toast.othersClosed'));
      },
      error: (err: AppError) => {
        this.revokingOthers.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async logoutAll(): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('settings.sessions.confirmLogoutAll.title'),
      message: this.i18n.translate('settings.sessions.confirmLogoutAll.message'),
      confirmLabel: this.i18n.translate('settings.sessions.confirmLogoutAll.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.loggingOutAll.set(true);
    this.auth.logoutAll().subscribe({
      next: () => this.router.navigate(['/authentication/login']),
      error: (err: AppError) => {
        this.loggingOutAll.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
