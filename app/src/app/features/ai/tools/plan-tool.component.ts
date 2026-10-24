import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { StudyLevel, StudyPlanResult } from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

const LEVELS: StudyLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];
const WEEKDAYS = [1, 2, 3, 4, 5, 6, 7];
const MINUTES = [30, 45, 60, 90, 120, 180, 240];

/** Local calendar day as yyyy-MM-dd (toISOString would give the UTC day, which can be yesterday or tomorrow). */
export function localIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** A day-by-day plan up to an exam date, saved as a checklist the student ticks off. */
@Component({
  selector: 'app-ai-plan-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.plan.intro' | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        <div class="ait__field">
          <label class="ait__label" for="plan-topic">{{ 'ai.field.topic' | t }}</label>
          <input id="plan-topic" class="ait__input" maxlength="500" [placeholder]="'ai.plan.topicPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
        </div>

        <div class="ait__row">
          <div class="ait__field">
            <label class="ait__label" for="plan-date">{{ 'ai.plan.examDate' | t }}</label>
            <input id="plan-date" class="ait__input" type="date" [min]="today" [max]="latest" [value]="examDate()" (input)="examDate.set($any($event.target).value)" />
          </div>
          <div class="ait__field">
            <label class="ait__label" for="plan-minutes">{{ 'ai.plan.minutes' | t }}</label>
            <select id="plan-minutes" class="ait__select" [value]="minutes()" (change)="minutes.set(+$any($event.target).value)">
              @for (m of minuteOptions; track m) {
                <option [value]="m" [selected]="m === minutes()">{{ 'ai.plan.minutesOption' | t: { n: m } }}</option>
              }
            </select>
          </div>
          <div class="ait__field">
            <label class="ait__label" for="plan-level">{{ 'ai.field.level' | t }}</label>
            <select id="plan-level" class="ait__select" [value]="level()" (change)="level.set($any($event.target).value)">
              @for (l of levels; track l) {
                <option [value]="l" [selected]="l === level()">{{ 'ai.level.' + l | t }}</option>
              }
            </select>
          </div>
        </div>

        <div class="ait__field">
          <span class="ait__label">{{ 'ai.plan.daysOff' | t }}</span>
          <div class="ait__chips">
            @for (d of weekdays; track d) {
              <button type="button" class="ait__chip" [class.ait__chip--on]="daysOff().includes(d)" [attr.aria-pressed]="daysOff().includes(d)" (click)="toggleDay(d)">{{ 'ai.weekday.' + d | t }}</button>
            }
          </div>
          <span class="ait__hint">{{ 'ai.plan.daysOffHint' | t }}</span>
        </div>

        <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }
        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">{{ 'ai.plan.generate' | t }}</app-button>
        </div>
      </form>

      @if (plan(); as p) {
        <section class="ait__result">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ p.title }}</h3>
            <span class="ait__live">{{ 'ai.plan.summaryDays' | t: { days: p.days.length, hours: totalHours() } }}</span>
          </div>
          @if (p.summary) {
            <p class="ait__intro">{{ p.summary }}</p>
          }
          <ol class="plan__days">
            @for (day of p.days; track day.date) {
              <li class="plan__day">
                <div class="plan__date">
                  <strong>{{ dayLabel(day.date) }}</strong>
                  <span>{{ day.focus }}</span>
                </div>
                <ul class="plan__tasks">
                  @for (task of day.tasks; track $index) {
                    <li><span>{{ task.text }}</span><em>{{ task.minutes }} min</em></li>
                  }
                </ul>
              </li>
            }
          </ol>
          @if (p.tips.length > 0) {
            <div class="plan__tips">
              <strong>{{ 'ai.plan.tips' | t }}</strong>
              <ul>
                @for (tip of p.tips; track $index) {
                  <li>{{ tip }}</li>
                }
              </ul>
            </div>
          }
          <div class="ait__actions">
            <app-button variant="dark" icon="check-circle" [loading]="saving()" (click)="save()">{{ 'ai.plan.create' | t }}</app-button>
            <app-button variant="text" icon="sparkles" (click)="generate()" [disabled]="!canGenerate()">{{ 'ai.regenerate' | t }}</app-button>
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .plan__days { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
    .plan__day { display: grid; grid-template-columns: 170px 1fr; gap: var(--space-md); padding: 10px 12px; background: var(--surface-sunken); border-radius: var(--radius-md); }
    .plan__date { display: flex; flex-direction: column; gap: 2px; font-size: var(--type-caption-size); strong { color: var(--text); text-transform: capitalize; } span { color: var(--text-muted); } }
    .plan__tasks { display: flex; flex-direction: column; gap: 4px; margin: 0; padding: 0; list-style: none; font-size: var(--type-caption-size); color: var(--text);
      li { display: flex; justify-content: space-between; gap: 12px; }
      em { flex-shrink: 0; font-style: normal; color: var(--text-subtle); }
    }
    .plan__tips { font-size: var(--type-caption-size); color: var(--text-muted); ul { margin: 6px 0 0; padding-left: 18px; } }
    @media (max-width: 640px) { .plan__day { grid-template-columns: 1fr; } }
  `,
})
export class PlanToolComponent extends AiToolBase implements OnInit {
  readonly levels = LEVELS;
  readonly weekdays = WEEKDAYS;
  readonly minuteOptions = MINUTES;
  readonly today = localIsoDate(new Date());
  readonly latest = localIsoDate(new Date(Date.now() + 119 * 86_400_000));

  readonly topic = signal('');
  readonly examDate = signal('');
  readonly minutes = signal(60);
  readonly level = signal<StudyLevel>('INTERMEDIATE');
  readonly daysOff = signal<number[]>([]);
  readonly sources = signal<string[]>([]);

  readonly plan = signal<StudyPlanResult | null>(null);

  readonly canGenerate = computed(
    () => !this.busy() && !!this.examDate() && (this.topic().trim().length > 0 || this.sources().length > 0),
  );
  readonly totalHours = computed(() => {
    const minutes = this.plan()?.days.reduce((sum, d) => sum + d.tasks.reduce((s, t) => s + t.minutes, 0), 0) ?? 0;
    return Math.round((minutes / 60) * 10) / 10;
  });

  ngOnInit(): void {
    const handoff = this.ai.handoff();
    if (handoff?.tool === 'plan') {
      if (handoff.sources) this.sources.set(handoff.sources);
      if (handoff.topic) this.topic.set(handoff.topic);
      this.ai.handoff.set(null);
    }
  }

  toggleDay(day: number): void {
    this.daysOff.update((list) => (list.includes(day) ? list.filter((d) => d !== day) : list.length >= 6 ? list : [...list, day]));
  }

  dayLabel(isoDate: string): string {
    const date = new Date(`${isoDate}T12:00:00`);
    return new Intl.DateTimeFormat(this.i18n.locale(), { weekday: 'short', day: 'numeric', month: 'short' }).format(date);
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    this.ai
      .studyPlan({
        workspaceId: this.workspaceId(),
        topic: this.topic().trim() || undefined,
        sources: this.sources().map((artifactId) => ({ artifactId })),
        examDate: this.examDate(),
        today: this.today,
        minutesPerDay: this.minutes(),
        daysOff: this.daysOff(),
        level: this.level(),
      })
      .subscribe({
        next: (plan) => {
          this.plan.set(plan);
          this.busy.set(false);
          this.refreshStatus();
        },
        error: (err) => this.fail(err),
      });
  }

  save(): void {
    const plan = this.plan();
    if (!plan) {
      return;
    }
    this.keep({ name: this.nameOf(plan.title, this.topic()), type: 'CHECKLIST', content: plan.markdown });
  }
}
