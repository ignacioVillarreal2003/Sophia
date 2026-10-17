/**
 * Whiteboard data model and geometry. A board is plain JSON: a list of elements (shapes, notes, text, arrows,
 * strokes, frames, images) in z-order plus the viewport. Elements are treated as immutable: every change replaces
 * the element object, which keeps undo cheap and rendering cacheable.
 */
export type ElementType =
  | 'note'
  | 'box'
  | 'diamond'
  | 'ellipse'
  | 'triangle'
  | 'text'
  | 'arrow'
  | 'line'
  | 'pen'
  | 'frame'
  | 'image';

/** Legacy named colors, still the default look of sticky notes. */
export type BoardColor = 'yellow' | 'blue' | 'green' | 'rose' | 'violet' | 'slate' | 'white';
export const BOARD_COLORS: BoardColor[] = ['yellow', 'blue', 'green', 'rose', 'violet', 'slate', 'white'];
export const BOARD_PALETTE: Record<BoardColor, { fill: string; stroke: string }> = {
  yellow: { fill: '#fef08a', stroke: '#ca8a04' },
  blue: { fill: '#bfdbfe', stroke: '#2563eb' },
  green: { fill: '#bbf7d0', stroke: '#16a34a' },
  rose: { fill: '#fecdd3', stroke: '#e11d48' },
  violet: { fill: '#ddd6fe', stroke: '#7c3aed' },
  slate: { fill: '#e2e8f0', stroke: '#475569' },
  white: { fill: '#ffffff', stroke: '#334155' },
};

export const INK = '#1e293b';
export const STROKE_SWATCHES = ['#1e293b', '#dc2626', '#16a34a', '#2563eb', '#d97706', '#7c3aed', '#db2777', '#0891b2'];
export const FILL_SWATCHES = ['none', '#ffffff', '#fecaca', '#bbf7d0', '#bfdbfe', '#fde68a', '#ddd6fe', '#fbcfe8', '#a5f3fc', '#e2e8f0'];
export const BACKGROUNDS = ['#ffffff', '#fdf6e3', '#f1f5f9', '#ecfdf5', '#fff1f2', 'transparent'];

export type FillStyle = 'solid' | 'hachure' | 'cross';
export type StrokeStyle = 'solid' | 'dashed' | 'dotted';
export type Head = 'none' | 'arrow' | 'triangle' | 'dot' | 'bar';
export type Route = 'straight' | 'curve' | 'elbow';
export type FontFamily = 'sans' | 'hand' | 'mono';
export type Align = 'left' | 'center' | 'right';

export const FONT_STACKS: Record<FontFamily, string> = {
  sans: "system-ui, -apple-system, 'Segoe UI', sans-serif",
  hand: "'Segoe Print', 'Bradley Hand', 'Comic Sans MS', 'Chalkboard SE', cursive",
  mono: "ui-monospace, 'Cascadia Code', Consolas, monospace",
};
export const FONT_SIZES = [16, 20, 28, 40];

export interface BoardElement {
  id: string;
  type: ElementType;
  x: number;
  y: number;
  w: number;
  h: number;
  text: string;
  color: BoardColor;
  // look
  stroke?: string;
  fill?: string;
  fillStyle?: FillStyle;
  strokeWidth?: number;
  strokeStyle?: StrokeStyle;
  opacity?: number;
  /** 0 clean, 1 hand-drawn, 2 very sketchy */
  rough?: number;
  round?: boolean;
  fontSize?: number;
  font?: FontFamily;
  align?: Align;
  // arrows and lines: free end point, glued ends, routing and heads
  x2?: number;
  y2?: number;
  from?: string;
  to?: string;
  route?: Route;
  startHead?: Head;
  endHead?: Head;
  // freehand
  points?: [number, number][];
  // organization
  group?: string;
  locked?: boolean;
  link?: string;
  /** image: data URL */
  src?: string;
}

export interface BoardView {
  x: number;
  y: number;
  zoom: number;
}

export interface Board {
  version: 2;
  elements: BoardElement[];
  view: BoardView;
  bg?: string;
  grid?: boolean;
}

export interface Point {
  x: number;
  y: number;
}

export interface Rect {
  x: number;
  y: number;
  w: number;
  h: number;
}

export const DEFAULT_SIZE: Record<'note' | 'box' | 'diamond' | 'ellipse' | 'triangle' | 'text' | 'frame', { w: number; h: number }> = {
  note: { w: 180, h: 140 },
  box: { w: 190, h: 100 },
  diamond: { w: 170, h: 120 },
  ellipse: { w: 190, h: 110 },
  triangle: { w: 150, h: 120 },
  text: { w: 220, h: 36 },
  frame: { w: 420, h: 300 },
};

export function newId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

// ---- resolved style ------------------------------------------------------------------------------------------

export interface ResolvedStyle {
  stroke: string;
  fill: string;
  fillStyle: FillStyle;
  strokeWidth: number;
  strokeStyle: StrokeStyle;
  opacity: number;
  rough: number;
  round: boolean;
  fontSize: number;
  font: FontFamily;
  align: Align;
  route: Route;
  startHead: Head;
  endHead: Head;
}

const FILLED_TYPES: ElementType[] = ['note', 'box', 'diamond', 'ellipse', 'triangle'];

/** Fills in everything an element leaves unsaid, so older boards keep their look. */
export function resolveStyle(el: BoardElement): ResolvedStyle {
  const preset = BOARD_PALETTE[el.color] ?? BOARD_PALETTE.yellow;
  const isLine = el.type === 'arrow' || el.type === 'line';
  const defaultStroke = isLine || el.type === 'text' ? INK : el.type === 'pen' ? preset.stroke : el.type === 'frame' ? '#94a3b8' : preset.stroke;
  const defaultFill = FILLED_TYPES.includes(el.type) ? preset.fill : 'none';
  return {
    stroke: el.stroke ?? defaultStroke,
    fill: el.fill ?? defaultFill,
    fillStyle: el.fillStyle ?? 'solid',
    strokeWidth: el.strokeWidth ?? (el.type === 'pen' ? 3 : 2),
    strokeStyle: el.strokeStyle ?? 'solid',
    opacity: el.opacity ?? 100,
    rough: el.rough ?? 0,
    round: el.round ?? (el.type === 'note' ? false : el.type === 'box'),
    fontSize: el.fontSize ?? (el.type === 'text' ? 20 : 16),
    font: el.font ?? 'sans',
    align: el.align ?? (el.type === 'note' || el.type === 'text' ? 'left' : 'center'),
    route: el.route ?? 'straight',
    startHead: el.startHead ?? 'none',
    endHead: el.endHead ?? (el.type === 'arrow' ? 'arrow' : 'none'),
  };
}

// ---- parse / serialize ---------------------------------------------------------------------------------------

export function emptyBoard(): Board {
  return { version: 2, elements: [], view: { x: 0, y: 0, zoom: 1 } };
}

export function parseBoard(json: string | null | undefined): Board {
  try {
    const raw = JSON.parse(json || '{}') as Partial<Board>;
    const elements = Array.isArray(raw.elements) ? raw.elements : [];
    const view = raw.view;
    return {
      version: 2,
      elements: elements
        .filter((e) => e && typeof e.id === 'string' && typeof e.type === 'string')
        .map((e) => ({
          ...e,
          x: Number(e.x) || 0,
          y: Number(e.y) || 0,
          w: Number(e.w) || 0,
          h: Number(e.h) || 0,
          text: typeof e.text === 'string' ? e.text : '',
          color: BOARD_COLORS.includes(e.color) ? e.color : 'yellow',
        })),
      view: view && Number.isFinite(view.zoom) ? { x: view.x ?? 0, y: view.y ?? 0, zoom: view.zoom } : { x: 0, y: 0, zoom: 1 },
      bg: typeof raw.bg === 'string' ? raw.bg : undefined,
      grid: raw.grid === true,
    };
  } catch {
    return emptyBoard();
  }
}

export function serializeBoard(board: Board): string {
  return JSON.stringify(board);
}

// ---- text ----------------------------------------------------------------------------------------------------

/** Splits text into lines that fit `maxChars`, keeping the user's own line breaks. */
export function wrapLines(text: string, maxChars: number): string[] {
  const lines: string[] = [];
  for (const paragraph of (text || '').split('\n')) {
    let line = '';
    for (const word of paragraph.split(/\s+/).filter(Boolean)) {
      let w = word;
      while (w.length > maxChars) {
        if (line) {
          lines.push(line);
          line = '';
        }
        lines.push(w.slice(0, maxChars));
        w = w.slice(maxChars);
      }
      if (!line) line = w;
      else if ((line + ' ' + w).length <= maxChars) line += ' ' + w;
      else {
        lines.push(line);
        line = w;
      }
    }
    lines.push(line);
  }
  return lines;
}

export function lineHeight(fontSize: number): number {
  return Math.round(fontSize * 1.3);
}

export function charsPerLine(widthPx: number, fontSize: number): number {
  return Math.max(3, Math.floor(widthPx / (fontSize * 0.56)));
}

export function textLines(el: BoardElement, st: ResolvedStyle): string[] {
  const inset = el.type === 'text' || el.type === 'note' ? 16 : 24;
  return wrapLines(el.text, charsPerLine(Math.max(20, el.w - inset), st.fontSize));
}

/** Height a text element needs for its lines. */
export function fitTextHeight(el: BoardElement): number {
  const st = resolveStyle(el);
  return Math.max(lineHeight(st.fontSize) + 8, textLines(el, st).length * lineHeight(st.fontSize) + 8);
}

// ---- geometry ------------------------------------------------------------------------------------------------

export function center(el: BoardElement): Point {
  return { x: el.x + el.w / 2, y: el.y + el.h / 2 };
}

/** Where a line from `from` towards the center of `el` meets the outline of `el` (plus a small gap). */
export function borderPoint(el: BoardElement, from: Point, gap = 4): Point {
  const c = center(el);
  const dx = from.x - c.x;
  const dy = from.y - c.y;
  if (dx === 0 && dy === 0) return c;
  const hw = el.w / 2 + gap;
  const hh = el.h / 2 + gap;
  let t: number;
  if (el.type === 'ellipse') {
    t = 1 / Math.sqrt((dx * dx) / (hw * hw) + (dy * dy) / (hh * hh));
  } else if (el.type === 'diamond') {
    t = 1 / (Math.abs(dx) / hw + Math.abs(dy) / hh);
  } else {
    t = Math.min(hw / Math.abs(dx || 1e-9), hh / Math.abs(dy || 1e-9));
  }
  return { x: c.x + dx * t, y: c.y + dy * t };
}

export type ElementLookup = (id: string | undefined) => BoardElement | undefined;

export interface ArrowGeometry {
  /** points of the polyline (straight and elbow) or the 3 control points of the quadratic curve */
  pts: Point[];
  route: Route;
  a: Point;
  b: Point;
  /** direction of the path at its start and end, for arrowheads */
  startAngle: number;
  endAngle: number;
  mid: Point;
}

/** Final end points of an arrow/line, with glued ends snapped to the outline of what they connect. */
export function arrowEnds(el: BoardElement, lookup: ElementLookup): { a: Point; b: Point } {
  const fromEl = lookup(el.from);
  const toEl = lookup(el.to);
  const aRaw: Point = fromEl ? center(fromEl) : { x: el.x, y: el.y };
  const bRaw: Point = toEl ? center(toEl) : { x: el.x2 ?? el.x, y: el.y2 ?? el.y };
  return {
    a: fromEl ? borderPoint(fromEl, bRaw) : aRaw,
    b: toEl ? borderPoint(toEl, aRaw) : bRaw,
  };
}

export function arrowGeometry(el: BoardElement, lookup: ElementLookup): ArrowGeometry {
  const { a, b } = arrowEnds(el, lookup);
  const route = resolveStyle(el).route;
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len = Math.hypot(dx, dy) || 1;
  if (route === 'curve') {
    const c: Point = { x: (a.x + b.x) / 2 - (dy / len) * len * 0.22, y: (a.y + b.y) / 2 + (dx / len) * len * 0.22 };
    return {
      pts: [a, c, b],
      route,
      a,
      b,
      startAngle: Math.atan2(c.y - a.y, c.x - a.x),
      endAngle: Math.atan2(b.y - c.y, b.x - c.x),
      mid: { x: (a.x + 2 * c.x + b.x) / 4, y: (a.y + 2 * c.y + b.y) / 4 },
    };
  }
  if (route === 'elbow') {
    const horizontal = Math.abs(dx) >= Math.abs(dy);
    const pts: Point[] = horizontal
      ? [a, { x: (a.x + b.x) / 2, y: a.y }, { x: (a.x + b.x) / 2, y: b.y }, b]
      : [a, { x: a.x, y: (a.y + b.y) / 2 }, { x: b.x, y: (a.y + b.y) / 2 }, b];
    return {
      pts,
      route,
      a,
      b,
      startAngle: horizontal ? (dx >= 0 ? 0 : Math.PI) : dy >= 0 ? Math.PI / 2 : -Math.PI / 2,
      endAngle: horizontal ? (dx >= 0 ? 0 : Math.PI) : dy >= 0 ? Math.PI / 2 : -Math.PI / 2,
      mid: { x: (pts[1].x + pts[2].x) / 2, y: (pts[1].y + pts[2].y) / 2 },
    };
  }
  const angle = Math.atan2(dy, dx);
  return { pts: [a, b], route, a, b, startAngle: angle, endAngle: angle, mid: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 } };
}

/** Bounding box of any element (arrows and strokes included). */
export function elementBounds(el: BoardElement, lookup: ElementLookup): Rect {
  if (el.type === 'arrow' || el.type === 'line') {
    const g = arrowGeometry(el, lookup);
    return boundsOfPoints(g.pts);
  }
  if (el.type === 'pen') {
    return boundsOfPoints((el.points ?? []).map(([x, y]) => ({ x, y })));
  }
  return { x: Math.min(el.x, el.x + el.w), y: Math.min(el.y, el.y + el.h), w: Math.abs(el.w), h: Math.abs(el.h) };
}

function boundsOfPoints(points: Point[]): Rect {
  if (points.length === 0) return { x: 0, y: 0, w: 0, h: 0 };
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const p of points) {
    minX = Math.min(minX, p.x);
    minY = Math.min(minY, p.y);
    maxX = Math.max(maxX, p.x);
    maxY = Math.max(maxY, p.y);
  }
  return { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
}

export function unionRect(rects: Rect[]): Rect | null {
  if (rects.length === 0) return null;
  const minX = Math.min(...rects.map((r) => r.x));
  const minY = Math.min(...rects.map((r) => r.y));
  const maxX = Math.max(...rects.map((r) => r.x + r.w));
  const maxY = Math.max(...rects.map((r) => r.y + r.h));
  return { x: minX, y: minY, w: maxX - minX, h: maxY - minY };
}

export function rectsIntersect(a: Rect, b: Rect): boolean {
  return a.x <= b.x + b.w && a.x + a.w >= b.x && a.y <= b.y + b.h && a.y + a.h >= b.y;
}

export function rectContains(outer: Rect, inner: Rect): boolean {
  return inner.x >= outer.x && inner.y >= outer.y && inner.x + inner.w <= outer.x + outer.w && inner.y + inner.h <= outer.y + outer.h;
}

function distanceToSegment(p: Point, a: Point, b: Point): number {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len2 = dx * dx + dy * dy;
  const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2));
  return Math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy));
}

function polylineOf(el: BoardElement, lookup: ElementLookup): Point[] {
  if (el.type === 'pen') return (el.points ?? []).map(([x, y]) => ({ x, y }));
  const g = arrowGeometry(el, lookup);
  if (g.route === 'curve') {
    const [a, c, b] = g.pts;
    const out: Point[] = [];
    for (let i = 0; i <= 16; i++) {
      const t = i / 16;
      out.push({ x: (1 - t) * (1 - t) * a.x + 2 * (1 - t) * t * c.x + t * t * b.x, y: (1 - t) * (1 - t) * a.y + 2 * (1 - t) * t * c.y + t * t * b.y });
    }
    return out;
  }
  return g.pts;
}

function insideShape(el: BoardElement, p: Point): boolean {
  const c = center(el);
  const hw = Math.abs(el.w) / 2;
  const hh = Math.abs(el.h) / 2;
  const dx = p.x - c.x;
  const dy = p.y - c.y;
  switch (el.type) {
    case 'ellipse':
      return hw > 0 && hh > 0 && (dx * dx) / (hw * hw) + (dy * dy) / (hh * hh) <= 1;
    case 'diamond':
      return hw > 0 && hh > 0 && Math.abs(dx) / hw + Math.abs(dy) / hh <= 1;
    case 'triangle': {
      if (p.y < el.y || p.y > el.y + el.h) return false;
      const k = (p.y - el.y) / (el.h || 1);
      return Math.abs(dx) <= hw * k;
    }
    default:
      return Math.abs(dx) <= hw && Math.abs(dy) <= hh;
  }
}

/** Is the point anywhere inside a shape, note, text or image, hollow or not? (Used to edit text in a hollow shape.) */
export function containsPoint(el: BoardElement, p: Point): boolean {
  if (isShape(el)) return insideShape(el, p);
  if (el.type === 'note' || el.type === 'text' || el.type === 'image') {
    return p.x >= el.x && p.x <= el.x + el.w && p.y >= el.y && p.y <= el.y + el.h;
  }
  return false;
}

/** Is the point on (or, for filled things, inside) the element? `tol` is in board units. */
export function hitElement(el: BoardElement, p: Point, tol: number, lookup: ElementLookup): boolean {
  switch (el.type) {
    case 'arrow':
    case 'line':
    case 'pen': {
      const line = polylineOf(el, lookup);
      for (let i = 1; i < line.length; i++) {
        if (distanceToSegment(p, line[i - 1], line[i]) <= tol + (resolveStyle(el).strokeWidth / 2)) return true;
      }
      return line.length === 1 && Math.hypot(p.x - line[0].x, p.y - line[0].y) <= tol;
    }
    case 'frame': {
      // Only the border and the title strip, so what is inside a frame stays reachable.
      const outer = { x: el.x - tol, y: el.y - tol, w: el.w + tol * 2, h: el.h + tol * 2 };
      const inner = { x: el.x + tol, y: el.y + tol, w: el.w - tol * 2, h: el.h - tol * 2 };
      const inOuter = p.x >= outer.x && p.x <= outer.x + outer.w && p.y >= outer.y && p.y <= outer.y + outer.h;
      const inInner = p.x >= inner.x && p.x <= inner.x + inner.w && p.y >= inner.y && p.y <= inner.y + inner.h;
      const inTitle = p.x >= el.x && p.x <= el.x + Math.min(el.w, 260) && p.y >= el.y - 26 && p.y <= el.y;
      return (inOuter && !inInner) || inTitle;
    }
    case 'text':
    case 'note':
    case 'image':
      return p.x >= el.x - tol && p.x <= el.x + el.w + tol && p.y >= el.y - tol && p.y <= el.y + el.h + tol;
    default: {
      const st = resolveStyle(el);
      if (st.fill !== 'none' || el.text) {
        const grown = { ...el, x: el.x - tol, y: el.y - tol, w: el.w + tol * 2, h: el.h + tol * 2 };
        return insideShape(grown, p);
      }
      // hollow shape: only near the outline
      const outer = { ...el, x: el.x - tol, y: el.y - tol, w: el.w + tol * 2, h: el.h + tol * 2 };
      const inner = { ...el, x: el.x + tol, y: el.y + tol, w: Math.max(0, el.w - tol * 2), h: Math.max(0, el.h - tol * 2) };
      return insideShape(outer, p) && !insideShape(inner, p);
    }
  }
}

// ---- points and scaling --------------------------------------------------------------------------------------

/** Moves an element by (dx, dy); glued arrow ends are left to follow what they are glued to. */
export function translate(el: BoardElement, dx: number, dy: number): BoardElement {
  const moved: BoardElement = { ...el, x: el.x + dx, y: el.y + dy };
  if (el.type === 'arrow' || el.type === 'line') {
    moved.x2 = (el.x2 ?? el.x) + dx;
    moved.y2 = (el.y2 ?? el.y) + dy;
  }
  if (el.type === 'pen') {
    moved.points = (el.points ?? []).map(([x, y]) => [x + dx, y + dy] as [number, number]);
  }
  return moved;
}

/** Maps an element from the `from` box into the `to` box (used to resize a selection). */
export function scaleInto(el: BoardElement, from: Rect, to: Rect): BoardElement {
  const sx = from.w === 0 ? 1 : to.w / from.w;
  const sy = from.h === 0 ? 1 : to.h / from.h;
  const mapX = (x: number) => to.x + (x - from.x) * sx;
  const mapY = (y: number) => to.y + (y - from.y) * sy;
  if (el.type === 'arrow' || el.type === 'line') {
    return { ...el, x: mapX(el.x), y: mapY(el.y), x2: mapX(el.x2 ?? el.x), y2: mapY(el.y2 ?? el.y) };
  }
  if (el.type === 'pen') {
    return { ...el, points: (el.points ?? []).map(([x, y]) => [mapX(x), mapY(y)] as [number, number]) };
  }
  return { ...el, x: mapX(el.x), y: mapY(el.y), w: el.w * sx, h: el.h * sy };
}

export function snap(value: number, grid: number): number {
  return Math.round(value / grid) * grid;
}

/** Thins a freehand stroke: drops points closer than `minDist` and keeps the last one. */
export function simplifyStroke(points: [number, number][], minDist = 2): [number, number][] {
  if (points.length < 3) return points;
  const out: [number, number][] = [points[0]];
  for (let i = 1; i < points.length - 1; i++) {
    const last = out[out.length - 1];
    if (Math.hypot(points[i][0] - last[0], points[i][1] - last[1]) >= minDist) out.push(points[i]);
  }
  out.push(points[points.length - 1]);
  return out;
}

export function isLinear(el: BoardElement): boolean {
  return el.type === 'arrow' || el.type === 'line';
}

export function isShape(el: BoardElement): boolean {
  return el.type === 'box' || el.type === 'diamond' || el.type === 'ellipse' || el.type === 'triangle';
}

/** Elements that can carry typed text. */
export function canHoldText(el: BoardElement): boolean {
  return isShape(el) || el.type === 'note' || el.type === 'text' || el.type === 'arrow' || el.type === 'line' || el.type === 'frame';
}
