/** A1-style addressing helpers. Rows and columns are zero-based everywhere in code. */

export function colToLetters(col: number): string {
  let n = col + 1;
  let out = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    out = String.fromCharCode(65 + rem) + out;
    n = Math.floor((n - 1) / 26);
  }
  return out;
}

export function lettersToCol(letters: string): number {
  let n = 0;
  for (const ch of letters.toUpperCase()) {
    n = n * 26 + (ch.charCodeAt(0) - 64);
  }
  return n - 1;
}

export function cellKey(row: number, col: number): string {
  return `${colToLetters(col)}${row + 1}`;
}

export interface CellAddress {
  readonly row: number;
  readonly col: number;
}

const KEY_RE = /^\$?([A-Za-z]{1,3})\$?(\d+)$/;

export function parseCellKey(key: string): CellAddress | null {
  const match = KEY_RE.exec(key);
  if (!match) return null;
  const row = Number(match[2]) - 1;
  if (row < 0) return null;
  return { row, col: lettersToCol(match[1]) };
}

export interface CellRange {
  readonly r1: number;
  readonly c1: number;
  readonly r2: number;
  readonly c2: number;
}

export function normalizeRange(a: CellAddress, b: CellAddress): CellRange {
  return {
    r1: Math.min(a.row, b.row),
    c1: Math.min(a.col, b.col),
    r2: Math.max(a.row, b.row),
    c2: Math.max(a.col, b.col),
  };
}

export function rangeLabel(range: CellRange): string {
  const start = cellKey(range.r1, range.c1);
  const end = cellKey(range.r2, range.c2);
  return start === end ? start : `${start}:${end}`;
}

export function parseRangeKey(text: string): CellRange | null {
  const [from, to] = text.split(':');
  const a = parseCellKey(from);
  const b = parseCellKey(to ?? from);
  return a && b ? normalizeRange(a, b) : null;
}
