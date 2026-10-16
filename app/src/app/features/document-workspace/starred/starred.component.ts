import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Artifact, Favorite } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { CardComponent } from '@shared/ui/card/card.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { artifactIcon } from '@shared/utils/artifact-icon';
import { FavoriteService } from '../data/favorite.service';

/** The caller's starred items in this workspace; click opens, the star removes. */
@Component({
  selector: 'app-starred',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CardComponent,
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

  ngOnInit(): void {
    this.favorites.favorites(this.documentWorkspaceId()).subscribe({
      next: (list) => {
        this.items.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  colorOf(artifact: Artifact): string | null {
    return artifact.color || null;
  }

  open(artifact: Artifact): void {
    const base = ['/document-workspaces', this.documentWorkspaceId()];
    this.router.navigate(artifact.type === 'FOLDER' ? [...base, 'folder', artifact.id] : [...base, 'a', artifact.id]);
  }

  unstar(favorite: Favorite, event: Event): void {
    event.stopPropagation();
    this.favorites.remove(this.documentWorkspaceId(), favorite.artifactId).subscribe({
      next: () => {
        this.items.update((list) => list.filter((f) => f.id !== favorite.id));
        this.toast.success(this.i18n.translate('explorer.toast.unstarred'));
      },
      error: (err) => this.toast.error(err.message),
    });
  }
}
