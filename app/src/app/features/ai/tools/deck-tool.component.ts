import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { DeckCard, DeckMode, StudyLevel } from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SLIDE_COLORS, SLIDE_COLOR_HEX, SlideColor, deckOf } from '../../document-workspace/presentation/deck.model';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

const LEVELS: StudyLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

/** A card board (one idea per card) or a flashcard set, both saved as a card board the study mode can run. */
@Component({
  selector: 'app-ai-deck-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.deck.intro' | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        <div class="ait__field">
          <span class="ait__label">{{ 'ai.deck.mode' | t }}</span>
          <div class="ait__chips" role="radiogroup" [attr.aria-label]="'ai.deck.mode' | t">
            @for (m of modes; track m) {
              <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="mode() === m" [attr.aria-checked]="mode() === m" (click)="mode.set(m)">
                {{ 'ai.deck.mode.' + m | t }}
              </button>
            }
          </div>
          <span class="ait__hint">{{ 'ai.deck.mode.' + mode() + '.hint' | t }}</span>
        </div>

        <div class="ait__field">
          <label class="ait__label" for="deck-topic">{{ 'ai.field.topic' | t }}</label>
          <input id="deck-topic" class="ait__input" maxlength="500" [placeholder]="'ai.deck.topicPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
        </div>

<details class="ait__more">
          <summary>{{ 'ai.options' | t }}</summary>
          <div class="ait__more-body">
        <div class="ait__row">
          <div class="ait__field">
            <label class="ait__label" for="deck-count">{{ 'ai.deck.count' | t }}</label>
            <input id="deck-count" class="ait__input" type="number" min="3" max="40" [value]="count()" (input)="setCount($any($event.target).value)" />
          </div>
          <div class="ait__field">
            <label class="ait__label" for="deck-level">{{ 'ai.field.level' | t }}</label>
            <select id="deck-level" class="ait__select" [value]="level()" (change)="level.set($any($event.target).value)">
              @for (l of levels; track l) {
                <option [value]="l" [selected]="l === level()">{{ 'ai.level.' + l | t }}</option>
              }
            </select>
          </div>
        </div>

          </div>
        </details>

        <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }

        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">{{ 'ai.deck.generate' | t }}</app-button>
        </div>
      </form>

      @if (cards().length > 0) {
        <section class="ait__result">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ title() }}</h3>
            <span class="ait__live">{{ 'ai.deck.cards' | t: { count: cards().length } }}</span>
          </div>
          <div class="ait__cards">
            @for (card of cards(); track $index) {
              <article class="ait__card" [style.--tone]="tone(card)">
                <h4>{{ card.title }}</h4>
                <p>{{ card.body }}</p>
                <button type="button" class="deck__drop" [attr.aria-label]="'ai.deck.removeCard' | t" (click)="remove($index)">
                  <app-icon name="x" [size]="12" />
                </button>
              </article>
            }
          </div>
          <div class="ait__actions">
            <app-button variant="dark" icon="layout-cards" [loading]="saving()" (click)="save()">{{ 'ai.deck.create' | t }}</app-button>
            <app-button variant="text" icon="sparkles" (click)="generate()" [disabled]="!canGenerate()">{{ 'ai.regenerate' | t }}</app-button>
          </div>
          <p class="ait__hint">{{ 'ai.deck.studyHint' | t }}</p>
        </section>
      }
    </div>
  `,
  styles: `
    .ait__card { position: relative; }
    .deck__drop {
      position: absolute; top: 6px; right: 6px; display: grid; place-items: center; width: 22px; height: 22px;
      padding: 0; color: var(--text-muted); background: var(--surface); border: 1px solid var(--border);
      border-radius: 50%; cursor: pointer; opacity: 0;
      transition: opacity var(--duration-fast) var(--ease-standard);
      &:hover { color: var(--danger); }
      &:focus-visible { opacity: 1; outline: 2px solid var(--accent-focus); }
    }
    .ait__card:hover .deck__drop, .ait__card:focus-within .deck__drop { opacity: 1; }
  `,
})
export class DeckToolComponent extends AiToolBase implements OnInit {
  readonly modes: DeckMode[] = ['CONCEPTS', 'FLASHCARDS'];
  readonly levels = LEVELS;

  readonly mode = signal<DeckMode>('CONCEPTS');
  readonly topic = signal('');
  readonly count = signal(12);
  readonly level = signal<StudyLevel>('INTERMEDIATE');
  readonly sources = signal<string[]>([]);

  readonly title = signal('');
  readonly cards = signal<DeckCard[]>([]);

  readonly canGenerate = computed(() => !this.busy() && (this.topic().trim().length > 0 || this.sources().length > 0));

  ngOnInit(): void {
    const handoff = this.ai.handoff();
    if (handoff?.tool === 'cards') {
      if (handoff.sources) this.sources.set(handoff.sources);
      if (handoff.topic) this.topic.set(handoff.topic);
      this.ai.handoff.set(null);
    }
  }

  setCount(value: string): void {
    const n = Math.round(Number(value));
    this.count.set(Number.isFinite(n) ? Math.min(40, Math.max(3, n)) : 12);
  }

  tone(card: DeckCard): string {
    return SLIDE_COLOR_HEX[(SLIDE_COLORS.includes(card.color as SlideColor) ? card.color : 'slate') as SlideColor];
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    this.ai
      .deck({
        workspaceId: this.workspaceId(),
        mode: this.mode(),
        topic: this.topic().trim() || undefined,
        sources: this.sources().map((artifactId) => ({ artifactId })),
        count: this.count(),
        level: this.level(),
      })
      .subscribe({
        next: (result) => {
          this.title.set(result.title);
          this.cards.set(result.slides);
          this.busy.set(false);
          this.refreshStatus();
        },
        error: (err) => this.fail(err),
      });
  }

  remove(index: number): void {
    this.cards.update((list) => list.filter((_, i) => i !== index));
  }

  save(): void {
    const prefix = this.mode() === 'FLASHCARDS' ? this.t('ai.deck.flashPrefix') : '';
    this.keep({
      name: this.nameOf(`${prefix}${this.title()}`, this.topic()),
      type: 'PRESENTATION',
      content: deckOf(this.cards().map((c) => [c.title, c.body, c.color as SlideColor])),
    });
  }
}
