import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { TableResult } from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { tableAsMarkdown, tableAsTsv, workbookContent } from '../lib/table-builder';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { fileProblem } from './scan-tool.component';
import { AiToolBase } from './tool-base';

/** A table out of pasted text, documents, a photo or a PDF, saved as a spreadsheet. */
@Component({
  selector: 'app-ai-table-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.table.intro' | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        <div class="ait__field">
          <label class="ait__label" for="table-text">{{ 'ai.table.text' | t }}</label>
          <textarea id="table-text" class="ait__textarea" rows="5" maxlength="30000" [placeholder]="'ai.table.textPlaceholder' | t" [value]="text()" (input)="text.set($any($event.target).value)"></textarea>
        </div>

        <div class="ait__field">
          <span class="ait__label">{{ 'ai.table.file' | t }}</span>
          <div class="ait__actions">
            @if (file(); as f) {
              <span class="tbl__file"><app-icon name="image" [size]="14" />{{ f.name }}</span>
              <app-button variant="text" size="sm" icon="x" (click)="file.set(null)">{{ 'ai.scan.remove' | t }}</app-button>
            } @else {
              <app-button variant="secondary" size="sm" icon="upload" (click)="input.click()">{{ 'ai.scan.choose' | t }}</app-button>
              <span class="ait__hint">{{ 'ai.scan.limits' | t }}</span>
            }
            <input #input type="file" hidden accept="image/png,image/jpeg,image/gif,image/webp,application/pdf" (change)="onPick($event)" />
          </div>
        </div>

        <div class="ait__field">
          <label class="ait__label" for="table-extra">{{ 'ai.field.instructions' | t }}</label>
          <input id="table-extra" class="ait__input" maxlength="500" [placeholder]="'ai.table.instructionsPlaceholder' | t" [value]="instructions()" (input)="instructions.set($any($event.target).value)" />
        </div>

        <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }
        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">{{ 'ai.table.generate' | t }}</app-button>
        </div>
      </form>

      @if (table(); as t) {
        <section class="ait__result">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ t.title }}</h3>
            <span class="ait__live">{{ 'ai.table.size' | t: { rows: t.rows.length, cols: t.headers.length } }}</span>
          </div>
          <div class="tbl__scroll" tabindex="0" role="region" [attr.aria-label]="t.title">
            <table class="tbl">
              <thead><tr>@for (h of t.headers; track $index) { <th scope="col">{{ h }}</th> }</tr></thead>
              <tbody>
                @for (row of t.rows; track $index) {
                  <tr>@for (cell of row.cells; track $index) { <td>{{ cell }}</td> }</tr>
                }
              </tbody>
            </table>
          </div>
          <div class="ait__actions">
            <app-button variant="dark" icon="table" [loading]="saving()" (click)="save()">{{ 'ai.table.create' | t }}</app-button>
            <app-button variant="secondary" icon="copy" (click)="copySheet()">{{ 'ai.table.copySheet' | t }}</app-button>
            <app-button variant="secondary" icon="copy" (click)="copyMarkdown()">{{ 'ai.table.copyMarkdown' | t }}</app-button>
            <app-button variant="text" icon="sparkles" (click)="generate()" [disabled]="!canGenerate()">{{ 'ai.regenerate' | t }}</app-button>
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .tbl__file { display: inline-flex; align-items: center; gap: 6px; font-size: var(--type-caption-size); color: var(--text); overflow-wrap: anywhere; }
    .tbl__scroll { max-height: 440px; overflow: auto; border: 1px solid var(--border); border-radius: var(--radius-md); &:focus-visible { outline: 2px solid var(--accent-focus); } }
    .tbl { width: 100%; border-collapse: collapse; font-size: var(--type-caption-size); }
    .tbl th { position: sticky; top: 0; padding: 8px 12px; text-align: left; font-weight: 600; color: var(--text); background: color-mix(in srgb, var(--accent) 10%, var(--surface-sunken)); border-bottom: 1px solid var(--border); white-space: nowrap; }
    .tbl td { padding: 7px 12px; color: var(--text); border-bottom: 1px solid var(--divider); vertical-align: top; }
    .tbl tbody tr:nth-child(even) td { background: var(--surface-sunken); }
  `,
})
export class TableToolComponent extends AiToolBase {
  readonly text = signal('');
  readonly instructions = signal('');
  readonly sources = signal<string[]>([]);
  readonly file = signal<File | null>(null);
  readonly table = signal<TableResult | null>(null);

  readonly canGenerate = computed(
    () => !this.busy() && (this.text().trim().length > 0 || this.sources().length > 0 || !!this.file()),
  );

  onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const problem = fileProblem(file);
    if (problem) {
      this.error.set(this.t(problem === 'type' ? 'ai.error.UNSUPPORTED_FILE' : 'ai.error.FILE_TOO_LARGE'));
      return;
    }
    this.error.set('');
    this.file.set(file);
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    const instructions = this.instructions().trim() || undefined;
    const file = this.file();
    const request$ = file && !this.text().trim() && this.sources().length === 0
      ? this.ai.tableFromFile(file, this.workspaceId(), instructions)
      : this.ai.table({
          workspaceId: this.workspaceId(),
          text: this.text().trim() || undefined,
          sources: this.sources().map((artifactId) => ({ artifactId })),
          instructions,
        });
    request$.subscribe({
      next: (table) => {
        this.table.set(table);
        this.busy.set(false);
        this.refreshStatus();
      },
      error: (err) => this.fail(err),
    });
  }

  save(): void {
    const table = this.table();
    if (table) {
      this.keep({ name: this.nameOf(table.title, ''), type: 'SPREADSHEET', content: workbookContent(table) });
    }
  }

  copySheet(): void {
    const table = this.table();
    if (table) {
      void this.copy(tableAsTsv(table));
    }
  }

  copyMarkdown(): void {
    const table = this.table();
    if (table) {
      void this.copy(tableAsMarkdown(table));
    }
  }
}
