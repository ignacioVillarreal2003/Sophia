import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import {
  Difficulty,
  GradedAnswer,
  QuestionType,
  QuizQuestion,
  QuizResult,
} from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import {
  EMPTY_ANSWER,
  QuizAnswer,
  QuizOutcome,
  defaultExamMinutes,
  formatClock,
  isAnswered,
  itemsToGrade,
  markChoice,
  outcome,
  quizMarkdown,
} from '../lib/quiz-session';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

type Phase = 'config' | 'running' | 'grading' | 'results';

const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD', 'MIXED'];
const TYPES: QuestionType[] = ['MULTIPLE_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER'];

/**
 * Quizzes and mock exams. A quiz tells you right away whether a choice was right; an exam holds every verdict back
 * until the end, runs against a clock and spreads its questions over all the documents. Short answers are graded by
 * the model against the reference answer; everything else is marked here.
 */
@Component({
  selector: 'app-ai-quiz-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      @switch (phase()) {
        @case ('config') {
          <p class="ait__intro">{{ 'ai.quiz.intro' | t }}</p>
          <form class="ait__form" (submit)="$event.preventDefault(); generate()">
            <div class="ait__field">
              <span class="ait__label">{{ 'ai.quiz.kind' | t }}</span>
              <div class="ait__chips" role="radiogroup" [attr.aria-label]="'ai.quiz.kind' | t">
                <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="!exam()" [attr.aria-checked]="!exam()" (click)="setExam(false)">{{ 'ai.quiz.kind.quiz' | t }}</button>
                <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="exam()" [attr.aria-checked]="exam()" (click)="setExam(true)">{{ 'ai.quiz.kind.exam' | t }}</button>
              </div>
              <span class="ait__hint">{{ (exam() ? 'ai.quiz.kind.exam.hint' : 'ai.quiz.kind.quiz.hint') | t }}</span>
            </div>

            <div class="ait__field">
              <label class="ait__label" for="quiz-topic">{{ 'ai.field.topic' | t }}</label>
              <input id="quiz-topic" class="ait__input" maxlength="500" [placeholder]="'ai.quiz.topicPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
            </div>

<details class="ait__more">
          <summary>{{ 'ai.options' | t }}</summary>
          <div class="ait__more-body">
            <div class="ait__row">
              <div class="ait__field">
                <label class="ait__label" for="quiz-count">{{ 'ai.quiz.count' | t }}</label>
                <input id="quiz-count" class="ait__input" type="number" min="3" max="30" [value]="count()" (input)="setCount($any($event.target).value)" />
              </div>
              <div class="ait__field">
                <label class="ait__label" for="quiz-difficulty">{{ 'ai.quiz.difficulty' | t }}</label>
                <select id="quiz-difficulty" class="ait__select" [value]="difficulty()" (change)="difficulty.set($any($event.target).value)">
                  @for (d of difficulties; track d) {
                    <option [value]="d" [selected]="d === difficulty()">{{ 'ai.difficulty.' + d | t }}</option>
                  }
                </select>
              </div>
              @if (exam()) {
                <div class="ait__field">
                  <label class="ait__label" for="quiz-minutes">{{ 'ai.quiz.minutes' | t }}</label>
                  <input id="quiz-minutes" class="ait__input" type="number" min="5" max="240" [value]="minutes()" (input)="setMinutes($any($event.target).value)" />
                </div>
              }
            </div>

            <div class="ait__field">
              <span class="ait__label">{{ 'ai.quiz.types' | t }}</span>
              <div class="ait__chips">
                @for (type of typeList; track type) {
                  <button type="button" class="ait__chip" [class.ait__chip--on]="types().includes(type)" [attr.aria-pressed]="types().includes(type)" (click)="toggleType(type)">
                    @if (types().includes(type)) {
                      <app-icon name="check" [size]="13" />
                    }
                    {{ 'ai.type.' + type | t }}
                  </button>
                }
              </div>
            </div>

          </div>
        </details>

            <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />

            @if (error()) {
              <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
            }
            <div class="ait__actions">
              <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">
                {{ (exam() ? 'ai.quiz.generateExam' : 'ai.quiz.generate') | t }}
              </app-button>
            </div>
          </form>
        }

        @case ('running') {
          @if (current(); as q) {
            <section class="ait__result" aria-live="polite">
              <div class="ait__result-head">
                <h3 class="ait__result-title">{{ quiz()?.title }}</h3>
                @if (exam()) {
                  <span class="qz__clock" [class.qz__clock--low]="remaining() < 60" role="timer">
                    <app-icon name="clock" [size]="15" />{{ clock() }}
                  </span>
                }
              </div>

              <div class="ait__meter" role="progressbar" [attr.aria-valuenow]="answeredCount()" [attr.aria-valuemax]="questions().length">
                <span [style.width.%]="(answeredCount() / questions().length) * 100"></span>
              </div>
              <div class="qz__nav" role="group" [attr.aria-label]="'ai.quiz.questions' | t">
                @for (item of questions(); track $index) {
                  <button type="button" class="qz__dot" [class.qz__dot--here]="$index === index()" [class.qz__dot--done]="isDone($index)" [attr.aria-label]="('ai.quiz.goTo' | t: { n: $index + 1 })" [attr.aria-current]="$index === index() ? 'step' : null" (click)="index.set($index)">{{ $index + 1 }}</button>
                }
              </div>

              <div class="qz__question">
                <span class="qz__count">{{ 'ai.quiz.of' | t: { n: index() + 1, total: questions().length } }}</span>
                <h4 class="qz__text">{{ q.question }}</h4>

                @switch (q.type) {
                  @case ('SHORT_ANSWER') {
                    <textarea class="ait__textarea" rows="4" [attr.aria-label]="'ai.quiz.yourAnswer' | t" [placeholder]="'ai.quiz.shortPlaceholder' | t" [disabled]="isChecked(index())" [value]="answerAt(index()).text" (input)="setText($any($event.target).value)"></textarea>
                  }
                  @default {
                    <div class="qz__options" role="radiogroup" [attr.aria-label]="q.question">
                      @for (option of optionsOf(q); track $index) {
                        <button type="button" role="radio" class="qz__option" [class.qz__option--on]="answerAt(index()).choice === $index" [class.qz__option--right]="isChecked(index()) && $index === q.correctIndex" [class.qz__option--wrong]="isChecked(index()) && answerAt(index()).choice === $index && $index !== q.correctIndex" [attr.aria-checked]="answerAt(index()).choice === $index" [disabled]="isChecked(index())" (click)="choose($index)">
                          <span class="qz__letter">{{ q.type === 'TRUE_FALSE' ? '' : letter($index) }}</span>
                          <span>{{ option }}</span>
                        </button>
                      }
                    </div>
                  }
                }

                @if (isChecked(index())) {
                  <div class="qz__feedback" role="status">
                    @if (q.type === 'SHORT_ANSWER') {
                      <strong>{{ 'ai.quiz.modelAnswer' | t }}</strong> {{ q.answer }}
                    } @else if (answerAt(index()).choice === q.correctIndex) {
                      <strong class="qz__good">{{ 'ai.quiz.correct' | t }}</strong>
                    } @else {
                      <strong class="qz__bad">{{ 'ai.quiz.incorrect' | t }}</strong>
                      {{ 'ai.quiz.theAnswerIs' | t }} <strong>{{ q.answer }}</strong>.
                    }
                    @if (q.explanation) {
                      <span class="qz__why">{{ q.explanation }}</span>
                    }
                  </div>
                }
              </div>

              <div class="ait__actions">
                <app-button variant="secondary" icon="arrow-left" [disabled]="index() === 0" (click)="index.set(index() - 1)">{{ 'ai.quiz.previous' | t }}</app-button>
                @if (!exam() && !isChecked(index())) {
                  <app-button variant="dark" [disabled]="!isAnswered(q, answerAt(index()))" (click)="check()">{{ 'ai.quiz.check' | t }}</app-button>
                }
                @if (index() < questions().length - 1) {
                  <app-button variant="secondary" (click)="index.set(index() + 1)">{{ 'ai.quiz.next' | t }}<app-icon name="arrow-right" [size]="15" /></app-button>
                }
                <span class="ait__spacer"></span>
                <app-button variant="dark" icon="check-circle" (click)="finish()">{{ 'ai.quiz.finish' | t }}</app-button>
              </div>
              @if (unansweredCount() > 0 && index() === questions().length - 1) {
                <p class="ait__hint">{{ 'ai.quiz.unanswered' | t: { count: unansweredCount() } }}</p>
              }
            </section>
          }
        }

        @case ('grading') {
          <div class="ait__empty" aria-live="polite">
            <app-icon name="loader" [size]="22" />
            <span>{{ 'ai.quiz.grading' | t }}</span>
          </div>
        }

        @case ('results') {
          @if (result(); as r) {
            <section class="ait__result">
              <div class="qz__score">
                <div class="qz__ring" [style.--pct]="r.percent" role="img" [attr.aria-label]="r.percent + '%'">
                  <span>{{ r.percent }}%</span>
                </div>
                <div>
                  <h3 class="ait__result-title">{{ 'ai.quiz.score' | t: { points: r.points, total: r.total } }}</h3>
                  <p class="ait__hint">{{ verdict() }}</p>
                  @if (gradeFailed()) {
                    <p class="ait__hint">{{ 'ai.quiz.gradeFailed' | t }}</p>
                  }
                </div>
              </div>

              <ol class="qz__review">
                @for (q of questions(); track $index) {
                  <li class="qz__item" [class.qz__item--right]="r.perQuestion[$index].score === 1" [class.qz__item--half]="r.perQuestion[$index].score === 0.5" [class.qz__item--wrong]="r.perQuestion[$index].score === 0">
                    <div class="qz__item-head">
                      <app-icon [name]="r.perQuestion[$index].score === 1 ? 'check-circle' : 'alert-circle'" [size]="16" />
                      <strong>{{ q.question }}</strong>
                    </div>
                    <p class="qz__line"><span>{{ 'ai.quiz.yourAnswer' | t }}:</span> {{ yourAnswer(q, $index) }}</p>
                    @if (r.perQuestion[$index].score < 1) {
                      <p class="qz__line"><span>{{ 'ai.quiz.correctAnswer' | t }}:</span> {{ correctAnswer(q) }}</p>
                    }
                    @if (r.perQuestion[$index].feedback) {
                      <p class="qz__line qz__line--note">{{ r.perQuestion[$index].feedback }}</p>
                    }
                    @if (q.explanation) {
                      <p class="qz__line qz__line--note">{{ q.explanation }}</p>
                    }
                  </li>
                }
              </ol>

              <div class="ait__actions">
                <app-button variant="dark" icon="file-text" [loading]="saving()" (click)="save()">{{ 'ai.quiz.saveDocument' | t }}</app-button>
                @if (r.missed.length > 0) {
                  <app-button variant="secondary" icon="brain" (click)="reviewMissed()">{{ 'ai.quiz.reviewMissed' | t }}</app-button>
                }
                <app-button variant="text" icon="sparkles" (click)="again()">{{ 'ai.quiz.again' | t }}</app-button>
              </div>
            </section>
          }
        }
      }
    </div>
  `,
  styles: `
    .qz__clock { display: inline-flex; align-items: center; gap: 6px; font-variant-numeric: tabular-nums; font-weight: 600; color: var(--text); }
    .qz__clock--low { color: var(--danger); }
    .qz__nav { display: flex; flex-wrap: wrap; gap: 6px; }
    .qz__dot {
      min-width: 30px; height: 30px; padding: 0 6px; font: inherit; font-size: var(--type-fine-size); color: var(--text-muted);
      background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-full); cursor: pointer;
      &--done { color: var(--text); background: color-mix(in srgb, var(--accent) 12%, var(--surface)); border-color: color-mix(in srgb, var(--accent) 40%, var(--border)); }
      &--here { outline: 2px solid var(--accent); outline-offset: 1px; color: var(--text); }
      &:focus-visible { outline: 2px solid var(--accent-focus); outline-offset: 1px; }
    }
    .qz__question { display: flex; flex-direction: column; gap: var(--space-md); }
    .qz__count { font-size: var(--type-fine-size); color: var(--text-subtle); }
    .qz__text { margin: 0; font-size: var(--type-body-strong-size); font-weight: 600; line-height: 1.4; color: var(--text); }
    .qz__options { display: flex; flex-direction: column; gap: 8px; }
    .qz__option {
      display: flex; align-items: center; gap: 12px; padding: 11px 14px; font: inherit; font-size: var(--type-caption-size);
      color: var(--text); text-align: left; background: var(--surface); border: 1px solid var(--border);
      border-radius: var(--radius-md); cursor: pointer;
      &:hover:not(:disabled) { background: var(--surface-muted); }
      &:focus-visible { outline: 2px solid var(--accent-focus); outline-offset: 1px; }
      &:disabled { cursor: default; }
      &--on { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 10%, var(--surface)); }
      &--right { border-color: var(--success); background: color-mix(in srgb, var(--success) 14%, var(--surface)); }
      &--wrong { border-color: var(--danger); background: var(--danger-surface); }
    }
    .qz__letter { display: grid; place-items: center; width: 24px; height: 24px; flex-shrink: 0; font-size: var(--type-fine-size); font-weight: 600; color: var(--text-muted); background: var(--surface-sunken); border-radius: 50%; &:empty { display: none; } }
    .qz__feedback { padding: 12px 14px; font-size: var(--type-caption-size); line-height: 1.5; background: var(--surface-sunken); border-radius: var(--radius-md); }
    .qz__good { color: var(--success); }
    .qz__bad { color: var(--danger); }
    .qz__why { display: block; margin-top: 6px; color: var(--text-muted); }
    .qz__score { display: flex; align-items: center; gap: var(--space-lg); }
    .qz__ring {
      display: grid; place-items: center; width: 84px; height: 84px; flex-shrink: 0; border-radius: 50%; font-weight: 700; color: var(--text);
      background: conic-gradient(var(--accent) calc(var(--pct) * 1%), var(--surface-sunken) 0);
      position: relative;
      &::before { content: ''; position: absolute; inset: 8px; background: var(--surface); border-radius: 50%; }
      span { position: relative; }
    }
    .qz__review { display: flex; flex-direction: column; gap: 10px; margin: 0; padding: 0; list-style: none; }
    .qz__item { padding: 12px 14px; background: var(--surface); border: 1px solid var(--border); border-left-width: 4px; border-radius: var(--radius-md);
      &--right { border-left-color: var(--success); }
      &--half { border-left-color: var(--warning); }
      &--wrong { border-left-color: var(--danger); }
    }
    .qz__item-head { display: flex; align-items: flex-start; gap: 8px; font-size: var(--type-caption-size); color: var(--text); app-icon { flex-shrink: 0; margin-top: 2px; } }
    .qz__line { margin: 6px 0 0 24px; font-size: var(--type-caption-size); color: var(--text); span { color: var(--text-subtle); }
      &--note { color: var(--text-muted); }
    }
  `,
})
export class QuizToolComponent extends AiToolBase implements OnInit {
  private readonly destroyRef = inject(DestroyRef);

  readonly difficulties = DIFFICULTIES;
  readonly typeList = TYPES;

  readonly phase = signal<Phase>('config');

  // configuration
  readonly exam = signal(false);
  readonly topic = signal('');
  readonly count = signal(10);
  readonly difficulty = signal<Difficulty>('MIXED');
  readonly types = signal<QuestionType[]>(['MULTIPLE_CHOICE', 'TRUE_FALSE']);
  readonly minutes = signal(defaultExamMinutes(10));
  readonly sources = signal<string[]>([]);

  // session
  readonly quiz = signal<QuizResult | null>(null);
  readonly questions = computed<QuizQuestion[]>(() => this.quiz()?.questions ?? []);
  readonly answers = signal<QuizAnswer[]>([]);
  readonly checked = signal<Set<number>>(new Set());
  readonly index = signal(0);
  readonly remaining = signal(0);
  readonly result = signal<QuizOutcome | null>(null);
  readonly gradeFailed = signal(false);
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly current = computed(() => this.questions()[this.index()] ?? null);
  readonly clock = computed(() => formatClock(this.remaining()));
  readonly answeredCount = computed(() => this.questions().filter((q, i) => isAnswered(q, this.answerAt(i))).length);
  readonly unansweredCount = computed(() => this.questions().length - this.answeredCount());
  readonly canGenerate = computed(
    () => !this.busy() && this.types().length > 0 && (this.topic().trim().length > 0 || this.sources().length > 0),
  );
  readonly isAnswered = isAnswered;

  constructor() {
    super();
    this.destroyRef.onDestroy(() => this.stopClock());
  }

  ngOnInit(): void {
    const handoff = this.ai.handoff();
    if (handoff?.tool === 'quiz') {
      if (handoff.sources) this.sources.set(handoff.sources);
      if (handoff.topic) this.topic.set(handoff.topic);
      this.ai.handoff.set(null);
    }
  }

  // ---- configuration ----------------------------------------------------------------------------------------

  setExam(value: boolean): void {
    this.exam.set(value);
    if (value) {
      this.count.update((n) => Math.max(n, 10));
      this.minutes.set(defaultExamMinutes(this.count()));
    }
  }

  setCount(value: string): void {
    const n = Math.round(Number(value));
    this.count.set(Number.isFinite(n) ? Math.min(30, Math.max(3, n)) : 10);
    this.minutes.set(defaultExamMinutes(this.count()));
  }

  setMinutes(value: string): void {
    const n = Math.round(Number(value));
    this.minutes.set(Number.isFinite(n) ? Math.min(240, Math.max(5, n)) : 30);
  }

  toggleType(type: QuestionType): void {
    this.types.update((list) => (list.includes(type) ? list.filter((x) => x !== type) : [...list, type]));
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    this.ai
      .quiz({
        workspaceId: this.workspaceId(),
        topic: this.topic().trim() || undefined,
        sources: this.sources().map((artifactId) => ({ artifactId })),
        count: this.count(),
        difficulty: this.difficulty(),
        types: this.types(),
        exam: this.exam(),
      })
      .subscribe({
        next: (quiz) => {
          this.busy.set(false);
          this.refreshStatus();
          this.start(quiz);
        },
        error: (err) => this.fail(err),
      });
  }

  private start(quiz: QuizResult): void {
    this.quiz.set(quiz);
    this.answers.set(quiz.questions.map(() => ({ ...EMPTY_ANSWER })));
    this.checked.set(new Set());
    this.index.set(0);
    this.result.set(null);
    this.gradeFailed.set(false);
    this.phase.set('running');
    if (this.exam()) {
      this.remaining.set(this.minutes() * 60);
      this.stopClock();
      this.timer = setInterval(() => {
        this.remaining.update((s) => s - 1);
        if (this.remaining() <= 0) {
          void this.finish();
        }
      }, 1000);
    }
  }

  private stopClock(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  // ---- answering --------------------------------------------------------------------------------------------

  answerAt(i: number): QuizAnswer {
    return this.answers()[i] ?? EMPTY_ANSWER;
  }

  isChecked(i: number): boolean {
    return this.checked().has(i);
  }

  isDone(i: number): boolean {
    const q = this.questions()[i];
    return !!q && isAnswered(q, this.answerAt(i));
  }

  optionsOf(q: QuizQuestion): string[] {
    return q.type === 'TRUE_FALSE' ? [this.t('ai.quiz.true'), this.t('ai.quiz.false')] : q.options;
  }

  letter(i: number): string {
    return String.fromCharCode(65 + i);
  }

  choose(option: number): void {
    this.patchAnswer({ choice: option });
  }

  setText(text: string): void {
    this.patchAnswer({ text });
  }

  private patchAnswer(patch: Partial<QuizAnswer>): void {
    const i = this.index();
    this.answers.update((list) => list.map((a, n) => (n === i ? { ...a, ...patch } : a)));
  }

  /** Quiz mode only: show whether this answer was right, and why. */
  check(): void {
    this.checked.update((set) => new Set(set).add(this.index()));
  }

  // ---- finishing --------------------------------------------------------------------------------------------

  async finish(): Promise<void> {
    if (this.phase() !== 'running') {
      return;
    }
    this.stopClock();
    this.phase.set('grading');
    const toGrade = itemsToGrade(this.questions(), this.answers());
    const graded = new Map<number, GradedAnswer>();
    if (toGrade.length > 0) {
      try {
        const reply = await new Promise<{ results: GradedAnswer[] }>((resolve, reject) =>
          this.ai.grade({ workspaceId: this.workspaceId(), items: toGrade.map((g) => g.item) }).subscribe({ next: resolve, error: reject }),
        );
        reply.results.forEach((grade, n) => {
          const original = toGrade[n]?.index;
          if (original !== undefined) {
            graded.set(original, { ...grade, index: original });
          }
        });
        this.refreshStatus();
      } catch {
        this.gradeFailed.set(true);
      }
    }
    this.result.set(outcome(this.questions(), this.answers(), graded));
    this.phase.set('results');
  }

  verdict(): string {
    const percent = this.result()?.percent ?? 0;
    return this.t(percent >= 85 ? 'ai.quiz.verdict.great' : percent >= 60 ? 'ai.quiz.verdict.good' : 'ai.quiz.verdict.review');
  }

  yourAnswer(q: QuizQuestion, i: number): string {
    const answer = this.answerAt(i);
    if (!isAnswered(q, answer)) {
      return this.t('ai.quiz.noAnswer');
    }
    return q.type === 'SHORT_ANSWER' ? answer.text : (this.optionsOf(q)[answer.choice!] ?? '');
  }

  correctAnswer(q: QuizQuestion): string {
    return q.type === 'TRUE_FALSE' ? this.optionsOf(q)[q.correctIndex] : q.answer;
  }

  save(): void {
    const quiz = this.quiz();
    if (!quiz) {
      return;
    }
    this.keep({
      name: this.nameOf(quiz.title, this.topic()),
      type: 'MARKDOWN_DOCUMENT',
      content: quizMarkdown(quiz.title, quiz.questions, {
        answers: this.t('ai.quiz.answersHeading'),
        true: this.t('ai.quiz.true'),
        false: this.t('ai.quiz.false'),
      }),
    });
  }

  /** The missed questions become the "needs another look" part of a review sheet over the same documents. */
  reviewMissed(): void {
    const missed = this.result()?.missed ?? [];
    if (this.sources().length === 0) {
      this.ai.handoff.set({
        tool: 'notes',
        topic: this.topic() || this.quiz()?.title,
        instructions: `${this.t('ai.quiz.focusOn')} ${missed.join('; ')}`,
      });
      this.requestTool('notes');
      return;
    }
    this.ai.handoff.set({ tool: 'review', sources: this.sources(), weakTopics: missed });
    this.requestTool('review');
  }

  private requestTool(tool: string): void {
    void this.router.navigate([], { queryParams: { tool }, queryParamsHandling: 'merge' });
  }

  again(): void {
    this.phase.set('config');
    this.quiz.set(null);
    this.result.set(null);
  }
}
