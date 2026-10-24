import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { NoteLength, RewriteAction, TagsResult } from '@core/models/ai/ai.model';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EditorSelection, MarkdownEditorComponent } from '../../document-workspace/markdown/markdown-editor.component';
import { MarkdownViewComponent } from '../../document-workspace/markdown/markdown-view.component';
import { AiError, AiService, AiStream } from '../data/ai.service';
import { aiErrorMessage } from '../lib/errors';
import { plainTextForSpeech } from '../lib/speech';
import { SpeechPlayerComponent } from '../ui/speech-player.component';

const REWRITE: RewriteAction[] = ['SIMPLIFY', 'EXPAND', 'SHORTEN', 'FORMAL', 'FRIENDLY', 'FIX', 'BULLETS', 'TRANSLATE'];
const EXPLAIN: RewriteAction[] = ['ANALOGY', 'STEP_BY_STEP', 'EXAMPLE', 'ELI10', 'SUMMARIZE'];
const ADDS_TEXT = new Set<RewriteAction>(EXPLAIN);
const LANGUAGES = ['English', 'Español', 'Português', 'Français', 'Deutsch', 'Italiano'];
const MAX_SELECTION = 20000;

type Job = { kind: 'rewrite'; action: RewriteAction } | { kind: 'continue' };

/**
 * The AI assistant of the note editor: rewrite or explain the selected text, or carry on writing from the cursor.
 * Nothing is applied on its own: the answer is shown as a draft and goes into the note only when the person says so,
 * and only if the text it was written for is still where it was.
 */
@Component({
  selector: 'app-ai-assistant-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, MarkdownViewComponent, SpeechPlayerComponent, SpinnerComponent, TranslatePipe],
  template: `
    <div class="as">
      <header class="as__head">
        <h2 class="as__title"><app-icon name="sparkles" [size]="16" />{{ 'ai.assistant.title' | t }}</h2>
        <app-button variant="text" size="sm" icon="x" iconOnly [attr.aria-label]="'common.close' | t" (click)="close.emit()" />
      </header>

      @if (blockedReason(); as why) {
        <p class="as__note" role="status"><app-icon name="info" [size]="14" /> {{ why }}</p>
      }

      <section class="as__section">
        <h3 class="as__h">{{ 'ai.assistant.selection' | t }}</h3>
        @if (selection()?.text; as text) {
          <blockquote class="as__quote">{{ preview(text) }}</blockquote>
          @if (text.length > max) {
            <p class="as__note">{{ 'ai.assistant.tooLong' | t: { max } }}</p>
          }
        } @else {
          <p class="as__hint">{{ 'ai.assistant.selectHint' | t }}</p>
        }
      </section>

      <section class="as__section">
        <h3 class="as__h">{{ 'ai.assistant.rewrite' | t }}</h3>
        <div class="as__chips">
          @for (a of rewrite; track a) {
            <button type="button" class="as__chip" [disabled]="!canRewrite()" (click)="run({ kind: 'rewrite', action: a })">{{ 'ai.action.' + a | t }}</button>
          }
        </div>
        <label class="as__lang">
          <span>{{ 'ai.assistant.translateTo' | t }}</span>
          <select [value]="targetLanguage()" (change)="targetLanguage.set($any($event.target).value)">
            @for (l of languages; track l) {
              <option [value]="l" [selected]="l === targetLanguage()">{{ l }}</option>
            }
          </select>
        </label>
      </section>

      <section class="as__section">
        <h3 class="as__h">{{ 'ai.assistant.explain' | t }}</h3>
        <div class="as__chips">
          @for (a of explain; track a) {
            <button type="button" class="as__chip" [disabled]="!canRewrite()" (click)="run({ kind: 'rewrite', action: a })">{{ 'ai.action.' + a | t }}</button>
          }
        </div>
      </section>

      <section class="as__section">
        <h3 class="as__h">{{ 'ai.assistant.continue' | t }}</h3>
        <div class="as__row">
          <select [attr.aria-label]="'ai.field.length' | t" [value]="length()" (change)="length.set($any($event.target).value)">
            @for (l of lengths; track l) {
              <option [value]="l" [selected]="l === length()">{{ 'ai.continue.' + l | t }}</option>
            }
          </select>
          <app-button variant="secondary" size="sm" icon="pencil" [disabled]="!canContinue()" (click)="run({ kind: 'continue' })">{{ 'ai.assistant.continueBtn' | t }}</app-button>
        </div>
      </section>

      <section class="as__section">
        <h3 class="as__h">{{ 'ai.assistant.tags' | t }}</h3>
        <div class="as__row">
          <app-button variant="secondary" size="sm" icon="tag" [loading]="tagging()" [disabled]="!canTag()" (click)="suggestTags()">{{ 'ai.assistant.suggestTags' | t }}</app-button>
        </div>
        @if (suggestion(); as s) {
          @if (s.summary) {
            <p class="as__hint">{{ s.summary }}</p>
          }
          <div class="as__chips">
            @for (tag of s.tags; track tag) {
              <button type="button" class="as__chip" [class.as__chip--on]="pickedTags().includes(tag)" [attr.aria-pressed]="pickedTags().includes(tag)" (click)="toggleTag(tag)">#{{ tag }}</button>
            }
          </div>
          @if (!readonly()) {
            <div class="as__actions">
              <app-button variant="primary" size="sm" icon="check" [disabled]="pickedTags().length === 0" (click)="insertTags()">{{ 'ai.assistant.insertTags' | t }}</app-button>
              <app-button variant="text" size="sm" (click)="copyTitle(s.title)">{{ 'ai.assistant.copyTitle' | t }}</app-button>
            </div>
          }
        }
      </section>

      <section class="as__section">
        <h3 class="as__h">{{ 'ai.assistant.listen' | t }}</h3>
        <p class="as__hint">{{ (selection()?.text ? 'ai.assistant.listenSelection' : 'ai.assistant.listenDocument') | t }}</p>
        <app-speech-player [text]="listenText()" />
      </section>

      @if (error()) {
        <p class="as__error" role="alert"><app-icon name="alert-circle" [size]="14" /> {{ error() }}</p>
      }

      @if (draft() || busy()) {
        <section class="as__section as__result" aria-live="polite">
          <div class="as__result-head">
            <h3 class="as__h">{{ 'ai.assistant.draft' | t }}</h3>
            @if (busy()) {
              <span class="as__live"><app-spinner [size]="12" />{{ 'ai.writing' | t }}</span>
              <app-button variant="text" size="sm" (click)="cancel()">{{ 'ai.stop' | t }}</app-button>
            }
          </div>
          <div class="as__paper"><app-markdown-view [source]="draft()" [readonly]="true" /></div>
          @if (!busy() && draft()) {
            <div class="as__actions">
              @if (!readonly()) {
                @if (job()?.kind === 'continue') {
                  <app-button variant="primary" size="sm" icon="check" (click)="applyContinue()">{{ 'ai.assistant.insertHere' | t }}</app-button>
                } @else if (addsText()) {
                  <app-button variant="primary" size="sm" icon="check" (click)="applyBelow()">{{ 'ai.assistant.insertBelow' | t }}</app-button>
                } @else {
                  <app-button variant="primary" size="sm" icon="check" (click)="applyReplace()">{{ 'ai.assistant.replace' | t }}</app-button>
                }
              }
              <app-button variant="secondary" size="sm" icon="copy" (click)="copy()">{{ 'ai.copy' | t }}</app-button>
              <app-button variant="text" size="sm" (click)="discard()">{{ 'ai.assistant.discard' | t }}</app-button>
            </div>
          }
        </section>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .as { display: flex; flex-direction: column; gap: var(--space-md); padding: var(--space-md); }
    .as__head { display: flex; align-items: center; justify-content: space-between; }
    .as__title { display: flex; align-items: center; gap: 8px; margin: 0; font-size: var(--type-body-strong-size); font-weight: 600; color: var(--text); }
    .as__section { display: flex; flex-direction: column; gap: 8px; }
    .as__h { margin: 0; font-size: var(--type-fine-size); font-weight: 600; letter-spacing: 0.04em; text-transform: uppercase; color: var(--text-subtle); }
    .as__hint, .as__note { margin: 0; font-size: var(--type-caption-size); color: var(--text-subtle); line-height: 1.45; }
    .as__note { display: flex; gap: 6px; align-items: flex-start; color: var(--text-muted); }
    .as__quote { margin: 0; padding: 8px 10px; max-height: 96px; overflow: auto; font-size: var(--type-caption-size); color: var(--text-muted); background: var(--surface-sunken); border-left: 3px solid var(--accent); border-radius: var(--radius-sm); white-space: pre-wrap; overflow-wrap: anywhere; }
    .as__chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .as__chip.as__chip--on { background: color-mix(in srgb, var(--accent) 14%, var(--surface)); border-color: var(--accent); }
    .as__chip {
      padding: 5px 11px; font: inherit; font-size: var(--type-caption-size); color: var(--text); background: var(--surface);
      border: 1px solid var(--border); border-radius: var(--radius-full); cursor: pointer;
      &:hover:not(:disabled) { background: var(--surface-muted); }
      &:focus-visible { outline: 2px solid var(--accent-focus); outline-offset: 1px; }
      &:disabled { opacity: 0.45; cursor: not-allowed; }
    }
    select { padding: 6px 10px; font: inherit; font-size: var(--type-caption-size); color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); &:focus-visible { outline: 2px solid var(--accent-focus); } }
    .as__lang { display: flex; align-items: center; gap: 8px; font-size: var(--type-fine-size); color: var(--text-subtle); }
    .as__row { display: flex; align-items: center; gap: 8px; }
    .as__error { display: flex; gap: 6px; align-items: flex-start; margin: 0; padding: 8px 10px; font-size: var(--type-caption-size); color: var(--danger); background: var(--danger-surface); border-radius: var(--radius-md); }
    .as__result-head { display: flex; align-items: center; gap: 8px; }
    .as__live { display: inline-flex; align-items: center; gap: 6px; flex: 1; font-size: var(--type-fine-size); color: var(--text-subtle); }
    .as__paper { max-height: 320px; overflow: auto; padding: 10px 12px; background: var(--surface-sunken); border-radius: var(--radius-md); }
    .as__actions { display: flex; flex-wrap: wrap; gap: 6px; }
  `,
})
export class AssistantPanelComponent implements OnInit {
  private readonly ai = inject(AiService);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);

  readonly workspaceId = input.required<string>();
  readonly editor = input<MarkdownEditorComponent | undefined>(undefined);
  readonly readonly = input(false);
  readonly close = output<void>();

  readonly rewrite = REWRITE;
  readonly explain = EXPLAIN;
  readonly languages = LANGUAGES;
  readonly lengths: NoteLength[] = ['SHORT', 'MEDIUM', 'LONG'];
  readonly max = MAX_SELECTION;

  readonly selection = signal<EditorSelection | null>(null);
  readonly targetLanguage = signal(LANGUAGES[0]);
  readonly length = signal<NoteLength>('SHORT');

  readonly busy = signal(false);
  readonly error = signal('');
  readonly draft = signal('');
  readonly job = signal<Job | null>(null);
  /** The selection the draft was written for; applying it checks the text is still there. */
  private snapshot: EditorSelection | null = null;
  private stream: AiStream | null = null;

  readonly addsText = computed(() => {
    const job = this.job();
    return job?.kind === 'rewrite' && ADDS_TEXT.has(job.action);
  });

  readonly blockedReason = computed(() => {
    if (!this.selection()) {
      return this.i18n.translate('ai.assistant.needsEditMode');
    }
    return '';
  });

  ngOnInit(): void {
    this.ai.loadStatus(this.workspaceId()).subscribe({ error: () => undefined });
    const timer = setInterval(() => this.readSelection(), 400);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      this.stream?.cancel();
    });
    this.readSelection();
  }

  /** Keeps the panel in step with the selection, which the browser does not announce to anyone but the textarea. */
  private readSelection(): void {
    if (this.busy()) {
      return;
    }
    const next = this.editor()?.selectionContext() ?? null;
    const prev = this.selection();
    if (next?.start !== prev?.start || next?.end !== prev?.end || next?.text !== prev?.text) {
      this.selection.set(next);
    }
  }

  canRewrite(): boolean {
    const text = this.selection()?.text ?? '';
    return !this.busy() && this.ai.available() && text.trim().length > 0 && text.length <= MAX_SELECTION;
  }

  canContinue(): boolean {
    const s = this.selection();
    return !this.busy() && this.ai.available() && !!s && s.before.trim().length > 0;
  }

  preview(text: string): string {
    return text.length > 400 ? `${text.slice(0, 400)}…` : text;
  }

  run(job: Job): void {
    const s = this.editor()?.selectionContext() ?? this.selection();
    if (!s) {
      return;
    }
    this.snapshot = s;
    this.job.set(job);
    this.draft.set('');
    this.error.set('');
    this.busy.set(true);
    const handlers = {
      delta: (piece: string) => this.draft.update((d) => d + piece),
      done: () => {
        this.busy.set(false);
        this.ai.loadStatus(this.workspaceId()).subscribe({ error: () => undefined });
      },
      error: (err: AiError) => {
        this.busy.set(false);
        this.error.set(aiErrorMessage(err, (key) => this.i18n.translate(key)));
      },
    };
    this.stream =
      job.kind === 'rewrite'
        ? this.ai.rewrite(
            {
              workspaceId: this.workspaceId(),
              text: s.text,
              action: job.action,
              targetLanguage: job.action === 'TRANSLATE' ? this.targetLanguage() : undefined,
            },
            handlers,
          )
        : this.ai.continueText(
            { workspaceId: this.workspaceId(), before: s.before, after: s.after || undefined, length: this.length() },
            handlers,
          );
  }

  cancel(): void {
    this.stream?.cancel();
    this.busy.set(false);
  }

  discard(): void {
    this.draft.set('');
    this.job.set(null);
    this.snapshot = null;
  }

  async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.draft());
      this.toast.success(this.i18n.translate('ai.copied'));
    } catch {
      this.toast.error(this.i18n.translate('ai.copyFailed'));
    }
  }

  applyReplace(): void {
    const s = this.snapshot;
    if (s && this.editor()?.replaceChecked(s.start, s.end, s.text, this.draft().trim(), s.before.slice(-30))) {
      this.applied();
    } else {
      this.changed();
    }
  }

  /** Explanations go under the selected text, so the original stays. */
  applyBelow(): void {
    const s = this.snapshot;
    if (s && this.editor()?.replaceChecked(s.end, s.end, '', `\n\n${this.draft().trim()}\n`, `${s.before}${s.text}`.slice(-30))) {
      this.applied();
    } else {
      this.changed();
    }
  }

  applyContinue(): void {
    const s = this.snapshot;
    if (!s) {
      return;
    }
    const text = this.draft().trim();
    const needsSpace = s.before.length > 0 && !/\s$/.test(s.before) && !/^[\s.,;:!?)]/.test(text);
    const insert = needsSpace ? ` ${text}` : text;
    if (this.editor()?.replaceChecked(s.end, s.end, '', insert, `${s.before}${s.text}`.slice(-30))) {
      this.applied();
    } else {
      this.changed();
    }
  }

  // ---- tags and listening -----------------------------------------------------------------------------------

  readonly tagging = signal(false);
  readonly suggestion = signal<TagsResult | null>(null);
  readonly pickedTags = signal<string[]>([]);

  /** The selection when there is one, otherwise the whole note. */
  readonly listenText = computed(() => {
    const selected = this.selection()?.text ?? '';
    return plainTextForSpeech(selected.trim() ? selected : (this.editor()?.value() ?? ''));
  });

  canTag(): boolean {
    return !this.tagging() && !this.busy() && this.ai.available() && (this.editor()?.value() ?? '').trim().length > 0;
  }

  suggestTags(): void {
    const text = (this.editor()?.value() ?? '').slice(0, 60000);
    if (!text.trim()) {
      return;
    }
    this.error.set('');
    this.tagging.set(true);
    this.ai.tags({ workspaceId: this.workspaceId(), text }).subscribe({
      next: (result) => {
        this.suggestion.set(result);
        this.pickedTags.set(result.tags);
        this.tagging.set(false);
        this.ai.loadStatus(this.workspaceId()).subscribe({ error: () => undefined });
      },
      error: (err: AiError) => {
        this.tagging.set(false);
        this.error.set(aiErrorMessage(err, (key) => this.i18n.translate(key)));
      },
    });
  }

  toggleTag(tag: string): void {
    this.pickedTags.update((list) => (list.includes(tag) ? list.filter((t) => t !== tag) : [...list, tag]));
  }

  /** Tags go on their own line at the end of the note, as `#tag`s, skipping the ones the note already has. */
  insertTags(): void {
    const editor = this.editor();
    const value = editor?.value() ?? '';
    const missing = this.pickedTags().filter((tag) => !new RegExp(`(^|\s)#${tag}(?![\p{L}\p{N}_-])`, 'u').test(value));
    if (!editor || missing.length === 0) {
      this.toast.info(this.i18n.translate('ai.assistant.tagsPresent'));
      return;
    }
    const line = missing.map((tag) => `#${tag}`).join(' ');
    const insert = `${value.endsWith('\n') || value === '' ? '' : '\n'}\n${line}\n`;
    if (editor.replaceChecked(value.length, value.length, '', insert)) {
      this.toast.success(this.i18n.translate('ai.assistant.applied'));
    } else {
      this.changed();
    }
  }

  async copyTitle(title: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(title);
      this.toast.success(this.i18n.translate('ai.copied'));
    } catch {
      this.toast.error(this.i18n.translate('ai.copyFailed'));
    }
  }

  private applied(): void {
    this.toast.success(this.i18n.translate('ai.assistant.applied'));
    this.discard();
  }

  private changed(): void {
    this.error.set(this.i18n.translate('ai.assistant.changed'));
  }
}
