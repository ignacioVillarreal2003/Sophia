import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { MarkdownViewComponent } from '../markdown/markdown-view.component';
import { Deck, SLIDE_COLORS, SLIDE_COLOR_HEX, Slide, SlideColor, newSlide, parseDeck, serializeDeck } from './deck.model';

type DeckMode = 'board' | 'focus' | 'study';

const SAVE_DEBOUNCE_MS = 400;

/**
 * Study deck: not for presenting, but a visual and horizontal way to lay information out. The board shows every
 * card side by side, focus shows one large card, and study turns the cards into flashcards (title in front, body
 * behind) with a "knew it / review" pass.
 */
@Component({
  selector: 'app-presentation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, MarkdownViewComponent, TranslatePipe],
  template: `
    <div class="pd">
      <div class="pd__bar">
        <div class="pd__modes" role="tablist">
          @for (m of modes; track m.id) {
            <button
              type="button"
              role="tab"
              class="pd__mode"
              [class.pd__mode--active]="mode() === m.id"
              [attr.aria-selected]="mode() === m.id"
              (click)="setMode(m.id)"
            >
              <app-icon [name]="m.icon" [size]="15" /> {{ 'deck.mode.' + m.id | t }}
            </button>
          }
        </div>
        <span class="pd__count">{{ 'deck.count' | t: { count: slides().length } }}</span>
        @if (!readonly()) {
          <app-button size="sm" variant="secondary" icon="plus" (click)="addSlide()">{{ 'deck.add' | t }}</app-button>
        }
      </div>

      @if (slides().length === 0) {
        <div class="pd__empty">
          <p>{{ 'deck.empty' | t }}</p>
          @if (!readonly()) {
            <app-button variant="primary" icon="plus" (click)="addSlide()">{{ 'deck.add' | t }}</app-button>
          }
        </div>
      } @else if (mode() === 'board') {
        <div class="pd__lane" (dragover)="$event.preventDefault()">
          @for (slide of slides(); track slide.id; let i = $index) {
            <article
              class="pd__card"
              [class.pd__card--selected]="selectedId() === slide.id"
              [class.pd__card--drag]="dragId() === slide.id"
              [style.--card-color]="hex(slide.color)"
              [attr.draggable]="!readonly()"
              tabindex="0"
              (click)="select(slide.id)"
              (keydown.enter)="select(slide.id)"
              (dragstart)="onDragStart($event, slide.id)"
              (dragend)="dragId.set(null)"
              (drop)="onDrop($event, slide.id)"
            >
              <header class="pd__card-head">
                <span class="pd__index">{{ i + 1 }}</span>
                <h3 class="pd__card-title" [class.pd__card-title--empty]="!slide.title">
                  {{ slide.title || ('deck.untitled' | t) }}
                </h3>
              </header>
              <div class="pd__card-body">
                <app-markdown-view [source]="slide.body" [readonly]="true" />
              </div>
            </article>
          }
        </div>

        @if (selected(); as card) {
          @if (!readonly()) {
            <section class="pd__editor" [style.--card-color]="hex(card.color)">
              <input
                class="pd__input"
                [value]="card.title"
                [placeholder]="'deck.titlePlaceholder' | t"
                (input)="update(card.id, { title: $any($event.target).value })"
              />
              <textarea
                class="pd__textarea"
                rows="6"
                [value]="card.body"
                [placeholder]="'deck.bodyPlaceholder' | t"
                (input)="update(card.id, { body: $any($event.target).value })"
              ></textarea>
              <div class="pd__editor-row">
                <div class="pd__swatches" role="group" [attr.aria-label]="'deck.color' | t">
                  @for (c of colors; track c) {
                    <button
                      type="button"
                      class="pd__swatch"
                      [class.pd__swatch--on]="card.color === c"
                      [style.background]="hex(c)"
                      [attr.aria-label]="c"
                      (click)="update(card.id, { color: c })"
                    ></button>
                  }
                </div>
                <span class="pd__spacer"></span>
                <app-button size="sm" variant="ghost" icon="arrow-left" iconOnly [disabled]="selectedIndex() === 0" (click)="move(card.id, -1)" />
                <app-button size="sm" variant="ghost" icon="arrow-right" iconOnly [disabled]="selectedIndex() === slides().length - 1" (click)="move(card.id, 1)" />
                <app-button size="sm" variant="ghost" icon="copy" iconOnly (click)="duplicate(card.id)" />
                <app-button size="sm" variant="ghost" icon="trash" iconOnly (click)="remove(card.id)" />
              </div>
            </section>
          }
        }
      } @else if (mode() === 'focus') {
        @if (current(); as card) {
          <div class="pd__focus">
            <button type="button" class="pd__nav" [disabled]="index() === 0" [attr.aria-label]="'deck.prev' | t" (click)="step(-1)">
              <app-icon name="chevron-left" [size]="22" />
            </button>
            <article class="pd__big" [style.--card-color]="hex(card.color)">
              <h2 class="pd__big-title">{{ card.title || ('deck.untitled' | t) }}</h2>
              <div class="pd__big-body"><app-markdown-view [source]="card.body" [readonly]="true" /></div>
              <footer class="pd__big-foot">{{ index() + 1 }} / {{ slides().length }}</footer>
            </article>
            <button type="button" class="pd__nav" [disabled]="index() >= slides().length - 1" [attr.aria-label]="'deck.next' | t" (click)="step(1)">
              <app-icon name="chevron-right" [size]="22" />
            </button>
          </div>
          <div class="pd__dots">
            @for (slide of slides(); track slide.id; let i = $index) {
              <button type="button" class="pd__dot" [class.pd__dot--on]="i === index()" [attr.aria-label]="i + 1" (click)="index.set(i)"></button>
            }
          </div>
        }
      } @else {
        <div class="pd__study">
          @if (studyDone()) {
            <div class="pd__summary">
              <h2>{{ 'deck.study.done' | t }}</h2>
              <p>{{ 'deck.study.result' | t: { known: known().size, total: studyOrder().length } }}</p>
              <div class="pd__summary-actions">
                @if (review().size > 0) {
                  <app-button variant="primary" (click)="startStudy(true)">{{ 'deck.study.reviewMissed' | t: { count: review().size } }}</app-button>
                }
                <app-button variant="secondary" (click)="startStudy(false)">{{ 'deck.study.restart' | t }}</app-button>
              </div>
            </div>
          } @else if (studyCard(); as card) {
            <div class="pd__study-progress">
              <span>{{ studyIndex() + 1 }} / {{ studyOrder().length }}</span>
              <span class="pd__study-bar"><span [style.width.%]="(studyIndex() / studyOrder().length) * 100"></span></span>
              <button type="button" class="pd__link" (click)="startStudy(false, true)">
                <app-icon name="swap" [size]="14" /> {{ 'deck.study.shuffle' | t }}
              </button>
            </div>
            <button type="button" class="pd__flash" [class.pd__flash--back]="flipped()" [style.--card-color]="hex(card.color)" (click)="flipped.set(!flipped())">
              @if (!flipped()) {
                <span class="pd__flash-hint">{{ 'deck.study.front' | t }}</span>
                <span class="pd__flash-title">{{ card.title || ('deck.untitled' | t) }}</span>
                <span class="pd__flash-tap">{{ 'deck.study.tap' | t }}</span>
              } @else {
                <span class="pd__flash-body"><app-markdown-view [source]="card.body || card.title" [readonly]="true" /></span>
              }
            </button>
            <div class="pd__grade">
              <app-button variant="secondary" icon="x" [disabled]="!flipped()" (click)="grade(false)">{{ 'deck.study.review' | t }}</app-button>
              <app-button variant="primary" icon="check" [disabled]="!flipped()" (click)="grade(true)">{{ 'deck.study.know' | t }}</app-button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styleUrl: './presentation.component.scss',
})
export class PresentationComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly content = input.required<string>();
  readonly readonly = input(false);
  readonly contentChange = output<string>();
  readonly saveRequested = output<void>();

  readonly colors = SLIDE_COLORS;
  readonly modes: { id: DeckMode; icon: 'layout-cards' | 'file' | 'brain' }[] = [
    { id: 'board', icon: 'layout-cards' },
    { id: 'focus', icon: 'file' },
    { id: 'study', icon: 'brain' },
  ];

  readonly mode = signal<DeckMode>('board');
  readonly deck = signal<Deck>({ version: 1, slides: [] });
  readonly slides = computed(() => this.deck().slides);
  readonly selectedId = signal<string | null>(null);
  readonly index = signal(0);
  readonly dragId = signal<string | null>(null);

  readonly selected = computed(() => this.slides().find((s) => s.id === this.selectedId()) ?? null);
  readonly selectedIndex = computed(() => this.slides().findIndex((s) => s.id === this.selectedId()));
  readonly current = computed(() => this.slides()[Math.min(this.index(), this.slides().length - 1)] ?? null);

  // study mode
  readonly studyOrder = signal<string[]>([]);
  readonly studyIndex = signal(0);
  readonly flipped = signal(false);
  readonly known = signal<Set<string>>(new Set());
  readonly review = signal<Set<string>>(new Set());
  readonly studyDone = computed(() => this.studyOrder().length > 0 && this.studyIndex() >= this.studyOrder().length);
  readonly studyCard = computed(() => {
    const id = this.studyOrder()[this.studyIndex()];
    return this.slides().find((s) => s.id === id) ?? null;
  });

  private lastEmitted = '';
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const json = this.content();
      untracked(() => {
        // Our own edits come back through the input; only adopt content that is really somebody else's.
        if (json === this.lastEmitted) return;
        this.lastEmitted = json;
        const deck = parseDeck(json);
        this.deck.set(deck);
        if (!deck.slides.some((s) => s.id === this.selectedId())) {
          this.selectedId.set(deck.slides[0]?.id ?? null);
        }
        this.index.update((i) => Math.min(i, Math.max(0, deck.slides.length - 1)));
      });
    });
    this.destroyRef.onDestroy(() => this.flush());
  }

  hex(color: SlideColor): string {
    return SLIDE_COLOR_HEX[color] ?? SLIDE_COLOR_HEX.slate;
  }

  setMode(mode: DeckMode): void {
    this.mode.set(mode);
    if (mode === 'study' && this.studyOrder().length === 0) this.startStudy(false);
  }

  // ---- editing ----------------------------------------------------------------------------------------------

  select(id: string): void {
    this.selectedId.set(id);
    this.index.set(Math.max(0, this.slides().findIndex((s) => s.id === id)));
  }

  addSlide(): void {
    if (this.readonly()) return;
    const card = newSlide({ color: this.slides().at(-1)?.color ?? 'slate' });
    this.commit({ ...this.deck(), slides: [...this.slides(), card] });
    this.selectedId.set(card.id);
    this.index.set(this.slides().length - 1);
    this.mode.set('board');
  }

  update(id: string, patch: Partial<Slide>): void {
    if (this.readonly()) return;
    this.commit({ ...this.deck(), slides: this.slides().map((s) => (s.id === id ? { ...s, ...patch } : s)) });
  }

  move(id: string, delta: number): void {
    const list = [...this.slides()];
    const from = list.findIndex((s) => s.id === id);
    const to = from + delta;
    if (from < 0 || to < 0 || to >= list.length) return;
    const [card] = list.splice(from, 1);
    list.splice(to, 0, card);
    this.commit({ ...this.deck(), slides: list });
  }

  duplicate(id: string): void {
    const list = [...this.slides()];
    const at = list.findIndex((s) => s.id === id);
    if (at < 0) return;
    const copy = newSlide({ ...list[at] });
    list.splice(at + 1, 0, copy);
    this.commit({ ...this.deck(), slides: list });
    this.selectedId.set(copy.id);
  }

  remove(id: string): void {
    const list = this.slides();
    const at = list.findIndex((s) => s.id === id);
    const next = list.filter((s) => s.id !== id);
    this.commit({ ...this.deck(), slides: next });
    this.selectedId.set(next[Math.min(at, next.length - 1)]?.id ?? null);
  }

  onDragStart(event: DragEvent, id: string): void {
    if (this.readonly()) return;
    this.dragId.set(id);
    event.dataTransfer?.setData('text/plain', id);
    if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
  }

  onDrop(event: DragEvent, targetId: string): void {
    event.preventDefault();
    const sourceId = this.dragId();
    this.dragId.set(null);
    if (!sourceId || sourceId === targetId || this.readonly()) return;
    const list = [...this.slides()];
    const from = list.findIndex((s) => s.id === sourceId);
    const to = list.findIndex((s) => s.id === targetId);
    if (from < 0 || to < 0) return;
    const [card] = list.splice(from, 1);
    list.splice(to, 0, card);
    this.commit({ ...this.deck(), slides: list });
  }

  /** Writes the pending change out right away (before saving or leaving the page). */
  flush(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
      this.emit();
    }
  }

  private commit(deck: Deck): void {
    this.deck.set(deck);
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      this.emit();
    }, SAVE_DEBOUNCE_MS);
  }

  private emit(): void {
    const json = serializeDeck(this.deck());
    this.lastEmitted = json;
    this.contentChange.emit(json);
  }

  // ---- focus ------------------------------------------------------------------------------------------------

  step(delta: number): void {
    this.index.update((i) => Math.max(0, Math.min(this.slides().length - 1, i + delta)));
  }

  @HostListener('window:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) return;
    if (this.mode() === 'focus') {
      if (event.key === 'ArrowRight') this.step(1);
      if (event.key === 'ArrowLeft') this.step(-1);
    } else if (this.mode() === 'study' && !this.studyDone()) {
      if (event.key === ' ' || event.key === 'Enter') {
        event.preventDefault();
        this.flipped.update((f) => !f);
      } else if (this.flipped() && event.key === 'ArrowRight') {
        this.grade(true);
      } else if (this.flipped() && event.key === 'ArrowLeft') {
        this.grade(false);
      }
    }
  }

  // ---- study ------------------------------------------------------------------------------------------------

  /** @param onlyMissed repeat just the cards marked for review; @param shuffle random order. */
  startStudy(onlyMissed: boolean, shuffle = false): void {
    let ids = this.slides().map((s) => s.id);
    if (onlyMissed) ids = ids.filter((id) => this.review().has(id));
    if (shuffle) {
      for (let i = ids.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [ids[i], ids[j]] = [ids[j], ids[i]];
      }
    }
    this.studyOrder.set(ids);
    this.studyIndex.set(0);
    this.flipped.set(false);
    this.known.set(new Set());
    this.review.set(new Set());
  }

  grade(knew: boolean): void {
    const id = this.studyOrder()[this.studyIndex()];
    if (!id) return;
    const bucket = knew ? this.known : this.review;
    bucket.update((set) => new Set(set).add(id));
    this.flipped.set(false);
    this.studyIndex.update((i) => i + 1);
  }
}
