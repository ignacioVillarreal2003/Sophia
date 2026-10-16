import { colToLetters, lettersToCol } from './cell-ref';
import { dateToSerial, formatValue, serialToDate } from './number-format';

// ---------------------------------------------------------------------------
// Values
// ---------------------------------------------------------------------------

export class FormulaError {
  constructor(readonly code: string) {}
  toString(): string {
    return this.code;
  }
}

export const ERR = {
  div0: new FormulaError('#DIV/0!'),
  ref: new FormulaError('#REF!'),
  name: new FormulaError('#NAME?'),
  value: new FormulaError('#VALUE!'),
  na: new FormulaError('#N/A'),
  num: new FormulaError('#NUM!'),
  circ: new FormulaError('#CIRC!'),
  parse: new FormulaError('#ERROR!'),
};

export type Scalar = number | string | boolean | null;
export type Value = Scalar | FormulaError;
export type Matrix = Value[][];
export type Operand = Value | Matrix;

export function isError(value: unknown): value is FormulaError {
  return value instanceof FormulaError;
}

function isMatrix(value: Operand): value is Matrix {
  return Array.isArray(value);
}

// ---------------------------------------------------------------------------
// AST
// ---------------------------------------------------------------------------

export type Node =
  | { t: 'num'; v: number }
  | { t: 'str'; v: string }
  | { t: 'bool'; v: boolean }
  | { t: 'err'; code: string }
  | { t: 'ref'; sheet: string | null; row: number; col: number; absRow: boolean; absCol: boolean }
  | {
      t: 'range';
      sheet: string | null;
      r1: number;
      c1: number;
      r2: number;
      c2: number;
      absR1: boolean;
      absC1: boolean;
      absR2: boolean;
      absC2: boolean;
    }
  | { t: 'func'; name: string; args: Node[] }
  | { t: 'un'; op: '-' | '+'; a: Node }
  | { t: 'pct'; a: Node }
  | { t: 'bin'; op: string; a: Node; b: Node };

type Token =
  | { k: 'num'; v: number }
  | { k: 'str'; v: string }
  | { k: 'id'; v: string }
  | { k: 'sheet'; v: string }
  | { k: 'op'; v: string }
  | { k: 'err'; v: string };

const CELL_RE = /^(\$?)([A-Za-z]{1,3})(\$?)(\d+)$/;
const COL_ONLY_RE = /^(\$?)([A-Za-z]{1,3})$/;
const ROW_ONLY_RE = /^(\$?)(\d+)$/;

function tokenize(src: string): Token[] {
  const tokens: Token[] = [];
  let i = 0;
  while (i < src.length) {
    const ch = src[i];
    if (/\s/.test(ch)) {
      i++;
    } else if (ch === '"') {
      let value = '';
      i++;
      while (i < src.length && !(src[i] === '"' && src[i + 1] !== '"')) {
        if (src[i] === '"' && src[i + 1] === '"') {
          value += '"';
          i += 2;
        } else value += src[i++];
      }
      if (i >= src.length) throw new Error('unterminated string');
      i++;
      tokens.push({ k: 'str', v: value });
    } else if (ch === "'") {
      let value = '';
      i++;
      while (i < src.length && !(src[i] === "'" && src[i + 1] !== "'")) {
        if (src[i] === "'" && src[i + 1] === "'") {
          value += "'";
          i += 2;
        } else value += src[i++];
      }
      if (src[i] !== "'" || src[i + 1] !== '!') throw new Error('bad sheet name');
      i += 2;
      tokens.push({ k: 'sheet', v: value });
    } else if (/[0-9]/.test(ch) || (ch === '.' && /[0-9]/.test(src[i + 1] ?? ''))) {
      const match = /^(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?/.exec(src.slice(i))!;
      tokens.push({ k: 'num', v: Number(match[0]) });
      i += match[0].length;
    } else if (/[A-Za-z_$]/.test(ch)) {
      const match = /^[$A-Za-z_][$A-Za-z0-9_.]*/.exec(src.slice(i))!;
      let word = match[0];
      i += word.length;
      if (src[i] === '!') {
        i++;
        tokens.push({ k: 'sheet', v: word });
        continue;
      }
      // Trailing dots belong to nothing useful; keep the identifier as written.
      if (word.endsWith('.')) {
        word = word.slice(0, -1);
        i--;
      }
      tokens.push({ k: 'id', v: word });
    } else if (ch === '#') {
      const match = /^#(?:DIV\/0!|REF!|NAME\?|VALUE!|N\/A|NUM!|NULL!|ERROR!)/i.exec(src.slice(i));
      if (!match) throw new Error('unexpected #');
      tokens.push({ k: 'err', v: match[0].toUpperCase() });
      i += match[0].length;
    } else {
      const two = src.slice(i, i + 2);
      if (['<=', '>=', '<>'].includes(two)) {
        tokens.push({ k: 'op', v: two });
        i += 2;
      } else if ('+-*/^&=<>(),:;%'.includes(ch)) {
        tokens.push({ k: 'op', v: ch === ';' ? ',' : ch });
        i++;
      } else {
        throw new Error(`unexpected character ${ch}`);
      }
    }
  }
  return tokens;
}

class Parser {
  private pos = 0;
  constructor(private readonly tokens: Token[]) {}

  parse(): Node {
    const node = this.compare();
    if (this.pos < this.tokens.length) throw new Error('unexpected token');
    return node;
  }

  private peek(): Token | undefined {
    return this.tokens[this.pos];
  }

  private isOp(value: string): boolean {
    const token = this.peek();
    return token?.k === 'op' && token.v === value;
  }

  private takeOp(...values: string[]): string | null {
    const token = this.peek();
    if (token?.k === 'op' && values.includes(token.v)) {
      this.pos++;
      return token.v;
    }
    return null;
  }

  private compare(): Node {
    let left = this.concat();
    for (let op = this.takeOp('=', '<>', '<', '>', '<=', '>='); op; op = this.takeOp('=', '<>', '<', '>', '<=', '>=')) {
      left = { t: 'bin', op, a: left, b: this.concat() };
    }
    return left;
  }

  private concat(): Node {
    let left = this.add();
    while (this.takeOp('&')) left = { t: 'bin', op: '&', a: left, b: this.add() };
    return left;
  }

  private add(): Node {
    let left = this.mul();
    for (let op = this.takeOp('+', '-'); op; op = this.takeOp('+', '-')) left = { t: 'bin', op, a: left, b: this.mul() };
    return left;
  }

  private mul(): Node {
    let left = this.pow();
    for (let op = this.takeOp('*', '/'); op; op = this.takeOp('*', '/')) left = { t: 'bin', op, a: left, b: this.pow() };
    return left;
  }

  private pow(): Node {
    let left = this.unary();
    while (this.takeOp('^')) left = { t: 'bin', op: '^', a: left, b: this.unary() };
    return left;
  }

  private unary(): Node {
    const op = this.takeOp('-', '+');
    if (op) return { t: 'un', op: op as '-' | '+', a: this.unary() };
    return this.postfix();
  }

  private postfix(): Node {
    let node = this.primary();
    while (this.takeOp('%')) node = { t: 'pct', a: node };
    return node;
  }

  private primary(): Node {
    const token = this.peek();
    if (!token) throw new Error('unexpected end');
    this.pos++;
    switch (token.k) {
      case 'num':
        return { t: 'num', v: token.v };
      case 'str':
        return { t: 'str', v: token.v };
      case 'err':
        return { t: 'err', code: token.v };
      case 'sheet':
        return this.reference(token.v, this.expectId());
      case 'id':
        if (this.isOp('(')) {
          this.pos++;
          const args: Node[] = [];
          if (!this.isOp(')')) {
            do {
              // Empty arguments (e.g. IF(A1,,2)) are treated as blank.
              args.push(this.isOp(',') || this.isOp(')') ? { t: 'str', v: '' } : this.compare());
            } while (this.takeOp(','));
          }
          if (!this.takeOp(')')) throw new Error('missing )');
          return { t: 'func', name: token.v.toUpperCase(), args };
        }
        if (/^true$/i.test(token.v)) return { t: 'bool', v: true };
        if (/^false$/i.test(token.v)) return { t: 'bool', v: false };
        return this.reference(null, token.v);
      case 'op':
        if (token.v === '(') {
          const inner = this.compare();
          if (!this.takeOp(')')) throw new Error('missing )');
          return inner;
        }
        throw new Error(`unexpected ${token.v}`);
    }
  }

  private expectId(): string {
    const token = this.peek();
    if (token?.k !== 'id') throw new Error('reference expected');
    this.pos++;
    return token.v;
  }

  private reference(sheet: string | null, text: string): Node {
    const first = parseRefPart(text);
    if (!first) throw new Error(`unknown name ${text}`);
    if (this.isOp(':')) {
      this.pos++;
      let secondSheetText = this.peek();
      if (secondSheetText?.k === 'sheet') {
        this.pos++;
        secondSheetText = this.peek();
      }
      const second = parseRefPart(this.expectId());
      if (!second) throw new Error('bad range');
      return makeRange(sheet, first, second);
    }
    if (first.row === null || first.col === null) throw new Error(`bad reference ${text}`);
    return { t: 'ref', sheet, row: first.row, col: first.col, absRow: first.absRow, absCol: first.absCol };
  }
}

interface RefPart {
  row: number | null;
  col: number | null;
  absRow: boolean;
  absCol: boolean;
}

function parseRefPart(text: string): RefPart | null {
  let m = CELL_RE.exec(text);
  if (m) return { col: lettersToCol(m[2]), row: Number(m[4]) - 1, absCol: m[1] === '$', absRow: m[3] === '$' };
  m = COL_ONLY_RE.exec(text);
  if (m) return { col: lettersToCol(m[2]), row: null, absCol: m[1] === '$', absRow: false };
  m = ROW_ONLY_RE.exec(text);
  if (m) return { col: null, row: Number(m[2]) - 1, absCol: false, absRow: m[1] === '$' };
  return null;
}

/** Whole-column / whole-row ranges use -1 as "to the end". */
function makeRange(sheet: string | null, a: RefPart, b: RefPart): Node {
  const r1 = a.row ?? 0;
  const c1 = a.col ?? 0;
  const r2 = b.row ?? -1;
  const c2 = b.col ?? -1;
  return {
    t: 'range',
    sheet,
    r1: Math.min(r1, r2 === -1 ? r1 : r2),
    c1: Math.min(c1, c2 === -1 ? c1 : c2),
    r2: r2 === -1 ? -1 : Math.max(r1, r2),
    c2: c2 === -1 ? -1 : Math.max(c1, c2),
    absR1: a.absRow,
    absC1: a.absCol,
    absR2: b.absRow,
    absC2: b.absCol,
  };
}

export function parseFormula(source: string): Node | null {
  const text = source.startsWith('=') ? source.slice(1) : source;
  try {
    return new Parser(tokenize(text)).parse();
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Printing and reference rewriting (copy/paste, fill, insert/delete rows and columns)
// ---------------------------------------------------------------------------

function sheetPrefix(sheet: string | null): string {
  if (sheet === null) return '';
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(sheet) ? `${sheet}!` : `'${sheet.replace(/'/g, "''")}'!`;
}

function refText(row: number, col: number, absRow: boolean, absCol: boolean): string {
  return `${absCol ? '$' : ''}${colToLetters(col)}${absRow ? '$' : ''}${row + 1}`;
}

export function formulaToString(node: Node): string {
  return `=${print(node, 0)}`;
}

const PRECEDENCE: Record<string, number> = { '=': 1, '<>': 1, '<': 1, '>': 1, '<=': 1, '>=': 1, '&': 2, '+': 3, '-': 3, '*': 4, '/': 4, '^': 5 };

function print(node: Node, parent: number): string {
  switch (node.t) {
    case 'num':
      return String(node.v);
    case 'str':
      return `"${node.v.replace(/"/g, '""')}"`;
    case 'bool':
      return node.v ? 'TRUE' : 'FALSE';
    case 'err':
      return node.code;
    case 'ref':
      return `${sheetPrefix(node.sheet)}${refText(node.row, node.col, node.absRow, node.absCol)}`;
    case 'range': {
      const prefix = sheetPrefix(node.sheet);
      if (node.r2 === -1 && node.c2 !== -1) {
        return `${prefix}${node.absC1 ? '$' : ''}${colToLetters(node.c1)}:${node.absC2 ? '$' : ''}${colToLetters(node.c2)}`;
      }
      if (node.c2 === -1 && node.r2 !== -1) {
        return `${prefix}${node.absR1 ? '$' : ''}${node.r1 + 1}:${node.absR2 ? '$' : ''}${node.r2 + 1}`;
      }
      return `${prefix}${refText(node.r1, node.c1, node.absR1, node.absC1)}:${refText(node.r2, node.c2, node.absR2, node.absC2)}`;
    }
    case 'func':
      return `${node.name}(${node.args.map((arg) => print(arg, 0)).join(',')})`;
    case 'un':
      return `${node.op}${print(node.a, 6)}`;
    case 'pct':
      return `${print(node.a, 7)}%`;
    case 'bin': {
      const prec = PRECEDENCE[node.op] ?? 1;
      const text = `${print(node.a, prec)}${node.op}${print(node.b, prec + 1)}`;
      return prec < parent ? `(${text})` : text;
    }
  }
}

/** Applies `fn` to every reference/range of the formula; returning null turns it into #REF!. */
export function mapReferences(node: Node, fn: (ref: Node) => Node | null): Node {
  switch (node.t) {
    case 'ref':
    case 'range':
      return fn(node) ?? { t: 'err', code: '#REF!' };
    case 'func':
      return { ...node, args: node.args.map((arg) => mapReferences(arg, fn)) };
    case 'un':
    case 'pct':
      return { ...node, a: mapReferences(node.a, fn) };
    case 'bin':
      return { ...node, a: mapReferences(node.a, fn), b: mapReferences(node.b, fn) };
    default:
      return node;
  }
}

/** Moves relative references by (dRow, dCol), as copy/paste and fill do. */
export function shiftFormula(formula: string, dRow: number, dCol: number): string {
  const ast = parseFormula(formula);
  if (!ast) return formula;
  const shifted = mapReferences(ast, (ref) => {
    if (ref.t === 'ref') {
      const row = ref.absRow ? ref.row : ref.row + dRow;
      const col = ref.absCol ? ref.col : ref.col + dCol;
      return row < 0 || col < 0 ? null : { ...ref, row, col };
    }
    if (ref.t === 'range') {
      const r1 = ref.absR1 ? ref.r1 : ref.r1 + dRow;
      const c1 = ref.absC1 ? ref.c1 : ref.c1 + dCol;
      const r2 = ref.r2 === -1 ? -1 : ref.absR2 ? ref.r2 : ref.r2 + dRow;
      const c2 = ref.c2 === -1 ? -1 : ref.absC2 ? ref.c2 : ref.c2 + dCol;
      return r1 < 0 || c1 < 0 || (r2 !== -1 && r2 < 0) || (c2 !== -1 && c2 < 0) ? null : { ...ref, r1, c1, r2, c2 };
    }
    return ref;
  });
  return formulaToString(shifted);
}

/**
 * Rewrites a formula after rows/columns were inserted (delta > 0) or deleted (delta < 0) at `index` of
 * `axis` in sheet `targetSheet`. `ownSheet` is the sheet the formula lives in.
 */
export function adjustForStructureChange(
  formula: string,
  ownSheet: string,
  targetSheet: string,
  axis: 'row' | 'col',
  index: number,
  delta: number,
): string {
  const ast = parseFormula(formula);
  if (!ast) return formula;
  const appliesTo = (sheet: string | null) => (sheet ?? ownSheet) === targetSheet;

  const moveSingle = (value: number): number | null => {
    if (delta > 0) return value >= index ? value + delta : value;
    const removed = -delta;
    if (value >= index + removed) return value + delta;
    if (value >= index) return null;
    return value;
  };

  const adjusted = mapReferences(ast, (ref) => {
    if (ref.t === 'ref') {
      if (!appliesTo(ref.sheet)) return ref;
      if (axis === 'row') {
        const row = moveSingle(ref.row);
        return row === null ? null : { ...ref, row };
      }
      const col = moveSingle(ref.col);
      return col === null ? null : { ...ref, col };
    }
    if (ref.t === 'range') {
      if (!appliesTo(ref.sheet)) return ref;
      const lo = axis === 'row' ? ref.r1 : ref.c1;
      const hi = axis === 'row' ? ref.r2 : ref.c2;
      if (hi === -1) return ref;
      let newLo: number | null;
      let newHi: number | null;
      if (delta > 0) {
        newLo = lo >= index ? lo + delta : lo;
        newHi = hi >= index ? hi + delta : hi;
      } else {
        const removed = -delta;
        const end = index + removed;
        if (lo >= index && hi < end) return null; // the whole range was deleted
        newLo = lo >= end ? lo + delta : lo >= index ? index : lo;
        newHi = hi >= end ? hi + delta : hi >= index ? index - 1 : hi;
      }
      return axis === 'row' ? { ...ref, r1: newLo, r2: newHi } : { ...ref, c1: newLo, c2: newHi };
    }
    return ref;
  });
  return formulaToString(adjusted);
}

/** Renames a sheet inside a formula's references. */
export function renameSheetInFormula(formula: string, from: string, to: string): string {
  const ast = parseFormula(formula);
  if (!ast) return formula;
  return formulaToString(mapReferences(ast, (ref) => ((ref.t === 'ref' || ref.t === 'range') && ref.sheet === from ? { ...ref, sheet: to } : ref)));
}

// ---------------------------------------------------------------------------
// Evaluation
// ---------------------------------------------------------------------------

export interface EvalContext {
  /** Sheet the formula belongs to. */
  readonly sheet: string;
  /** Evaluated value of one cell (the resolver handles caching and cycle detection). */
  cell(sheet: string, row: number, col: number): Value;
  /** Index of the last row/column in use of a sheet, for whole-column ranges. */
  lastRow(sheet: string): number;
  lastCol(sheet: string): number;
  /** Zero-based position of the cell holding the formula (ROW()/COLUMN() without args). */
  readonly origin: { row: number; col: number };
}

export function evaluateFormula(source: string, ctx: EvalContext): Value {
  const ast = parseFormula(source);
  if (!ast) return ERR.parse;
  const result = evaluate(ast, ctx);
  if (isMatrix(result)) return result[0]?.[0] ?? null;
  return result;
}

function evaluate(node: Node, ctx: EvalContext): Operand {
  switch (node.t) {
    case 'num':
      return node.v;
    case 'str':
      return node.v;
    case 'bool':
      return node.v;
    case 'err':
      return new FormulaError(node.code);
    case 'ref':
      return ctx.cell(node.sheet ?? ctx.sheet, node.row, node.col);
    case 'range': {
      const sheet = node.sheet ?? ctx.sheet;
      const r2 = node.r2 === -1 ? ctx.lastRow(sheet) : node.r2;
      const c2 = node.c2 === -1 ? ctx.lastCol(sheet) : node.c2;
      const matrix: Matrix = [];
      for (let r = node.r1; r <= r2; r++) {
        const row: Value[] = [];
        for (let c = node.c1; c <= c2; c++) row.push(ctx.cell(sheet, r, c));
        matrix.push(row);
      }
      return matrix;
    }
    case 'un': {
      const value = scalar(evaluate(node.a, ctx));
      if (isError(value)) return value;
      const n = toNumber(value);
      return isError(n) ? n : node.op === '-' ? -n : n;
    }
    case 'pct': {
      const n = toNumber(scalar(evaluate(node.a, ctx)));
      return isError(n) ? n : n / 100;
    }
    case 'bin':
      return binary(node.op, scalar(evaluate(node.a, ctx)), scalar(evaluate(node.b, ctx)));
    case 'func':
      return callFunction(node, ctx);
  }
}

function scalar(operand: Operand): Value {
  return isMatrix(operand) ? (operand[0]?.[0] ?? null) : operand;
}

export function toNumber(value: Value): number | FormulaError {
  if (isError(value)) return value;
  if (value === null || value === '') return 0;
  if (typeof value === 'number') return value;
  if (typeof value === 'boolean') return value ? 1 : 0;
  const text = value.trim();
  if (/^[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?%?$/.test(text)) {
    return text.endsWith('%') ? Number(text.slice(0, -1)) / 100 : Number(text);
  }
  return ERR.value;
}

export function toText(value: Value): string {
  if (isError(value)) return value.code;
  if (value === null) return '';
  if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
  if (typeof value === 'number') return formatValue(value, 'General');
  return value;
}

export function toBool(value: Value): boolean | FormulaError {
  if (isError(value)) return value;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return value !== 0;
  if (value === null || value === '') return false;
  if (/^true$/i.test(value)) return true;
  if (/^false$/i.test(value)) return false;
  return ERR.value;
}

function compareValues(a: Value, b: Value): number {
  const rank = (v: Value) => (v === null ? 0 : typeof v === 'number' ? 0 : typeof v === 'string' ? 1 : 2);
  if (a === null && typeof b === 'string') a = '';
  if (b === null && typeof a === 'string') b = '';
  if (a === null) a = typeof b === 'boolean' ? false : 0;
  if (b === null) b = typeof a === 'boolean' ? false : 0;
  const ra = rank(a);
  const rb = rank(b);
  if (ra !== rb) return ra - rb;
  if (typeof a === 'string' && typeof b === 'string') {
    const x = a.toLowerCase();
    const y = b.toLowerCase();
    return x < y ? -1 : x > y ? 1 : 0;
  }
  return Number(a) - Number(b);
}

function binary(op: string, a: Value, b: Value): Value {
  if (isError(a)) return a;
  if (isError(b)) return b;
  switch (op) {
    case '&':
      return toText(a) + toText(b);
    case '=':
      return compareValues(a, b) === 0;
    case '<>':
      return compareValues(a, b) !== 0;
    case '<':
      return compareValues(a, b) < 0;
    case '>':
      return compareValues(a, b) > 0;
    case '<=':
      return compareValues(a, b) <= 0;
    case '>=':
      return compareValues(a, b) >= 0;
  }
  const x = toNumber(a);
  if (isError(x)) return x;
  const y = toNumber(b);
  if (isError(y)) return y;
  switch (op) {
    case '+':
      return x + y;
    case '-':
      return x - y;
    case '*':
      return x * y;
    case '/':
      return y === 0 ? ERR.div0 : x / y;
    case '^': {
      const result = Math.pow(x, y);
      return Number.isFinite(result) ? result : ERR.num;
    }
  }
  return ERR.value;
}

// ---------------------------------------------------------------------------
// Functions
// ---------------------------------------------------------------------------

type Fn = (args: Operand[], ctx: EvalContext) => Operand;

function flatten(operand: Operand): Value[] {
  return isMatrix(operand) ? operand.flat() : [operand];
}

/** Numbers of the arguments: ranges contribute only real numbers, direct arguments are coerced. */
function numbers(args: Operand[]): number[] | FormulaError {
  const out: number[] = [];
  for (const arg of args) {
    if (isMatrix(arg)) {
      for (const value of arg.flat()) {
        if (isError(value)) return value;
        if (typeof value === 'number') out.push(value);
      }
    } else {
      if (isError(arg)) return arg;
      if (arg === null) continue;
      const n = toNumber(arg);
      if (isError(n)) return n;
      out.push(n);
    }
  }
  return out;
}

function num(arg: Operand | undefined, fallback?: number): number | FormulaError {
  if (arg === undefined) return fallback === undefined ? ERR.value : fallback;
  return toNumber(scalar(arg));
}

function str(arg: Operand | undefined): string {
  return toText(scalar(arg ?? null));
}

function firstError(...values: unknown[]): FormulaError | null {
  for (const value of values) if (isError(value)) return value;
  return null;
}

function wildcardToRegExp(pattern: string): RegExp {
  const escaped = pattern.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/~\*/g, '\u0001').replace(/~\?/g, '\u0002').replace(/\*/g, '.*').replace(/\?/g, '.');
  return new RegExp(`^${escaped.replace(/\u0001/g, '\\*').replace(/\u0002/g, '\\?')}$`, 'i');
}

function criterion(raw: Operand): (value: Value) => boolean {
  const crit = scalar(raw);
  if (typeof crit !== 'string') {
    return (value) => !isError(value) && compareValues(value, crit as Value) === 0 && (typeof value === typeof crit || value === null);
  }
  const match = /^(<=|>=|<>|=|<|>)?(.*)$/s.exec(crit)!;
  const op = match[1] ?? '=';
  const rhs = match[2];
  const rhsNumber = rhs.trim() !== '' && !Number.isNaN(Number(rhs)) ? Number(rhs) : null;
  return (value) => {
    if (isError(value)) return false;
    if (rhsNumber !== null) {
      if (typeof value !== 'number') {
        if (op === '<>') return true;
        return false;
      }
      return op === '=' ? value === rhsNumber : op === '<>' ? value !== rhsNumber : op === '<' ? value < rhsNumber : op === '>' ? value > rhsNumber : op === '<=' ? value <= rhsNumber : value >= rhsNumber;
    }
    const text = value === null ? '' : toText(value);
    if (op === '=') return wildcardToRegExp(rhs).test(text);
    if (op === '<>') return !wildcardToRegExp(rhs).test(text);
    const cmp = text.toLowerCase().localeCompare(rhs.toLowerCase());
    return op === '<' ? cmp < 0 : op === '>' ? cmp > 0 : op === '<=' ? cmp <= 0 : cmp >= 0;
  };
}

function matrixOf(operand: Operand): Matrix {
  return isMatrix(operand) ? operand : [[operand]];
}

function stat(values: number[], kind: 'var' | 'stdev', sample: boolean): number | FormulaError {
  const n = values.length;
  if (n < (sample ? 2 : 1)) return ERR.div0;
  const mean = values.reduce((a, b) => a + b, 0) / n;
  const variance = values.reduce((a, b) => a + (b - mean) ** 2, 0) / (sample ? n - 1 : n);
  return kind === 'var' ? variance : Math.sqrt(variance);
}

function roundTo(value: number, digits: number, mode: 'round' | 'up' | 'down'): number {
  const factor = 10 ** digits;
  const scaled = value * factor;
  const rounded = mode === 'round' ? Math.sign(scaled) * Math.round(Math.abs(scaled)) : mode === 'up' ? Math.sign(scaled) * Math.ceil(Math.abs(scaled)) : Math.trunc(scaled);
  return rounded / factor;
}

function lookupMatch(needle: Value, haystack: Value[], type: number): number {
  if (type === 0) {
    const regex = typeof needle === 'string' ? wildcardToRegExp(needle) : null;
    return haystack.findIndex((value) => (regex && typeof value === 'string' ? regex.test(value) : compareValues(value, needle) === 0 && typeof value === typeof needle));
  }
  let best = -1;
  for (let i = 0; i < haystack.length; i++) {
    const cmp = compareValues(haystack[i], needle);
    if (type === 1 ? cmp <= 0 : cmp >= 0) best = i;
    else if (type === 1 ? cmp > 0 : cmp < 0) break;
  }
  return best;
}

const FUNCTIONS: Record<string, Fn> = {
  SUM: (args) => {
    const n = numbers(args);
    return isError(n) ? n : n.reduce((a, b) => a + b, 0);
  },
  PRODUCT: (args) => {
    const n = numbers(args);
    return isError(n) ? n : n.reduce((a, b) => a * b, 1);
  },
  AVERAGE: (args) => {
    const n = numbers(args);
    return isError(n) ? n : n.length ? n.reduce((a, b) => a + b, 0) / n.length : ERR.div0;
  },
  MIN: (args) => {
    const n = numbers(args);
    return isError(n) ? n : n.length ? Math.min(...n) : 0;
  },
  MAX: (args) => {
    const n = numbers(args);
    return isError(n) ? n : n.length ? Math.max(...n) : 0;
  },
  MEDIAN: (args) => {
    const n = numbers(args);
    if (isError(n)) return n;
    if (!n.length) return ERR.num;
    const sorted = [...n].sort((a, b) => a - b);
    const mid = Math.floor(sorted.length / 2);
    return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
  },
  STDEV: (args) => {
    const n = numbers(args);
    return isError(n) ? n : stat(n, 'stdev', true);
  },
  STDEVP: (args) => {
    const n = numbers(args);
    return isError(n) ? n : stat(n, 'stdev', false);
  },
  VAR: (args) => {
    const n = numbers(args);
    return isError(n) ? n : stat(n, 'var', true);
  },
  VARP: (args) => {
    const n = numbers(args);
    return isError(n) ? n : stat(n, 'var', false);
  },
  COUNT: (args) => {
    let count = 0;
    for (const arg of args) {
      if (isMatrix(arg)) count += arg.flat().filter((v) => typeof v === 'number').length;
      else if (typeof arg === 'number' || (typeof arg === 'string' && !isError(toNumber(arg)) && arg.trim() !== '')) count++;
    }
    return count;
  },
  COUNTA: (args) => args.flatMap(flatten).filter((v) => v !== null && v !== '').length,
  COUNTBLANK: (args) => args.flatMap(flatten).filter((v) => v === null || v === '').length,
  LARGE: (args) => {
    const n = numbers([args[0]]);
    const k = num(args[1]);
    if (isError(n)) return n;
    if (isError(k)) return k;
    const sorted = [...n].sort((a, b) => b - a);
    return sorted[k - 1] ?? ERR.num;
  },
  SMALL: (args) => {
    const n = numbers([args[0]]);
    const k = num(args[1]);
    if (isError(n)) return n;
    if (isError(k)) return k;
    const sorted = [...n].sort((a, b) => a - b);
    return sorted[k - 1] ?? ERR.num;
  },
  SUMPRODUCT: (args) => {
    const matrices = args.map(matrixOf);
    const rows = matrices[0].length;
    const cols = matrices[0][0]?.length ?? 0;
    let total = 0;
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        let product = 1;
        for (const matrix of matrices) {
          const cell = matrix[r]?.[c];
          if (isError(cell)) return cell;
          product *= typeof cell === 'number' ? cell : 0;
        }
        total += product;
      }
    }
    return total;
  },
  ABS: (a) => map1(a, Math.abs),
  SQRT: (a) => map1(a, (x) => (x < 0 ? ERR.num : Math.sqrt(x))),
  EXP: (a) => map1(a, Math.exp),
  LN: (a) => map1(a, (x) => (x <= 0 ? ERR.num : Math.log(x))),
  LOG10: (a) => map1(a, (x) => (x <= 0 ? ERR.num : Math.log10(x))),
  LOG: (a) => {
    const x = num(a[0]);
    const base = num(a[1], 10);
    const err = firstError(x, base);
    if (err) return err;
    return (x as number) <= 0 || (base as number) <= 0 ? ERR.num : Math.log(x as number) / Math.log(base as number);
  },
  INT: (a) => map1(a, Math.floor),
  TRUNC: (a) => map1(a, Math.trunc),
  SIGN: (a) => map1(a, Math.sign),
  PI: () => Math.PI,
  SIN: (a) => map1(a, Math.sin),
  COS: (a) => map1(a, Math.cos),
  TAN: (a) => map1(a, Math.tan),
  RAND: () => Math.random(),
  RANDBETWEEN: (a) => {
    const lo = num(a[0]);
    const hi = num(a[1]);
    const err = firstError(lo, hi);
    return err ?? Math.floor((lo as number) + Math.random() * ((hi as number) - (lo as number) + 1));
  },
  POWER: (a) => {
    const x = num(a[0]);
    const y = num(a[1]);
    return firstError(x, y) ?? binary('^', x as number, y as number);
  },
  MOD: (a) => {
    const x = num(a[0]);
    const y = num(a[1]);
    const err = firstError(x, y);
    if (err) return err;
    if (y === 0) return ERR.div0;
    return (x as number) - (y as number) * Math.floor((x as number) / (y as number));
  },
  ROUND: (a) => round(a, 'round'),
  ROUNDUP: (a) => round(a, 'up'),
  ROUNDDOWN: (a) => round(a, 'down'),
  CEILING: (a) => {
    const x = num(a[0]);
    const sig = num(a[1], 1);
    const err = firstError(x, sig);
    return err ?? ((sig as number) === 0 ? 0 : Math.ceil((x as number) / (sig as number)) * (sig as number));
  },
  FLOOR: (a) => {
    const x = num(a[0]);
    const sig = num(a[1], 1);
    const err = firstError(x, sig);
    return err ?? ((sig as number) === 0 ? 0 : Math.floor((x as number) / (sig as number)) * (sig as number));
  },
  SUMIF: (a) => {
    const range = matrixOf(a[0]).flat();
    const test = criterion(a[1]);
    const sumRange = a[2] === undefined ? range : matrixOf(a[2]).flat();
    let total = 0;
    range.forEach((value, i) => {
      const v = sumRange[i];
      if (test(value) && typeof v === 'number') total += v;
    });
    return total;
  },
  AVERAGEIF: (a) => {
    const range = matrixOf(a[0]).flat();
    const test = criterion(a[1]);
    const avgRange = a[2] === undefined ? range : matrixOf(a[2]).flat();
    const picked = range.map((v, i) => (test(v) ? avgRange[i] : undefined)).filter((v): v is number => typeof v === 'number');
    return picked.length ? picked.reduce((x, y) => x + y, 0) / picked.length : ERR.div0;
  },
  COUNTIF: (a) => {
    const test = criterion(a[1]);
    return matrixOf(a[0]).flat().filter(test).length;
  },
  SUMIFS: (a) => multiCriteria(a, 'sum'),
  COUNTIFS: (a) => multiCriteria([null, ...a], 'count'),
  AVERAGEIFS: (a) => multiCriteria(a, 'avg'),
  AND: (a) => logical(a, (x, y) => x && y),
  OR: (a) => logical(a, (x, y) => x || y),
  XOR: (a) => logical(a, (x, y) => x !== y),
  NOT: (a) => {
    const b = toBool(scalar(a[0] ?? null));
    return isError(b) ? b : !b;
  },
  TRUE: () => true,
  FALSE: () => false,
  CONCAT: (a) => a.flatMap(flatten).map(toText).join(''),
  CONCATENATE: (a) => a.map((x) => toText(scalar(x))).join(''),
  TEXTJOIN: (a) => {
    const delimiter = str(a[0]);
    const skipEmpty = toBool(scalar(a[1] ?? false)) === true;
    return a
      .slice(2)
      .flatMap(flatten)
      .filter((v) => !skipEmpty || (v !== null && v !== ''))
      .map(toText)
      .join(delimiter);
  },
  LEN: (a) => str(a[0]).length,
  UPPER: (a) => str(a[0]).toUpperCase(),
  LOWER: (a) => str(a[0]).toLowerCase(),
  PROPER: (a) => str(a[0]).toLowerCase().replace(/(^|[^\p{L}\p{N}])(\p{L})/gu, (_m, sep, ch) => sep + ch.toUpperCase()),
  TRIM: (a) => str(a[0]).trim().replace(/\s+/g, ' '),
  LEFT: (a) => {
    const n = num(a[1], 1);
    return isError(n) ? n : n < 0 ? ERR.value : str(a[0]).slice(0, n);
  },
  RIGHT: (a) => {
    const n = num(a[1], 1);
    return isError(n) ? n : n < 0 ? ERR.value : n === 0 ? '' : str(a[0]).slice(-n);
  },
  MID: (a) => {
    const start = num(a[1]);
    const len = num(a[2]);
    const err = firstError(start, len);
    return err ?? ((start as number) < 1 || (len as number) < 0 ? ERR.value : str(a[0]).substr((start as number) - 1, len as number));
  },
  FIND: (a) => findText(a, true),
  SEARCH: (a) => findText(a, false),
  SUBSTITUTE: (a) => {
    const text = str(a[0]);
    const from = str(a[1]);
    const to = str(a[2]);
    if (!from) return text;
    if (a[3] === undefined) return text.split(from).join(to);
    const instance = num(a[3]);
    if (isError(instance)) return instance;
    let count = 0;
    let index = -1;
    while ((index = text.indexOf(from, index + 1)) !== -1) {
      if (++count === instance) return text.slice(0, index) + to + text.slice(index + from.length);
    }
    return text;
  },
  REPLACE: (a) => {
    const start = num(a[1]);
    const len = num(a[2]);
    const err = firstError(start, len);
    if (err) return err;
    const text = str(a[0]);
    return text.slice(0, (start as number) - 1) + str(a[3]) + text.slice((start as number) - 1 + (len as number));
  },
  REPT: (a) => {
    const n = num(a[1]);
    return isError(n) ? n : n < 0 ? ERR.value : str(a[0]).repeat(n);
  },
  VALUE: (a) => {
    const n = toNumber(scalar(a[0] ?? null));
    return n;
  },
  TEXT: (a) => {
    const v = scalar(a[0] ?? null);
    return isError(v) ? v : formatValue(typeof v === 'string' && !isError(toNumber(v)) ? (toNumber(v) as number) : v, str(a[1]));
  },
  EXACT: (a) => str(a[0]) === str(a[1]),
  CHAR: (a) => map1(a, (x) => String.fromCharCode(x)),
  CODE: (a) => str(a[0]).charCodeAt(0) || ERR.value,
  ISNUMBER: (a) => typeof scalar(a[0] ?? null) === 'number',
  ISTEXT: (a) => typeof scalar(a[0] ?? null) === 'string' && scalar(a[0]) !== '',
  ISBLANK: (a) => scalar(a[0] ?? null) === null,
  ISERROR: (a) => isError(scalar(a[0] ?? null)),
  ISNA: (a) => scalar(a[0] ?? null) === ERR.na,
  ISLOGICAL: (a) => typeof scalar(a[0] ?? null) === 'boolean',
  NA: () => ERR.na,
  TODAY: () => Math.floor(dateToSerial(new Date())),
  NOW: () => dateToSerial(new Date()),
  DATE: (a) => {
    const y = num(a[0]);
    const m = num(a[1]);
    const d = num(a[2]);
    const err = firstError(y, m, d);
    return err ?? Math.round(Date.UTC(y as number, (m as number) - 1, d as number) / 86_400_000 + 25569);
  },
  YEAR: (a) => dateField(a, (d) => d.getUTCFullYear()),
  MONTH: (a) => dateField(a, (d) => d.getUTCMonth() + 1),
  DAY: (a) => dateField(a, (d) => d.getUTCDate()),
  HOUR: (a) => dateField(a, (d) => d.getUTCHours()),
  MINUTE: (a) => dateField(a, (d) => d.getUTCMinutes()),
  SECOND: (a) => dateField(a, (d) => d.getUTCSeconds()),
  WEEKDAY: (a) => dateField(a, (d) => d.getUTCDay() + 1),
  EDATE: (a) => {
    const serial = num(a[0]);
    const months = num(a[1]);
    const err = firstError(serial, months);
    if (err) return err;
    const d = serialToDate(serial as number);
    d.setUTCMonth(d.getUTCMonth() + (months as number));
    return Math.floor(d.getTime() / 86_400_000 + 25569);
  },
  DATEDIF: (a) => {
    const s = num(a[0]);
    const e = num(a[1]);
    const err = firstError(s, e);
    if (err) return err;
    const unit = str(a[2]).toUpperCase();
    const from = serialToDate(s as number);
    const to = serialToDate(e as number);
    if (to < from) return ERR.num;
    const months = (to.getUTCFullYear() - from.getUTCFullYear()) * 12 + to.getUTCMonth() - from.getUTCMonth() - (to.getUTCDate() < from.getUTCDate() ? 1 : 0);
    return unit === 'D' ? Math.floor((e as number) - (s as number)) : unit === 'M' ? months : unit === 'Y' ? Math.floor(months / 12) : ERR.value;
  },
  ROW: (a, ctx) => {
    const ref = a[0];
    return ref === undefined ? ctx.origin.row + 1 : ctx.origin.row + 1;
  },
  COLUMN: (_a, ctx) => ctx.origin.col + 1,
  ROWS: (a) => matrixOf(a[0]).length,
  COLUMNS: (a) => matrixOf(a[0])[0]?.length ?? 0,
  CHOOSE: (a) => {
    const index = num(a[0]);
    if (isError(index)) return index;
    return a[Math.trunc(index)] ?? ERR.value;
  },
  INDEX: (a) => {
    const matrix = matrixOf(a[0]);
    const r = num(a[1], 1);
    const c = num(a[2], 1);
    const err = firstError(r, c);
    if (err) return err;
    const row = (r as number) === 0 ? 0 : (r as number) - 1;
    const col = (c as number) === 0 ? 0 : (c as number) - 1;
    if (matrix.length === 1 && a[2] === undefined) return matrix[0][row] ?? ERR.ref;
    return matrix[row]?.[col] ?? ERR.ref;
  },
  MATCH: (a) => {
    const needle = scalar(a[0] ?? null);
    const type = num(a[2], 1);
    if (isError(type)) return type;
    const index = lookupMatch(needle, matrixOf(a[1]).flat(), type);
    return index === -1 ? ERR.na : index + 1;
  },
  VLOOKUP: (a) => {
    const needle = scalar(a[0] ?? null);
    const matrix = matrixOf(a[1]);
    const col = num(a[2]);
    if (isError(col)) return col;
    const exact = a[3] !== undefined && toBool(scalar(a[3])) === false;
    const index = lookupMatch(needle, matrix.map((row) => row[0]), exact ? 0 : 1);
    if (index === -1) return ERR.na;
    return matrix[index][col - 1] ?? ERR.ref;
  },
  HLOOKUP: (a) => {
    const needle = scalar(a[0] ?? null);
    const matrix = matrixOf(a[1]);
    const row = num(a[2]);
    if (isError(row)) return row;
    const exact = a[3] !== undefined && toBool(scalar(a[3])) === false;
    const index = lookupMatch(needle, matrix[0], exact ? 0 : 1);
    if (index === -1) return ERR.na;
    return matrix[row - 1]?.[index] ?? ERR.ref;
  },
  XLOOKUP: (a) => {
    const needle = scalar(a[0] ?? null);
    const lookup = matrixOf(a[1]).flat();
    const result = matrixOf(a[2]).flat();
    const index = lookupMatch(needle, lookup, 0);
    if (index === -1) return a[3] === undefined ? ERR.na : a[3];
    return result[index] ?? ERR.ref;
  },
  TRANSPOSE: (a) => {
    const m = matrixOf(a[0]);
    return m[0]?.map((_v, c) => m.map((row) => row[c])) ?? [[null]];
  },
};

function map1(args: Operand[], fn: (n: number) => number | FormulaError | string): Operand {
  const n = num(args[0]);
  if (isError(n)) return n;
  return fn(n);
}

function round(args: Operand[], mode: 'round' | 'up' | 'down'): Operand {
  const x = num(args[0]);
  const digits = num(args[1], 0);
  const err = firstError(x, digits);
  return err ?? roundTo(x as number, digits as number, mode);
}

function logical(args: Operand[], fn: (a: boolean, b: boolean) => boolean): Operand {
  let acc: boolean | undefined;
  for (const value of args.flatMap(flatten)) {
    if (value === null || value === '') continue;
    const b = toBool(value);
    if (isError(b)) return b;
    acc = acc === undefined ? b : fn(acc, b);
  }
  return acc ?? ERR.value;
}

function findText(args: Operand[], caseSensitive: boolean): Operand {
  const needle = str(args[0]);
  const hay = str(args[1]);
  const start = num(args[2], 1);
  if (isError(start)) return start;
  const index = caseSensitive ? hay.indexOf(needle, start - 1) : hay.toLowerCase().indexOf(needle.toLowerCase(), start - 1);
  return index === -1 ? ERR.value : index + 1;
}

function dateField(args: Operand[], pick: (d: Date) => number): Operand {
  const serial = num(args[0]);
  return isError(serial) ? serial : pick(serialToDate(serial));
}

function multiCriteria(args: Operand[], mode: 'sum' | 'count' | 'avg'): Operand {
  const sumRange = mode === 'count' ? null : matrixOf(args[0]).flat();
  const pairs = mode === 'count' ? args.slice(1) : args.slice(1);
  const ranges: Value[][] = [];
  const tests: ((value: Value) => boolean)[] = [];
  for (let i = 0; i + 1 < pairs.length; i += 2) {
    ranges.push(matrixOf(pairs[i]).flat());
    tests.push(criterion(pairs[i + 1]));
  }
  const length = ranges[0]?.length ?? 0;
  let total = 0;
  let count = 0;
  for (let i = 0; i < length; i++) {
    if (ranges.every((range, k) => tests[k](range[i]))) {
      count++;
      const v = sumRange?.[i];
      if (typeof v === 'number') total += v;
    }
  }
  return mode === 'count' ? count : mode === 'sum' ? total : count ? total / count : ERR.div0;
}

function callFunction(node: Extract<Node, { t: 'func' }>, ctx: EvalContext): Operand {
  const { name, args } = node;
  // Lazy functions: only the taken branch is evaluated.
  if (name === 'IF') {
    const cond = toBool(scalar(evaluate(args[0], ctx)));
    if (isError(cond)) return cond;
    if (cond) return args[1] ? evaluate(args[1], ctx) : true;
    return args[2] ? evaluate(args[2], ctx) : false;
  }
  if (name === 'IFERROR' || name === 'IFNA') {
    const value = evaluate(args[0], ctx);
    const probe = scalar(value);
    const failed = name === 'IFNA' ? probe === ERR.na || (isError(probe) && probe.code === '#N/A') : isError(probe);
    return failed ? (args[1] ? evaluate(args[1], ctx) : null) : value;
  }
  if (name === 'IFS') {
    for (let i = 0; i + 1 < args.length; i += 2) {
      const cond = toBool(scalar(evaluate(args[i], ctx)));
      if (isError(cond)) return cond;
      if (cond) return evaluate(args[i + 1], ctx);
    }
    return ERR.na;
  }
  if (name === 'SWITCH') {
    const subject = scalar(evaluate(args[0], ctx));
    let i = 1;
    for (; i + 1 < args.length; i += 2) {
      if (compareValues(subject, scalar(evaluate(args[i], ctx))) === 0) return evaluate(args[i + 1], ctx);
    }
    return i < args.length ? evaluate(args[i], ctx) : ERR.na;
  }
  if (name === 'ROW' && args[0]?.t === 'ref') return args[0].row + 1;
  if (name === 'COLUMN' && args[0]?.t === 'ref') return args[0].col + 1;

  const fn = FUNCTIONS[name];
  if (!fn) return ERR.name;
  const values = args.map((arg) => evaluate(arg, ctx));
  return fn(values, ctx);
}

/** Names of the supported functions, for autocomplete. */
export const FUNCTION_NAMES: readonly string[] = [...Object.keys(FUNCTIONS), 'IF', 'IFERROR', 'IFNA', 'IFS', 'SWITCH'].sort();
