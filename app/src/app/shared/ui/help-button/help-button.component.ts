import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { IconComponent } from '../icon/icon.component';
import { DialogService } from '../dialog/dialog.service';
import { HelpDialogComponent, HelpDialogData } from './help-dialog.component';
import { TranslationService } from '@core/services/i18n';

@Component({
  selector: 'app-help-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <button type="button" class="hb" [attr.aria-label]="i18n.translate('common.help')" (click)="open()">
      <app-icon name="help-circle" [size]="20" />
    </button>
  `,
  styles: [
    `
      .hb {
        position: fixed;
        right: var(--space-lg);
        bottom: var(--space-lg);
        z-index: 40;
        width: 36px;
        height: 36px;
        display: grid;
        place-items: center;
        border: none;
        border-radius: var(--radius-pill);
        background: var(--accent);
        color: var(--on-accent);
        cursor: pointer;
        box-shadow: 0 4px 10px rgb(0 0 0 / 20%);
        transition: filter var(--duration-fast) var(--ease-standard);

        &:hover {
          filter: brightness(0.93);
        }

        &:focus-visible {
          outline: none;
          box-shadow: var(--focus-ring);
        }
      }
    `,
  ],
})
export class HelpButtonComponent {
  private readonly dialog = inject(DialogService);
  readonly i18n = inject(TranslationService);

  readonly title = input.required<string>();
  readonly paragraphs = input.required<string[]>();

  open(): void {
    this.dialog.open<HelpDialogComponent, HelpDialogData, void>(HelpDialogComponent, {
      data: { title: this.title(), paragraphs: this.paragraphs() },
    });
  }
}
