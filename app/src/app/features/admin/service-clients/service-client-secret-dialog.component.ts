import {ChangeDetectionStrategy, Component, inject, signal} from '@angular/core';
import {DIALOG_DATA, DialogRef} from '@angular/cdk/dialog';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {IconComponent} from '@shared/ui/icon/icon.component';

export interface ServiceClientSecretData {
  name: string;
  secret: string;
}

@Component({
  selector: 'app-service-client-secret-dialog',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, TranslatePipe],
  template: `
    <div class="scs">
      <h2 class="scs__title">{{ 'admin.serviceClients.secret.title' | t: {name: data.name} }}</h2>
      <p class="scs__warning">{{ 'admin.serviceClients.secret.warning' | t }}</p>
      <div class="scs__box">
        <code class="scs__secret">{{ data.secret }}</code>
        <button type="button" class="scs__copy" (click)="copy()"
                [attr.aria-label]="'admin.serviceClients.secret.copyAria' | t">
          <app-icon [name]="copied() ? 'check' : 'copy'" [size]="16"/>
        </button>
      </div>
      <div class="scs__actions">
        <app-button (click)="ref.close()">{{ 'admin.serviceClients.secret.saved' | t }}</app-button>
      </div>
    </div>
  `,
  styles: [
    `
      .scs {
        padding: var(--space-lg);
        width: min(90vw, 480px);
      }

      .scs__title {
        font-family: var(--font-text);
        font-size: var(--type-tagline-size);
        font-weight: 600;
      }

      .scs__warning {
        margin-top: var(--space-xs);
        color: var(--text-muted);
        font-size: var(--type-body-size);
        line-height: 1.5;
      }

      .scs__box {
        margin-top: var(--space-md);
        display: flex;
        align-items: center;
        gap: var(--space-xs);
        padding: 12px 14px;
        background: var(--surface-sunken);
        border-radius: var(--radius-md);
      }

      .scs__secret {
        flex: 1;
        min-width: 0;
        overflow-x: auto;
        white-space: nowrap;
        font-family: monospace;
        font-size: 13px;
        color: var(--text);
      }

      .scs__copy {
        display: grid;
        place-items: center;
        width: 32px;
        height: 32px;
        flex-shrink: 0;
        border-radius: var(--radius-sm);
        border: none;
        background: transparent;
        color: var(--text-subtle);
        cursor: pointer;

        &:hover {
          background: var(--surface-raised);
          color: var(--text);
        }
      }

      .scs__actions {
        display: flex;
        justify-content: flex-end;
        margin-top: var(--space-lg);
      }
    `,
  ],
})
export class ServiceClientSecretDialogComponent {
  readonly ref = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<ServiceClientSecretData>(DIALOG_DATA);
  readonly copied = signal(false);

  async copy(): Promise<void> {
    await navigator.clipboard.writeText(this.data.secret);
    this.copied.set(true);
    setTimeout(() => this.copied.set(false), 2000);
  }
}
