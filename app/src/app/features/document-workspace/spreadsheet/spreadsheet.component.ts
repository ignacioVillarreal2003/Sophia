import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { CellRange, CellAddress, cellKey, colToLetters, normalizeRange, parseCellKey, parseRangeKey, rangeLabel } from './cell-ref';
import { FUNCTION_NAMES, isError } from './formula';
import { NUMBER_FORMATS } from './number-format';
import {
  Cell,
  CellPatch,
  CellStyle,
  DEFAULT_COL_WIDTH,
  DEFAULT_ROW_HEIGHT,
  MAX_COLS,
  MAX_ROWS,
  Sheet,
  SheetEngine,
  Workbook,
  newSheet,
  parseWorkbook,
} from './sheet-engine';

export const ROW_HEADER_WIDTH = 52;
export const COL_HEADER_HEIGHT = 26;
const OVERSCAN = 4;
const PALETTE = [
  '#000000', '#434343', '#666666', '#999999', '#cccccc', '#efefef', '#f3f3f3', '#ffffff',
  '#d92d20', '#e8590c', '#f59f00', '#1a9d5b', '#0c8599', '#2d6cdf', '#6741d9', '#c2255c',
  '#fde8e8', '#ffe8cc', '#fff3bf', '#d3f9d8', '#c5f6fa', '#d0ebff', '#e5dbff', '#ffdeeb',
  '#f5a3a3', '#ffc078', '#ffe066', '#8ce99a', '#99e9f2', '#a5d8ff', '#d0bfff', '#fcc2d7',
];

interface Selection {
  anchor: CellAddress;
  focus: CellAddress;
}

interface EditState {
  row: number;
  col: number;
  text: string;
  /** True when the formula bar (not the cell) owns the caret. */
  fromBar: boolean;
}

type HistoryEntry =
  | { kind: 'cells'; sheetId: string; undo: CellPatch; redo: CellPatch }
  | { kind: 'workbook'; before: string; after: string };

interface CellView {
  readonly key: string;
  readonly row: number;
  readonly col: number;
  readonly x: number;
  readonly y: number;
  readonly w: number;
  readonly h: number;
  readonly text: string;
  readonly align: 'left' | 'center' | 'right';
  readonly style: Record<string, string>;
  readonly error: boolean;
  readonly match: boolean;
}

interface Clipboard {
  readonly data: (Cell | null)[][];
  readonly origin: { row: number; col: number };
  readonly text: string;
}

/**
 * Spreadsheet in the spirit of Google Sheets: sheets, A1 formulas with ~120 functions, formatting,
 * number/date formats, resizing, row/column operations, sorting, fill, find and replace, undo/redo,
 * clipboard with formula adjustment, and CSV/XLSX through the backend.
 */
@Component({
  selector: 'app-spreadsheet',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  templateUrl: './spreadsheet.component.html',
  styleUrl: './spreadsheet.component.scss',
})
export class SpreadsheetComponent {
  private readonly destroyRef = inject(DestroyRef);

  /** Workbook JSON. Replacing it from outside (restore, reload) reloads the grid. */
  readonly content = input.required<string>();
  readonly readonly = input(false);

  /** Emits the workbook JSON (with cached formula results) shortly after every change. */
  readonly contentChange = output<string>();
  readonly saveRequested = output<void>();

  private readonly viewport = viewChild<ElementRef<HTMLElement>>('viewport');
  private readonly cellEditor = viewChild<ElementRef<HTMLInputElement>>('cellEditor');
  private readonly barEditor = viewChild<ElementRef<HTMLInputElement>>('barEditor');

  protected readonly engine = new SheetEngine(parseWorkbook(null));
  readonly rev = signal(0);
  readonly activeId = signal('');
  readonly selection = signal<Selection>({ anchor: { row: 0, col: 0 }, focus: { row: 0, col: 0 } });
  readonly editing = signal<EditState | null>(null);
  readonly scroll = signal({ top: 0, left: 0, width: 800, height: 500 });
  readonly menu = signal<{ x: number; y: number; kind: 'cell' | 'col' | 'row'; index: number } | null>(null);
  readonly popover = signal<'format' | 'color' | 'fill' | 'sort' | null>(null);
  readonly renaming = signal<string | null>(null);
  readonly renameText = signal('');
  readonly findOpen = signal(false);
  readonly findText = signal('');
  readonly replaceText = signal('');
  readonly findIndex = signal(0);
  readonly dragFill = signal<{ source: CellRange; target: CellRange } | null>(null);
  readonly canUndo = signal(false);
  readonly canRedo = signal(false);

  readonly palette = PALETTE;
  readonly numberFormats = NUMBER_FORMATS;
  readonly rowHeaderWidth = ROW_HEADER_WIDTH;
  readonly colHeaderHeight = COL_HEADER_HEIGHT;

  private undoStack: HistoryEntry[] = [];
  private redoStack: HistoryEntry[] = [];
  private clipboard: Clipboard | null = null;
  private lastEmitted = '';
  private loadedFrom = '';
  private emitTimer: ReturnType<typeof setTimeout> | null = null;
  private dragging: 'select' | null = null;
  private resizing: { axis: 'col' | 'row'; index: number; start: number; size: number } | null = null;

  // ------------------------------------------------------------------
  // Derived state
  // ------------------------------------------------------------------

  readonly sheet = computed<Sheet>(() => {
    this.rev();
    const sheets = this.engine.workbook.sheets;
    return sheets.find((s) => s.id === this.activeId()) ?? sheets[0];
  });
  readonly sheets = computed(() => {
    this.rev();
    return this.engine.workbook.sheets.map((s) => ({ id: s.id, name: s.name }));
  });

  readonly colX = computed(() => {
    this.rev();
    const sheet = this.sheet();
    const out = [0];
    for (let c = 0; c < sheet.cols; c++) out.push(out[c] + (sheet.colWidths?.[c] ?? DEFAULT_COL_WIDTH));
    return out;
  });
  readonly rowY = computed(() => {
    this.rev();
    const sheet = this.sheet();
    const out = [0];
    for (let r = 0; r < sheet.rows; r++) out.push(out[r] + (sheet.rowHeights?.[r] ?? DEFAULT_ROW_HEIGHT));
    return out;
  });
  readonly totalWidth = computed(() => this.colX()[this.colX().length - 1]);
  readonly totalHeight = computed(() => this.rowY()[this.rowY().length - 1]);

  private readonly visibleCols = computed(() => this.visibleSpan(this.colX(), this.scroll().left, this.scroll().width - ROW_HEADER_WIDTH));
  private readonly visibleRows = computed(() => this.visibleSpan(this.rowY(), this.scroll().top, this.scroll().height - COL_HEADER_HEIGHT));

  /** Merged ranges keyed by their top-left cell, and the set of cells hidden underneath. */
  private readonly merges = computed(() => {
    this.rev();
    const anchors = new Map<string, CellRange>();
    const covered = new Set<string>();
    for (const text of this.sheet().merges ?? []) {
      const range = parseRangeKey(text);
      if (!range) continue;
      anchors.set(cellKey(range.r1, range.c1), range);
      for (let r = range.r1; r <= range.r2; r++) {
        for (let c = range.c1; c <= range.c2; c++) if (r !== range.r1 || c !== range.c1) covered.add(cellKey(r, c));
      }
    }
    return { anchors, covered };
  });

  readonly matches = computed(() => {
    this.rev();
    const needle = this.findText().toLowerCase();
    if (!this.findOpen() || !needle) return [] as string[];
    const sheet = this.sheet();
    return Object.keys(sheet.cells)
      .filter((key) => {
        const address = parseCellKey(key)!;
        return this.engine.display(sheet, address.row, address.col).toLowerCase().includes(needle);
      })
      .sort((a, b) => {
        const pa = parseCellKey(a)!;
        const pb = parseCellKey(b)!;
        return pa.row - pb.row || pa.col - pb.col;
      });
  });

  readonly cells = computed<CellView[]>(() => {
    this.rev();
    const sheet = this.sheet();
    const { first: r0, last: r1 } = this.visibleRows();
    const { first: c0, last: c1 } = this.visibleCols();
    const colX = this.colX();
    const rowY = this.rowY();
    const { anchors, covered } = this.merges();
    const matches = new Set(this.matches());
    const out: CellView[] = [];
    for (let r = r0; r <= r1; r++) {
      for (let c = c0; c <= c1; c++) {
        const key = cellKey(r, c);
        if (covered.has(key)) continue;
        const merge = anchors.get(key);
        const cell = sheet.cells[key];
        const width = merge ? colX[merge.c2 + 1] - colX[merge.c1] : colX[c + 1] - colX[c];
        const height = merge ? rowY[merge.r2 + 1] - rowY[merge.r1] : rowY[r + 1] - rowY[r];
        if (!cell && !merge && !matches.has(key)) {
          out.push({ key, row: r, col: c, x: colX[c], y: rowY[r], w: width, h: height, text: '', align: 'left', style: {}, error: false, match: false });
          continue;
        }
        const value = cell ? this.engine.value(sheet, r, c) : null;
        const text = cell ? this.engine.display(sheet, r, c) : '';
        const style = cell?.s ?? {};
        const isNumber = typeof value === 'number';
        const align = style.al ?? (isError(value) || typeof value === 'boolean' ? 'center' : isNumber ? 'right' : 'left');
        out.push({
          key,
          row: r,
          col: c,
          x: colX[c],
          y: rowY[r],
          w: width,
          h: height,
          text,
          align,
          error: isError(value),
          match: matches.has(key),
          style: this.cssFor(style),
        });
      }
    }
    return out;
  });

  readonly selectionRange = computed(() => {
    const s = this.selection();
    return normalizeRange(s.anchor, s.focus);
  });
  readonly activeCell = computed(() => this.selection().anchor);
  readonly activeLabel = computed(() => rangeLabel(this.selectionRange()));

  readonly selectionRect = computed(() => this.rectOf(this.selectionRange()));
  readonly activeRect = computed(() => {
    const a = this.activeCell();
    const merge = this.merges().anchors.get(cellKey(a.row, a.col));
    return this.rectOf(merge ?? { r1: a.row, c1: a.col, r2: a.row, c2: a.col });
  });
  readonly fillRect = computed(() => {
    const fill = this.dragFill();
    return fill ? this.rectOf(fill.target) : null;
  });

  readonly colHeaders = computed(() => {
    const { first, last } = this.visibleCols();
    const colX = this.colX();
    const sel = this.selectionRange();
    return Array.from({ length: last - first + 1 }, (_v, i) => {
      const c = first + i;
      return { index: c, label: colToLetters(c), x: colX[c], w: colX[c + 1] - colX[c], active: c >= sel.c1 && c <= sel.c2 };
    });
  });
  readonly rowHeaders = computed(() => {
    const { first, last } = this.visibleRows();
    const rowY = this.rowY();
    const sel = this.selectionRange();
    return Array.from({ length: last - first + 1 }, (_v, i) => {
      const r = first + i;
      return { index: r, label: String(r + 1), y: rowY[r], h: rowY[r + 1] - rowY[r], active: r >= sel.r1 && r <= sel.r2 };
    });
  });

  /** Style of the active cell, for the toolbar state. */
  readonly activeStyle = computed<CellStyle>(() => {
    this.rev();
    const a = this.activeCell();
    return this.sheet().cells[cellKey(a.row, a.col)]?.s ?? {};
  });
  readonly editText = computed(() => {
    const edit = this.editing();
    if (edit) return edit.text;
    this.rev();
    const a = this.activeCell();
    return this.engine.rawText(this.sheet(), a.row, a.col);
  });
  readonly summary = computed(() => {
    this.rev();
    const range = this.selectionRange();
    if (range.r1 === range.r2 && range.c1 === range.c2) return null;
    return this.engine.summarize(this.sheet(), range);
  });

  readonly suggestions = computed(() => {
    const edit = this.editing();
    if (!edit || !edit.text.startsWith('=')) return [] as string[];
    const match = /[A-Za-z][A-Za-z0-9.]*$/.exec(edit.text);
    if (!match || /^[A-Za-z]{1,3}\d*$/.test(match[0]) && /\d$/.test(match[0])) return [];
    const prefix = match[0].toUpperCase();
    return FUNCTION_NAMES.filter((name) => name.startsWith(prefix) && name !== prefix).slice(0, 6);
  });

  /** The input that currently owns the edit; only changes when the edit moves, not on every keystroke. */
  private readonly editorTarget = computed(() => {
    const edit = this.editing();
    if (!edit) return null;
    return (edit.fromBar ? this.barEditor() : this.cellEditor())?.nativeElement ?? null;
  });

  constructor() {
    // Focus the editor as soon as it exists (the view renders after the signal changes).
    effect(() => {
      const el = this.editorTarget();
      if (el) {
        el.focus();
        const end = el.value.length;
        el.setSelectionRange(end, end);
      }
    });
    // Load / reload the workbook when the content input changes from outside.
    effect(() => {
      const content = this.content();
      untracked(() => {
        if (content === this.lastEmitted || content === this.loadedFrom) return;
        this.loadedFrom = content;
        this.engine.load(parseWorkbook(content));
        this.activeId.set(this.engine.workbook.activeSheet ?? this.engine.workbook.sheets[0].id);
        this.selection.set({ anchor: { row: 0, col: 0 }, focus: { row: 0, col: 0 } });
        this.undoStack = [];
        this.redoStack = [];
        this.syncHistoryFlags();
        this.rev.update((n) => n + 1);
      });
    });
    this.destroyRef.onDestroy(() => {
      if (this.emitTimer) {
        clearTimeout(this.emitTimer);
        this.flush();
      }
    });
  }

  // ------------------------------------------------------------------
  // Geometry helpers
  // ------------------------------------------------------------------

  private visibleSpan(offsets: number[], start: number, size: number): { first: number; last: number } {
    const count = offsets.length - 1;
    const find = (position: number) => {
      let lo = 0;
      let hi = count - 1;
      while (lo < hi) {
        const mid = (lo + hi + 1) >> 1;
        if (offsets[mid] <= position) lo = mid;
        else hi = mid - 1;
      }
      return lo;
    };
    const first = Math.max(0, find(start) - OVERSCAN);
    const last = Math.min(count - 1, find(start + size) + OVERSCAN);
    return { first, last };
  }

  private rectOf(range: CellRange): { x: number; y: number; w: number; h: number } {
    const colX = this.colX();
    const rowY = this.rowY();
    return {
      x: colX[range.c1],
      y: rowY[range.r1],
      w: colX[Math.min(range.c2 + 1, colX.length - 1)] - colX[range.c1],
      h: rowY[Math.min(range.r2 + 1, rowY.length - 1)] - rowY[range.r1],
    };
  }

  private cssFor(style: CellStyle): Record<string, string> {
    const css: Record<string, string> = {};
    if (style.b) css['font-weight'] = '700';
    if (style.i) css['font-style'] = 'italic';
    const decorations = [style.u ? 'underline' : '', style.st ? 'line-through' : ''].filter(Boolean).join(' ');
    if (decorations) css['text-decoration'] = decorations;
    if (style.c) css['color'] = style.c;
    if (style.bg) css['background'] = style.bg;
    if (style.fs) css['font-size'] = `${style.fs}px`;
    if (style.wrap) {
      css['white-space'] = 'normal';
      css['overflow-wrap'] = 'anywhere';
    }
    if (style.bd) css['box-shadow'] = 'inset 0 0 0 1px var(--text-muted)';
    return css;
  }

  private hit(event: MouseEvent): CellAddress | null {
    const layer = this.viewport()?.nativeElement.querySelector('.ss__cells');
    if (!layer) return null;
    const rect = layer.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const colX = this.colX();
    const rowY = this.rowY();
    const find = (offsets: number[], position: number) => {
      if (position < 0) return 0;
      let lo = 0;
      let hi = offsets.length - 2;
      while (lo < hi) {
        const mid = (lo + hi + 1) >> 1;
        if (offsets[mid] <= position) lo = mid;
        else hi = mid - 1;
      }
      return lo;
    };
    return { row: find(rowY, y), col: find(colX, x) };
  }

  onScroll(event: Event): void {
    const el = event.target as HTMLElement;
    this.scroll.set({ top: el.scrollTop, left: el.scrollLeft, width: el.clientWidth, height: el.clientHeight });
    this.menu.set(null);
  }

  private measure(): void {
    const el = this.viewport()?.nativeElement;
    if (el) this.scroll.set({ top: el.scrollTop, left: el.scrollLeft, width: el.clientWidth, height: el.clientHeight });
  }

  ngAfterViewInit(): void {
    this.measure();
    new ResizeObserver(() => this.measure()).observe(this.viewport()!.nativeElement);
  }

  private ensureVisible(row: number, col: number): void {
    const el = this.viewport()?.nativeElement;
    if (!el) return;
    const colX = this.colX();
    const rowY = this.rowY();
    const x0 = colX[col];
    const x1 = colX[col + 1];
    const y0 = rowY[row];
    const y1 = rowY[row + 1];
    const viewW = el.clientWidth - ROW_HEADER_WIDTH;
    const viewH = el.clientHeight - COL_HEADER_HEIGHT;
    if (x0 < el.scrollLeft) el.scrollLeft = x0;
    else if (x1 > el.scrollLeft + viewW) el.scrollLeft = x1 - viewW;
    if (y0 < el.scrollTop) el.scrollTop = y0;
    else if (y1 > el.scrollTop + viewH) el.scrollTop = y1 - viewH;
  }

  // ------------------------------------------------------------------
  // Change tracking, history and emission
  // ------------------------------------------------------------------

  private touch(): void {
    this.engine.invalidate();
    this.rev.update((n) => n + 1);
    if (this.emitTimer) clearTimeout(this.emitTimer);
    this.emitTimer = setTimeout(() => this.flush(), 600);
  }

  /** Emits immediately when there are unsent changes. */
  flush(): void {
    if (this.emitTimer) {
      clearTimeout(this.emitTimer);
      this.emitTimer = null;
    }
    this.engine.workbook.activeSheet = this.activeId();
    this.engine.freezeResults();
    const json = JSON.stringify(this.engine.workbook);
    if (json === this.lastEmitted) return;
    this.lastEmitted = json;
    this.contentChange.emit(json);
  }

  private syncHistoryFlags(): void {
    this.canUndo.set(this.undoStack.length > 0);
    this.canRedo.set(this.redoStack.length > 0);
  }

  private push(entry: HistoryEntry): void {
    this.undoStack.push(entry);
    if (this.undoStack.length > 100) this.undoStack.shift();
    this.redoStack = [];
    this.syncHistoryFlags();
  }

  private commit(patch: CellPatch): void {
    if (this.readonly() || patch.size === 0) return;
    const sheet = this.sheet();
    const redo: CellPatch = new Map([...patch].map(([k, v]) => [k, v ? structuredClone(v) : null]));
    const undo = this.engine.apply(sheet, patch);
    this.push({ kind: 'cells', sheetId: sheet.id, undo, redo });
    this.touch();
  }

  /** Structural edits (rows, columns, sheets) are undone by restoring the whole workbook. */
  private structural(change: () => void): void {
    if (this.readonly()) return;
    const before = JSON.stringify(this.engine.workbook);
    change();
    const after = JSON.stringify(this.engine.workbook);
    this.push({ kind: 'workbook', before, after });
    this.touch();
  }

  undo(): void {
    const entry = this.undoStack.pop();
    if (!entry) return;
    this.redoStack.push(entry);
    this.replay(entry, 'undo');
    this.syncHistoryFlags();
  }

  redo(): void {
    const entry = this.redoStack.pop();
    if (!entry) return;
    this.undoStack.push(entry);
    this.replay(entry, 'redo');
    this.syncHistoryFlags();
  }

  private replay(entry: HistoryEntry, direction: 'undo' | 'redo'): void {
    if (entry.kind === 'workbook') {
      const wb = JSON.parse(direction === 'undo' ? entry.before : entry.after) as Workbook;
      this.engine.load(wb);
      if (!wb.sheets.some((s) => s.id === this.activeId())) this.activeId.set(wb.sheets[0].id);
    } else {
      const sheet = this.engine.workbook.sheets.find((s) => s.id === entry.sheetId);
      if (sheet) this.engine.apply(sheet, new Map(direction === 'undo' ? entry.undo : entry.redo));
    }
    this.touch();
  }

  // ------------------------------------------------------------------
  // Selection and navigation
  // ------------------------------------------------------------------

  select(row: number, col: number, extend = false): void {
    const sheet = this.sheet();
    row = Math.max(0, Math.min(row, MAX_ROWS - 1));
    col = Math.max(0, Math.min(col, MAX_COLS - 1));
    if (row >= sheet.rows - 3) sheet.rows = Math.min(MAX_ROWS, row + 50);
    if (col >= sheet.cols - 1) sheet.cols = Math.min(MAX_COLS, col + 6);
    this.selection.update((s) => (extend ? { anchor: s.anchor, focus: { row, col } } : { anchor: { row, col }, focus: { row, col } }));
    this.rev.update((n) => n + 1);
    this.ensureVisible(row, col);
  }

  private move(dRow: number, dCol: number, extend: boolean, jump = false): void {
    const sel = this.selection();
    const from = extend ? sel.focus : sel.anchor;
    let row = from.row;
    let col = from.col;
    if (jump) {
      const sheet = this.sheet();
      const filled = (r: number, c: number) => !!sheet.cells[cellKey(r, c)] && (sheet.cells[cellKey(r, c)].v !== undefined || sheet.cells[cellKey(r, c)].f !== undefined);
      const extent = this.engine.extent(sheet);
      const step = (r: number, c: number) => ({ r: r + dRow, c: c + dCol });
      let next = step(row, col);
      const inside = (r: number, c: number) => r >= 0 && c >= 0 && r <= Math.max(extent.row, 0) + 1 && c <= Math.max(extent.col, 0) + 1;
      if (!inside(next.r, next.c)) {
        next = { r: Math.max(0, Math.min(extent.row, next.r)), c: Math.max(0, Math.min(extent.col, next.c)) };
        row = dRow !== 0 ? (dRow < 0 ? 0 : extent.row) : row;
        col = dCol !== 0 ? (dCol < 0 ? 0 : extent.col) : col;
      } else if (filled(row, col) && filled(next.r, next.c)) {
        while (inside(next.r, next.c) && filled(next.r, next.c)) {
          row = next.r;
          col = next.c;
          next = step(row, col);
        }
      } else {
        row = next.r;
        col = next.c;
        while (inside(row, col) && !filled(row, col)) {
          const after = step(row, col);
          if (!inside(after.r, after.c)) break;
          row = after.r;
          col = after.c;
        }
      }
    } else {
      row += dRow;
      col += dCol;
    }
    this.select(Math.max(0, row), Math.max(0, col), extend);
  }

  // ------------------------------------------------------------------
  // Mouse
  // ------------------------------------------------------------------

  onMouseDown(event: MouseEvent): void {
    if (event.button !== 0) return;
    this.menu.set(null);
    this.popover.set(null);
    const target = event.target as HTMLElement;
    if (target.closest('.ss__editor')) return;
    const address = this.hit(event);
    if (!address) return;

    const editing = this.editing();
    if (editing && editing.text.startsWith('=') && /[=(,+\-*/^&<>:;]$/.test(editing.text)) {
      event.preventDefault();
      this.insertReference(cellKey(address.row, address.col));
      return;
    }
    if (editing) this.commitEdit();
    this.viewport()?.nativeElement.focus({ preventScroll: true });
    this.select(address.row, address.col, event.shiftKey);
    this.dragging = 'select';
  }

  onDoubleClick(event: MouseEvent): void {
    const address = this.hit(event);
    if (address && !this.readonly()) this.startEdit(address.row, address.col, null);
  }

  onContextMenu(event: MouseEvent): void {
    event.preventDefault();
    const address = this.hit(event);
    if (!address) return;
    const range = this.selectionRange();
    const inside = address.row >= range.r1 && address.row <= range.r2 && address.col >= range.c1 && address.col <= range.c2;
    if (!inside) this.select(address.row, address.col);
    this.menu.set({ x: event.clientX, y: event.clientY, kind: 'cell', index: 0 });
  }

  headerMenu(event: MouseEvent, kind: 'col' | 'row', index: number): void {
    event.preventDefault();
    const range = this.selectionRange();
    const inside = kind === 'col' ? index >= range.c1 && index <= range.c2 : index >= range.r1 && index <= range.r2;
    if (!inside) this.selectHeader(kind, index, false);
    this.menu.set({ x: event.clientX, y: event.clientY, kind, index });
  }

  selectHeader(kind: 'col' | 'row', index: number, extend: boolean): void {
    this.commitEdit();
    const sheet = this.sheet();
    this.viewport()?.nativeElement.focus({ preventScroll: true });
    if (kind === 'col') {
      const anchor = extend ? this.selection().anchor.col : index;
      this.selection.set({ anchor: { row: 0, col: anchor }, focus: { row: sheet.rows - 1, col: index } });
    } else {
      const anchor = extend ? this.selection().anchor.row : index;
      this.selection.set({ anchor: { row: anchor, col: 0 }, focus: { row: index, col: sheet.cols - 1 } });
    }
    this.rev.update((n) => n + 1);
  }

  selectAll(): void {
    const sheet = this.sheet();
    this.selection.set({ anchor: { row: 0, col: 0 }, focus: { row: sheet.rows - 1, col: sheet.cols - 1 } });
    this.rev.update((n) => n + 1);
  }

  startResize(event: MouseEvent, axis: 'col' | 'row', index: number): void {
    event.preventDefault();
    event.stopPropagation();
    const sheet = this.sheet();
    const size = axis === 'col' ? (sheet.colWidths?.[index] ?? DEFAULT_COL_WIDTH) : (sheet.rowHeights?.[index] ?? DEFAULT_ROW_HEIGHT);
    this.resizing = { axis, index, start: axis === 'col' ? event.clientX : event.clientY, size };
  }

  startFill(event: MouseEvent): void {
    if (this.readonly()) return;
    event.preventDefault();
    event.stopPropagation();
    const source = this.selectionRange();
    this.dragFill.set({ source, target: source });
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent): void {
    if (this.resizing) {
      const { axis, index, start, size } = this.resizing;
      const delta = (axis === 'col' ? event.clientX : event.clientY) - start;
      const sheet = this.sheet();
      const next = Math.max(axis === 'col' ? 28 : 16, Math.round(size + delta));
      if (axis === 'col') (sheet.colWidths ??= {})[index] = next;
      else (sheet.rowHeights ??= {})[index] = next;
      this.touch();
      return;
    }
    const fill = this.dragFill();
    if (fill) {
      const address = this.hit(event);
      if (!address) return;
      const { source } = fill;
      const dRow = address.row > source.r2 ? address.row - source.r2 : address.row < source.r1 ? address.row - source.r1 : 0;
      const dCol = address.col > source.c2 ? address.col - source.c2 : address.col < source.c1 ? address.col - source.c1 : 0;
      let target = source;
      if (Math.abs(dRow) >= Math.abs(dCol) && dRow !== 0) {
        target = { ...source, r1: dRow < 0 ? address.row : source.r1, r2: dRow > 0 ? address.row : source.r2 };
      } else if (dCol !== 0) {
        target = { ...source, c1: dCol < 0 ? address.col : source.c1, c2: dCol > 0 ? address.col : source.c2 };
      }
      this.dragFill.set({ source, target });
      return;
    }
    if (this.dragging === 'select') {
      const address = this.hit(event);
      if (address) this.select(address.row, address.col, true);
    }
  }

  @HostListener('document:mouseup')
  onMouseUp(): void {
    this.dragging = null;
    if (this.resizing) {
      this.resizing = null;
      return;
    }
    const fill = this.dragFill();
    if (fill) {
      this.dragFill.set(null);
      const { source, target } = fill;
      if (target.r1 !== source.r1 || target.r2 !== source.r2 || target.c1 !== source.c1 || target.c2 !== source.c2) {
        this.commit(this.engine.fillPatch(this.sheet(), source, target));
        this.selection.set({ anchor: { row: target.r1, col: target.c1 }, focus: { row: target.r2, col: target.c2 } });
      }
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.ss__popover, .ss__menu, .ss__tool--menu')) {
      this.popover.set(null);
      this.menu.set(null);
    }
  }

  // ------------------------------------------------------------------
  // Keyboard
  // ------------------------------------------------------------------

  onKeydown(event: KeyboardEvent): void {
    if (this.editing()) return;
    const mod = event.ctrlKey || event.metaKey;
    const key = event.key;

    if (mod) {
      switch (key.toLowerCase()) {
        case 'z':
          event.preventDefault();
          if (event.shiftKey) this.redo();
          else this.undo();
          return;
        case 'y':
          event.preventDefault();
          this.redo();
          return;
        case 'b':
          event.preventDefault();
          this.toggleStyle('b');
          return;
        case 'i':
          event.preventDefault();
          this.toggleStyle('i');
          return;
        case 'u':
          event.preventDefault();
          this.toggleStyle('u');
          return;
        case 'a':
          event.preventDefault();
          this.selectAll();
          return;
        case 'd':
          event.preventDefault();
          this.fillDirection('down');
          return;
        case 'r':
          event.preventDefault();
          this.fillDirection('right');
          return;
        case 'f':
        case 'h':
          event.preventDefault();
          this.openFind();
          return;
        case 's':
          event.preventDefault();
          this.saveRequested.emit();
          return;
        default:
      }
    }

    switch (key) {
      case 'ArrowUp':
        event.preventDefault();
        this.move(-1, 0, event.shiftKey, mod);
        return;
      case 'ArrowDown':
        event.preventDefault();
        this.move(1, 0, event.shiftKey, mod);
        return;
      case 'ArrowLeft':
        event.preventDefault();
        this.move(0, -1, event.shiftKey, mod);
        return;
      case 'ArrowRight':
        event.preventDefault();
        this.move(0, 1, event.shiftKey, mod);
        return;
      case 'Tab':
        event.preventDefault();
        this.move(0, event.shiftKey ? -1 : 1, false);
        return;
      case 'Enter':
        event.preventDefault();
        if (this.readonly()) this.move(event.shiftKey ? -1 : 1, 0, false);
        else this.startEdit(this.activeCell().row, this.activeCell().col, null);
        return;
      case 'F2':
        event.preventDefault();
        this.startEdit(this.activeCell().row, this.activeCell().col, null);
        return;
      case 'Home':
        event.preventDefault();
        this.select(mod ? 0 : this.activeCell().row, 0, event.shiftKey);
        return;
      case 'End': {
        event.preventDefault();
        const extent = this.engine.extent(this.sheet());
        this.select(mod ? extent.row : this.activeCell().row, extent.col, event.shiftKey);
        return;
      }
      case 'PageDown':
        event.preventDefault();
        this.move(20, 0, event.shiftKey);
        return;
      case 'PageUp':
        event.preventDefault();
        this.move(-20, 0, event.shiftKey);
        return;
      case 'Delete':
      case 'Backspace':
        event.preventDefault();
        this.clearSelection();
        return;
      case 'Escape':
        this.menu.set(null);
        this.popover.set(null);
        return;
      default:
    }
    if (key.length === 1 && !mod && !event.altKey && !this.readonly()) {
      event.preventDefault();
      this.startEdit(this.activeCell().row, this.activeCell().col, key);
    }
  }

  // ------------------------------------------------------------------
  // Editing
  // ------------------------------------------------------------------

  startEdit(row: number, col: number, initial: string | null, fromBar = false): void {
    if (this.readonly()) return;
    this.select(row, col);
    const text = initial ?? this.engine.rawText(this.sheet(), row, col);
    this.editing.set({ row, col, text, fromBar });
  }

  onEditInput(event: Event): void {
    const edit = this.editing();
    if (edit) this.editing.set({ ...edit, text: (event.target as HTMLInputElement).value });
  }

  /** The formula bar mirrors the active cell; typing in it starts an edit. */
  onBarFocus(): void {
    if (!this.editing() && !this.readonly()) {
      const a = this.activeCell();
      this.startEdit(a.row, a.col, null, true);
    }
  }

  onEditKeydown(event: KeyboardEvent): void {
    const edit = this.editing();
    if (!edit) return;
    event.stopPropagation();
    if (event.key === 'Enter') {
      event.preventDefault();
      this.commitEdit();
      this.move(event.shiftKey ? -1 : 1, 0, false);
      this.viewport()?.nativeElement.focus({ preventScroll: true });
    } else if (event.key === 'Tab') {
      event.preventDefault();
      const suggestion = this.suggestions()[0];
      if (suggestion) {
        this.acceptSuggestion(suggestion);
        return;
      }
      this.commitEdit();
      this.move(0, event.shiftKey ? -1 : 1, false);
      this.viewport()?.nativeElement.focus({ preventScroll: true });
    } else if (event.key === 'Escape') {
      event.preventDefault();
      this.editing.set(null);
      this.viewport()?.nativeElement.focus({ preventScroll: true });
    } else if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
      event.preventDefault();
      this.commitEdit();
      this.saveRequested.emit();
    }
  }

  commitEdit(): void {
    const edit = this.editing();
    if (!edit) return;
    this.editing.set(null);
    const sheet = this.sheet();
    const key = cellKey(edit.row, edit.col);
    const previous = sheet.cells[key];
    const before = this.engine.rawText(sheet, edit.row, edit.col);
    if (before === edit.text) return;
    const cell = this.engine.parseInput(edit.text, previous);
    this.commit(new Map([[key, cell]]));
  }

  acceptSuggestion(name: string): void {
    const edit = this.editing();
    if (!edit) return;
    const text = edit.text.replace(/[A-Za-z][A-Za-z0-9.]*$/, `${name}(`);
    this.editing.set({ ...edit, text });
    setTimeout(() => (edit.fromBar ? this.barEditor() : this.cellEditor())?.nativeElement.focus());
  }

  private insertReference(ref: string): void {
    const edit = this.editing();
    if (!edit) return;
    this.editing.set({ ...edit, text: edit.text + ref });
    setTimeout(() => (edit.fromBar ? this.barEditor() : this.cellEditor())?.nativeElement.focus());
  }

  goToLabel(text: string): void {
    const range = parseRangeKey(text.trim().toUpperCase());
    if (!range) return;
    this.select(range.r1, range.c1);
    this.selection.set({ anchor: { row: range.r1, col: range.c1 }, focus: { row: range.r2, col: range.c2 } });
    this.ensureVisible(range.r1, range.c1);
    this.viewport()?.nativeElement.focus();
  }

  clearSelection(): void {
    const range = this.selectionRange();
    const sheet = this.sheet();
    const patch: CellPatch = new Map();
    for (let r = range.r1; r <= range.r2; r++) {
      for (let c = range.c1; c <= range.c2; c++) {
        const key = cellKey(r, c);
        const cell = sheet.cells[key];
        if (cell) patch.set(key, cell.s ? { s: cell.s } : null);
      }
    }
    this.commit(patch);
  }

  clearFormatting(): void {
    this.mapSelection((cell) => {
      const next = { ...cell };
      delete next.s;
      return next;
    });
  }

  // ------------------------------------------------------------------
  // Clipboard
  // ------------------------------------------------------------------

  onCopy(event: ClipboardEvent, cut = false): void {
    if (this.editing()) return;
    event.preventDefault();
    const range = this.selectionRange();
    const sheet = this.sheet();
    const rows: string[] = [];
    for (let r = range.r1; r <= range.r2; r++) {
      const cols: string[] = [];
      for (let c = range.c1; c <= range.c2; c++) cols.push(this.engine.display(sheet, r, c));
      rows.push(cols.join('\t'));
    }
    const text = rows.join('\n');
    this.clipboard = { data: this.engine.snapshot(sheet, range), origin: { row: range.r1, col: range.c1 }, text };
    event.clipboardData?.setData('text/plain', text);
    if (cut) this.clearSelection();
  }

  onPaste(event: ClipboardEvent): void {
    if (this.editing() || this.readonly()) return;
    event.preventDefault();
    const text = event.clipboardData?.getData('text/plain') ?? '';
    const target = this.selectionRange();
    const sheet = this.sheet();
    if (this.clipboard && text === this.clipboard.text) {
      this.commit(this.engine.pastePatch(this.clipboard.data, this.clipboard.origin, target.r1, target.c1));
      const rows = this.clipboard.data.length;
      const cols = this.clipboard.data[0]?.length ?? 1;
      this.selection.set({ anchor: { row: target.r1, col: target.c1 }, focus: { row: target.r1 + rows - 1, col: target.c1 + cols - 1 } });
    } else if (text) {
      const patch = this.engine.textPatch(text, target.r1, target.c1, sheet);
      this.commit(patch);
      const lines = text.replace(/\n$/, '').split(/\r?\n/);
      this.selection.set({
        anchor: { row: target.r1, col: target.c1 },
        focus: { row: target.r1 + lines.length - 1, col: target.c1 + Math.max(...lines.map((l) => l.split('\t').length)) - 1 },
      });
    }
  }

  fillDirection(direction: 'down' | 'right'): void {
    const range = this.selectionRange();
    if (direction === 'down' && range.r2 > range.r1) {
      this.commit(this.engine.fillPatch(this.sheet(), { ...range, r2: range.r1 }, range));
    } else if (direction === 'right' && range.c2 > range.c1) {
      this.commit(this.engine.fillPatch(this.sheet(), { ...range, c2: range.c1 }, range));
    }
  }

  // ------------------------------------------------------------------
  // Formatting
  // ------------------------------------------------------------------

  private mapSelection(fn: (cell: Cell, row: number, col: number) => Cell | null): void {
    const range = this.selectionRange();
    const sheet = this.sheet();
    const patch: CellPatch = new Map();
    for (let r = range.r1; r <= range.r2; r++) {
      for (let c = range.c1; c <= range.c2; c++) {
        const key = cellKey(r, c);
        patch.set(key, fn(structuredClone(sheet.cells[key] ?? {}), r, c));
      }
    }
    this.commit(patch);
  }

  setStyle(change: Partial<CellStyle>): void {
    this.mapSelection((cell) => {
      const style: CellStyle = { ...cell.s, ...change };
      for (const key of Object.keys(style) as (keyof CellStyle)[]) {
        if (style[key] === undefined || style[key] === false || style[key] === '') delete style[key];
      }
      return { ...cell, s: style };
    });
  }

  toggleStyle(key: 'b' | 'i' | 'u' | 'st' | 'wrap' | 'bd'): void {
    this.setStyle({ [key]: !this.activeStyle()[key] } as Partial<CellStyle>);
  }

  setFormat(format: string): void {
    this.setStyle({ nf: format === 'General' ? undefined : format });
    this.popover.set(null);
  }

  adjustDecimals(delta: number): void {
    const current = this.activeStyle().nf ?? 'General';
    const base = current === 'General' ? '0' : current;
    const match = /^(.*?)(?:\.(0+))?([^0#.]*)$/.exec(base);
    const decimals = Math.max(0, (match?.[2]?.length ?? 0) + delta);
    const integerPart = match?.[1] ?? '0';
    const suffix = match?.[3] ?? '';
    this.setStyle({ nf: `${integerPart}${decimals ? '.' + '0'.repeat(decimals) : ''}${suffix}` });
  }

  setColor(kind: 'c' | 'bg', color: string | undefined): void {
    this.setStyle({ [kind]: color } as Partial<CellStyle>);
    this.popover.set(null);
  }

  setFontSize(value: string): void {
    this.setStyle({ fs: Number(value) || undefined });
  }

  toggleMerge(): void {
    const range = this.selectionRange();
    const sheet = this.sheet();
    const text = rangeLabel(range);
    const merges = sheet.merges ?? [];
    this.structural(() => {
      const existing = merges.findIndex((m) => m.split(':')[0] === cellKey(range.r1, range.c1));
      if (existing >= 0) {
        merges.splice(existing, 1);
      } else if (range.r1 !== range.r2 || range.c1 !== range.c2) {
        for (let r = range.r1; r <= range.r2; r++) {
          for (let c = range.c1; c <= range.c2; c++) {
            if (r !== range.r1 || c !== range.c1) delete sheet.cells[cellKey(r, c)];
          }
        }
        merges.push(text);
      }
      sheet.merges = merges;
    });
  }

  // ------------------------------------------------------------------
  // Rows, columns, sheets
  // ------------------------------------------------------------------

  insertRows(where: 'above' | 'below'): void {
    const range = this.selectionRange();
    const count = range.r2 - range.r1 + 1;
    const index = where === 'above' ? range.r1 : range.r2 + 1;
    this.structural(() => this.engine.insert(this.sheet(), 'row', index, count));
    this.menu.set(null);
  }

  insertCols(where: 'left' | 'right'): void {
    const range = this.selectionRange();
    const count = range.c2 - range.c1 + 1;
    const index = where === 'left' ? range.c1 : range.c2 + 1;
    this.structural(() => this.engine.insert(this.sheet(), 'col', index, count));
    this.menu.set(null);
  }

  deleteRows(): void {
    const range = this.selectionRange();
    this.structural(() => this.engine.remove(this.sheet(), 'row', range.r1, range.r2 - range.r1 + 1));
    this.menu.set(null);
  }

  deleteCols(): void {
    const range = this.selectionRange();
    this.structural(() => this.engine.remove(this.sheet(), 'col', range.c1, range.c2 - range.c1 + 1));
    this.menu.set(null);
  }

  sortBy(ascending: boolean): void {
    const range = this.selectionRange();
    const sheet = this.sheet();
    const single = range.r1 === range.r2 && range.c1 === range.c2;
    const extent = this.engine.extent(sheet);
    const area: CellRange = single ? { r1: 0, c1: 0, r2: extent.row, c2: extent.col } : range;
    const header = this.looksLikeHeader(sheet, area);
    this.commit(this.engine.sort(sheet, area, this.activeCell().col, ascending, header));
    this.popover.set(null);
    this.menu.set(null);
  }

  private looksLikeHeader(sheet: Sheet, area: CellRange): boolean {
    if (area.r2 <= area.r1) return false;
    const textOnly = (r: number) => {
      for (let c = area.c1; c <= area.c2; c++) {
        const value = this.engine.value(sheet, r, c);
        if (typeof value === 'number') return false;
      }
      return true;
    };
    let secondHasNumbers = false;
    for (let c = area.c1; c <= area.c2; c++) {
      if (typeof this.engine.value(sheet, area.r1 + 1, c) === 'number') secondHasNumbers = true;
    }
    return textOnly(area.r1) && secondHasNumbers;
  }

  addSheet(): void {
    let n = this.engine.workbook.sheets.length + 1;
    while (this.engine.sheetByName(`Sheet${n}`)) n++;
    const sheet = newSheet(`Sheet${n}`);
    this.structural(() => this.engine.workbook.sheets.push(sheet));
    this.switchSheet(sheet.id);
  }

  switchSheet(id: string): void {
    this.commitEdit();
    this.activeId.set(id);
    this.selection.set({ anchor: { row: 0, col: 0 }, focus: { row: 0, col: 0 } });
    this.rev.update((n) => n + 1);
    const el = this.viewport()?.nativeElement;
    if (el) {
      el.scrollTop = 0;
      el.scrollLeft = 0;
    }
  }

  startRename(id: string, name: string): void {
    if (this.readonly()) return;
    this.renaming.set(id);
    this.renameText.set(name);
    setTimeout(() => document.querySelector<HTMLInputElement>('.ss__tab-input')?.select());
  }

  finishRename(): void {
    const id = this.renaming();
    this.renaming.set(null);
    if (!id) return;
    const name = this.renameText().trim().slice(0, 31);
    const sheet = this.engine.workbook.sheets.find((s) => s.id === id);
    if (!sheet || !name || name === sheet.name) return;
    const clash = this.engine.workbook.sheets.some((s) => s !== sheet && s.name.toLowerCase() === name.toLowerCase());
    if (clash) return;
    this.structural(() => this.engine.renameSheet(sheet, name));
  }

  duplicateSheet(id: string): void {
    const source = this.engine.workbook.sheets.find((s) => s.id === id);
    if (!source) return;
    const copy: Sheet = structuredClone(source);
    copy.id = Math.random().toString(36).slice(2, 10);
    let name = `${source.name} (copy)`;
    let n = 2;
    while (this.engine.sheetByName(name)) name = `${source.name} (copy ${n++})`;
    copy.name = name.slice(0, 31);
    this.structural(() => this.engine.workbook.sheets.splice(this.engine.workbook.sheets.indexOf(source) + 1, 0, copy));
    this.switchSheet(copy.id);
  }

  deleteSheet(id: string): void {
    const sheets = this.engine.workbook.sheets;
    if (sheets.length <= 1) return;
    const index = sheets.findIndex((s) => s.id === id);
    if (index < 0) return;
    this.structural(() => sheets.splice(index, 1));
    if (this.activeId() === id) this.switchSheet(sheets[Math.max(0, index - 1)].id);
  }

  // ------------------------------------------------------------------
  // Find and replace
  // ------------------------------------------------------------------

  openFind(): void {
    this.findOpen.set(true);
    setTimeout(() => document.querySelector<HTMLInputElement>('.ss__find-input')?.focus());
  }

  closeFind(): void {
    this.findOpen.set(false);
    this.viewport()?.nativeElement.focus();
  }

  findStep(delta: number): void {
    const matches = this.matches();
    if (!matches.length) return;
    const index = (this.findIndex() + delta + matches.length) % matches.length;
    this.findIndex.set(index);
    const address = parseCellKey(matches[index])!;
    this.select(address.row, address.col);
  }

  replaceAll(): void {
    const needle = this.findText();
    if (!needle || this.readonly()) return;
    const sheet = this.sheet();
    const patch: CellPatch = new Map();
    for (const key of this.matches()) {
      const address = parseCellKey(key)!;
      const raw = this.engine.rawText(sheet, address.row, address.col);
      const regex = new RegExp(needle.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi');
      patch.set(key, this.engine.parseInput(raw.replace(regex, this.replaceText()), sheet.cells[key]));
    }
    this.commit(patch);
  }

  // ------------------------------------------------------------------
  // Template helpers
  // ------------------------------------------------------------------

  trackKey(_index: number, item: { key: string }): string {
    return item.key;
  }

  formatLabel(): string {
    const nf = this.activeStyle().nf ?? 'General';
    return this.numberFormats.find((f) => f.format === nf)?.label ?? 'Custom';
  }

  /** Whether the active cell is the top-left of a merge (toolbar state). */
  readonly merged = computed(() => {
    const a = this.activeCell();
    return this.merges().anchors.has(cellKey(a.row, a.col));
  });
}
