import { NEO_PATTERNS } from './workspace-patterns-neo';

/**
 * The marks a workspace is told apart by: abstract, one ink, no colour. Each is drawn on a 48 x 48 tile with
 * `currentColor`, so it follows the theme; the tile's own background shows through the gaps.
 */
export interface WorkspacePattern {
  readonly id: string;
  /** What goes inside the <svg viewBox="0 0 48 48">. */
  readonly svg: string;
}

const P = (d: string) => `<path d="${d}"/>`;
const range = (n: number) => Array.from({ length: n }, (_, i) => i);
const join = (items: string[]) => items.join('');

/** A basket weave: pairs of short bars, across in one cell and up in the next. */
function basket(): string {
  return join(
    range(9).map((n) => {
      const x = (n % 3) * 16;
      const y = Math.floor(n / 3) * 16;
      return (n % 3 + Math.floor(n / 3)) % 2 === 0
        ? P(`M${x + 3} ${y + 5}H${x + 13}`) + P(`M${x + 3} ${y + 11}H${x + 13}`)
        : P(`M${x + 5} ${y + 3}V${y + 13}`) + P(`M${x + 11} ${y + 3}V${y + 13}`);
    }),
  );
}

const OWN_PATTERNS: readonly WorkspacePattern[] = [
  { id: 'ripple', svg: join([10, 20, 30, 40, 50].map((r) => `<circle cx="0" cy="48" r="${r}"/>`)) },
  { id: 'tide', svg: join([10, 20, 30, 40].map((y) => P(`M0 ${y}Q12 ${y - 9} 24 ${y}T48 ${y}`))) },
  { id: 'weave', svg: basket() },
  {
    id: 'strata',
    svg: join(
      [[0, 30], [10, 38], [4, 22], [18, 30], [0, 44], [12, 26]].map(
        ([x, w], i) => `<rect x="${x}" y="${3 + i * 7.5}" width="${w}" height="4.5" rx="2.25" fill="currentColor" stroke="none"/>`,
      ),
    ),
  },
  {
    id: 'contour',
    svg: join(
      [[1, 0], [0.74, 24], [0.5, 48], [0.28, 72]].map(
        ([s, r]) => `<path transform="translate(24 24) rotate(${r}) scale(${s}) translate(-24 -24)" d="M24 4C36 3 45 13 43 25S34 45 22 43 3 34 5 22 13 5 24 4Z"/>`,
      ),
    ),
  },
  { id: 'labyrinth', svg: P('M5 5H43V43H11V17H35V35H19V25H27') },
  {
    id: 'arches',
    svg: join([16, 28, 40].map((y, i) => P(`M${i % 2 ? -8 : 0} ${y}${range(4).map(() => 'a8 8 0 0 1 16 0').join('')}`))),
  },
  { id: 'burst', svg: join(range(20).map((i) => { const a = (i / 20) * Math.PI * 2; const r1 = 7; const r2 = i % 2 ? 15 : 22; return P(`M${(24 + Math.cos(a) * r1).toFixed(1)} ${(24 + Math.sin(a) * r1).toFixed(1)}L${(24 + Math.cos(a) * r2).toFixed(1)} ${(24 + Math.sin(a) * r2).toFixed(1)}`); })) },
  { id: 'ribbons', svg: join([-14, -4, 6].map((o) => P(`M-4 ${14 + o}C14 ${14 + o} 14 ${30 + o} 24 ${30 + o}S34 ${46 + o} 52 ${46 + o}`))) },
  {
    id: 'skyline',
    svg: join([14, 32, 22, 42, 18, 36].map((h, i) => `<path stroke-width="4.5" d="M${6 + i * 7.2} 46V${46 - h}"/>`)),
  },
  { id: 'venn', svg: `<circle cx="18" cy="19" r="13"/><circle cx="30" cy="19" r="13"/><circle cx="24" cy="30" r="13"/>` },
  { id: 'brackets', svg: join([5, 14, 23, 32].map((o) => P(`M${o} 43V${o}H43`))) },
  {
    id: 'mosaic',
    svg:
      `<rect x="4" y="4" width="18" height="12" rx="1.5" fill="currentColor" stroke="none"/><rect x="26" y="4" width="18" height="22" rx="1.5"/>` +
      `<rect x="4" y="20" width="18" height="24" rx="1.5"/><rect x="26" y="30" width="18" height="14" rx="1.5" fill="currentColor" stroke="none"/>`,
  },
  { id: 'rainbow', svg: join([8, 16, 24, 32, 40].map((r) => P(`M${24 - r} 46A${r} ${r} 0 0 1 ${24 + r} 46`))) },
  { id: 'knots', svg: `<rect x="9" y="9" width="30" height="30" rx="7" transform="rotate(45 24 24)"/><rect x="14" y="14" width="20" height="20" rx="5"/>` },
  { id: 'flow', svg: join([6, 14, 22, 30, 38].map((y, i) => P(`M0 ${y}C20 ${y} 28 ${12 + i * 6} 48 ${6 + i * 9}`))) },
];

/** The ones taken from the Neo Geo sheet, by their number in it. */
const NEO_CHOSEN = ['n151', 'n152', 'n153', 'n166', 'n169', 'n170', 'n181', 'n182', 'n185', 'n176', 'n191', 'n194', 'n195', 'n268', 'n266', 'n269', 'n296'];

export const WORKSPACE_PATTERNS: readonly WorkspacePattern[] = [
  ...OWN_PATTERNS,
  ...NEO_CHOSEN.map((id) => NEO_PATTERNS.find((p) => p.id === id)).filter((p): p is WorkspacePattern => !!p),
];

export const PATTERN_IDS: readonly string[] = WORKSPACE_PATTERNS.map((p) => p.id);

/** The mark for a stored value: the pattern it names, or one picked from `seed` (older workspaces stored a colour). */
export function patternFor(value: string | null | undefined, seed: string): WorkspacePattern {
  const named = WORKSPACE_PATTERNS.find((p) => p.id === value);
  if (named) return named;
  let hash = 0;
  for (const ch of seed || value || 'sophia') hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return WORKSPACE_PATTERNS[hash % WORKSPACE_PATTERNS.length];
}

/** A pattern to start from when a workspace is made. */
export function randomPatternId(): string {
  return PATTERN_IDS[Math.floor(Math.random() * PATTERN_IDS.length)];
}

export const PATTERN_SVG_ATTRS = 'fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"';

/** The mark as an image, for places that take a picture (the sidebar title); drawn in fixed neutral tones. */
export function patternDataUrl(value: string | null | undefined, seed: string): string {
  const { svg } = patternFor(value, seed);
  const markup =
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48"><rect width="48" height="48" fill="#ece8dd"/>` +
    `<g ${PATTERN_SVG_ATTRS.replace('currentColor', '#4a4638')} color="#4a4638" style="--wl-bg:#ece8dd">${svg.replaceAll('var(--wl-bg, #fff)', '#ece8dd').replaceAll('var(--wl-bg, transparent)', '#ece8dd')}</g></svg>`;
  return `data:image/svg+xml;utf8,${encodeURIComponent(markup)}`;
}
