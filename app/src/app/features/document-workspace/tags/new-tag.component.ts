import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { TagService } from '../data/tag.service';
import { WORKSPACE_COLORS } from '../workspace-colors';

/** Makes a tag: a name and a colour; back to the list once it exists. */
@Component({
  selector: 'app-new-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, PageHeaderComponent, TranslatePipe],
  templateUrl: './new-tag.component.html',
  styleUrl: './new-tag.component.scss',
})
export class NewTagComponent implements OnInit {
  private readonly service = inject(TagService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  /** Route param. */
  readonly documentWorkspaceId = input.required<string>();

  readonly swatches = WORKSPACE_COLORS;
  readonly name = signal('');
  readonly color = signal(WORKSPACE_COLORS[0]);
  readonly saving = signal(false);

  ngOnInit(): void {
    // The next colour in turn, so tags made one after another do not all look the same.
    this.service.list(this.documentWorkspaceId()).subscribe({
      next: (list) => this.color.set(WORKSPACE_COLORS[list.length % WORKSPACE_COLORS.length]),
    });
  }

  backUrl(): string {
    return `/document-workspaces/${this.documentWorkspaceId()}/tags`;
  }

  create(): void {
    const name = this.name().trim();
    if (!name || this.saving()) return;
    this.saving.set(true);
    this.service.create(this.documentWorkspaceId(), name, this.color()).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('tags.created'));
        void this.router.navigateByUrl(this.backUrl());
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
