import { ChangeDetectionStrategy, Component, OnInit, computed, input, signal } from '@angular/core';
import { NoteLength, NotesInclude, StudyLevel } from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { MarkdownViewComponent } from '../../document-workspace/markdown/markdown-view.component';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

const INCLUDES: NotesInclude[] = ['SUMMARY', 'KEY_TERMS', 'EXAMPLES', 'QUESTIONS', 'TABLES'];
const LEVELS: StudyLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
const LENGTHS: NoteLength[] = ['SHORT', 'MEDIUM', 'LONG'];

/**
 * The two tools that write a document as they go: study notes from a topic and/or documents, and a one-page review
 * sheet from documents (with extra attention on topics the student keeps missing).
 */
@Component({
  selector: 'app-ai-text-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SpinnerComponent, MarkdownViewComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ (kind() === 'notes' ? 'ai.notes.intro' : 'ai.review.intro') | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        @if (kind() === 'notes') {
          <div class="ait__field">
            <label class="ait__label" for="notes-topic">{{ 'ai.field.topic' | t }}</label>
            <input
              id="notes-topic"
              class="ait__input"
              maxlength="500"
              [placeholder]="'ai.notes.topicPlaceholder' | t"
              [value]="topic()"
              (input)="topic.set($any($event.target).value)"
            />
          </div>
<details class="ait__more">
          <summary>{{ 'ai.options' | t }}</summary>
          <div class="ait__more-body">
          <div class="ait__row">
            <div class="ait__field">
              <label class="ait__label" for="notes-level">{{ 'ai.field.level' | t }}</label>
              <select id="notes-level" class="ait__select" [value]="level()" (change)="level.set($any($event.target).value)">
                @for (l of levels; track l) {
                  <option [value]="l" [selected]="l === level()">{{ 'ai.level.' + l | t }}</option>
                }
              </select>
            </div>
            <div class="ait__field">
              <label class="ait__label" for="notes-length">{{ 'ai.field.length' | t }}</label>
              <select id="notes-length" class="ait__select" [value]="length()" (change)="length.set($any($event.target).value)">
                @for (l of lengths; track l) {
                  <option [value]="l" [selected]="l === length()">{{ 'ai.length.' + l | t }}</option>
                }
              </select>
            </div>
          </div>
          <div class="ait__field">
            <span class="ait__label">{{ 'ai.field.include' | t }}</span>
            <div class="ait__chips">
              @for (item of includes; track item) {
                <button type="button" class="ait__chip" [class.ait__chip--on]="include().includes(item)" [attr.aria-pressed]="include().includes(item)" (click)="toggleInclude(item)">
                  @if (include().includes(item)) {
                    <app-icon name="check" [size]="13" />
                  }
                  {{ 'ai.include.' + item | t }}
                </button>
              }
            </div>
          </div>
          <div class="ait__field">
            <label class="ait__label" for="notes-extra">{{ 'ai.field.instructions' | t }}</label>
            <textarea
              id="notes-extra"
              class="ait__textarea"
              rows="2"
              maxlength="1000"
              [placeholder]="'ai.field.instructionsPlaceholder' | t"
              [value]="instructions()"
              (input)="instructions.set($any($event.target).value)"
            ></textarea>
          </div>
          </div>
        </details>

        } @else {
          <div class="ait__field">
            <label class="ait__label" for="review-weak">{{ 'ai.review.weak' | t }}</label>
            <textarea
              id="review-weak"
              class="ait__textarea"
              rows="3"
              [placeholder]="'ai.review.weakPlaceholder' | t"
              [value]="weak()"
              (input)="weak.set($any($event.target).value)"
            ></textarea>
            <span class="ait__hint">{{ 'ai.review.weakHint' | t }}</span>
          </div>
        }

        <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }

        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">
            {{ (kind() === 'notes' ? 'ai.notes.generate' : 'ai.review.generate') | t }}
          </app-button>
          @if (busy()) {
            <app-button variant="secondary" (click)="cancel()">{{ 'ai.stop' | t }}</app-button>
          }
        </div>
      </form>

      @if (text() || busy()) {
        <section class="ait__result" aria-live="polite">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ 'ai.result' | t }}</h3>
            @if (busy()) {
              <span class="ait__live"><app-spinner [size]="14" />{{ 'ai.writing' | t }}</span>
            } @else if (truncated()) {
              <span class="ait__live">{{ 'ai.truncated' | t }}</span>
            }
          </div>
          <div class="ait__paper">
            <app-markdown-view [source]="text()" [readonly]="true" />
          </div>
          @if (!busy() && text()) {
            <div class="ait__actions">
              <app-button variant="dark" icon="file-text" [loading]="saving()" (click)="save()">{{ 'ai.createDocument' | t }}</app-button>
              <app-button variant="secondary" icon="copy" (click)="copy(text())">{{ 'ai.copy' | t }}</app-button>
              <app-button variant="text" icon="sparkles" (click)="generate()" [disabled]="!canGenerate()">{{ 'ai.regenerate' | t }}</app-button>
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class TextToolComponent extends AiToolBase implements OnInit {
  readonly kind = input<'notes' | 'review'>('notes');

  readonly levels = LEVELS;
  readonly lengths = LENGTHS;
  readonly includes = INCLUDES;

  readonly topic = signal('');
  readonly level = signal<StudyLevel>('INTERMEDIATE');
  readonly length = signal<NoteLength>('MEDIUM');
  readonly include = signal<NotesInclude[]>(['SUMMARY']);
  readonly instructions = signal('');
  readonly sources = signal<string[]>([]);
  readonly weak = signal('');

  readonly text = signal('');
  readonly truncated = signal(false);

  readonly canGenerate = computed(() => {
    if (this.busy()) {
      return false;
    }
    return this.kind() === 'notes' ? this.topic().trim().length > 0 || this.sources().length > 0 : this.sources().length > 0;
  });

  ngOnInit(): void {
    const handoff = this.ai.handoff();
    if (handoff && handoff.tool === (this.kind() === 'notes' ? 'notes' : 'review')) {
      if (handoff.sources) this.sources.set(handoff.sources);
      if (handoff.topic) this.topic.set(handoff.topic);
      if (handoff.weakTopics) this.weak.set(handoff.weakTopics.join('\n'));
      if (handoff.instructions) this.instructions.set(handoff.instructions.slice(0, 1000));
      this.ai.handoff.set(null);
    }
  }

  toggleInclude(item: NotesInclude): void {
    this.include.update((list) => (list.includes(item) ? list.filter((i) => i !== item) : [...list, item]));
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    this.text.set('');
    this.truncated.set(false);
    const sources = this.sources().map((artifactId) => ({ artifactId }));
    const handlers = {
      delta: (piece: string) => this.text.update((current) => current + piece),
      done: (data: { truncated?: boolean }) => {
        this.truncated.set(!!data.truncated);
        this.busy.set(false);
        this.refreshStatus();
      },
      error: (err: { code?: string; message?: string; status?: number }) => this.fail(err),
    };
    if (this.kind() === 'notes') {
      this.stream = this.ai.notes(
        {
          workspaceId: this.workspaceId(),
          topic: this.topic().trim() || undefined,
          level: this.level(),
          length: this.length(),
          include: this.include(),
          instructions: this.instructions().trim() || undefined,
          sources,
        },
        handlers,
      );
    } else {
      const weakTopics = this.weak()
        .split('\n')
        .map((line) => line.trim().slice(0, 200))
        .filter(Boolean)
        .slice(0, 20);
      this.stream = this.ai.reviewSummary({ workspaceId: this.workspaceId(), sources, weakTopics }, handlers);
    }
  }

  save(): void {
    const first = this.text().match(/^#\s+(.+)$/m)?.[1];
    this.keep({
      name: this.nameOf(first ?? '', this.topic()),
      type: 'MARKDOWN_DOCUMENT',
      content: this.text(),
    });
  }
}
