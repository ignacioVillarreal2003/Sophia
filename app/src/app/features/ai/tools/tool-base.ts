import { Directive, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { CreateArtifactRequest } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { ArtifactTreeStore } from '../../document-workspace/data/artifact-tree.store';
import { AiError, AiService, AiStream } from '../data/ai.service';
import { aiErrorMessage } from '../lib/errors';

/**
 * What every tool does around its own form: ask the service, show a busy state, show a readable error, and turn the
 * result into a document of the workspace. Results are drafts until the person chooses to keep them.
 */
@Directive()
export abstract class AiToolBase {
  protected readonly ai = inject(AiService);
  protected readonly i18n = inject(TranslationService);
  protected readonly toast = inject(ToastService);
  protected readonly router = inject(Router);
  protected readonly tree = inject(ArtifactTreeStore);

  readonly workspaceId = input.required<string>();

  readonly busy = signal(false);
  readonly error = signal('');
  readonly saving = signal(false);

  protected stream: AiStream | null = null;

  protected t(key: string, params?: Record<string, string | number>): string {
    return this.i18n.translate(key, params);
  }

  protected begin(): void {
    this.error.set('');
    this.busy.set(true);
  }

  protected fail(error: AiError | { code?: string; message?: string; status?: number }): void {
    this.error.set(aiErrorMessage(error, (key) => this.t(key)));
    this.busy.set(false);
    this.refreshStatus();
  }

  /** The allowance changes with every generation, so the header is refreshed after each one. */
  protected refreshStatus(): void {
    this.ai.loadStatus(this.workspaceId()).subscribe({ error: () => undefined });
  }

  cancel(): void {
    this.stream?.cancel();
    this.stream = null;
    this.busy.set(false);
  }

  /** Creates the document in the workspace root and offers to open it. */
  protected keep(request: CreateArtifactRequest, done?: () => void): void {
    this.saving.set(true);
    (this.tree.create(request) as Observable<{ id: string }>).subscribe({
      next: (artifact) => {
        this.saving.set(false);
        this.toast.success(this.t('ai.saved', { name: request.name }), {
          action: {
            label: this.t('ai.open'),
            run: () => this.router.navigate(['/document-workspaces', this.workspaceId(), 'a', artifact.id]),
          },
        });
        done?.();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(err?.message || this.t('ai.error.GENERATION_FAILED'));
      },
    });
  }

  protected async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.toast.success(this.t('ai.copied'));
    } catch {
      this.toast.error(this.t('ai.copyFailed'));
    }
  }

  /** Unnamed results get the topic or the first source as their name. */
  protected nameOf(title: string, fallback: string): string {
    const name = (title || fallback || this.t('ai.untitled')).trim();
    return name.length > 120 ? name.slice(0, 120) : name;
  }
}
