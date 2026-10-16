import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from '@core/services/toast.service';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

@Component({
  selector: 'app-toast-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  template: `
    <div class="toast-host" role="region" aria-live="polite" [attr.aria-label]="'a11y.toastRegion' | t">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast" [class]="'toast--' + toast.variant">
          <app-icon class="toast__icon" [name]="iconFor(toast.variant)" [size]="18" />
          <span class="toast__msg">{{ toast.message }}</span>
          @if (toast.action) {
            <button class="toast__action" (click)="toast.action!.run(); toasts.dismiss(toast.id)">
              {{ toast.action.label }}
            </button>
          }
          <button class="toast__close" (click)="toasts.dismiss(toast.id)" [attr.aria-label]="'a11y.close' | t">
            <app-icon name="x" [size]="14" />
          </button>
        </div>
      }
    </div>
  `,
  styleUrl: './toast-host.component.scss',
})
export class ToastHostComponent {
  readonly toasts = inject(ToastService);

  iconFor(variant: string): IconName {
    switch (variant) {
      case 'success':
        return 'check-circle';
      case 'danger':
        return 'alert-circle';
      case 'warning':
        return 'alert-circle';
      default:
        return 'info';
    }
  }
}
