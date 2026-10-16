import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, debounceTime, switchMap } from 'rxjs';
import { CommandPaletteService } from '@core/services/command-palette.service';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { SearchService } from '@features/document-workspace/search/data/search.service';
import { SearchHit } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';
import { artifactIcon } from '@shared/utils/artifact-icon';

interface Command {
  id: string;
  label: string;
  icon: IconName;
  hint?: string;
  run: () => void;
}

@Component({
  selector: 'app-command-palette',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, TranslatePipe],
  templateUrl: './command-palette.component.html',
  styleUrl: './command-palette.component.scss',
})
export class CommandPaletteComponent {
  private readonly palette = inject(CommandPaletteService);
  private readonly router = inject(Router);
  private readonly documentWorkspaces = inject(DocumentWorkspaceService);
  private readonly search = inject(SearchService);
  private readonly i18n = inject(TranslationService);

  readonly open = this.palette.open;
  readonly query = signal('');
  readonly activeIndex = signal(0);
  readonly hits = signal<SearchHit[]>([]);

  private readonly query$ = new Subject<string>();

  private navCommands(): Command[] {
    return [
      { id: 'home', label: this.i18n.translate('commandPalette.goHome'), icon: 'home', run: () => this.go('/') },
      { id: 'search', label: this.i18n.translate('commandPalette.search'), icon: 'search', run: () => this.go('/search') },
      {
        id: 'templates',
        label: this.i18n.translate('commandPalette.templates'),
        icon: 'layout-template',
        run: () => this.go('/templates'),
      },
      {
        id: 'trash',
        label: this.i18n.translate('commandPalette.trash'),
        icon: 'trash',
        run: () => this.go('/document-workspaces/trash'),
      },
      {
        id: 'notifications',
        label: this.i18n.translate('commandPalette.notifications'),
        icon: 'bell',
        run: () => this.go('/notifications'),
      },
      {
        id: 'settings',
        label: this.i18n.translate('commandPalette.settings'),
        icon: 'settings',
        run: () => this.go('/settings'),
      },
      {
        id: 'new-workspace',
        label: this.i18n.translate('commandPalette.newDocumentWorkspace'),
        icon: 'plus',
        run: () => this.go('/document-workspaces/new'),
      },
    ];
  }

  readonly results = computed(() => {
    const q = this.query().trim().toLowerCase();
    const navCommands = this.navCommands();
    const nav = q ? navCommands.filter((c) => c.label.toLowerCase().includes(q)) : navCommands;
    const hitCommands: Command[] = this.hits().map((h) => ({
      id: h.id,
      label: h.name,
      icon: artifactIcon(h.type),
      hint: this.i18n.translate('commandPalette.documentHint'),
      run: () => this.go(`/document-workspaces/${h.workspaceId}/a/${h.id}`),
    }));
    return [...hitCommands, ...nav];
  });

  constructor() {
    effect(() => {
      if (this.open()) {
        this.query.set('');
        this.hits.set([]);
        this.activeIndex.set(0);
      }
    });

    this.query$
      .pipe(
        debounceTime(200),
        switchMap((q) => {
          const ws = this.documentWorkspaces.active() ?? this.documentWorkspaces.documentWorkspaces()[0];
          if (!q.trim() || !ws) {
            this.hits.set([]);
            return [];
          }
          return this.search.search(ws.id, q);
        }),
      )
      .subscribe({
        next: (result) => this.hits.set(result.hits.slice(0, 6)),
        error: () => this.hits.set([]),
      });
  }

  onInput(value: string): void {
    this.query.set(value);
    this.activeIndex.set(0);
    this.query$.next(value);
  }

  onKeydown(event: KeyboardEvent): void {
    const items = this.results();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (items.length > 0) {
          this.activeIndex.set((this.activeIndex() + 1) % items.length);
        }
        break;
      case 'ArrowUp':
        event.preventDefault();
        if (items.length > 0) {
          this.activeIndex.set((this.activeIndex() - 1 + items.length) % items.length);
        }
        break;
      case 'Enter':
        event.preventDefault();
        items[this.activeIndex()]?.run();
        break;
      case 'Escape':
        this.palette.hide();
        break;
    }
  }

  runAt(index: number): void {
    this.results()[index]?.run();
  }

  close(): void {
    this.palette.hide();
  }

  private go(url: string): void {
    this.palette.hide();
    this.router.navigateByUrl(url);
  }
}
