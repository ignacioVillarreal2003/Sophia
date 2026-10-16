import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { SearchHit } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { SearchService } from './data/search.service';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { artifactIcon } from '@shared/utils/artifact-icon';

@Component({
  selector: 'app-search',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    TranslatePipe,
  ],
  templateUrl: './search.component.html',
  styleUrl: './search.component.scss',
})
export class SearchComponent implements OnInit {
  private readonly service = inject(SearchService);
  private readonly documentWorkspaces = inject(DocumentWorkspaceService);
  private readonly router = inject(Router);

  readonly query = signal('');
  readonly hits = signal<SearchHit[]>([]);
  readonly loading = signal(false);
  readonly searched = signal(false);

  private readonly query$ = new Subject<string>();

  readonly activeWorkspace = computed(
    () => this.documentWorkspaces.active() ?? this.documentWorkspaces.documentWorkspaces()[0] ?? null,
  );

  ngOnInit(): void {
    if (!this.documentWorkspaces.loaded()) {
      this.documentWorkspaces.loadAll().subscribe();
    }
    this.query$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => {
          const ws = this.activeWorkspace();
          if (!q.trim() || !ws) {
            this.loading.set(false);
            return [];
          }
          this.loading.set(true);
          return this.service.search(ws.id, q);
        }),
      )
      .subscribe({
        next: (result) => {
          this.hits.set(result.hits);
          this.loading.set(false);
          this.searched.set(true);
        },
        error: () => {
          this.hits.set([]);
          this.loading.set(false);
          this.searched.set(true);
        },
      });
  }

  onInput(value: string): void {
    this.query.set(value);
    this.query$.next(value);
  }

  icon = artifactIcon;

  open(hit: SearchHit): void {
    this.router.navigate(['/document-workspaces', hit.workspaceId, 'a', hit.id]);
  }
}
