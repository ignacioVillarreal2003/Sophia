import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { OcrMode } from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { MarkdownViewComponent } from '../../document-workspace/markdown/markdown-view.component';
import { AiToolBase } from './tool-base';

const MODES: OcrMode[] = ['TRANSCRIBE', 'NOTES'];
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const MAX_PDF_BYTES = 15 * 1024 * 1024;
const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp'];

/** Why a file cannot be read, or null when it can. The server checks again; this just saves the round trip. */
export function fileProblem(file: { name: string; type: string; size: number }): 'type' | 'size' | null {
  const name = file.name.toLowerCase();
  const pdf = file.type === 'application/pdf' || name.endsWith('.pdf');
  const image = IMAGE_TYPES.includes(file.type) || /\.(png|jpe?g|gif|webp)$/.test(name);
  if (!pdf && !image) {
    return 'type';
  }
  return file.size > (pdf ? MAX_PDF_BYTES : MAX_IMAGE_BYTES) ? 'size' : null;
}

/** Scans, photos of a notebook page and PDFs become editable text. */
@Component({
  selector: 'app-ai-scan-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SpinnerComponent, MarkdownViewComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.scan.intro' | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        <div
          class="scan__drop"
          [class.scan__drop--over]="over()"
          [class.scan__drop--has]="!!file()"
          (dragover)="onDragOver($event)"
          (dragleave)="over.set(false)"
          (drop)="onDrop($event)"
        >
          @if (file(); as f) {
            @if (preview()) {
              <img class="scan__thumb" [src]="preview()" [alt]="f.name" />
            } @else {
              <app-icon name="file" [size]="30" />
            }
            <strong class="scan__name">{{ f.name }}</strong>
            <span class="ait__hint">{{ sizeLabel() }}</span>
            <app-button variant="text" size="sm" icon="x" (click)="clear()">{{ 'ai.scan.remove' | t }}</app-button>
          } @else {
            <app-icon name="upload" [size]="28" />
            <strong>{{ 'ai.scan.drop' | t }}</strong>
            <span class="ait__hint">{{ 'ai.scan.limits' | t }}</span>
            <app-button variant="secondary" size="sm" icon="image" (click)="input.click()">{{ 'ai.scan.choose' | t }}</app-button>
          }
          <input #input type="file" hidden accept="image/png,image/jpeg,image/gif,image/webp,application/pdf" (change)="onPick($event)" />
        </div>

        <div class="ait__field">
          <span class="ait__label">{{ 'ai.scan.mode' | t }}</span>
          <div class="ait__chips" role="radiogroup" [attr.aria-label]="'ai.scan.mode' | t">
            @for (m of modes; track m) {
              <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="mode() === m" [attr.aria-checked]="mode() === m" (click)="mode.set(m)">{{ 'ai.scan.mode.' + m | t }}</button>
            }
          </div>
          <span class="ait__hint">{{ 'ai.scan.mode.' + mode() + '.hint' | t }}</span>
        </div>

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }

        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">{{ 'ai.scan.generate' | t }}</app-button>
          @if (busy()) {
            <app-button variant="secondary" (click)="cancel()">{{ 'ai.stop' | t }}</app-button>
          }
        </div>
        <p class="ait__hint">{{ 'ai.scan.privacy' | t }}</p>
      </form>

      @if (text() || busy()) {
        <section class="ait__result" aria-live="polite">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ 'ai.result' | t }}</h3>
            @if (busy()) {
              <span class="ait__live"><app-spinner [size]="14" />{{ 'ai.scan.reading' | t }}</span>
            } @else if (truncated()) {
              <span class="ait__live">{{ 'ai.truncated' | t }}</span>
            }
          </div>
          <div class="ait__paper"><app-markdown-view [source]="text()" [readonly]="true" /></div>
          @if (!busy() && text()) {
            <div class="ait__actions">
              <app-button variant="dark" icon="file-text" [loading]="saving()" (click)="save()">{{ 'ai.createDocument' | t }}</app-button>
              <app-button variant="secondary" icon="copy" (click)="copy(text())">{{ 'ai.copy' | t }}</app-button>
            </div>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .scan__drop {
      display: flex; flex-direction: column; align-items: center; gap: 8px; padding: var(--space-xl) var(--space-lg); text-align: center;
      color: var(--text-muted); background: var(--surface); border: 2px dashed var(--border); border-radius: var(--radius-lg);
      transition: border-color var(--duration-fast) var(--ease-standard), background var(--duration-fast) var(--ease-standard);
      &--over { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 8%, var(--surface)); }
      &--has { border-style: solid; }
      strong { color: var(--text); font-size: var(--type-caption-size); }
    }
    .scan__thumb { max-width: 100%; max-height: 220px; object-fit: contain; border-radius: var(--radius-md); box-shadow: 0 0 0 1px var(--border); }
    .scan__name { max-width: 100%; overflow-wrap: anywhere; }
  `,
})
export class ScanToolComponent extends AiToolBase {
  private readonly destroyRef = inject(DestroyRef);

  readonly modes = MODES;
  readonly mode = signal<OcrMode>('TRANSCRIBE');
  readonly file = signal<File | null>(null);
  readonly preview = signal<string | null>(null);
  readonly over = signal(false);
  readonly text = signal('');
  readonly truncated = signal(false);

  readonly canGenerate = computed(() => !this.busy() && !!this.file());
  readonly sizeLabel = computed(() => {
    const size = this.file()?.size ?? 0;
    return size >= 1024 * 1024 ? `${(size / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(size / 1024))} KB`;
  });

  constructor() {
    super();
    this.destroyRef.onDestroy(() => this.revoke());
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.over.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.over.set(false);
    const file = event.dataTransfer?.files?.[0];
    if (file) {
      this.take(file);
    }
  }

  onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) {
      this.take(file);
    }
  }

  private take(file: File): void {
    const problem = fileProblem(file);
    if (problem) {
      this.error.set(this.t(problem === 'type' ? 'ai.error.UNSUPPORTED_FILE' : 'ai.error.FILE_TOO_LARGE'));
      return;
    }
    this.error.set('');
    this.revoke();
    this.file.set(file);
    this.preview.set(file.type.startsWith('image/') ? URL.createObjectURL(file) : null);
    this.text.set('');
  }

  clear(): void {
    this.revoke();
    this.file.set(null);
    this.text.set('');
  }

  private revoke(): void {
    const url = this.preview();
    if (url) {
      URL.revokeObjectURL(url);
    }
    this.preview.set(null);
  }

  generate(): void {
    const file = this.file();
    if (!file || !this.canGenerate()) {
      return;
    }
    this.begin();
    this.text.set('');
    this.truncated.set(false);
    this.stream = this.ai.ocr(file, this.mode(), this.workspaceId(), {
      delta: (piece) => this.text.update((current) => current + piece),
      done: (data) => {
        this.truncated.set(!!data.truncated);
        this.busy.set(false);
        this.refreshStatus();
      },
      error: (err) => this.fail(err),
    });
  }

  save(): void {
    const first = this.text().match(/^#\s+(.+)$/m)?.[1];
    this.keep({
      name: this.nameOf(first ?? '', (this.file()?.name ?? '').replace(/\.[^.]+$/, '')),
      type: 'MARKDOWN_DOCUMENT',
      content: this.text(),
    });
  }
}
