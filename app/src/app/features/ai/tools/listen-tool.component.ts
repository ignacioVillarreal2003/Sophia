import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Artifact } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { ArtifactService } from '../../document-workspace/data/artifact.service';
import { DocumentContentService } from '../../document-workspace/data/document-content.service';
import { plainTextForSpeech } from '../lib/speech';
import { SpeechPlayerComponent } from '../ui/speech-player.component';
import { AiToolBase } from './tool-base';

const MAX_CHARS = 20000;

/**
 * Listen to a note. Reading it as it is costs nothing and happens on the device; the AI version first rewrites it for
 * the ear (lists become sentences, tables are described, symbols are spoken), which is better for dense material.
 */
@Component({
  selector: 'app-ai-listen-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SpeechPlayerComponent, SpinnerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.listen.intro' | t }}</p>

      <div class="ait__form">
        <div class="ait__field">
          <label class="ait__label" for="listen-doc">{{ 'ai.listen.fromDocument' | t }}</label>
          <select id="listen-doc" class="ait__select" (change)="load($any($event.target).value)">
            <option value="">{{ 'ai.listen.pick' | t }}</option>
            @for (d of documents(); track d.id) {
              <option [value]="d.id">{{ d.name }}</option>
            }
          </select>
        </div>
        <div class="ait__field">
          <label class="ait__label" for="listen-text">{{ 'ai.listen.text' | t }}</label>
          <textarea id="listen-text" class="ait__textarea" rows="8" [attr.maxlength]="max" [placeholder]="'ai.listen.textPlaceholder' | t" [value]="text()" (input)="onText($any($event.target).value)"></textarea>
          <span class="ait__count">{{ text().length }} / {{ max }}</span>
        </div>
        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }
      </div>

      <section class="ait__result">
        <div class="ait__result-head">
          <h3 class="ait__result-title">{{ 'ai.listen.title' | t }}</h3>
          <div class="ait__chips" role="radiogroup" [attr.aria-label]="'ai.listen.version' | t">
            <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="!useScript()" [attr.aria-checked]="!useScript()" (click)="useScript.set(false)">{{ 'ai.listen.asIs' | t }}</button>
            <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="useScript()" [attr.aria-checked]="useScript()" (click)="useScript.set(true)">{{ 'ai.listen.ai' | t }}</button>
          </div>
        </div>

        @if (useScript()) {
          <div class="ait__actions">
            <app-button variant="secondary" size="sm" icon="sparkles" [loading]="busy()" [disabled]="!canScript()" (click)="makeScript()">{{ 'ai.listen.makeScript' | t }}</app-button>
            @if (busy()) {
              <app-button variant="text" size="sm" (click)="cancel()">{{ 'ai.stop' | t }}</app-button>
              <span class="ait__live"><app-spinner [size]="14" />{{ 'ai.writing' | t }}</span>
            }
          </div>
          @if (script()) {
            <div class="ait__paper"><p class="listen__script">{{ script() }}</p></div>
          }
        }

        <app-speech-player [text]="spoken()" />
      </section>
    </div>
  `,
  styles: `
    .listen__script { margin: 0; white-space: pre-wrap; font-size: var(--type-caption-size); line-height: 1.6; color: var(--text); }
  `,
})
export class ListenToolComponent extends AiToolBase implements OnInit {
  private readonly artifacts = inject(ArtifactService);
  private readonly content = inject(DocumentContentService);

  readonly max = MAX_CHARS;
  readonly documents = signal<Artifact[]>([]);
  readonly text = signal('');
  readonly useScript = signal(false);
  readonly script = signal('');

  /** What the voice reads: the AI version when chosen and ready, otherwise the note with its symbols removed. */
  readonly spoken = computed(() => (this.useScript() && this.script() ? this.script() : plainTextForSpeech(this.text())));
  readonly canScript = computed(() => !this.busy() && this.text().trim().length > 0);

  ngOnInit(): void {
    this.artifacts.documents(this.workspaceId(), '', 100).subscribe({
      next: (docs) => this.documents.set(docs.filter((d) => d.type === 'MARKDOWN_DOCUMENT' || d.type === 'CHECKLIST')),
      error: () => undefined,
    });
  }

  onText(value: string): void {
    this.text.set(value);
    this.script.set(''); // a script belongs to the text it was made from
  }

  load(id: string): void {
    if (!id) {
      return;
    }
    this.error.set('');
    this.content.get(this.workspaceId(), id).subscribe({
      next: (doc) => {
        const markdown = doc.content ?? '';
        if (markdown.length > MAX_CHARS) {
          this.error.set(this.t('ai.listen.tooLong', { max: MAX_CHARS }));
        }
        this.onText(markdown.slice(0, MAX_CHARS));
      },
      error: (err) => this.error.set(err?.message || this.t('ai.error.GENERATION_FAILED')),
    });
  }

  makeScript(): void {
    if (!this.canScript()) {
      return;
    }
    this.begin();
    this.script.set('');
    this.stream = this.ai.listen(
      { workspaceId: this.workspaceId(), text: this.text().trim() },
      {
        delta: (piece) => this.script.update((current) => current + piece),
        done: () => {
          this.busy.set(false);
          this.refreshStatus();
        },
        error: (err) => this.fail(err),
      },
    );
  }
}
