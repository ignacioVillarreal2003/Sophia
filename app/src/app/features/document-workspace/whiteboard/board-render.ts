import {
  Board,
  BoardElement,
  ElementLookup,
  FONT_STACKS,
  Point,
  Rect,
  ResolvedStyle,
  arrowGeometry,
  elementBounds,
  lineHeight,
  resolveStyle,
  textLines,
  unionRect,
} from './board.model';

/**
 * Pure SVG generation for board elements. The live canvas and the exporters share it, so what you see is what you
 * export. Every value that reaches the markup is escaped or validated: boards are shared, so their content is not
 * trusted.
 */

export function esc(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

const COLOR_RE = /^(#[0-9a-fA-F]{3,8}|none|transparent)$/;

function safeColor(value: string, fallback: string): string {
  return COLOR_RE.test(value) ? value : fallback;
}

function n(value: number): string {
  return Number.isFinite(value) ? String(Math.round(value * 100) / 100) : '0';
}

/** Small deterministic generator so a sketchy shape looks the same every time it is drawn. */
function rng(seedText: string): () => number {
  let h = 1779033703 ^ seedText.length;
  for (let i = 0; i < seedText.length; i++) {
    h = Math.imul(h ^ seedText.charCodeAt(i), 3432918353);
    h = (h << 13) | (h >>> 19);
  }
  let a = h >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function dash(st: ResolvedStyle): string {
  if (st.strokeStyle === 'dashed') return ` stroke-dasharray="${n(st.strokeWidth * 4)} ${n(st.strokeWidth * 3)}"`;
  if (st.strokeStyle === 'dotted') return ` stroke-dasharray="1 ${n(st.strokeWidth * 2.2)}" stroke-linecap="round"`;
  return '';
}

// ---- outlines ------------------------------------------------------------------------------------------------

function outline(el: BoardElement): Point[] {
  const { x, y, w, h } = el;
  switch (el.type) {
    case 'diamond':
      return [{ x: x + w / 2, y }, { x: x + w, y: y + h / 2 }, { x: x + w / 2, y: y + h }, { x, y: y + h / 2 }];
    case 'triangle':
      return [{ x: x + w / 2, y }, { x: x + w, y: y + h }, { x, y: y + h }];
    case 'ellipse': {
      const pts: Point[] = [];
      for (let i = 0; i < 36; i++) {
        const t = (i / 36) * Math.PI * 2;
        pts.push({ x: x + w / 2 + (w / 2) * Math.cos(t), y: y + h / 2 + (h / 2) * Math.sin(t) });
      }
      return pts;
    }
    default:
      return [{ x, y }, { x: x + w, y }, { x: x + w, y: y + h }, { x, y: y + h }];
  }
}

function cleanPath(el: BoardElement, round: boolean): string {
  const { x, y, w, h } = el;
  if (el.type === 'ellipse') {
    return `M ${n(x)} ${n(y + h / 2)} a ${n(w / 2)} ${n(h / 2)} 0 1 0 ${n(w)} 0 a ${n(w / 2)} ${n(h / 2)} 0 1 0 ${n(-w)} 0 z`;
  }
  if ((el.type === 'box' || el.type === 'note' || el.type === 'frame') && round) {
    const r = Math.min(14, Math.abs(w) / 4, Math.abs(h) / 4);
    return `M ${n(x + r)} ${n(y)} H ${n(x + w - r)} Q ${n(x + w)} ${n(y)} ${n(x + w)} ${n(y + r)} V ${n(y + h - r)} Q ${n(x + w)} ${n(y + h)} ${n(x + w - r)} ${n(y + h)} H ${n(x + r)} Q ${n(x)} ${n(y + h)} ${n(x)} ${n(y + h - r)} V ${n(y + r)} Q ${n(x)} ${n(y)} ${n(x + r)} ${n(y)} z`;
  }
  return outline(el).map((p, i) => `${i === 0 ? 'M' : 'L'} ${n(p.x)} ${n(p.y)}`).join(' ') + ' z';
}

/** A hand-drawn version of a closed outline: wobbly corners, two slightly different passes. */
function sketchyPath(points: Point[], amp: number, rand: () => number, closed: boolean): string {
  const pass = () => {
    const pts = points.map((p) => ({ x: p.x + (rand() - 0.5) * amp * 2, y: p.y + (rand() - 0.5) * amp * 2 }));
    let d = `M ${n(pts[0].x)} ${n(pts[0].y)}`;
    const count = closed ? pts.length : pts.length - 1;
    for (let i = 0; i < count; i++) {
      const a = pts[i];
      const b = pts[(i + 1) % pts.length];
      const c = { x: (a.x + b.x) / 2 + (rand() - 0.5) * amp * 1.6, y: (a.y + b.y) / 2 + (rand() - 0.5) * amp * 1.6 };
      d += ` Q ${n(c.x)} ${n(c.y)} ${n(b.x)} ${n(b.y)}`;
    }
    return d;
  };
  return `${pass()} ${pass()}`;
}

function subdivide(points: Point[], closed: boolean, step = 40): Point[] {
  const out: Point[] = [];
  const count = closed ? points.length : points.length - 1;
  for (let i = 0; i < count; i++) {
    const a = points[i];
    const b = points[(i + 1) % points.length];
    const parts = Math.max(1, Math.round(Math.hypot(b.x - a.x, b.y - a.y) / step));
    for (let k = 0; k < parts; k++) out.push({ x: a.x + ((b.x - a.x) * k) / parts, y: a.y + ((b.y - a.y) * k) / parts });
  }
  if (!closed) out.push(points[points.length - 1]);
  return out;
}

// ---- elements ------------------------------------------------------------------------------------------------

function safeId(id: string): string {
  return id.replace(/[^a-zA-Z0-9_-]/g, '');
}

function fillMarkup(el: BoardElement, st: ResolvedStyle, d: string): string {
  const fill = safeColor(st.fill, 'none');
  if (fill === 'none' || fill === 'transparent') return '';
  if (st.fillStyle === 'solid') return `<path d="${d}" fill="${fill}" stroke="none"/>`;
  const id = `hp-${safeId(el.id)}`;
  const stroke = safeColor(st.stroke, '#1e293b');
  const lines =
    `<line x1="0" y1="0" x2="0" y2="9" stroke="${stroke}" stroke-width="1.4"/>` +
    (st.fillStyle === 'cross' ? `<line x1="0" y1="0" x2="9" y2="0" stroke="${stroke}" stroke-width="1.4"/>` : '');
  return (
    `<defs><pattern id="${id}" patternUnits="userSpaceOnUse" width="9" height="9" patternTransform="rotate(45)">${lines}</pattern></defs>` +
    `<path d="${d}" fill="${fill}" fill-opacity="0.35" stroke="none"/><path d="${d}" fill="url(#${id})" stroke="none"/>`
  );
}

function strokeMarkup(el: BoardElement, st: ResolvedStyle, d: string, rand: () => number): string {
  const stroke = safeColor(st.stroke, '#1e293b');
  const common = `fill="none" stroke="${stroke}" stroke-width="${n(st.strokeWidth)}" stroke-linejoin="round" stroke-linecap="round"${dash(st)}`;
  if (st.rough === 0 || el.type === 'note') {
    return `<path d="${d}" ${common}/>`;
  }
  const amp = st.rough === 1 ? 1.4 : 3;
  const base = el.type === 'ellipse' ? outline(el) : subdivide(outline(el), true);
  return `<path d="${sketchyPath(base, amp, rand, true)}" ${common}/>`;
}

function textMarkup(el: BoardElement, st: ResolvedStyle): string {
  if (!el.text) return '';
  const lines = textLines(el, st);
  const lh = lineHeight(st.fontSize);
  const left = el.type === 'text' || el.type === 'note';
  const anchor = st.align === 'left' ? 'start' : st.align === 'right' ? 'end' : 'middle';
  const pad = left ? 8 : 12;
  const tx = st.align === 'left' ? el.x + pad : st.align === 'right' ? el.x + el.w - pad : el.x + el.w / 2;
  let ty: number;
  if (left) {
    ty = el.y + pad + st.fontSize * 0.85;
  } else {
    const shown = Math.min(lines.length, Math.max(1, Math.floor(el.h / lh)));
    ty = el.y + el.h / 2 - ((shown - 1) * lh) / 2 + st.fontSize * 0.35;
  }
  const color = el.type === 'text' ? safeColor(st.stroke, '#1e293b') : '#1e293b';
  const tspans = lines.map((line, i) => `<tspan x="${n(tx)}" dy="${i === 0 ? 0 : lh}">${esc(line) || '&#8203;'}</tspan>`).join('');
  return `<text x="${n(tx)}" y="${n(ty)}" text-anchor="${anchor}" font-size="${st.fontSize}" font-family="${esc(FONT_STACKS[st.font])}" fill="${color}">${tspans}</text>`;
}

function headMarkup(kind: string, at: Point, angle: number, st: ResolvedStyle): string {
  if (kind === 'none') return '';
  const stroke = safeColor(st.stroke, '#1e293b');
  const size = 10 + st.strokeWidth * 2.2;
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  const pt = (back: number, side: number) => `${n(at.x - cos * back - sin * side)} ${n(at.y - sin * back + cos * side)}`;
  const base = `stroke="${stroke}" stroke-width="${n(st.strokeWidth)}" stroke-linejoin="round" stroke-linecap="round"`;
  switch (kind) {
    case 'triangle':
      return `<path d="M ${pt(0, 0)} L ${pt(size, size / 2.2)} L ${pt(size, -size / 2.2)} z" fill="${stroke}" ${base}/>`;
    case 'dot':
      return `<circle cx="${n(at.x - cos * size * 0.35)}" cy="${n(at.y - sin * size * 0.35)}" r="${n(size * 0.38)}" fill="${stroke}" stroke="none"/>`;
    case 'bar':
      return `<path d="M ${pt(0, size / 1.6)} L ${pt(0, -size / 1.6)}" fill="none" ${base}/>`;
    default:
      return `<path d="M ${pt(size, size / 2.4)} L ${pt(0, 0)} L ${pt(size, -size / 2.4)}" fill="none" ${base}/>`;
  }
}

function linearMarkup(el: BoardElement, st: ResolvedStyle, lookup: ElementLookup, rand: () => number): string {
  const g = arrowGeometry(el, lookup);
  let d: string;
  if (g.route === 'curve') {
    d = `M ${n(g.pts[0].x)} ${n(g.pts[0].y)} Q ${n(g.pts[1].x)} ${n(g.pts[1].y)} ${n(g.pts[2].x)} ${n(g.pts[2].y)}`;
  } else if (st.rough > 0) {
    d = sketchyPath(subdivide(g.pts, false), st.rough === 1 ? 1.2 : 2.6, rand, false);
  } else {
    d = g.pts.map((p, i) => `${i === 0 ? 'M' : 'L'} ${n(p.x)} ${n(p.y)}`).join(' ');
  }
  const stroke = safeColor(st.stroke, '#1e293b');
  let out = `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${n(st.strokeWidth)}" stroke-linejoin="round" stroke-linecap="round"${dash(st)}/>`;
  out += headMarkup(st.endHead, g.b, g.endAngle, st);
  out += headMarkup(st.startHead, g.a, g.startAngle + Math.PI, st);
  if (el.text) {
    const label = esc(el.text.split('\n')[0]);
    out += `<text x="${n(g.mid.x)}" y="${n(g.mid.y - 8)}" text-anchor="middle" font-size="${st.fontSize - 3}" font-family="${esc(FONT_STACKS[st.font])}" fill="#1e293b" paint-order="stroke" stroke="#ffffff" stroke-width="5" stroke-linejoin="round">${label}</text>`;
  }
  return out;
}

function penMarkup(el: BoardElement, st: ResolvedStyle): string {
  const pts = el.points ?? [];
  if (pts.length === 0) return '';
  const stroke = safeColor(st.stroke, '#475569');
  let d = `M ${n(pts[0][0])} ${n(pts[0][1])}`;
  for (let i = 1; i < pts.length - 1; i++) {
    const mx = (pts[i][0] + pts[i + 1][0]) / 2;
    const my = (pts[i][1] + pts[i + 1][1]) / 2;
    d += ` Q ${n(pts[i][0])} ${n(pts[i][1])} ${n(mx)} ${n(my)}`;
  }
  if (pts.length > 1) d += ` L ${n(pts[pts.length - 1][0])} ${n(pts[pts.length - 1][1])}`;
  else d += ` l 0.01 0`;
  return `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="${n(st.strokeWidth)}" stroke-linecap="round" stroke-linejoin="round"${dash(st)}/>`;
}

const IMAGE_SRC = /^data:image\/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$/;

export function isSafeImage(src: string | undefined): src is string {
  return !!src && IMAGE_SRC.test(src);
}

/** SVG markup (no wrapping <svg>) of one element. */
export function renderElement(el: BoardElement, lookup: ElementLookup): string {
  const st = resolveStyle(el);
  const rand = rng(el.id);
  let body = '';
  switch (el.type) {
    case 'arrow':
    case 'line':
      body = linearMarkup(el, st, lookup, rand);
      break;
    case 'pen':
      body = penMarkup(el, st);
      break;
    case 'text':
      body = textMarkup(el, st);
      break;
    case 'image':
      body = isSafeImage(el.src)
        ? `<image href="${el.src}" x="${n(el.x)}" y="${n(el.y)}" width="${n(el.w)}" height="${n(el.h)}" preserveAspectRatio="none"/>`
        : `<rect x="${n(el.x)}" y="${n(el.y)}" width="${n(el.w)}" height="${n(el.h)}" fill="#e2e8f0" stroke="#94a3b8"/>`;
      break;
    case 'frame': {
      const stroke = safeColor(st.stroke, '#94a3b8');
      const title = el.text ? `<text x="${n(el.x)}" y="${n(el.y - 8)}" font-size="14" font-weight="600" font-family="${esc(FONT_STACKS.sans)}" fill="#475569">${esc(el.text.split('\n')[0])}</text>` : '';
      const fill = safeColor(st.fill, 'none');
      body = `<path d="${cleanPath(el, true)}" fill="${fill === 'none' ? 'transparent' : fill}" fill-opacity="${fill === 'none' ? 0 : 0.35}" stroke="${stroke}" stroke-width="1.5" stroke-dasharray="6 5"/>${title}`;
      break;
    }
    default: {
      const d = cleanPath(el, st.round);
      const shadow = el.type === 'note' ? `<path d="${cleanPath({ ...el, x: el.x + 3, y: el.y + 4 }, false)}" fill="rgba(15,23,42,0.16)" stroke="none"/>` : '';
      body = shadow + fillMarkup(el, st, d) + strokeMarkup(el, st, d, rand) + textMarkup(el, st);
    }
  }
  const opacity = st.opacity < 100 ? ` opacity="${n(Math.max(0, st.opacity) / 100)}"` : '';
  return `<g${opacity}>${body}</g>`;
}

// ---- export --------------------------------------------------------------------------------------------------

export interface ExportOptions {
  /** only these elements (selection export) */
  ids?: string[];
  /** include the board background */
  background?: boolean;
  padding?: number;
}

export interface SvgDocument {
  markup: string;
  width: number;
  height: number;
}

/** A standalone SVG of the board (or of some of its elements). Null when there is nothing to draw. */
export function buildSvg(board: Board, options: ExportOptions = {}): SvgDocument | null {
  const byId = new Map(board.elements.map((e) => [e.id, e]));
  const lookup: ElementLookup = (id) => (id ? byId.get(id) : undefined);
  const chosen = options.ids ? board.elements.filter((e) => options.ids!.includes(e.id)) : board.elements;
  const box = unionRect(chosen.map((e) => elementBounds(e, lookup)));
  if (!box) return null;
  const pad = options.padding ?? 32;
  const width = Math.ceil(box.w + pad * 2);
  const height = Math.ceil(box.h + pad * 2);
  const bg = options.background === false || !board.bg || board.bg === 'transparent' ? (options.background === false ? '' : '#ffffff') : board.bg;
  const background = bg && COLOR_RE.test(bg) && bg !== 'transparent' ? `<rect width="100%" height="100%" fill="${bg}"/>` : '';
  const content = chosen.map((e) => renderElement(e, lookup)).join('');
  const markup =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">` +
    `${background}<g transform="translate(${n(pad - box.x)} ${n(pad - box.y)})">${content}</g></svg>`;
  return { markup, width, height };
}

export function boardBounds(board: Board): Rect | null {
  const byId = new Map(board.elements.map((e) => [e.id, e]));
  return unionRect(board.elements.map((e) => elementBounds(e, (id) => (id ? byId.get(id) : undefined))));
}
