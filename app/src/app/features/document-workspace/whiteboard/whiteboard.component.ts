import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { CdkContextMenuTrigger, CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { DialogService } from '@shared/ui/dialog/dialog.service';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { IconName } from '@shared/ui/icon/icons';
import {
  Align,
  BACKGROUNDS,
  Board,
  BoardElement,
  DEFAULT_SIZE,
  ElementLookup,
  FILL_SWATCHES,
  FONT_SIZES,
  FillStyle,
  FontFamily,
  Head,
  Point,
  Rect,
  Route,
  STROKE_SWATCHES,
  StrokeStyle,
  arrowEnds,
  arrowGeometry,
  center,
  elementBounds,
  emptyBoard,
  fitTextHeight,
  canHoldText,
  containsPoint,
  hitElement,
  isLinear,
  isShape,
  newId,
  parseBoard,
  rectContains,
  resolveStyle,
  scaleInto,
  serializeBoard,
  simplifyStroke,
  snap,
  translate,
  unionRect,
} from './board.model';
import { buildSvg, isSafeImage, renderElement } from './board-render';
import { TEMPLATE_IDS, TemplateId, buildTemplate } from './board-templates';

type Tool = 'select' | 'hand' | 'box' | 'diamond' | 'ellipse' | 'triangle' | 'arrow' | 'line' | 'pen' | 'text' | 'note' | 'frame' | 'image' | 'eraser';

type Drag =
  | { kind: 'pan'; sx: number; sy: number; vx: number; vy: number }
  | { kind: 'move'; start: Point; origs: Map<string, BoardElement>; box: Rect; moved: boolean }
  | { kind: 'resize'; handle: string; start: Point; box: Rect; origs: Map<string, BoardElement>; moved: boolean }
  | { kind: 'marquee'; start: Point; base: string[] }
  | { kind: 'create'; id: string; start: Point; tool: Tool }
  | { kind: 'linear'; id: string }
  | { kind: 'arrow-end'; id: string; end: 'a' | 'b' }
  | { kind: 'pen'; id: string }
  | { kind: 'erase' };

interface ToolDef {
  id: Tool;
  icon: IconName;
  key: string;
}

interface Rendered {
  id: string;
  html: SafeHtml;
}

interface EditingState {
  id: string;
  text: string;
  left: number;
  top: number;
  width: number;
  height: number;
  fontSize: number;
  font: FontFamily;
  align: Align;
}

const SAVE_DEBOUNCE_MS = 500;
const HISTORY_LIMIT = 80;
const MIN_ZOOM = 0.1;
const MAX_ZOOM = 4;
const GRID = 20;
const MAX_CONTENT_BYTES = 7_000_000;
const CLIPBOARD_PREFIX = 'sophia-board:';
const MIN_SIZE = 6;

const TOOLS: ToolDef[] = [
  { id: 'hand', icon: 'hand', key: 'H' },
  { id: 'select', icon: 'cursor', key: 'V' },
  { id: 'box', icon: 'square', key: 'R' },
  { id: 'diamond', icon: 'diamond', key: 'D' },
  { id: 'ellipse', icon: 'circle', key: 'O' },
  { id: 'triangle', icon: 'triangle', key: '' },
  { id: 'arrow', icon: 'arrow-right', key: 'A' },
  { id: 'line', icon: 'line-tool', key: 'L' },
  { id: 'pen', icon: 'pencil', key: 'P' },
  { id: 'text', icon: 'type', key: 'T' },
  { id: 'note', icon: 'file-text', key: 'N' },
  { id: 'image', icon: 'image', key: '9' },
  { id: 'frame', icon: 'frame', key: 'F' },
  { id: 'eraser', icon: 'eraser', key: 'E' },
];

const HOTKEYS: Record<string, Tool> = {
  v: 'select', '1': 'select', h: 'hand', r: 'box', '2': 'box', d: 'diamond', '3': 'diamond', o: 'ellipse', '4': 'ellipse',
  a: 'arrow', '5': 'arrow', l: 'line', '6': 'line', p: 'pen', '7': 'pen', t: 'text', '8': 'text', n: 'note', '9': 'image',
  f: 'frame', e: 'eraser', '0': 'eraser',
};

const DRAW_TOOLS: Tool[] = ['box', 'diamond', 'ellipse', 'triangle', 'arrow', 'line', 'pen', 'text', 'note', 'frame'];

interface DefaultStyle {
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

const INITIAL_STYLE: DefaultStyle = {
  stroke: STROKE_SWATCHES[0],
  fill: 'none',
  fillStyle: 'solid',
  strokeWidth: 2,
  strokeStyle: 'solid',
  opacity: 100,
  rough: 0,
  round: true,
  fontSize: 20,
  font: 'sans',
  align: 'left',
  route: 'straight',
  startHead: 'none',
  endHead: 'arrow',
};

const HEADS: Head[] = ['none', 'arrow', 'triangle', 'dot', 'bar'];

/**
 * Whiteboard in the spirit of Excalidraw and Miro, aimed at studying: concept maps, diagrams and notes on a free
 * canvas. Shapes, sticky notes, text, arrows glued to what they connect (straight, curved or elbow), freehand,
 * frames and images; hand-drawn look, fills and styles; multi-select, groups, alignment, layers and locking; copy
 * and paste; templates; pan and zoom with a minimap; undo/redo; export to PNG, SVG and JSON.
 */
@Component({
  selector: 'app-whiteboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CdkContextMenuTrigger, CdkMenu, CdkMenuItem, CdkMenuTrigger, IconComponent, TranslatePipe],
  templateUrl: './whiteboard.component.html',
  styleUrl: './whiteboard.component.scss',
})
export class WhiteboardComponent {
  private readonly destroyRef = inject(DestroyRef);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly i18n = inject(TranslationService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);

  readonly content = input.required<string>();
  readonly readonly = input(false);
  readonly contentChange = output<string>();
  readonly saveRequested = output<void>();

  private readonly svg = viewChild.required<ElementRef<SVGSVGElement>>('svg');
  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly stageEl = viewChild.required<ElementRef<HTMLElement>>('stage');
  private readonly editor = viewChild<ElementRef<HTMLTextAreaElement>>('editor');
  private readonly imageInput = viewChild<ElementRef<HTMLInputElement>>('imageInput');
  private readonly jsonInput = viewChild<ElementRef<HTMLInputElement>>('jsonInput');

  readonly GRID_SIZE = GRID;
  readonly tools = TOOLS;
  readonly strokeSwatches = STROKE_SWATCHES;
  readonly fillSwatches = FILL_SWATCHES;
  readonly backgrounds = BACKGROUNDS;
  readonly fontSizes = FONT_SIZES;
  readonly heads = HEADS;
  readonly templateIds = TEMPLATE_IDS;
  readonly widths = [1, 2, 4];
  readonly strokeStyles: StrokeStyle[] = ['solid', 'dashed', 'dotted'];
  readonly fillStyles: FillStyle[] = ['solid', 'hachure', 'cross'];
  readonly routes: Route[] = ['straight', 'curve', 'elbow'];
  readonly fonts: FontFamily[] = ['sans', 'hand', 'mono'];
  readonly aligns: Align[] = ['left', 'center', 'right'];
  readonly roughLevels = [0, 1, 2];

  // ---- state ------------------------------------------------------------------------------------------------
  readonly board = signal<Board>(emptyBoard());
  readonly elements = computed(() => this.board().elements);
  readonly view = computed(() => this.board().view);
  readonly zoom = computed(() => this.board().view.zoom);
  readonly zoomPercent = computed(() => Math.round(this.zoom() * 100));
  readonly transform = computed(() => `translate(${this.view().x} ${this.view().y}) scale(${this.zoom()})`);
  readonly background = computed(() => this.board().bg ?? '#ffffff');
  readonly showGrid = computed(() => this.board().grid === true);

  readonly tool = signal<Tool>('select');
  readonly toolLock = signal(false);
  readonly selection = signal<string[]>([]);
  readonly editing = signal<EditingState | null>(null);
  readonly marquee = signal<Rect | null>(null);
  readonly bindTarget = signal<string | null>(null);
  readonly style = signal<DefaultStyle>({ ...INITIAL_STYLE });
  readonly snapOn = signal(false);
  readonly minimap = signal(true);
  readonly panelOpen = signal(false);
  readonly helpOpen = signal(false);
  readonly spaceDown = signal(false);
  readonly stage = signal({ w: 800, h: 500 });
  readonly tooBig = signal(false);
  readonly canUndo = signal(false);
  readonly canRedo = signal(false);

  private readonly byId = computed(() => new Map(this.elements().map((e) => [e.id, e])));
  private readonly lookup: ElementLookup = (id) => (id ? this.byId().get(id) : undefined);

  readonly selected = computed(() => {
    const ids = new Set(this.selection());
    return this.elements().filter((e) => ids.has(e.id));
  });
  readonly single = computed(() => (this.selected().length === 1 ? this.selected()[0] : null));
  readonly selectionBox = computed<Rect | null>(() => unionRect(this.selected().map((e) => elementBounds(e, this.lookup))));
  readonly anyLocked = computed(() => this.selected().some((e) => e.locked));
  readonly canGroup = computed(() => this.selected().length > 1);
  readonly canUngroup = computed(() => this.selected().some((e) => e.group));
  readonly handleSize = computed(() => 9 / this.zoom());

  // Markup per element, cached by the identity of the element and of the elements it is glued to.
  private readonly renderCache = new Map<string, { el: BoardElement; from?: BoardElement; to?: BoardElement; html: SafeHtml }>();
  readonly rendered = computed<Rendered[]>(() => {
    const byId = this.byId();
    const seen = new Set<string>();
    const out: Rendered[] = [];
    for (const el of this.elements()) {
      seen.add(el.id);
      const from = el.from ? byId.get(el.from) : undefined;
      const to = el.to ? byId.get(el.to) : undefined;
      let entry = this.renderCache.get(el.id);
      if (!entry || entry.el !== el || entry.from !== from || entry.to !== to) {
        entry = { el, from, to, html: this.sanitizer.bypassSecurityTrustHtml(renderElement(el, this.lookup)) };
        this.renderCache.set(el.id, entry);
      }
      out.push({ id: el.id, html: entry.html });
    }
    for (const id of this.renderCache.keys()) if (!seen.has(id)) this.renderCache.delete(id);
    return out;
  });

  /** Outlines of everything selected when more than one thing is, to show what is in the selection. */
  readonly outlines = computed(() => (this.selected().length > 1 ? this.selected().filter((e) => !isLinear(e)).map((e) => elementBounds(e, this.lookup)) : []));

  /** Arrow ends of the selected arrow/line, for its two drag handles. */
  readonly linearEnds = computed(() => {
    const s = this.single();
    return s && isLinear(s) ? arrowEnds(s, this.lookup) : null;
  });

  readonly connectors = computed(() => {
    const s = this.single();
    if (!s || this.readonly() || this.tool() !== 'select' || s.locked || !(isShape(s) || s.type === 'note')) return [];
    const g = 18 / this.zoom();
    const c = center(s);
    return [
      { dir: 'n', x: c.x, y: s.y - g },
      { dir: 'e', x: s.x + s.w + g, y: c.y },
      { dir: 's', x: c.x, y: s.y + s.h + g },
      { dir: 'w', x: s.x - g, y: c.y },
    ];
  });

  readonly linkBadge = computed(() => {
    const s = this.single();
    return s?.link ? { link: s.link, x: s.x + s.w, y: s.y - 6 / this.zoom() } : null;
  });

  /** The eight resize handles around the selection (not for a lone arrow, which has its two end handles). */
  readonly handles = computed(() => {
    const box = this.selectionBox();
    if (!box || this.readonly() || this.anyLocked() || this.linearEnds()) return [];
    const pad = 4 / this.zoom();
    const x0 = box.x - pad;
    const y0 = box.y - pad;
    const x1 = box.x + box.w + pad;
    const y1 = box.y + box.h + pad;
    const mx = (x0 + x1) / 2;
    const my = (y0 + y1) / 2;
    return [
      { id: 'nw', x: x0, y: y0, cursor: 'nwse-resize' },
      { id: 'n', x: mx, y: y0, cursor: 'ns-resize' },
      { id: 'ne', x: x1, y: y0, cursor: 'nesw-resize' },
      { id: 'e', x: x1, y: my, cursor: 'ew-resize' },
      { id: 'se', x: x1, y: y1, cursor: 'nwse-resize' },
      { id: 's', x: mx, y: y1, cursor: 'ns-resize' },
      { id: 'sw', x: x0, y: y1, cursor: 'nesw-resize' },
      { id: 'w', x: x0, y: my, cursor: 'ew-resize' },
    ];
  });

  /** Keyboard help: [keys, translation key of what they do]. */
  readonly helpRows: [string, string][] = [
    ['V · H · R · D · O', 'board.help.tools'],
    ['A · L · P · T · N · F · E', 'board.help.tools2'],
    ['Shift + drag', 'board.help.shift'],
    ['Alt + drag', 'board.help.alt'],
    ['Tab', 'board.help.tab'],
    ['Enter · double click', 'board.help.edit'],
    ['Ctrl + A / C / X / V / D', 'board.help.clipboard'],
    ['Ctrl + Z / Shift + Ctrl + Z', 'board.help.undo'],
    ['Ctrl + G / Shift + Ctrl + G', 'board.help.group'],
    ['Ctrl + ] / [', 'board.help.order'],
    ['Ctrl + Shift + L', 'board.help.lock'],
    ['Arrows · Shift + arrows', 'board.help.nudge'],
    ['Space + drag · wheel', 'board.help.pan'],
    ['Ctrl + wheel · + · -', 'board.help.zoom'],
    ['Shift + 1 · Shift + 2', 'board.help.fit'],
    ['Delete', 'board.help.delete'],
    ['?', 'board.help.help'],
  ];

  readonly bindOutline = computed(() => {
    const id = this.bindTarget();
    const e = id ? this.byId().get(id) : undefined;
    return e ? { x: e.x - 4, y: e.y - 4, w: e.w + 8, h: e.h + 8 } : null;
  });

  // ---- panel capabilities -----------------------------------------------------------------------------------
  private readonly targetTypes = computed(() => {
    const sel = this.selected();
    if (sel.length) return sel.map((e) => e.type);
    const tool = this.tool();
    return DRAW_TOOLS.includes(tool) ? [tool as BoardElement['type']] : [];
  });
  readonly panelVisible = computed(() => !this.readonly() && this.targetTypes().length > 0);
  readonly caps = computed(() => {
    const types = this.targetTypes();
    const some = (...t: BoardElement['type'][]) => types.some((x) => t.includes(x));
    return {
      stroke: some('box', 'diamond', 'ellipse', 'triangle', 'arrow', 'line', 'pen', 'text', 'frame', 'note'),
      fill: some('box', 'diamond', 'ellipse', 'triangle', 'note', 'frame'),
      fillStyle: some('box', 'diamond', 'ellipse', 'triangle'),
      width: some('box', 'diamond', 'ellipse', 'triangle', 'arrow', 'line', 'pen'),
      strokeStyle: some('box', 'diamond', 'ellipse', 'triangle', 'arrow', 'line', 'pen'),
      rough: some('box', 'diamond', 'ellipse', 'triangle', 'arrow', 'line'),
      round: some('box'),
      text: some('box', 'diamond', 'ellipse', 'triangle', 'note', 'text', 'arrow', 'line'),
      align: some('box', 'diamond', 'ellipse', 'triangle', 'note', 'text'),
      arrow: some('arrow', 'line'),
      heads: some('arrow', 'line'),
      link: types.length === 1 && this.selection().length === 1,
    };
  });

  /** Value of a style property for the panel: the selection's own, or the defaults for new elements. */
  val<K extends keyof DefaultStyle>(key: K): DefaultStyle[K] {
    const first = this.selected()[0];
    return first ? (resolveStyle(first)[key] as DefaultStyle[K]) : this.style()[key];
  }

  // ---- history, saving --------------------------------------------------------------------------------------
  private undoStack: BoardElement[][] = [];
  private redoStack: BoardElement[][] = [];
  private drag: Drag | null = null;
  private pointers = new Map<number, Point>();
  private pinchDistance = 0;
  private clipboard: BoardElement[] = [];
  private pasteCount = 0;
  private lastEmitted = '';
  private loaded = false;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      const json = this.content();
      untracked(() => {
        if (json === this.lastEmitted) return;
        this.lastEmitted = json;
        const next = parseBoard(json);
        // Keep the user's viewport once the board is open; adopt the saved one only on first load.
        const firstLoad = !this.loaded;
        this.board.set(this.loaded ? { ...next, view: this.board().view } : next);
        this.loaded = true;
        // A board that was generated (never panned or zoomed) starts centred on its content, not on its corner.
        const pristine = next.view.x === 0 && next.view.y === 0 && next.view.zoom === 1;
        if (firstLoad && pristine && next.elements.length > 0) {
          setTimeout(() => this.fit());
        }
        const ids = new Set(next.elements.map((e) => e.id));
        this.selection.update((s) => s.filter((id) => ids.has(id)));
      });
    });

    // The textarea exists only after the render that follows `editing`, so focus it when it shows up.
    effect(() => {
      const area = this.editor()?.nativeElement;
      if (area) {
        area.focus();
        area.select();
      }
    });

    effect((onCleanup) => {
      const el = this.stageEl().nativeElement;
      const observer = new ResizeObserver(() => this.stage.set({ w: el.clientWidth, h: el.clientHeight }));
      observer.observe(el);
      this.stage.set({ w: el.clientWidth, h: el.clientHeight });
      onCleanup(() => observer.disconnect());
    });

    this.destroyRef.onDestroy(() => this.flush());
  }

  private snapshot(): void {
    this.undoStack.push(this.elements());
    if (this.undoStack.length > HISTORY_LIMIT) this.undoStack.shift();
    this.redoStack = [];
    this.syncHistoryFlags();
  }

  private dropSnapshot(): void {
    this.undoStack.pop();
    this.syncHistoryFlags();
  }

  private syncHistoryFlags(): void {
    this.canUndo.set(this.undoStack.length > 0);
    this.canRedo.set(this.redoStack.length > 0);
  }

  undo(): void {
    const prev = this.undoStack.pop();
    if (!prev) return;
    this.redoStack.push(this.elements());
    this.setElements(prev);
    this.pruneSelection();
    this.syncHistoryFlags();
    this.scheduleEmit();
  }

  redo(): void {
    const next = this.redoStack.pop();
    if (!next) return;
    this.undoStack.push(this.elements());
    this.setElements(next);
    this.pruneSelection();
    this.syncHistoryFlags();
    this.scheduleEmit();
  }

  private pruneSelection(): void {
    const ids = new Set(this.elements().map((e) => e.id));
    this.selection.update((s) => s.filter((id) => ids.has(id)));
  }

  private setElements(elements: BoardElement[]): void {
    this.board.update((b) => ({ ...b, elements }));
  }

  /** Adds elements on top, except frames, which always go behind everything they may contain. */
  private appendElements(added: BoardElement[]): void {
    const frames = added.filter((e) => e.type === 'frame');
    const rest = added.filter((e) => e.type !== 'frame');
    this.setElements([...frames, ...this.elements(), ...rest]);
  }

  private replaceMany(changes: Map<string, BoardElement>): void {
    this.setElements(this.elements().map((e) => changes.get(e.id) ?? e));
  }

  private replace(id: string, change: Partial<BoardElement>): void {
    this.setElements(this.elements().map((e) => (e.id === id ? { ...e, ...change } : e)));
  }

  private setView(view: { x: number; y: number; zoom: number }): void {
    this.board.update((b) => ({ ...b, view }));
  }

  private scheduleEmit(): void {
    if (this.readonly()) return;
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      this.emit();
    }, SAVE_DEBOUNCE_MS);
  }

  flush(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
      this.emit();
    }
  }

  private emit(): void {
    const json = serializeBoard(this.board());
    if (json.length > MAX_CONTENT_BYTES) {
      this.tooBig.set(true);
      return;
    }
    this.tooBig.set(false);
    this.lastEmitted = json;
    this.contentChange.emit(json);
  }

  // ---- coordinates and hit testing --------------------------------------------------------------------------

  private toWorld(event: { clientX: number; clientY: number }): Point {
    const rect = this.svg().nativeElement.getBoundingClientRect();
    const v = this.view();
    return { x: (event.clientX - rect.left - v.x) / v.zoom, y: (event.clientY - rect.top - v.y) / v.zoom };
  }

  private toScreen(p: Point): Point {
    const v = this.view();
    return { x: p.x * v.zoom + v.x, y: p.y * v.zoom + v.y };
  }

  private viewCenter(): Point {
    const v = this.view();
    const s = this.stage();
    return { x: (s.w / 2 - v.x) / v.zoom, y: (s.h / 2 - v.y) / v.zoom };
  }

  private hitAt(p: Point, ignore?: Set<string>): BoardElement | undefined {
    const tol = 6 / this.zoom();
    const list = this.elements();
    for (let i = list.length - 1; i >= 0; i--) {
      const e = list[i];
      if (ignore?.has(e.id)) continue;
      if (hitElement(e, p, tol, this.lookup)) return e;
    }
    return undefined;
  }

  /** What an arrow end can glue to: shapes, notes, text and images (never arrows, strokes or frames). */
  private bindableAt(p: Point, ignoreId?: string): BoardElement | undefined {
    const list = this.elements();
    for (let i = list.length - 1; i >= 0; i--) {
      const e = list[i];
      if (e.id === ignoreId || !(isShape(e) || e.type === 'note' || e.type === 'text' || e.type === 'image')) continue;
      if (p.x >= e.x - 4 && p.x <= e.x + e.w + 4 && p.y >= e.y - 4 && p.y <= e.y + e.h + 4) return e;
    }
    return undefined;
  }

  private withGroups(ids: string[]): string[] {
    const groups = new Set(this.elements().filter((e) => ids.includes(e.id) && e.group).map((e) => e.group));
    if (groups.size === 0) return ids;
    const all = new Set(ids);
    for (const e of this.elements()) if (e.group && groups.has(e.group)) all.add(e.id);
    return [...all];
  }

  // ---- tools ------------------------------------------------------------------------------------------------

  setTool(tool: Tool): void {
    if (this.readonly() && tool !== 'hand' && tool !== 'select') return;
    this.commitIfEditing();
    this.tool.set(tool);
    if (tool !== 'select') this.selection.set([]);
    if (tool === 'image') {
      this.imageInput()?.nativeElement.click();
      this.tool.set('select');
    }
  }

  private afterCreate(): void {
    if (!this.toolLock()) this.tool.set('select');
  }

  private defaults(type: BoardElement['type']): Partial<BoardElement> {
    const s = this.style();
    switch (type) {
      case 'box':
      case 'diamond':
      case 'ellipse':
      case 'triangle':
        return { stroke: s.stroke, fill: s.fill, fillStyle: s.fillStyle, strokeWidth: s.strokeWidth, strokeStyle: s.strokeStyle, opacity: s.opacity, rough: s.rough, round: s.round, fontSize: s.fontSize, font: s.font, align: 'center' };
      case 'arrow':
      case 'line':
        return { stroke: s.stroke, strokeWidth: s.strokeWidth, strokeStyle: s.strokeStyle, opacity: s.opacity, rough: s.rough, route: s.route, startHead: s.startHead, endHead: type === 'line' ? 'none' : s.endHead, fontSize: 16 };
      case 'pen':
        return { stroke: s.stroke, strokeWidth: Math.max(2, s.strokeWidth), strokeStyle: s.strokeStyle, opacity: s.opacity };
      case 'text':
        return { stroke: s.stroke, fontSize: s.fontSize, font: s.font, align: s.align, opacity: s.opacity };
      case 'note':
        return { fontSize: Math.min(s.fontSize, 20), font: s.font, opacity: s.opacity };
      default:
        return {};
    }
  }

  private makeElement(type: BoardElement['type'], x: number, y: number, w: number, h: number, extra: Partial<BoardElement> = {}): BoardElement {
    return { id: newId(), type, x, y, w, h, text: '', color: type === 'note' ? 'yellow' : 'slate', ...this.defaults(type), ...extra };
  }

  // ---- pointer ----------------------------------------------------------------------------------------------

  onDown(event: PointerEvent): void {
    this.host().nativeElement.focus({ preventScroll: true });
    if (this.editing()) return;
    this.svg().nativeElement.setPointerCapture(event.pointerId);
    this.pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (this.pointers.size === 2) {
      this.drag = null;
      this.pinchDistance = this.pointerDistance();
      return;
    }
    const p = this.toWorld(event);
    const v = this.view();

    if (event.button === 2) {
      // right click: select what is under the cursor so the context menu acts on it
      const hit = this.hitAt(p);
      if (hit && !this.selection().includes(hit.id)) this.selection.set(this.withGroups([hit.id]));
      if (!hit) this.selection.set([]);
      return;
    }
    if (event.button === 1 || this.tool() === 'hand' || this.readonly() || this.spaceDown()) {
      this.drag = { kind: 'pan', sx: event.clientX, sy: event.clientY, vx: v.x, vy: v.y };
      return;
    }

    const tool = this.tool();
    const handle = (event.target as Element).getAttribute?.('data-handle') ?? null;

    if (tool === 'select') {
      if (handle) {
        this.startHandleDrag(handle, p);
        return;
      }
      const hit = this.hitAt(p);
      if (hit) {
        let ids = this.selection();
        if (event.shiftKey) {
          ids = ids.includes(hit.id) ? ids.filter((id) => id !== hit.id) : [...ids, ...this.withGroups([hit.id])];
          this.selection.set(ids);
          if (!ids.includes(hit.id)) return;
        } else if (!ids.includes(hit.id)) {
          ids = this.withGroups([hit.id]);
          this.selection.set(ids);
        }
        this.startMove(p, event.altKey);
        return;
      }
      const base = event.shiftKey ? this.selection() : [];
      if (!event.shiftKey) this.selection.set([]);
      this.drag = { kind: 'marquee', start: p, base };
      return;
    }

    if (tool === 'eraser') {
      this.snapshot();
      this.drag = { kind: 'erase' };
      this.eraseAt(p);
      return;
    }
    if (tool === 'text') {
      this.snapshot();
      const el = this.makeElement('text', p.x, p.y - 12, DEFAULT_SIZE.text.w, DEFAULT_SIZE.text.h);
      this.setElements([...this.elements(), el]);
      this.selection.set([el.id]);
      this.afterCreate();
      this.startEdit(el.id);
      return;
    }
    if (tool === 'arrow' || tool === 'line') {
      this.snapshot();
      const from = this.bindableAt(p);
      const el = this.makeElement(tool, p.x, p.y, 0, 0, { x2: p.x, y2: p.y, ...(from ? { from: from.id } : {}) });
      this.setElements([...this.elements(), el]);
      this.drag = { kind: 'linear', id: el.id };
      return;
    }
    if (tool === 'pen') {
      this.snapshot();
      const el = this.makeElement('pen', p.x, p.y, 0, 0, { points: [[p.x, p.y]] });
      this.setElements([...this.elements(), el]);
      this.drag = { kind: 'pen', id: el.id };
      return;
    }
    if (DRAW_TOOLS.includes(tool)) {
      this.snapshot();
      const q = this.snapPoint(p);
      const el = this.makeElement(tool as BoardElement['type'], q.x, q.y, 0, 0);
      // frames live behind everything they will contain
      this.setElements(tool === 'frame' ? [el, ...this.elements()] : [...this.elements(), el]);
      this.drag = { kind: 'create', id: el.id, start: q, tool };
    }
  }

  private pointerDistance(): number {
    const [a, b] = [...this.pointers.values()];
    return Math.hypot(a.x - b.x, a.y - b.y);
  }

  private snapPoint(p: Point): Point {
    return this.snapOn() ? { x: snap(p.x, GRID), y: snap(p.y, GRID) } : p;
  }

  private startMove(p: Point, duplicate: boolean): void {
    let ids = this.selection();
    if (duplicate && !this.readonly()) {
      this.snapshot();
      const copies = this.cloneElements(this.selected(), 0, 0);
      this.appendElements(copies);
      ids = copies.map((c) => c.id);
      this.selection.set(ids);
    }
    const moving = new Set(ids);
    // A frame carries what is inside it.
    for (const id of ids) {
      const frame = this.elements().find((e) => e.id === id);
      if (frame?.type === 'frame') {
        const box = { x: frame.x, y: frame.y, w: frame.w, h: frame.h };
        for (const e of this.elements()) {
          if (e.type !== 'frame' && !moving.has(e.id) && rectContains(box, elementBounds(e, this.lookup))) moving.add(e.id);
        }
      }
    }
    const current = new Map(this.elements().map((e) => [e.id, e]));
    const origs = new Map<string, BoardElement>();
    for (const id of moving) {
      const e = current.get(id);
      if (e && !e.locked) origs.set(id, e);
    }
    // An arrow dragged without what it is glued to becomes free, so it can actually move.
    for (const [id, e] of origs) {
      if (isLinear(e) && ((e.from && !moving.has(e.from)) || (e.to && !moving.has(e.to)))) {
        const ends = arrowEnds(e, this.lookup);
        origs.set(id, {
          ...e,
          x: ends.a.x,
          y: ends.a.y,
          x2: ends.b.x,
          y2: ends.b.y,
          from: e.from && moving.has(e.from) ? e.from : undefined,
          to: e.to && moving.has(e.to) ? e.to : undefined,
        });
      }
    }
    const box = unionRect([...origs.values()].map((e) => elementBounds(e, this.lookup))) ?? { x: p.x, y: p.y, w: 0, h: 0 };
    this.drag = { kind: 'move', start: p, origs, box, moved: false };
  }

  private startHandleDrag(handle: string, p: Point): void {
    if (handle === 'arrow-a' || handle === 'arrow-b') {
      const s = this.single();
      if (!s || s.locked) return;
      this.snapshot();
      this.drag = { kind: 'arrow-end', id: s.id, end: handle === 'arrow-a' ? 'a' : 'b' };
      return;
    }
    if (handle.startsWith('add-')) {
      this.addConnected(handle.slice(4));
      return;
    }
    if (handle.startsWith('size-')) {
      const box = this.selectionBox();
      if (!box || this.anyLocked()) return;
      this.snapshot();
      const origs = new Map(this.selected().map((e) => [e.id, e]));
      this.drag = { kind: 'resize', handle: handle.slice(5), start: p, box, origs, moved: false };
    }
  }

  onMove(event: PointerEvent): void {
    if (this.pointers.has(event.pointerId)) this.pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    if (this.pointers.size === 2) {
      const dist = this.pointerDistance();
      if (this.pinchDistance > 0) {
        const [a, b] = [...this.pointers.values()];
        const rect = this.svg().nativeElement.getBoundingClientRect();
        this.zoomAt(dist / this.pinchDistance, (a.x + b.x) / 2 - rect.left, (a.y + b.y) / 2 - rect.top);
      }
      this.pinchDistance = dist;
      return;
    }
    const d = this.drag;
    if (!d) return;
    if (d.kind === 'pan') {
      this.setView({ ...this.view(), x: d.vx + event.clientX - d.sx, y: d.vy + event.clientY - d.sy });
      return;
    }
    const p = this.toWorld(event);
    switch (d.kind) {
      case 'move':
        this.onMoveDrag(d, p);
        return;
      case 'resize':
        this.onResizeDrag(d, p, event.shiftKey);
        return;
      case 'marquee': {
        const rect = this.rectOf(d.start, p);
        this.marquee.set(rect);
        const hit = this.elements().filter((e) => !e.locked && rectContains(rect, elementBounds(e, this.lookup))).map((e) => e.id);
        this.selection.set(this.withGroups([...new Set([...d.base, ...hit])]));
        return;
      }
      case 'create': {
        const q = this.snapPoint(p);
        let w = q.x - d.start.x;
        let h = q.y - d.start.y;
        if (event.shiftKey && d.tool !== 'frame') {
          const size = Math.max(Math.abs(w), Math.abs(h));
          w = Math.sign(w || 1) * size;
          h = Math.sign(h || 1) * size;
        }
        this.replace(d.id, { x: w < 0 ? d.start.x + w : d.start.x, y: h < 0 ? d.start.y + h : d.start.y, w: Math.abs(w), h: Math.abs(h) });
        return;
      }
      case 'linear': {
        const end = this.constrain(d.id, p, event.shiftKey);
        this.replace(d.id, { x2: end.x, y2: end.y });
        this.bindTarget.set(this.bindableAt(p, this.byId().get(d.id)?.from)?.id ?? null);
        return;
      }
      case 'arrow-end': {
        const el = this.byId().get(d.id);
        if (!el) return;
        const q = this.constrain(d.id, p, event.shiftKey, d.end);
        this.replace(d.id, d.end === 'a' ? { x: q.x, y: q.y, from: undefined } : { x2: q.x, y2: q.y, to: undefined });
        this.bindTarget.set(this.bindableAt(p, d.end === 'a' ? el.to : el.from)?.id ?? null);
        return;
      }
      case 'pen': {
        const el = this.byId().get(d.id);
        if (el) this.replace(d.id, { points: [...(el.points ?? []), [p.x, p.y]] });
        return;
      }
      case 'erase':
        this.eraseAt(p);
        return;
    }
  }

  /** With shift, an arrow snaps to 15° steps. */
  private constrain(id: string, p: Point, shift: boolean, end: 'a' | 'b' = 'b'): Point {
    if (!shift) return p;
    const el = this.byId().get(id);
    if (!el) return p;
    const origin = end === 'b' ? { x: el.x, y: el.y } : { x: el.x2 ?? el.x, y: el.y2 ?? el.y };
    const angle = Math.round(Math.atan2(p.y - origin.y, p.x - origin.x) / (Math.PI / 12)) * (Math.PI / 12);
    const len = Math.hypot(p.x - origin.x, p.y - origin.y);
    return { x: origin.x + Math.cos(angle) * len, y: origin.y + Math.sin(angle) * len };
  }

  private rectOf(a: Point, b: Point): Rect {
    return { x: Math.min(a.x, b.x), y: Math.min(a.y, b.y), w: Math.abs(a.x - b.x), h: Math.abs(a.y - b.y) };
  }

  private onMoveDrag(d: Extract<Drag, { kind: 'move' }>, p: Point): void {
    let dx = p.x - d.start.x;
    let dy = p.y - d.start.y;
    if (!d.moved && Math.hypot(dx, dy) * this.zoom() < 3) return;
    if (!d.moved) {
      d.moved = true;
      this.snapshot();
    }
    if (this.snapOn()) {
      dx = snap(d.box.x + dx, GRID) - d.box.x;
      dy = snap(d.box.y + dy, GRID) - d.box.y;
    }
    const changes = new Map<string, BoardElement>();
    for (const [id, orig] of d.origs) changes.set(id, translate(orig, dx, dy));
    this.replaceMany(changes);
  }

  private onResizeDrag(d: Extract<Drag, { kind: 'resize' }>, p: Point, keepRatio: boolean): void {
    d.moved = true;
    const right = d.box.x + d.box.w;
    const bottom = d.box.y + d.box.h;
    const q = this.snapPoint(p);
    let left = d.box.x;
    let top = d.box.y;
    let r = right;
    let b = bottom;
    if (d.handle.includes('w')) left = Math.min(q.x, right - MIN_SIZE);
    if (d.handle.includes('e')) r = Math.max(q.x, d.box.x + MIN_SIZE);
    if (d.handle.includes('n')) top = Math.min(q.y, bottom - MIN_SIZE);
    if (d.handle.includes('s')) b = Math.max(q.y, d.box.y + MIN_SIZE);
    if (keepRatio && d.box.w > 0 && d.box.h > 0 && d.handle.length === 2) {
      const ratio = d.box.w / d.box.h;
      const nw = r - left;
      const nh = b - top;
      if (nw / nh > ratio) {
        const adj = nw / ratio;
        if (d.handle.includes('n')) top = b - adj;
        else b = top + adj;
      } else {
        const adj = nh * ratio;
        if (d.handle.includes('w')) left = r - adj;
        else r = left + adj;
      }
    }
    const to: Rect = { x: left, y: top, w: r - left, h: b - top };
    const changes = new Map<string, BoardElement>();
    for (const [id, orig] of d.origs) {
      let next = scaleInto(orig, d.box, to);
      if (next.type === 'text') next = { ...next, h: fitTextHeight(next) };
      changes.set(id, next);
    }
    this.replaceMany(changes);
  }

  onUp(event: PointerEvent): void {
    this.pointers.delete(event.pointerId);
    if (this.pointers.size < 2) this.pinchDistance = 0;
    const d = this.drag;
    this.drag = null;
    this.bindTarget.set(null);
    this.marquee.set(null);
    if (!d) return;
    const p = this.toWorld(event);

    switch (d.kind) {
      case 'create': {
        const el = this.byId().get(d.id);
        if (!el) break;
        if (el.w < 4 && el.h < 4) {
          const size = DEFAULT_SIZE[d.tool as keyof typeof DEFAULT_SIZE];
          this.replace(d.id, { x: d.start.x - size.w / 2, y: d.start.y - size.h / 2, w: size.w, h: size.h });
        } else if (el.w < MIN_SIZE || el.h < MIN_SIZE) {
          this.replace(d.id, { w: Math.max(el.w, MIN_SIZE), h: Math.max(el.h, MIN_SIZE) });
        }
        this.selection.set([d.id]);
        this.afterCreate();
        if (d.tool === 'note') this.startEdit(d.id);
        break;
      }
      case 'linear':
      case 'arrow-end': {
        const el = this.byId().get(d.id);
        if (!el) break;
        const startingEnd = d.kind === 'arrow-end' && d.end === 'a';
        const target = this.bindableAt(p, startingEnd ? el.to : el.from);
        if (target) this.replace(d.id, startingEnd ? { from: target.id } : { to: target.id });
        const fresh = this.byId().get(d.id);
        if (d.kind === 'linear' && fresh) {
          const ends = arrowEnds(fresh, this.lookup);
          if (Math.hypot(ends.b.x - ends.a.x, ends.b.y - ends.a.y) < 10) {
            this.setElements(this.elements().filter((x) => x.id !== d.id));
            this.dropSnapshot();
            return;
          }
          this.selection.set([d.id]);
          this.afterCreate();
        }
        break;
      }
      case 'pen': {
        const el = this.byId().get(d.id);
        if (!el) break;
        const points = simplifyStroke(el.points ?? []);
        if (points.length < 2) {
          this.setElements(this.elements().filter((x) => x.id !== d.id));
          this.dropSnapshot();
          return;
        }
        this.replace(d.id, { points });
        break;
      }
      case 'move':
        if (!d.moved) return;
        break;
      case 'resize':
        if (!d.moved) {
          this.dropSnapshot();
          return;
        }
        break;
      case 'marquee':
      case 'pan':
        return;
      case 'erase':
        break;
    }
    this.scheduleEmit();
  }

  private eraseAt(p: Point): void {
    const hit = this.hitAt(p);
    if (hit && !hit.locked) {
      this.setElements(this.elements().filter((e) => e.id !== hit.id && e.from !== hit.id && e.to !== hit.id));
      this.scheduleEmit();
    }
  }

  onDouble(event: MouseEvent): void {
    if (this.readonly() || this.tool() !== 'select') return;
    const p = this.toWorld(event);
    const list = this.elements();
    let inside: BoardElement | undefined;
    for (let i = list.length - 1; i >= 0 && !inside; i--) if (containsPoint(list[i], p)) inside = list[i];
    const hit = inside ?? this.hitAt(p);
    if (hit) {
      if (!hit.locked && canHoldText(hit)) {
        this.selection.set([hit.id]);
        this.startEdit(hit.id);
      }
      return;
    }
    // double click on empty canvas: a text right there
    this.snapshot();
    const el = this.makeElement('text', p.x, p.y - 12, DEFAULT_SIZE.text.w, DEFAULT_SIZE.text.h);
    this.setElements([...this.elements(), el]);
    this.selection.set([el.id]);
    this.startEdit(el.id);
  }

  onWheel(event: WheelEvent): void {
    event.preventDefault();
    const rect = this.svg().nativeElement.getBoundingClientRect();
    if (event.ctrlKey || event.metaKey) {
      this.zoomAt(event.deltaY < 0 ? 1.1 : 1 / 1.1, event.clientX - rect.left, event.clientY - rect.top);
    } else {
      // plain wheel / trackpad scroll pans, like Excalidraw
      const v = this.view();
      this.setView({ ...v, x: v.x - (event.shiftKey ? event.deltaY : event.deltaX), y: v.y - (event.shiftKey ? 0 : event.deltaY) });
    }
  }

  // ---- view -------------------------------------------------------------------------------------------------

  zoomBy(factor: number): void {
    const s = this.stage();
    this.zoomAt(factor, s.w / 2, s.h / 2);
  }

  private zoomAt(factor: number, sx: number, sy: number): void {
    const v = this.view();
    const zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, v.zoom * factor));
    const k = zoom / v.zoom;
    this.setView({ zoom, x: sx - (sx - v.x) * k, y: sy - (sy - v.y) * k });
  }

  resetZoom(): void {
    const s = this.stage();
    this.zoomAt(1 / this.zoom(), s.w / 2, s.h / 2);
  }

  /** Fits everything (or just the selection) in the screen. */
  fit(onlySelection = false): void {
    const box = onlySelection && this.selectionBox() ? this.selectionBox() : unionRect(this.elements().map((e) => elementBounds(e, this.lookup)));
    const s = this.stage();
    if (!box || s.w === 0) {
      this.setView({ x: 0, y: 0, zoom: 1 });
      return;
    }
    const pad = 60;
    const zoom = Math.min(2, MAX_ZOOM, Math.max(MIN_ZOOM, Math.min((s.w - pad * 2) / Math.max(box.w, 1), (s.h - pad * 2) / Math.max(box.h, 1))));
    this.setView({ zoom, x: (s.w - box.w * zoom) / 2 - box.x * zoom, y: (s.h - box.h * zoom) / 2 - box.y * zoom });
  }

  // ---- minimap ----------------------------------------------------------------------------------------------

  readonly minimapData = computed(() => {
    const els = this.elements().map((e) => ({ id: e.id, ...elementBounds(e, this.lookup) }));
    const v = this.view();
    const s = this.stage();
    const viewport: Rect = { x: -v.x / v.zoom, y: -v.y / v.zoom, w: s.w / v.zoom, h: s.h / v.zoom };
    const all = unionRect([viewport, ...els]) ?? viewport;
    const pad = Math.max(all.w, all.h) * 0.05;
    const world: Rect = { x: all.x - pad, y: all.y - pad, w: all.w + pad * 2, h: all.h + pad * 2 };
    return { els, viewport, world };
  });

  onMinimap(event: PointerEvent): void {
    if (event.type === 'pointermove' && event.buttons === 0) return;
    const target = event.currentTarget as SVGSVGElement;
    const rect = target.getBoundingClientRect();
    const data = this.minimapData();
    const scale = Math.max(data.world.w / rect.width, data.world.h / rect.height);
    const wx = data.world.x + (event.clientX - rect.left) * scale;
    const wy = data.world.y + (event.clientY - rect.top) * scale;
    const v = this.view();
    const s = this.stage();
    this.setView({ ...v, x: s.w / 2 - wx * v.zoom, y: s.h / 2 - wy * v.zoom });
  }

  // ---- text editing -----------------------------------------------------------------------------------------

  private startEdit(id: string): void {
    const el = this.elements().find((e) => e.id === id);
    if (!el || this.readonly() || el.locked) return;
    const z = this.zoom();
    const st = resolveStyle(el);
    let box: Rect = { x: el.x, y: el.y, w: el.w, h: el.h };
    if (isLinear(el)) {
      const m = arrowGeometry(el, this.lookup).mid;
      box = { x: m.x - 80, y: m.y - 16, w: 160, h: 32 };
    } else if (el.type === 'frame') {
      box = { x: el.x, y: el.y - 30, w: Math.min(el.w, 280), h: 28 };
    }
    const s = this.toScreen({ x: box.x, y: box.y });
    this.editing.set({
      id,
      text: el.text,
      left: s.x,
      top: s.y,
      width: Math.max(box.w * z, 90),
      height: Math.max(box.h * z, 34),
      fontSize: (isLinear(el) ? st.fontSize - 3 : el.type === 'frame' ? 14 : st.fontSize) * z,
      font: st.font,
      align: isLinear(el) || el.type === 'frame' ? 'center' : st.align,
    });
  }

  private commitIfEditing(): void {
    const area = this.editor()?.nativeElement;
    if (this.editing() && area) this.commitEdit(area.value);
  }

  commitEdit(value: string): void {
    const ed = this.editing();
    if (!ed) return;
    this.editing.set(null);
    const el = this.byId().get(ed.id);
    if (!el) return;
    if (el.type === 'text' && !value.trim()) {
      // an empty text is nothing: drop it
      this.setElements(this.elements().filter((e) => e.id !== ed.id));
      this.selection.set([]);
      this.scheduleEmit();
      return;
    }
    if (el.text !== value) {
      this.snapshot();
      let next: BoardElement = { ...el, text: value };
      if (el.type === 'text') next = { ...next, h: fitTextHeight(next) };
      this.replace(ed.id, next);
      this.scheduleEmit();
    }
  }

  cancelEdit(): void {
    this.editing.set(null);
  }

  // ---- style ------------------------------------------------------------------------------------------------

  /** Applies a look to the selection (if any) and remembers it for the next things you draw. */
  setStyle(patch: Partial<BoardElement>): void {
    this.style.update((s) => {
      const next = { ...s } as Record<string, unknown>;
      for (const [k, v] of Object.entries(patch)) if (k in s) next[k] = v;
      return next as unknown as DefaultStyle;
    });
    const targets = this.selected().filter((e) => !e.locked);
    if (targets.length === 0) return;
    this.snapshot();
    const changes = new Map<string, BoardElement>();
    for (const e of targets) {
      let next: BoardElement = { ...e, ...patch };
      if (next.type === 'text' && ('fontSize' in patch || 'font' in patch)) next = { ...next, h: fitTextHeight(next) };
      changes.set(e.id, next);
    }
    this.replaceMany(changes);
    this.scheduleEmit();
  }

  setLink(value: string): void {
    const el = this.single();
    if (!el) return;
    const url = value.trim();
    if (url && !/^https?:\/\//i.test(url)) {
      this.toast.error(this.i18n.translate('board.linkInvalid'));
      return;
    }
    this.snapshot();
    this.replace(el.id, { link: url || undefined });
    this.scheduleEmit();
  }

  openLink(link: string): void {
    if (/^https?:\/\//i.test(link)) window.open(link, '_blank', 'noopener,noreferrer');
  }

  setBackground(color: string): void {
    this.board.update((b) => ({ ...b, bg: color }));
    this.scheduleEmit();
  }

  toggleGrid(): void {
    this.board.update((b) => ({ ...b, grid: !b.grid }));
    this.scheduleEmit();
  }

  // ---- element operations -----------------------------------------------------------------------------------

  private cloneElements(source: BoardElement[], dx: number, dy: number): BoardElement[] {
    const idMap = new Map(source.map((e) => [e.id, newId()]));
    const groups = new Map<string, string>();
    return source.map((e) => {
      const moved = translate(e, dx, dy);
      let group = e.group;
      if (group) {
        if (!groups.has(group)) groups.set(group, newId());
        group = groups.get(group);
      }
      const ends = isLinear(e) ? arrowEnds(e, this.lookup) : null;
      const keepFrom = !!e.from && idMap.has(e.from);
      const keepTo = !!e.to && idMap.has(e.to);
      return {
        ...moved,
        id: idMap.get(e.id)!,
        group,
        // glued ends stay glued only when what they point at is copied too
        from: keepFrom ? idMap.get(e.from!) : undefined,
        to: keepTo ? idMap.get(e.to!) : undefined,
        ...(ends && e.from && !keepFrom ? { x: ends.a.x + dx, y: ends.a.y + dy } : {}),
        ...(ends && e.to && !keepTo ? { x2: ends.b.x + dx, y2: ends.b.y + dy } : {}),
      };
    });
  }

  duplicate(): void {
    const sel = this.selected();
    if (!sel.length || this.readonly()) return;
    this.snapshot();
    const copies = this.cloneElements(sel, 24, 24);
    this.appendElements(copies);
    this.selection.set(copies.map((c) => c.id));
    this.scheduleEmit();
  }

  removeSelected(): void {
    const ids = new Set(this.selected().filter((e) => !e.locked).map((e) => e.id));
    if (!ids.size || this.readonly()) return;
    this.snapshot();
    // Arrows glued to something that disappears keep their last position.
    const next = this.elements()
      .filter((e) => !ids.has(e.id))
      .map((e) => {
        if (!isLinear(e) || !((e.from && ids.has(e.from)) || (e.to && ids.has(e.to)))) return e;
        const ends = arrowEnds(e, this.lookup);
        return {
          ...e,
          ...(e.from && ids.has(e.from) ? { from: undefined, x: ends.a.x, y: ends.a.y } : {}),
          ...(e.to && ids.has(e.to) ? { to: undefined, x2: ends.b.x, y2: ends.b.y } : {}),
        };
      });
    this.setElements(next);
    this.selection.set([]);
    this.scheduleEmit();
  }

  selectAll(): void {
    this.selection.set(this.elements().filter((e) => !e.locked).map((e) => e.id));
  }

  group(): void {
    if (!this.canGroup()) return;
    this.snapshot();
    const id = newId();
    const ids = new Set(this.selection());
    this.setElements(this.elements().map((e) => (ids.has(e.id) ? { ...e, group: id } : e)));
    this.scheduleEmit();
  }

  ungroup(): void {
    const ids = new Set(this.selection());
    this.snapshot();
    this.setElements(this.elements().map((e) => (ids.has(e.id) && e.group ? { ...e, group: undefined } : e)));
    this.scheduleEmit();
  }

  toggleLock(): void {
    const sel = this.selected();
    if (!sel.length) return;
    const lock = !sel.every((e) => e.locked);
    this.snapshot();
    const ids = new Set(sel.map((e) => e.id));
    this.setElements(this.elements().map((e) => (ids.has(e.id) ? { ...e, locked: lock || undefined } : e)));
    this.scheduleEmit();
  }

  order(action: 'front' | 'back' | 'forward' | 'backward'): void {
    const ids = new Set(this.selection());
    if (!ids.size) return;
    const list = [...this.elements()];
    const picked = list.filter((e) => ids.has(e.id));
    const rest = list.filter((e) => !ids.has(e.id));
    let next: BoardElement[];
    if (action === 'front') next = [...rest, ...picked];
    else if (action === 'back') next = [...picked, ...rest];
    else {
      next = [...list];
      const step = action === 'forward' ? 1 : -1;
      const indexes = list.map((e, i) => (ids.has(e.id) ? i : -1)).filter((i) => i >= 0);
      const ordered = step === 1 ? indexes.reverse() : indexes;
      for (const i of ordered) {
        const j = i + step;
        if (j < 0 || j >= next.length || ids.has(next[j].id)) continue;
        [next[i], next[j]] = [next[j], next[i]];
      }
    }
    this.snapshot();
    this.setElements(next);
    this.scheduleEmit();
  }

  align(kind: 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom'): void {
    const sel = this.selected().filter((e) => !e.locked);
    if (sel.length < 2) return;
    const box = unionRect(sel.map((e) => elementBounds(e, this.lookup)))!;
    this.snapshot();
    const changes = new Map<string, BoardElement>();
    for (const e of sel) {
      const b = elementBounds(e, this.lookup);
      let dx = 0;
      let dy = 0;
      if (kind === 'left') dx = box.x - b.x;
      if (kind === 'center') dx = box.x + box.w / 2 - (b.x + b.w / 2);
      if (kind === 'right') dx = box.x + box.w - (b.x + b.w);
      if (kind === 'top') dy = box.y - b.y;
      if (kind === 'middle') dy = box.y + box.h / 2 - (b.y + b.h / 2);
      if (kind === 'bottom') dy = box.y + box.h - (b.y + b.h);
      changes.set(e.id, translate(e, dx, dy));
    }
    this.replaceMany(changes);
    this.scheduleEmit();
  }

  distribute(axis: 'h' | 'v'): void {
    const sel = this.selected().filter((e) => !e.locked);
    if (sel.length < 3) return;
    const items = sel.map((e) => ({ e, b: elementBounds(e, this.lookup) })).sort((a, b) => (axis === 'h' ? a.b.x - b.b.x : a.b.y - b.b.y));
    const first = items[0].b;
    const last = items[items.length - 1].b;
    const total = items.reduce((sum, i) => sum + (axis === 'h' ? i.b.w : i.b.h), 0);
    const span = axis === 'h' ? last.x + last.w - first.x : last.y + last.h - first.y;
    const gap = (span - total) / (items.length - 1);
    this.snapshot();
    const changes = new Map<string, BoardElement>();
    let cursor = axis === 'h' ? first.x : first.y;
    for (const { e, b } of items) {
      const delta = cursor - (axis === 'h' ? b.x : b.y);
      changes.set(e.id, translate(e, axis === 'h' ? delta : 0, axis === 'v' ? delta : 0));
      cursor += (axis === 'h' ? b.w : b.h) + gap;
    }
    this.replaceMany(changes);
    this.scheduleEmit();
  }

  nudge(dx: number, dy: number): void {
    const targets = this.selected().filter((e) => !e.locked);
    if (!targets.length) return;
    this.snapshot();
    const changes = new Map<string, BoardElement>();
    for (const e of targets) changes.set(e.id, translate(e, dx, dy));
    this.replaceMany(changes);
    this.scheduleEmit();
  }

  /** A new element next to the selected one, joined to it by an arrow: the quick way to grow a map. */
  addConnected(dir: string): void {
    const src = this.single();
    if (!src || this.readonly()) return;
    const gap = 90;
    const pos = { x: src.x, y: src.y };
    if (dir === 'e') pos.x = src.x + src.w + gap;
    if (dir === 'w') pos.x = src.x - src.w - gap;
    if (dir === 's') pos.y = src.y + src.h + gap;
    if (dir === 'n') pos.y = src.y - src.h - gap;
    this.snapshot();
    const copy: BoardElement = { ...src, id: newId(), x: pos.x, y: pos.y, text: '', group: undefined, locked: undefined, link: undefined };
    const arrow = this.makeElement('arrow', 0, 0, 0, 0, { from: src.id, to: copy.id, route: this.style().route });
    this.setElements([...this.elements(), arrow, copy]);
    this.selection.set([copy.id]);
    this.scheduleEmit();
    this.startEdit(copy.id);
  }

  // ---- clipboard, images, templates -------------------------------------------------------------------------

  copy(): void {
    const sel = this.selected();
    if (!sel.length) return;
    this.clipboard = sel;
    this.pasteCount = 0;
    void navigator.clipboard?.writeText(CLIPBOARD_PREFIX + JSON.stringify(sel)).catch(() => undefined);
  }

  cut(): void {
    this.copy();
    this.removeSelected();
  }

  /** Handles Ctrl+V: images, another board's elements, or plain text (which becomes a sticky note). */
  onPaste(event: ClipboardEvent): void {
    if (this.readonly() || this.editing()) return;
    const target = event.target as HTMLElement | null;
    if (target && ['INPUT', 'TEXTAREA'].includes(target.tagName)) return;
    const data = event.clipboardData;
    if (!data) return;
    const image = [...data.files].find((f) => f.type.startsWith('image/'));
    if (image) {
      event.preventDefault();
      void this.addImage(image);
      return;
    }
    const text = data.getData('text/plain');
    if (text.startsWith(CLIPBOARD_PREFIX)) {
      event.preventDefault();
      try {
        this.insertCopies(JSON.parse(text.slice(CLIPBOARD_PREFIX.length)) as BoardElement[]);
      } catch {
        // not ours after all
      }
    } else if (this.clipboard.length && !text) {
      event.preventDefault();
      this.insertCopies(this.clipboard);
    } else if (text.trim()) {
      event.preventDefault();
      const c = this.viewCenter();
      this.snapshot();
      const el = this.makeElement('note', c.x - 90, c.y - 70, DEFAULT_SIZE.note.w, DEFAULT_SIZE.note.h, { text: text.trim().slice(0, 600) });
      this.setElements([...this.elements(), el]);
      this.selection.set([el.id]);
      this.scheduleEmit();
    }
  }

  pasteFromMenu(): void {
    if (this.clipboard.length) this.insertCopies(this.clipboard);
  }

  private insertCopies(source: BoardElement[]): void {
    const valid = parseBoard(JSON.stringify({ elements: source })).elements;
    if (!valid.length) return;
    this.pasteCount++;
    this.snapshot();
    const copies = this.cloneElements(valid, 24 * this.pasteCount, 24 * this.pasteCount);
    this.appendElements(copies);
    this.selection.set(copies.map((c) => c.id));
    this.scheduleEmit();
  }

  onImagePicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = [...(input.files ?? [])];
    input.value = '';
    for (const file of files) void this.addImage(file);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    if (this.readonly()) return;
    const at = this.toWorld(event);
    for (const file of [...(event.dataTransfer?.files ?? [])]) {
      if (file.type.startsWith('image/')) void this.addImage(file, at);
    }
  }

  private async addImage(file: File, at?: Point): Promise<void> {
    if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type)) {
      this.toast.error(this.i18n.translate('board.imageType'));
      return;
    }
    try {
      const { src, w, h } = await this.shrinkImage(file);
      if (!isSafeImage(src) || src.length > 2_500_000) {
        this.toast.error(this.i18n.translate('board.imageTooBig'));
        return;
      }
      const target = at ?? this.viewCenter();
      const scale = Math.min(1, 420 / Math.max(w, h));
      const el: BoardElement = { id: newId(), type: 'image', x: target.x - (w * scale) / 2, y: target.y - (h * scale) / 2, w: w * scale, h: h * scale, text: '', color: 'white', src };
      this.snapshot();
      this.setElements([...this.elements(), el]);
      this.selection.set([el.id]);
      this.scheduleEmit();
    } catch {
      this.toast.error(this.i18n.translate('board.imageType'));
    }
  }

  /** Scales a picture down to a size that is fine on a board, so the saved board stays light. */
  private shrinkImage(file: File): Promise<{ src: string; w: number; h: number }> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onerror = () => reject(reader.error);
      reader.onload = () => {
        const img = new Image();
        img.onerror = () => reject(new Error('image'));
        img.onload = () => {
          const max = 1400;
          const k = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
          const w = Math.max(1, Math.round(img.naturalWidth * k));
          const h = Math.max(1, Math.round(img.naturalHeight * k));
          const canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          canvas.getContext('2d')?.drawImage(img, 0, 0, w, h);
          const keepsAlpha = file.type === 'image/png' || file.type === 'image/gif';
          resolve({ src: canvas.toDataURL(keepsAlpha ? 'image/png' : 'image/jpeg', 0.86), w, h });
        };
        img.src = String(reader.result);
      };
      reader.readAsDataURL(file);
    });
  }

  insertTemplate(id: TemplateId): void {
    if (this.readonly()) return;
    const c = this.viewCenter();
    const els = buildTemplate(id, (k) => this.i18n.translate(k)).map((e) => translate(e, c.x, c.y));
    this.snapshot();
    this.appendElements(els);
    this.selection.set(els.map((e) => e.id));
    this.scheduleEmit();
    setTimeout(() => this.fit(true));
  }

  async clearBoard(): Promise<void> {
    if (this.readonly() || !this.elements().length) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('board.clear.title'),
      message: this.i18n.translate('board.clear.message'),
      confirmLabel: this.i18n.translate('board.clear.confirm'),
      danger: true,
    });
    if (!ok) return;
    this.snapshot();
    this.setElements([]);
    this.selection.set([]);
    this.scheduleEmit();
  }

  // ---- keyboard ---------------------------------------------------------------------------------------------

  onKeyUp(event: KeyboardEvent): void {
    if (event.key === ' ') this.spaceDown.set(false);
  }

  onKey(event: KeyboardEvent): void {
    const target = event.target as HTMLElement | null;
    if (this.editing() || (target && ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))) return;
    const key = event.key.toLowerCase();
    const mod = event.ctrlKey || event.metaKey;
    if (key === ' ' && !mod) {
      event.preventDefault();
      this.spaceDown.set(true);
      return;
    }
    if (key === '?' || (key === '/' && event.shiftKey)) {
      this.helpOpen.update((v) => !v);
      return;
    }
    if (mod && key === 'a') {
      event.preventDefault();
      this.selectAll();
    } else if (mod && key === 'c') {
      this.copy();
    } else if (mod && key === 'x' && !this.readonly()) {
      event.preventDefault();
      this.cut();
    } else if (mod && key === 'z') {
      event.preventDefault();
      if (event.shiftKey) this.redo();
      else this.undo();
    } else if (mod && key === 'y') {
      event.preventDefault();
      this.redo();
    } else if (mod && key === 'd') {
      event.preventDefault();
      this.duplicate();
    } else if (mod && key === 'g') {
      event.preventDefault();
      if (event.shiftKey) this.ungroup();
      else this.group();
    } else if (mod && key === 's') {
      event.preventDefault();
      this.flush();
      this.saveRequested.emit();
    } else if (mod && (key === ']' || key === '[')) {
      event.preventDefault();
      this.order(key === ']' ? (event.shiftKey ? 'front' : 'forward') : event.shiftKey ? 'back' : 'backward');
    } else if (mod && key === 'l' && event.shiftKey) {
      event.preventDefault();
      this.toggleLock();
    } else if ((key === 'delete' || key === 'backspace') && this.selection().length) {
      event.preventDefault();
      this.removeSelected();
    } else if (key === 'enter' && this.single()) {
      event.preventDefault();
      const s = this.single()!;
      if (canHoldText(s)) this.startEdit(s.id);
    } else if (key === 'tab' && this.single() && !this.readonly()) {
      event.preventDefault();
      this.addConnected(event.shiftKey ? 'w' : 'e');
    } else if (key === 'escape') {
      this.selection.set([]);
      this.helpOpen.set(false);
      this.tool.set('select');
    } else if (key.startsWith('arrow') && this.selection().length && !this.readonly()) {
      event.preventDefault();
      const step = event.shiftKey ? (this.snapOn() ? GRID : 10) : 1;
      this.nudge(key === 'arrowleft' ? -step : key === 'arrowright' ? step : 0, key === 'arrowup' ? -step : key === 'arrowdown' ? step : 0);
    } else if (key === '+' || key === '=') {
      this.zoomBy(1.2);
    } else if (key === '-') {
      this.zoomBy(1 / 1.2);
    } else if (event.shiftKey && key === '!') {
      this.fit();
    } else if (event.shiftKey && key === '@') {
      this.fit(true);
    } else if (!mod && !event.altKey && HOTKEYS[key]) {
      this.setTool(HOTKEYS[key]);
    }
  }

  // ---- export / import --------------------------------------------------------------------------------------

  private exportIds(selectionOnly: boolean): string[] | undefined {
    return selectionOnly && this.selection().length ? this.selection() : undefined;
  }

  exportSvg(selectionOnly = false): void {
    const doc = buildSvg(this.board(), { ids: this.exportIds(selectionOnly) });
    if (!doc) {
      this.toast.info(this.i18n.translate('board.exportEmpty'));
      return;
    }
    this.download(new Blob([doc.markup], { type: 'image/svg+xml;charset=utf-8' }), 'whiteboard.svg');
  }

  private async renderPng(selectionOnly: boolean, scale: number, background: boolean): Promise<Blob | null> {
    const doc = buildSvg(this.board(), { ids: this.exportIds(selectionOnly), background });
    if (!doc) {
      this.toast.info(this.i18n.translate('board.exportEmpty'));
      return null;
    }
    const image = new Image();
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve();
      image.onerror = () => reject(new Error('svg'));
      image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(doc.markup);
    });
    const canvas = document.createElement('canvas');
    canvas.width = doc.width * scale;
    canvas.height = doc.height * scale;
    const ctx = canvas.getContext('2d');
    if (!ctx) return null;
    ctx.scale(scale, scale);
    ctx.drawImage(image, 0, 0);
    return new Promise((resolve) => canvas.toBlob((b) => resolve(b), 'image/png'));
  }

  async exportPng(selectionOnly = false, scale = 2, background = true): Promise<void> {
    try {
      const blob = await this.renderPng(selectionOnly, scale, background);
      if (blob) this.download(blob, 'whiteboard.png');
    } catch {
      this.toast.error(this.i18n.translate('board.exportError'));
    }
  }

  async copyPng(): Promise<void> {
    try {
      const blob = await this.renderPng(this.selection().length > 0, 2, true);
      if (!blob) return;
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
      this.toast.success(this.i18n.translate('board.copied'));
    } catch {
      this.toast.error(this.i18n.translate('board.exportError'));
    }
  }

  exportJson(): void {
    this.download(new Blob([serializeBoard(this.board())], { type: 'application/json' }), 'whiteboard.json');
  }

  pickJson(): void {
    this.jsonInput()?.nativeElement.click();
  }

  async onJsonPicked(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.readonly()) return;
    try {
      const imported = parseBoard(await file.text());
      if (!imported.elements.length) throw new Error('empty');
      this.pasteCount = 0;
      this.insertCopies(imported.elements);
      this.pasteCount = 0;
      setTimeout(() => this.fit());
    } catch {
      this.toast.error(this.i18n.translate('board.importError'));
    }
  }

  private download(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  toggleFullscreen(): void {
    const el = this.host().nativeElement;
    if (document.fullscreenElement) void document.exitFullscreen();
    else void el.requestFullscreen?.();
  }
}
