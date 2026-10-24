import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { AiSettings, AiUsage } from '@core/models/ai/ai.model';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { AiService } from '../../ai/data/ai.service';

const PERIODS = [7, 14, 30, 90];

/** Turns the AI features on or off, sets the daily allowances, and shows who is spending what. */
@Component({
  selector: 'app-ai-admin',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, CardComponent, IconComponent, PageHeaderComponent, SpinnerComponent, TranslatePipe],
  template: `
    <div class="aa app-container">
      <app-page-header [title]="'admin.ai.title' | t" [subtitle]="'admin.ai.subtitle' | t" />

      @if (loading()) {
        <div class="aa__loading"><app-spinner /></div>
      } @else if (settings(); as s) {
        <div class="aa__grid">
          <app-card>
            <h2 class="aa__h">{{ 'admin.ai.provider' | t }}</h2>
            <dl class="aa__facts">
              <div><dt>{{ 'admin.ai.state' | t }}</dt>
                <dd>
                  @if (!s.configured) { <span class="aa__pill aa__pill--bad">{{ 'admin.ai.notConfigured' | t }}</span> }
                  @else if (s.mock) { <span class="aa__pill aa__pill--warn">{{ 'admin.ai.mock' | t }}</span> }
                  @else { <span class="aa__pill aa__pill--ok">{{ 'admin.ai.configured' | t }}</span> }
                </dd>
              </div>
              <div><dt>{{ 'admin.ai.model' | t }}</dt><dd>{{ s.model }}</dd></div>
              @if (s.updatedAt) {
                <div><dt>{{ 'admin.ai.updated' | t }}</dt><dd>{{ formatDate(s.updatedAt) }}</dd></div>
              }
            </dl>
            @if (!s.configured) {
              <p class="aa__hint"><app-icon name="info" [size]="14" /> {{ 'admin.ai.keyHint' | t }}</p>
            }
          </app-card>

          <app-card>
            <h2 class="aa__h">{{ 'admin.ai.limits' | t }}</h2>
            <form class="aa__form" (submit)="$event.preventDefault(); save()">
              <label class="aa__switch">
                <input type="checkbox" role="switch" [checked]="enabled()" (change)="enabled.set($any($event.target).checked)" />
                <span>
                  <strong>{{ 'admin.ai.enabled' | t }}</strong>
                  <small>{{ 'admin.ai.enabledHint' | t }}</small>
                </span>
              </label>
              <div class="aa__field">
                <label for="aa-user">{{ 'admin.ai.perUser' | t }}</label>
                <input id="aa-user" type="number" min="0" step="10000" [value]="perUser()" (input)="perUser.set(toNumber($any($event.target).value))" />
              </div>
              <div class="aa__field">
                <label for="aa-ws">{{ 'admin.ai.perWorkspace' | t }}</label>
                <input id="aa-ws" type="number" min="0" step="10000" [value]="perWorkspace()" (input)="perWorkspace.set(toNumber($any($event.target).value))" />
              </div>
              <p class="aa__hint">{{ 'admin.ai.limitsHint' | t }}</p>
              <div>
                <app-button type="submit" variant="primary" [loading]="saving()" [disabled]="!dirty()">{{ 'common.save' | t }}</app-button>
              </div>
            </form>
          </app-card>
        </div>
      }

      <app-card class="aa__usage">
        <div class="aa__usage-head">
          <h2 class="aa__h">{{ 'admin.ai.usage' | t }}</h2>
          <select [attr.aria-label]="'admin.ai.period' | t" [value]="days()" (change)="setDays(+$any($event.target).value)">
            @for (p of periods; track p) {
              <option [value]="p" [selected]="p === days()">{{ 'admin.ai.lastDays' | t: { n: p } }}</option>
            }
          </select>
        </div>

        @if (usage(); as u) {
          <div class="aa__stats">
            <div><span>{{ 'admin.ai.today' | t }}</span><strong>{{ number(u.tokensToday) }}</strong></div>
            <div><span>{{ 'admin.ai.period' | t }}</span><strong>{{ number(u.tokensTotal) }}</strong></div>
            <div><span>{{ 'admin.ai.requests' | t }}</span><strong>{{ number(u.requestsTotal) }}</strong></div>
          </div>

          @if (u.perDay.length > 0) {
            <div class="aa__chart" role="img" [attr.aria-label]="'admin.ai.chart' | t">
              @for (d of u.perDay; track d.day) {
                <div class="aa__bar" [attr.title]="d.day + ' · ' + number(d.tokens) + ' tokens · ' + d.requests + ' ' + ('admin.ai.requests' | t)">
                  <span [style.height.%]="barHeight(d.tokens)"></span>
                </div>
              }
            </div>
            <div class="aa__axis"><span>{{ u.perDay[0].day }}</span><span>{{ u.perDay[u.perDay.length - 1].day }}</span></div>
          } @else {
            <p class="aa__hint">{{ 'admin.ai.noUsage' | t }}</p>
          }

          <div class="aa__tables">
            <div>
              <h3 class="aa__h2">{{ 'admin.ai.byFeature' | t }}</h3>
              <table>
                <thead><tr><th>{{ 'admin.ai.feature' | t }}</th><th class="num">Tokens</th><th class="num">{{ 'admin.ai.requests' | t }}</th></tr></thead>
                <tbody>
                  @for (f of u.perFeature; track f.feature) {
                    <tr><td>{{ f.feature }}</td><td class="num">{{ number(f.tokens) }}</td><td class="num">{{ number(f.requests) }}</td></tr>
                  } @empty {
                    <tr><td colspan="3" class="aa__empty">{{ 'admin.ai.noUsage' | t }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
            <div>
              <h3 class="aa__h2">{{ 'admin.ai.topUsers' | t }}</h3>
              <table>
                <thead><tr><th>{{ 'admin.ai.user' | t }}</th><th class="num">Tokens</th><th class="num">{{ 'admin.ai.requests' | t }}</th></tr></thead>
                <tbody>
                  @for (user of u.topUsers; track user.userId) {
                    <tr><td>{{ user.email || user.userId }}</td><td class="num">{{ number(user.tokens) }}</td><td class="num">{{ number(user.requests) }}</td></tr>
                  } @empty {
                    <tr><td colspan="3" class="aa__empty">{{ 'admin.ai.noUsage' | t }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        } @else {
          <div class="aa__loading"><app-spinner /></div>
        }
      </app-card>
    </div>
  `,
  styles: `
    .aa { padding-block: var(--space-xl); display: flex; flex-direction: column; gap: var(--space-lg); }
    .aa__loading { display: grid; place-items: center; padding: var(--space-xl); }
    .aa__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: var(--space-lg); }
    .aa__h { margin: 0 0 var(--space-md); font-size: var(--type-body-strong-size); font-weight: 600; color: var(--text); }
    .aa__h2 { margin: 0 0 var(--space-sm); font-size: var(--type-caption-size); font-weight: 600; color: var(--text); }
    .aa__facts { display: flex; flex-direction: column; gap: 10px; margin: 0; div { display: flex; justify-content: space-between; gap: var(--space-md); font-size: var(--type-caption-size); } dt { color: var(--text-subtle); } dd { margin: 0; color: var(--text); } }
    .aa__pill { padding: 2px 10px; font-size: var(--type-fine-size); font-weight: 600; border-radius: var(--radius-full);
      &--ok { color: var(--success); background: color-mix(in srgb, var(--success) 14%, transparent); }
      &--warn { color: var(--warning); background: color-mix(in srgb, var(--warning) 14%, transparent); }
      &--bad { color: var(--danger); background: var(--danger-surface); }
    }
    .aa__hint { display: flex; align-items: flex-start; gap: 6px; margin: var(--space-md) 0 0; font-size: var(--type-fine-size); line-height: 1.45; color: var(--text-subtle); }
    .aa__form { display: flex; flex-direction: column; gap: var(--space-md); }
    .aa__switch { display: flex; align-items: flex-start; gap: 12px; cursor: pointer; input { margin-top: 3px; width: 18px; height: 18px; accent-color: var(--accent); } span { display: flex; flex-direction: column; gap: 2px; } strong { font-size: var(--type-caption-size); color: var(--text); } small { font-size: var(--type-fine-size); color: var(--text-subtle); } }
    .aa__field { display: flex; flex-direction: column; gap: 6px; label { font-size: var(--type-caption-size); font-weight: 600; color: var(--text); }
      input { padding: 9px 12px; font: inherit; font-size: var(--type-caption-size); color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); &:focus-visible { outline: 2px solid var(--accent-focus); outline-offset: 1px; } }
    }
    .aa__usage-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); .aa__h { margin: 0; } select { padding: 6px 10px; font: inherit; font-size: var(--type-caption-size); color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); } }
    .aa__stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--space-md); margin: var(--space-md) 0; div { display: flex; flex-direction: column; gap: 2px; padding: 12px 14px; background: var(--surface-sunken); border-radius: var(--radius-md); } span { font-size: var(--type-fine-size); color: var(--text-subtle); } strong { font-size: 22px; font-variant-numeric: tabular-nums; color: var(--text); } }
    .aa__chart { display: flex; align-items: flex-end; gap: 3px; height: 130px; padding: 8px; background: var(--surface-sunken); border-radius: var(--radius-md); }
    .aa__bar { display: flex; align-items: flex-end; flex: 1; height: 100%; min-width: 2px; span { display: block; width: 100%; min-height: 2px; background: var(--accent); border-radius: 3px 3px 0 0; } &:hover span { opacity: 0.75; } }
    .aa__axis { display: flex; justify-content: space-between; margin-top: 4px; font-size: var(--type-fine-size); color: var(--text-subtle); }
    .aa__tables { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: var(--space-lg); margin-top: var(--space-lg); }
    table { width: 100%; border-collapse: collapse; font-size: var(--type-caption-size); }
    th { text-align: left; font-weight: 600; color: var(--text-subtle); font-size: var(--type-fine-size); padding: 6px 8px; border-bottom: 1px solid var(--divider); }
    td { padding: 7px 8px; color: var(--text); border-bottom: 1px solid var(--divider); overflow-wrap: anywhere; }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    .aa__empty { text-align: center; color: var(--text-subtle); }
  `,
})
export class AiAdminComponent implements OnInit {
  private readonly ai = inject(AiService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly periods = PERIODS;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly settings = signal<AiSettings | null>(null);
  readonly usage = signal<AiUsage | null>(null);
  readonly days = signal(14);

  readonly enabled = signal(true);
  readonly perUser = signal(0);
  readonly perWorkspace = signal(0);

  readonly dirty = computed(() => {
    const s = this.settings();
    return !!s && (s.enabled !== this.enabled() || s.dailyTokensPerUser !== this.perUser() || s.dailyTokensPerWorkspace !== this.perWorkspace());
  });
  private readonly peak = computed(() => Math.max(1, ...(this.usage()?.perDay.map((d) => d.tokens) ?? [0])));

  ngOnInit(): void {
    this.ai.settings().subscribe({
      next: (s) => {
        this.apply(s);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
    this.loadUsage();
  }

  private apply(s: AiSettings): void {
    this.settings.set(s);
    this.enabled.set(s.enabled);
    this.perUser.set(s.dailyTokensPerUser);
    this.perWorkspace.set(s.dailyTokensPerWorkspace);
  }

  private loadUsage(): void {
    this.usage.set(null);
    this.ai.usage(this.days()).subscribe({ next: (u) => this.usage.set(u), error: () => undefined });
  }

  setDays(days: number): void {
    this.days.set(days);
    this.loadUsage();
  }

  save(): void {
    this.saving.set(true);
    this.ai
      .updateSettings({ enabled: this.enabled(), dailyTokensPerUser: this.perUser(), dailyTokensPerWorkspace: this.perWorkspace() })
      .subscribe({
        next: (s) => {
          this.apply(s);
          this.saving.set(false);
          this.toast.success(this.i18n.translate('admin.ai.saved'));
        },
        error: (err) => {
          this.saving.set(false);
          this.toast.error(err?.message || this.i18n.translate('ai.error.GENERATION_FAILED'));
        },
      });
  }

  toNumber(value: string): number {
    const n = Math.floor(Number(value));
    return Number.isFinite(n) && n > 0 ? n : 0;
  }

  barHeight(tokens: number): number {
    return Math.max(2, Math.round((tokens / this.peak()) * 100));
  }

  number(n: number): string {
    return new Intl.NumberFormat(this.i18n.locale()).format(n);
  }

  formatDate(iso: string): string {
    return new Intl.DateTimeFormat(this.i18n.locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
  }
}
