/** A visual study deck: cards laid out on a horizontal board. The body of each card is Markdown. */
export type SlideColor = 'slate' | 'blue' | 'green' | 'amber' | 'rose' | 'violet';

export const SLIDE_COLORS: SlideColor[] = ['slate', 'blue', 'green', 'amber', 'rose', 'violet'];

/** Accent used for the top band and the tint of each color. */
export const SLIDE_COLOR_HEX: Record<SlideColor, string> = {
  slate: '#64748b',
  blue: '#3b82f6',
  green: '#22c55e',
  amber: '#f59e0b',
  rose: '#f43f5e',
  violet: '#8b5cf6',
};

export interface Slide {
  id: string;
  title: string;
  body: string;
  color: SlideColor;
}

export interface Deck {
  version: 1;
  slides: Slide[];
}

export function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export function newSlide(partial: Partial<Slide> = {}): Slide {
  return { id: newId(), title: '', body: '', color: 'slate', ...partial };
}

/** Tolerant parse: anything that is not a deck becomes an empty one-card deck. */
export function parseDeck(json: string | null | undefined): Deck {
  try {
    const raw = JSON.parse(json || '{}') as Partial<Deck>;
    const slides = Array.isArray(raw.slides) ? raw.slides : [];
    return {
      version: 1,
      slides: slides.map((s) => ({
        id: typeof s?.id === 'string' && s.id ? s.id : newId(),
        title: typeof s?.title === 'string' ? s.title : '',
        body: typeof s?.body === 'string' ? s.body : '',
        color: SLIDE_COLORS.includes(s?.color as SlideColor) ? (s.color as SlideColor) : 'slate',
      })),
    };
  } catch {
    return { version: 1, slides: [] };
  }
}

export function serializeDeck(deck: Deck): string {
  return JSON.stringify(deck);
}

/** Builds the JSON of a deck from [title, body, color?] triples, for starter templates. */
export function deckOf(cards: [string, string, SlideColor?][]): string {
  return serializeDeck({
    version: 1,
    slides: cards.map(([title, body, color]) => newSlide({ title, body, color: color ?? 'slate' })),
  });
}
