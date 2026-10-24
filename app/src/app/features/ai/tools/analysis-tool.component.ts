import { ChangeDetectionStrategy, Component, OnInit, computed, input, signal } from '@angular/core';
import {
  DebateResult,
  GapsResult,
  LENSES,
  Lens,
  PerspectivesResult,
  StudyLevel,
} from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { MarkdownViewComponent } from '../../document-workspace/markdown/markdown-view.component';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

export type AnalysisKind = 'perspectives' | 'debate' | 'gaps';
type Report = PerspectivesResult | DebateResult | GapsResult;

const LEVELS: StudyLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

/**
 * The three tools that look at material critically, each ending in a document: a subject seen from several points
 * of view, the case for and against a motion, and a review of the student's own notes for what is missing or wrong.
 */
@Component({
  selector: 'app-ai-analysis-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, MarkdownViewComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.' + kind() + '.intro' | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        @switch (kind()) {
          @case ('perspectives') {
            <div class="ait__field">
              <label class="ait__label" for="an-topic">{{ 'ai.field.topic' | t }}</label>
              <input id="an-topic" class="ait__input" maxlength="500" [placeholder]="'ai.perspectives.topicPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
            </div>
            <div class="ait__field">
              <span class="ait__label">{{ 'ai.perspectives.lenses' | t }}</span>
              <div class="ait__chips">
                @for (lens of lenses; track lens) {
                  <button type="button" class="ait__chip" [class.ait__chip--on]="chosenLenses().includes(lens)" [attr.aria-pressed]="chosenLenses().includes(lens)" (click)="toggleLens(lens)">
                    @if (chosenLenses().includes(lens)) {
                      <app-icon name="check" [size]="13" />
                    }
                    {{ 'ai.lens.' + lens | t }}
                  </button>
                }
              </div>
              <span class="ait__hint">{{ 'ai.perspectives.lensesHint' | t }}</span>
            </div>
            <div class="ait__field">
              <label class="ait__label" for="an-custom">{{ 'ai.perspectives.custom' | t }}</label>
              <input id="an-custom" class="ait__input" maxlength="190" [placeholder]="'ai.perspectives.customPlaceholder' | t" [value]="custom()" (input)="custom.set($any($event.target).value)" />
              <span class="ait__hint">{{ 'ai.perspectives.customHint' | t }}</span>
            </div>
          }
          @case ('debate') {
            <div class="ait__field">
              <label class="ait__label" for="an-motion">{{ 'ai.debate.motion' | t }}</label>
              <input id="an-motion" class="ait__input" maxlength="500" [placeholder]="'ai.debate.motionPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
              <span class="ait__hint">{{ 'ai.debate.motionHint' | t }}</span>
            </div>
          }
          @case ('gaps') {
            <div class="ait__field">
              <label class="ait__label" for="an-subject">{{ 'ai.gaps.subject' | t }}</label>
              <input id="an-subject" class="ait__input" maxlength="500" [placeholder]="'ai.gaps.subjectPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
            </div>
          }
        }

        @if (kind() === 'gaps') {
          <app-ai-sources [workspaceId]="workspaceId()" [max]="6" [label]="'ai.gaps.notes' | t" [(selected)]="sources" />
          <app-ai-sources [workspaceId]="workspaceId()" [max]="6" [label]="'ai.gaps.reference' | t" [(selected)]="reference" />
          <span class="ait__hint">{{ 'ai.gaps.referenceHint' | t }}</span>
        } @else {
          <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />
        }

        <div class="ait__field">
          <label class="ait__label" for="an-level">{{ 'ai.field.level' | t }}</label>
          <select id="an-level" class="ait__select" [value]="level()" (change)="level.set($any($event.target).value)">
            @for (l of levels; track l) {
              <option [value]="l" [selected]="l === level()">{{ 'ai.level.' + l | t }}</option>
            }
          </select>
        </div>

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }
        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">{{ 'ai.' + kind() + '.generate' | t }}</app-button>
        </div>
      </form>

      @if (report(); as r) {
        <section class="ait__result">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ r.title }}</h3>
            @if (gaps(); as g) {
              <span class="ait__live">{{ 'ai.gaps.coverage' | t: { pct: g.coverage } }}</span>
            }
          </div>
          @if (gaps(); as g) {
            <div class="ait__meter" role="progressbar" [attr.aria-valuenow]="g.coverage" aria-valuemin="0" aria-valuemax="100"><span [style.width.%]="g.coverage"></span></div>
          }
          <div class="ait__paper">
            <app-markdown-view [source]="r.markdown" [readonly]="true" />
          </div>
          <div class="ait__actions">
            <app-button variant="dark" icon="file-text" [loading]="saving()" (click)="save()">{{ 'ai.createDocument' | t }}</app-button>
            <app-button variant="secondary" icon="copy" (click)="copy(r.markdown)">{{ 'ai.copy' | t }}</app-button>
            <app-button variant="text" icon="sparkles" (click)="generate()" [disabled]="!canGenerate()">{{ 'ai.regenerate' | t }}</app-button>
          </div>
        </section>
      }
    </div>
  `,
})
export class AnalysisToolComponent extends AiToolBase implements OnInit {
  readonly kind = input<AnalysisKind>('perspectives');

  readonly lenses = LENSES;
  readonly levels = LEVELS;

  readonly topic = signal('');
  readonly level = signal<StudyLevel>('INTERMEDIATE');
  readonly chosenLenses = signal<Lens[]>([]);
  readonly custom = signal('');
  readonly sources = signal<string[]>([]);
  readonly reference = signal<string[]>([]);

  readonly report = signal<Report | null>(null);
  readonly gaps = computed(() => {
    const r = this.report();
    return r && 'coverage' in r ? r : null;
  });

  readonly canGenerate = computed(() => {
    if (this.busy()) {
      return false;
    }
    switch (this.kind()) {
      case 'debate':
        return this.topic().trim().length > 0;
      case 'gaps':
        return this.sources().length > 0;
      default:
        return this.topic().trim().length > 0 || this.sources().length > 0;
    }
  });

  ngOnInit(): void {
    const handoff = this.ai.handoff();
    if (handoff && handoff.tool === this.kind()) {
      if (handoff.sources) this.sources.set(handoff.sources);
      if (handoff.topic) this.topic.set(handoff.topic);
      this.ai.handoff.set(null);
    }
  }

  toggleLens(lens: Lens): void {
    this.chosenLenses.update((list) => (list.includes(lens) ? list.filter((l) => l !== lens) : list.length >= 5 ? list : [...list, lens]));
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    const workspaceId = this.workspaceId();
    const sources = this.sources().map((artifactId) => ({ artifactId }));
    const topic = this.topic().trim() || undefined;
    const level = this.level();
    const next = (result: Report) => {
      this.report.set(result);
      this.busy.set(false);
      this.refreshStatus();
    };
    const error = (err: { code?: string; message?: string; status?: number }) => this.fail(err);
    switch (this.kind()) {
      case 'perspectives': {
        const customLenses = this.custom()
          .split(',')
          .map((s) => s.trim().slice(0, 60))
          .filter(Boolean)
          .slice(0, 3);
        this.ai
          .perspectives({ workspaceId, topic, sources, level, lenses: this.chosenLenses(), customLenses })
          .subscribe({ next, error });
        break;
      }
      case 'debate':
        this.ai.debate({ workspaceId, motion: this.topic().trim(), sources, level }).subscribe({ next, error });
        break;
      case 'gaps':
        this.ai
          .gaps({
            workspaceId,
            topic,
            level,
            notes: sources,
            reference: this.reference().map((artifactId) => ({ artifactId })),
          })
          .subscribe({ next, error });
        break;
    }
  }

  save(): void {
    const r = this.report();
    if (!r) {
      return;
    }
    this.keep({ name: this.nameOf(r.title, this.topic()), type: 'MARKDOWN_DOCUMENT', content: r.markdown });
  }
}
