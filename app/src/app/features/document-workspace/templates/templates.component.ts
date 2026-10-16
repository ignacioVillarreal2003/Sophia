import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Template } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { DocumentWorkspaceService } from '@features/document-workspace/data/document-workspace.service';
import { TemplateService } from './data/template.service';
import { CardComponent } from '@shared/ui/card/card.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { BadgeComponent } from '@shared/ui/badge/badge.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';

@Component({
  selector: 'app-templates',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CardComponent,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    BadgeComponent,
    PageHeaderComponent,
    TranslatePipe,
  ],
  templateUrl: './templates.component.html',
  styleUrl: './templates.component.scss',
})
export class TemplatesComponent implements OnInit {
  private readonly service = inject(TemplateService);
  private readonly documentWorkspaces = inject(DocumentWorkspaceService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly templates = signal<Template[]>([]);
  readonly loading = signal(true);

  readonly activeWorkspace = computed(
    () => this.documentWorkspaces.active() ?? this.documentWorkspaces.documentWorkspaces()[0] ?? null,
  );

  ngOnInit(): void {
    if (!this.documentWorkspaces.loaded()) {
      this.documentWorkspaces.loadAll().subscribe(() => this.load());
    } else {
      this.load();
    }
  }

  private load(): void {
    const ws = this.activeWorkspace();
    if (!ws) {
      this.loading.set(false);
      return;
    }
    this.service.list(ws.id).subscribe({
      next: (list) => {
        this.templates.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  use(template: Template): void {
    const ws = this.activeWorkspace();
    if (!ws) return;
    this.service.instantiate(ws.id, template.id).subscribe({
      next: (artifact) => {
        this.toast.success(this.i18n.translate('templates.toast.applied'));
        this.router.navigate(['/document-workspaces', ws.id, 'a', artifact.id]);
      },
      error: (err) => this.toast.error(err.message),
    });
  }
}
