import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Artifact, ArtifactType, Favorite } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PaginationComponent } from '@shared/ui/pagination/pagination.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { artifactIcon } from '@shared/utils/artifact-icon';
import { artifactTone } from '@shared/utils/artifact-tone';
import { FavoriteService } from '../data/favorite.service';
import { bindUrlState } from '@shared/utils/url-state';

const PAGE_SIZE = 20;

/** The caller's starred items in this workspace; click opens, the star removes. */
@Component({
  selector: 'app-starred',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    PaginationComponent,
    EmptyStateComponent,
    IconComponent,
    PageHeaderComponent,
    SpinnerComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './starred.component.html',
  styleUrl: './starred.component.scss',
})
export class StarredComponent implements OnInit {
  private readonly favorites = inject(FavoriteService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  /** Route param. */
  readonly documentWorkspaceId = input.required<string>();

  readonly items = signal<Favorite[]>([]);
  readonly loading = signal(true);
  readonly icon = artifactIcon;

  readonly page = signal(0);

  /** The address and this state are one: filters, order and page are read from it and kept in it. */
  private readonly urlState = bindUrlState([
    { signal: this.page, param: 'page', page: true },
  ]);
  readonly totalPages = signal(1);
  readonly first = signal(true);
  readonly last = signal(true);

  ngOnInit(): void {
    this.load(0);
  }

  goTo(page: number): void {
    this.load(page);
  }

  typeLabel(type: ArtifactType): string {
    return this.i18n.translate(`shared.type.${type}`);
  }

  private load(page: number): void {
    this.favorites.favoritesPage(this.documentWorkspaceId(), page, PAGE_SIZE).subscribe({
      next: (result) => {
        // The last page may have emptied since it was last read; step back to the one before.
        if (result.content.length === 0 && page > 0) return this.load(page - 1);
        this.items.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(Math.max(1, result.totalPages));
        this.first.set(result.first);
        this.last.set(result.last);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  colorOf(artifact: Artifact): string {
    return artifact.color || artifactTone(artifact.type);
  }

  open(artifact: Artifact): void {
    const base = ['/document-workspaces', this.documentWorkspaceId()];
    this.router.navigate(artifact.type === 'FOLDER' ? [...base, 'folder', artifact.id] : [...base, 'a', artifact.id]);
  }

  unstar(favorite: Favorite, event: Event): void {
    event.stopPropagation();
    this.favorites.remove(this.documentWorkspaceId(), favorite.artifactId).subscribe({
      next: () => {
        this.load(this.page());
        this.toast.success(this.i18n.translate('explorer.toast.unstarred'));
      },
      error: (err) => this.toast.error(err.message),
    });
  }
}
