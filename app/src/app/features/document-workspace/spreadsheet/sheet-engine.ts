import { CellRange, cellKey, parseCellKey } from './cell-ref';
import {
  ERR,
  EvalContext,
  Scalar,
  Value,
  adjustForStructureChange,
  evaluateFormula,
  isError,
  renameSheetInFormula,
  shiftFormula,
  toNumber,
} from './formula';
import { dateToSerial, formatValue, isDateFormat, serialToDate } from './number-format';

export interface CellStyle {
  b?: boolean;
  i?: boolean;
  u?: boolean;
  st?: boolean;
  al?: 'left' | 'center' | 'right';
  c?: string;
  bg?: string;
  nf?: string;
  wrap?: boolean;
  fs?: number;
  bd?: boolean;
}

export interface Cell {
  v?: Scalar;
  f?: string;
  s?: CellStyle;
}

export interface Sheet {
  id: string;
  name: string;
  rows: number;
  cols: number;
  cells: Record<string, Cell>;
  colWidths?: Record<string, number>;
  rowHeights?: Record<string, number>;
  frozenRows?: number;
  frozenCols?: number;
  merges?: string[];
}

export interface Workbook {
  version: number;
  sheets: Sheet[];
  activeSheet?: string;
}

export const DEFAULT_COL_WIDTH = 104;
export const DEFAULT_ROW_HEIGHT = 26;
export const MAX_ROWS = 100_000;
export const MAX_COLS = 702;

export function newSheet(name: string): Sheet {
  return { id: Math.random().toString(36).slice(2, 10), name, rows: 100, cols: 26, cells: {} };
}

export function emptyWorkbook(): Workbook {
  const sheet = newSheet('Sheet1');
  return { version: 1, sheets: [sheet], activeSheet: sheet.id };
}

export function parseWorkbook(json: string | null | undefined): Workbook {
  if (!json || !json.trim()) return emptyWorkbook();
  try {
    const parsed = JSON.parse(json) as Workbook;
    if (!parsed || !Array.isArray(parsed.sheets) || parsed.sheets.length === 0) return emptyWorkbook();
    for (const sheet of parsed.sheets) {
      sheet.cells ??= {};
      sheet.rows = Math.max(sheet.rows ?? 100, 20);
      sheet.cols = Math.max(sheet.cols ?? 26, 10);
      sheet.id ??= Math.random().toString(36).slice(2, 10);
    }
    parsed.activeSheet ??= parsed.sheets[0].id;
    return parsed;
  } catch {
    return emptyWorkbook();
  }
}

/** A patch of cells: `null` removes the cell. */
export type CellPatch = Map<string, Cell | null>;

const NUMBER_RE = /^[-+]?(\d{1,3}(,\d{3})+|\d+)(\.\d+)?([eE][-+]?\d+)?$/;
const ISO_DATE_RE = /^(\d{4})-(\d{1,2})-(\d{1,2})$/;
const SLASH_DATE_RE = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/;

/**
 * Evaluation and editing logic of a workbook. The workbook object is mutated in place; every mutation
 * calls {@link invalidate} so cached formula results are recomputed lazily.
 */
export class SheetEngine {
  private cache = new Map<string, Value>();
  private evaluating = new Set<string>();

  constructor(public workbook: Workbook) {}

  load(workbook: Workbook): void {
    this.workbook = workbook;
    this.invalidate();
  }

  invalidate(): void {
    this.cache.clear();
  }

  sheetByName(name: string): Sheet | undefined {
    const lower = name.toLowerCase();
    return this.workbook.sheets.find((s) => s.name.toLowerCase() === lower);
  }

  // ------------------------------------------------------------------
  // Evaluation
  // ------------------------------------------------------------------

  value(sheet: Sheet, row: number, col: number): Value {
    const key = cellKey(row, col);
    const cell = sheet.cells[key];
    if (!cell) return null;
    if (cell.f === undefined) return cell.v ?? null;

    const cacheKey = `${sheet.id}!${key}`;
    const cached = this.cache.get(cacheKey);
    if (cached !== undefined) return cached;
    if (this.evaluating.has(cacheKey)) return ERR.circ;

    this.evaluating.add(cacheKey);
    let result: Value;
    try {
      result = evaluateFormula(cell.f, this.context(sheet, row, col));
    } finally {
      this.evaluating.delete(cacheKey);
    }
    this.cache.set(cacheKey, result);
    return result;
  }

  private context(sheet: Sheet, row: number, col: number): EvalContext {
    return {
      sheet: sheet.name,
      origin: { row, col },
      cell: (sheetName, r, c) => {
        const target = sheetName === sheet.name ? sheet : this.sheetByName(sheetName);
        return target ? this.value(target, r, c) : ERR.ref;
      },
      lastRow: (sheetName) => this.extent(this.sheetByName(sheetName) ?? sheet).row,
      lastCol: (sheetName) => this.extent(this.sheetByName(sheetName) ?? sheet).col,
    };
  }

  /** Last row/column (zero-based) that holds a value. */
  extent(sheet: Sheet): { row: number; col: number } {
    let row = 0;
    let col = 0;
    for (const key of Object.keys(sheet.cells)) {
      const cell = sheet.cells[key];
      if (cell.v === undefined && cell.f === undefined) continue;
      const address = parseCellKey(key);
      if (address) {
        row = Math.max(row, address.row);
        col = Math.max(col, address.col);
      }
    }
    return { row, col };
  }

  display(sheet: Sheet, row: number, col: number): string {
    const cell = sheet.cells[cellKey(row, col)];
    if (!cell) return '';
    const value = this.value(sheet, row, col);
    if (isError(value)) return value.code;
    return formatValue(value, cell.s?.nf);
  }

  /** The text shown in the formula bar / cell editor. */
  rawText(sheet: Sheet, row: number, col: number): string {
    const cell = sheet.cells[cellKey(row, col)];
    if (!cell) return '';
    if (cell.f !== undefined) return cell.f;
    if (cell.v === undefined || cell.v === null) return '';
    if (typeof cell.v === 'number' && cell.s?.nf && isDateFormat(cell.s.nf)) {
      return formatValue(cell.v, cell.s.nf.includes(':') ? 'yyyy-mm-dd hh:mm' : 'yyyy-mm-dd');
    }
    if (typeof cell.v === 'boolean') return cell.v ? 'TRUE' : 'FALSE';
    return String(cell.v);
  }

  /** Stores cached results next to every formula so exports and search see values. */
  freezeResults(): void {
    for (const sheet of this.workbook.sheets) {
      for (const [key, cell] of Object.entries(sheet.cells)) {
        if (cell.f === undefined) continue;
        const address = parseCellKey(key)!;
        const value = this.value(sheet, address.row, address.col);
        cell.v = isError(value) ? value.code : value;
      }
    }
  }

  // ------------------------------------------------------------------
  // User input
  // ------------------------------------------------------------------

  /** Interprets what the user typed, returning the cell to store (or null to clear). */
  parseInput(text: string, previous?: Cell): Cell | null {
    const style = previous?.s;
    if (text === '') return style ? { s: style } : null;
    if (text.startsWith('=') && text.length > 1) return { f: text, ...(style ? { s: style } : {}) };
    if (text.startsWith("'")) return { v: text.slice(1), s: style };

    const trimmed = text.trim();
    if (NUMBER_RE.test(trimmed)) return { v: Number(trimmed.replace(/,/g, '')), ...(style ? { s: style } : {}) };
    if (/^[-+]?\d+(\.\d+)?%$/.test(trimmed)) {
      return { v: Number(trimmed.slice(0, -1)) / 100, s: { ...style, nf: style?.nf ?? '0.##%' } };
    }
    if (/^true$/i.test(trimmed)) return { v: true, ...(style ? { s: style } : {}) };
    if (/^false$/i.test(trimmed)) return { v: false, ...(style ? { s: style } : {}) };
    const iso = ISO_DATE_RE.exec(trimmed);
    if (iso) {
      return { v: dateToSerial(new Date(Number(iso[1]), Number(iso[2]) - 1, Number(iso[3]))), s: { ...style, nf: style?.nf ?? 'yyyy-mm-dd' } };
    }
    const slash = SLASH_DATE_RE.exec(trimmed);
    if (slash) {
      return { v: dateToSerial(new Date(Number(slash[3]), Number(slash[2]) - 1, Number(slash[1]))), s: { ...style, nf: style?.nf ?? 'dd/mm/yyyy' } };
    }
    return { v: text, ...(style ? { s: style } : {}) };
  }

  // ------------------------------------------------------------------
  // Patches (cell level, with inverse for undo)
  // ------------------------------------------------------------------

  /** Applies a patch and returns the patch that undoes it. */
  apply(sheet: Sheet, patch: CellPatch): CellPatch {
    const inverse: CellPatch = new Map();
    for (const [key, cell] of patch) {
      const existing = sheet.cells[key];
      inverse.set(key, existing ? structuredClone(existing) : null);
      if (cell === null || (cell.v === undefined && cell.f === undefined && !hasStyle(cell))) {
        delete sheet.cells[key];
      } else {
        sheet.cells[key] = cell;
      }
      const address = parseCellKey(key);
      if (address) {
        sheet.rows = Math.min(MAX_ROWS, Math.max(sheet.rows, address.row + 1));
        sheet.cols = Math.min(MAX_COLS, Math.max(sheet.cols, address.col + 1));
      }
    }
    this.invalidate();
    return inverse;
  }

  /** Snapshot of a range, usable as clipboard content and for undo. */
  snapshot(sheet: Sheet, range: CellRange): (Cell | null)[][] {
    const out: (Cell | null)[][] = [];
    for (let r = range.r1; r <= range.r2; r++) {
      const row: (Cell | null)[] = [];
      for (let c = range.c1; c <= range.c2; c++) {
        const cell = sheet.cells[cellKey(r, c)];
        row.push(cell ? structuredClone(cell) : null);
      }
      out.push(row);
    }
    return out;
  }

  /** Cells to write when pasting `data` (taken from `origin`) so its top-left lands on (row, col). */
  pastePatch(data: (Cell | null)[][], origin: { row: number; col: number }, row: number, col: number, only: 'all' | 'values' = 'all'): CellPatch {
    const patch: CellPatch = new Map();
    data.forEach((line, dr) => {
      line.forEach((cell, dc) => {
        const key = cellKey(row + dr, col + dc);
        if (!cell) {
          patch.set(key, null);
          return;
        }
        const copy: Cell = structuredClone(cell);
        if (copy.f !== undefined) copy.f = shiftFormula(copy.f, row - origin.row, col - origin.col);
        if (only === 'values') {
          const value = copy.f !== undefined ? this.valueOfPending(copy) : copy.v;
          patch.set(key, { v: value as Scalar });
        } else {
          patch.set(key, copy);
        }
      });
    });
    return patch;
  }

  private valueOfPending(cell: Cell): Scalar {
    return cell.v ?? null;
  }

  /** Paste of plain text (tab/newline separated). */
  textPatch(text: string, row: number, col: number, sheet: Sheet): CellPatch {
    const patch: CellPatch = new Map();
    const lines = text.replace(/\r\n/g, '\n').replace(/\n$/, '').split('\n');
    lines.forEach((line, dr) => {
      line.split('\t').forEach((part, dc) => {
        const key = cellKey(row + dr, col + dc);
        patch.set(key, this.parseInput(part, sheet.cells[key]));
      });
    });
    return patch;
  }

  /** Fill: copies `source` across `target`, extending simple number series and shifting formulas. */
  fillPatch(sheet: Sheet, source: CellRange, target: CellRange): CellPatch {
    const patch: CellPatch = new Map();
    const vertical = target.c1 === source.c1 && target.c2 === source.c2;
    const height = source.r2 - source.r1 + 1;
    const width = source.c2 - source.c1 + 1;

    const lines = vertical ? width : height;
    for (let line = 0; line < lines; line++) {
      const cells: { key: string; cell: Cell | undefined }[] = [];
      const count = vertical ? height : width;
      for (let i = 0; i < count; i++) {
        const key = vertical ? cellKey(source.r1 + i, source.c1 + line) : cellKey(source.r1 + line, source.c1 + i);
        cells.push({ key, cell: sheet.cells[key] });
      }
      const numeric = cells.every((entry) => typeof entry.cell?.v === 'number' && entry.cell.f === undefined);
      const step = numeric && cells.length > 1 ? (cells[1].cell!.v as number) - (cells[0].cell!.v as number) : numeric && cells.length === 1 ? 1 : 0;
      const isSeries = numeric && (cells.length === 1 ? false : cells.every((entry, i) => i === 0 || (entry.cell!.v as number) - (cells[i - 1].cell!.v as number) === step));

      const forward = vertical ? target.r2 > source.r2 : target.c2 > source.c2;
      const start = vertical ? (forward ? source.r2 + 1 : target.r1) : forward ? source.c2 + 1 : target.c1;
      const end = vertical ? (forward ? target.r2 : source.r1 - 1) : forward ? target.c2 : source.c1 - 1;
      for (let pos = start; pos <= end; pos++) {
        const offset = forward ? pos - (vertical ? source.r1 : source.c1) : pos - (vertical ? source.r1 : source.c1);
        const index = ((offset % count) + count) % count;
        const from = cells[index];
        const fromAddress = parseCellKey(from.key)!;
        const toRow = vertical ? pos : fromAddress.row;
        const toCol = vertical ? fromAddress.col : pos;
        const key = cellKey(toRow, toCol);
        if (!from.cell) {
          patch.set(key, null);
        } else if (isSeries) {
          const base = cells[0].cell!.v as number;
          patch.set(key, { ...structuredClone(from.cell), v: base + step * offset });
        } else if (from.cell.f !== undefined) {
          patch.set(key, { ...structuredClone(from.cell), f: shiftFormula(from.cell.f, toRow - fromAddress.row, toCol - fromAddress.col) });
        } else if (numeric && cells.length === 1) {
          patch.set(key, structuredClone(from.cell));
        } else {
          patch.set(key, structuredClone(from.cell));
        }
      }
    }
    return patch;
  }

  // ------------------------------------------------------------------
  // Structure
  // ------------------------------------------------------------------

  insert(sheet: Sheet, axis: 'row' | 'col', index: number, count: number): void {
    this.shiftCells(sheet, axis, index, count);
    const widths = axis === 'col' ? sheet.colWidths : sheet.rowHeights;
    if (widths) this.shiftMap(widths, index, count);
    if (axis === 'row') sheet.rows = Math.min(MAX_ROWS, sheet.rows + count);
    else sheet.cols = Math.min(MAX_COLS, sheet.cols + count);
    sheet.merges = (sheet.merges ?? []).map((merge) => shiftMerge(merge, axis, index, count)).filter((m): m is string => m !== null);
    this.rewriteFormulas(sheet, axis, index, count);
    this.invalidate();
  }

  remove(sheet: Sheet, axis: 'row' | 'col', index: number, count: number): void {
    for (const key of Object.keys(sheet.cells)) {
      const address = parseCellKey(key)!;
      const position = axis === 'row' ? address.row : address.col;
      if (position >= index && position < index + count) delete sheet.cells[key];
    }
    this.shiftCells(sheet, axis, index + count, -count);
    const widths = axis === 'col' ? sheet.colWidths : sheet.rowHeights;
    if (widths) {
      for (const key of Object.keys(widths)) {
        const n = Number(key);
        if (n >= index && n < index + count) delete widths[key];
      }
      this.shiftMap(widths, index + count, -count);
    }
    sheet.merges = (sheet.merges ?? []).map((merge) => shiftMerge(merge, axis, index, -count)).filter((m): m is string => m !== null);
    this.rewriteFormulas(sheet, axis, index, -count);
    this.invalidate();
  }

  private shiftCells(sheet: Sheet, axis: 'row' | 'col', from: number, delta: number): void {
    const moved: Record<string, Cell> = {};
    for (const [key, cell] of Object.entries(sheet.cells)) {
      const address = parseCellKey(key)!;
      const position = axis === 'row' ? address.row : address.col;
      if (position >= from) {
        moved[axis === 'row' ? cellKey(address.row + delta, address.col) : cellKey(address.row, address.col + delta)] = cell;
      } else {
        moved[key] = cell;
      }
    }
    sheet.cells = moved;
  }

  private shiftMap(map: Record<string, number>, from: number, delta: number): void {
    const entries = Object.entries(map).map(([key, value]) => [Number(key), value] as const);
    for (const key of Object.keys(map)) delete map[key];
    for (const [position, value] of entries) map[String(position >= from ? position + delta : position)] = value;
  }

  /** Every formula in the workbook is rewritten so references follow the moved cells. */
  private rewriteFormulas(target: Sheet, axis: 'row' | 'col', index: number, delta: number): void {
    for (const sheet of this.workbook.sheets) {
      for (const cell of Object.values(sheet.cells)) {
        if (cell.f !== undefined) cell.f = adjustForStructureChange(cell.f, sheet.name, target.name, axis, index, delta);
      }
    }
  }

  renameSheet(sheet: Sheet, name: string): void {
    const previous = sheet.name;
    sheet.name = name;
    for (const other of this.workbook.sheets) {
      for (const cell of Object.values(other.cells)) {
        if (cell.f !== undefined) cell.f = renameSheetInFormula(cell.f, previous, name);
      }
    }
    this.invalidate();
  }

  /** Sorts the rows of `range` by one column; blanks go last. Returns false when nothing changed. */
  sort(sheet: Sheet, range: CellRange, byCol: number, ascending: boolean, hasHeader: boolean): CellPatch {
    const first = hasHeader ? range.r1 + 1 : range.r1;
    const rows: { values: (Cell | null)[]; key: Value }[] = [];
    for (let r = first; r <= range.r2; r++) {
      const values: (Cell | null)[] = [];
      for (let c = range.c1; c <= range.c2; c++) values.push(sheet.cells[cellKey(r, c)] ?? null);
      rows.push({ values, key: this.value(sheet, r, byCol) });
    }
    const collator = new Intl.Collator(undefined, { numeric: true, sensitivity: 'base' });
    const rank = (v: Value) => (v === null || v === '' ? 3 : typeof v === 'number' ? 0 : typeof v === 'string' ? 1 : 2);
    rows.sort((a, b) => {
      const ra = rank(a.key);
      const rb = rank(b.key);
      if (ra === 3 || rb === 3) return ra - rb;
      if (ra !== rb) return ra - rb;
      const cmp = typeof a.key === 'number' && typeof b.key === 'number' ? a.key - b.key : collator.compare(String(a.key), String(b.key));
      return ascending ? cmp : -cmp;
    });
    const patch: CellPatch = new Map();
    rows.forEach((row, i) => {
      row.values.forEach((cell, j) => patch.set(cellKey(first + i, range.c1 + j), cell ? structuredClone(cell) : null));
    });
    return patch;
  }

  /** Numeric summary of a range for the status bar. */
  summarize(sheet: Sheet, range: CellRange): { count: number; numbers: number; sum: number; average: number | null } {
    let count = 0;
    let numbers = 0;
    let sum = 0;
    for (let r = range.r1; r <= Math.min(range.r2, range.r1 + 50_000); r++) {
      for (let c = range.c1; c <= range.c2; c++) {
        const value = this.value(sheet, r, c);
        if (value === null || value === '') continue;
        count++;
        if (typeof value === 'number') {
          numbers++;
          sum += value;
        } else if (typeof value === 'string' && !isError(toNumber(value)) && value.trim() !== '') {
          numbers++;
          sum += toNumber(value) as number;
        }
      }
    }
    return { count, numbers, sum, average: numbers ? sum / numbers : null };
  }
}

function hasStyle(cell: Cell): boolean {
  return !!cell.s && Object.keys(cell.s).length > 0;
}

function shiftMerge(merge: string, axis: 'row' | 'col', index: number, delta: number): string | null {
  const [a, b] = merge.split(':');
  const from = parseCellKey(a);
  const to = parseCellKey(b ?? a);
  if (!from || !to) return null;
  const move = (value: number, isEnd: boolean): number | null => {
    if (delta > 0) return value >= index ? value + delta : value;
    const removed = -delta;
    if (value >= index + removed) return value + delta;
    if (value >= index) return isEnd ? index - 1 : index;
    return value;
  };
  const r1 = axis === 'row' ? move(from.row, false) : from.row;
  const r2 = axis === 'row' ? move(to.row, true) : to.row;
  const c1 = axis === 'col' ? move(from.col, false) : from.col;
  const c2 = axis === 'col' ? move(to.col, true) : to.col;
  if (r1 === null || r2 === null || c1 === null || c2 === null || r2 < r1 || c2 < c1) return null;
  const start = cellKey(r1, c1);
  const end = cellKey(r2, c2);
  return start === end ? null : `${start}:${end}`;
}

export { serialToDate };
