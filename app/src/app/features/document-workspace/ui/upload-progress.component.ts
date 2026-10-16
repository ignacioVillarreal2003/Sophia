import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { UploadManagerService } from '../data/upload-manager.service';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';

/** Floating panel showing active uploads. Mount once inside the shell. */
@Component({
  selector: 'app-upload-progress',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  template: `
    @if (uploads.tasks().length > 0) {
      <div class="up">
        <div class="up__head">{{ 'documentView.uploading' | t: { count: uploads.tasks().length } }}</div>
        @for (task of uploads.tasks(); track task.id) {
          <div class="up__item">
            <app-icon [name]="icon(task.status)" [size]="16" [class]="'up__icon up__icon--' + task.status" />
            <div class="up__body">
              <span class="up__name">{{ task.name }}</span>
              <div class="up__bar">
                <span class="up__bar-fill" [style.width.%]="task.percent"></span>
              </div>
            </div>
            <span class="up__pct">{{ task.percent }}%</span>
          </div>
        }
      </div>
    }
  `,
  styleUrl: './upload-progress.component.scss',
})
export class UploadProgressComponent {
  readonly uploads = inject(UploadManagerService);

  icon(status: string): 'upload' | 'check-circle' | 'alert-circle' {
    return status === 'done' ? 'check-circle' : status === 'error' ? 'alert-circle' : 'upload';
  }
}
