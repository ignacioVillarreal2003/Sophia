import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Tag } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { bindUrlState } from '@shared/utils/url-state';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { TagService } from '../data/tag.service';

/** Rows on one page of the list. */
const PAGE_SIZE = 15;

/** The tags of a workspace, paged, with a way to make one and to delete them. */
@Component({
  selector: 'app-tags',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, EmptyStateComponent, RouterLink, PageHeaderComponent, PaginationComponent, SpinnerComponent, RelativeTimePipe, TranslatePipe],
  templateUrl: './tags.component.html',
  styleUrl: './tags.component.scss',
})
export class TagsComponent implements OnInit {
  private readonly service = inject(TagService);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  /** Route param. */
  readonly documentWorkspaceId = input.required<string>();

  readonly tags = signal<Tag[]>([]);
  /** The page of the list on show; the list is read whole and cut here. */
  readonly page = signal(0);
  private readonly urlState = bindUrlState([{ signal: this.page, param: 'page', page: true }]);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.tags().length / PAGE_SIZE)));
  readonly pageTags = computed(() => {
    const from = Math.min(this.page(), this.totalPages() - 1) * PAGE_SIZE;
    return this.tags().slice(from, from + PAGE_SIZE);
  });
  readonly loading = signal(true);
  ngOnInit(): void {
    this.service.list(this.documentWorkspaceId()).subscribe({
      next: (list) => {
        this.tags.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  newUrl(): string {
    return `/document-workspaces/${this.documentWorkspaceId()}/tags/new`;
  }

  async remove(tag: Tag): Promise<void> {
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('tags.delete'),
      message: this.i18n.translate('tags.delete.confirm', { name: tag.name }),
      confirmLabel: this.i18n.translate('tags.delete'),
      danger: true,
    });
    if (!ok) return;
    this.service.remove(this.documentWorkspaceId(), tag.id).subscribe({
      next: () => this.tags.update((list) => list.filter((t) => t.id !== tag.id)),
      error: (err) => this.toast.error(err.message),
    });
  }
}
