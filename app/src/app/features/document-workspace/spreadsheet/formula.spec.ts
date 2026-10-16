import { describe, expect, it } from 'vitest';
import {
  ERR,
  EvalContext,
  FormulaError,
  Value,
  adjustForStructureChange,
  evaluateFormula,
  parseFormula,
  shiftFormula,
} from './formula';
import { parseCellKey } from './cell-ref';
import { formatValue } from './number-format';

/** Evaluates a formula against a sheet given as { A1: value | '=formula' }. */
function run(formula: string, cells: Record<string, Value | string> = {}): Value {
  const lookup = (row: number, col: number): Value => {
    for (const [key, raw] of Object.entries(cells)) {
      const address = parseCellKey(key)!;
      if (address.row === row && address.col === col) {
        return typeof raw === 'string' && raw.startsWith('=') ? evaluateFormula(raw, ctx(row, col)) : (raw as Value);
      }
    }
    return null;
  };
  const ctx = (row = 0, col = 0): EvalContext => ({
    sheet: 'Sheet1',
    origin: { row, col },
    cell: (_sheet, r, c) => lookup(r, c),
    lastRow: () => Math.max(0, ...Object.keys(cells).map((k) => parseCellKey(k)!.row)),
    lastCol: () => Math.max(0, ...Object.keys(cells).map((k) => parseCellKey(k)!.col)),
  });
  return evaluateFormula(formula, ctx());
}

const DATA = { A1: 10, A2: 20, A3: 30, B1: 'apple', B2: 'banana', B3: 'apple', C1: 1, C2: 2, C3: 3 };

describe('formula evaluation', () => {
  it('does arithmetic with precedence and parentheses', () => {
    expect(run('=1+2*3')).toBe(7);
    expect(run('=(1+2)*3')).toBe(9);
    expect(run('=2^3^2')).toBe(64);
    expect(run('=-2^2')).toBe(4);
    expect(run('=50%')).toBe(0.5);
    expect(run('=10/4')).toBe(2.5);
  });

  it('reports errors', () => {
    expect(run('=1/0')).toBe(ERR.div0);
    expect(run('=NOPE(1)')).toBe(ERR.name);
    expect(run('="a"+1')).toBe(ERR.value);
    expect(run('=1+')).toBe(ERR.parse);
  });

  it('reads cells and ranges', () => {
    expect(run('=A1+A2', DATA)).toBe(30);
    expect(run('=SUM(A1:A3)', DATA)).toBe(60);
    expect(run('=AVERAGE(A1:A3)', DATA)).toBe(20);
    expect(run('=MAX(A1:A3)-MIN(A1:A3)', DATA)).toBe(20);
    expect(run('=COUNT(A1:B3)', DATA)).toBe(3);
    expect(run('=COUNTA(A1:B3)', DATA)).toBe(6);
    expect(run('=SUM(A:A)', DATA)).toBe(60);
  });

  it('chains formulas stored in cells', () => {
    expect(run('=B1', { A1: 5, B1: '=A1*2' })).toBe(10);
  });

  it('handles conditionals', () => {
    expect(run('=IF(A1>5,"big","small")', DATA)).toBe('big');
    expect(run('=IF(A1>50,"big")', DATA)).toBe(false);
    expect(run('=IFERROR(1/0,"n/a")')).toBe('n/a');
    expect(run('=AND(A1>1,A2>1)', DATA)).toBe(true);
    expect(run('=OR(A1>100,A2>100)', DATA)).toBe(false);
    expect(run('=IFS(A1>100,"a",A1>5,"b")', DATA)).toBe('b');
    expect(run('=SWITCH(2,1,"one",2,"two","other")')).toBe('two');
  });

  it('supports criteria functions', () => {
    expect(run('=COUNTIF(B1:B3,"apple")', DATA)).toBe(2);
    expect(run('=SUMIF(A1:A3,">15")', DATA)).toBe(50);
    expect(run('=SUMIF(B1:B3,"apple",A1:A3)', DATA)).toBe(40);
    expect(run('=COUNTIF(B1:B3,"a*")', DATA)).toBe(2);
    expect(run('=AVERAGEIF(A1:A3,"<=20")', DATA)).toBe(15);
    expect(run('=SUMIFS(A1:A3,B1:B3,"apple",C1:C3,">1")', DATA)).toBe(30);
  });

  it('does text functions', () => {
    expect(run('="a"&"b"&1')).toBe('ab1');
    expect(run('=UPPER("abc")')).toBe('ABC');
    expect(run('=LEFT("hello",2)&RIGHT("hello",3)')).toBe('hello');
    expect(run('=MID("hello",2,3)')).toBe('ell');
    expect(run('=LEN(TRIM("  a   b  "))')).toBe(3);
    expect(run('=SUBSTITUTE("a-b-c","-","+")')).toBe('a+b+c');
    expect(run('=PROPER("hello wORLD")')).toBe('Hello World');
    expect(run('=TEXTJOIN(", ",TRUE,A1:A3)', DATA)).toBe('10, 20, 30');
    expect(run('=TEXT(0.256,"0.0%")')).toBe('25.6%');
  });

  it('looks values up', () => {
    const table = { A1: 'x', B1: 1, A2: 'y', B2: 2, A3: 'z', B3: 3 };
    expect(run('=VLOOKUP("y",A1:B3,2,FALSE)', table)).toBe(2);
    expect(run('=INDEX(B1:B3,3)', table)).toBe(3);
    expect(run('=MATCH("z",A1:A3,0)', table)).toBe(3);
    expect(run('=XLOOKUP("x",A1:A3,B1:B3)', table)).toBe(1);
    expect(run('=VLOOKUP("q",A1:B3,2,FALSE)', table)).toBe(ERR.na);
  });

  it('rounds', () => {
    expect(run('=ROUND(2.345,2)')).toBe(2.35);
    expect(run('=ROUNDDOWN(2.999,1)')).toBe(2.9);
    expect(run('=ROUNDUP(2.001,1)')).toBe(2.1);
    expect(run('=MOD(-3,2)')).toBe(1);
    expect(run('=INT(-1.5)')).toBe(-2);
  });

  it('computes dates', () => {
    expect(run('=YEAR(DATE(2026,10,16))')).toBe(2026);
    expect(run('=DAY(DATE(2026,10,16))')).toBe(16);
    expect(run('=DATE(2026,10,16)-DATE(2026,10,1)')).toBe(15);
  });

  it('propagates errors from referenced cells', () => {
    const result = run('=A1+1', { A1: ERR.ref });
    expect(result).toBeInstanceOf(FormulaError);
    expect((result as FormulaError).code).toBe('#REF!');
  });
});

describe('formula rewriting', () => {
  it('shifts relative references and keeps absolute ones', () => {
    expect(shiftFormula('=A1+$B$2+C$3', 1, 1)).toBe('=B2+$B$2+D$3');
    expect(shiftFormula('=SUM(A1:B2)', 2, 0)).toBe('=SUM(A3:B4)');
    expect(shiftFormula('=A1', -1, 0)).toBe('=#REF!');
  });

  it('adjusts references when rows are inserted or deleted', () => {
    expect(adjustForStructureChange('=A3+A1', 'Sheet1', 'Sheet1', 'row', 1, 1)).toBe('=A4+A1');
    expect(adjustForStructureChange('=SUM(A1:A5)', 'Sheet1', 'Sheet1', 'row', 2, 1)).toBe('=SUM(A1:A6)');
    expect(adjustForStructureChange('=SUM(A1:A5)', 'Sheet1', 'Sheet1', 'row', 1, -2)).toBe('=SUM(A1:A3)');
    expect(adjustForStructureChange('=A2', 'Sheet1', 'Sheet1', 'row', 1, -1)).toBe('=#REF!');
    expect(adjustForStructureChange('=C1', 'Sheet1', 'Sheet1', 'col', 0, 1)).toBe('=D1');
    expect(adjustForStructureChange('=Other!A3', 'Sheet1', 'Sheet1', 'row', 0, 1)).toBe('=Other!A3');
  });

  it('round-trips through print', () => {
    for (const formula of ['=SUM(A1:B2)*2', '=IF(A1>1,"x","y")', "='My sheet'!A1+1", '=-A1', '=(1+2)*3', '=A:A']) {
      expect(parseFormula(shiftFormula(formula, 0, 0))).toEqual(parseFormula(formula));
    }
  });
});

describe('number formats', () => {
  it('formats numbers', () => {
    expect(formatValue(1234.5, 'General')).toBe('1234.5');
    expect(formatValue(1234.5, '#,##0.00')).toBe('1,234.50');
    expect(formatValue(0.256, '0.0%')).toBe('25.6%');
    expect(formatValue(1234.5, '"$"#,##0.00')).toBe('$1,234.50');
    expect(formatValue(-5, '0.00')).toBe('-5.00');
    expect(formatValue(7, '0')).toBe('7');
    expect(formatValue(1e12, 'General')).toContain('E');
  });

  it('formats dates and times', () => {
    expect(formatValue(46311, 'yyyy-mm-dd')).toBe('2026-10-16');
    expect(formatValue(46311.5, 'yyyy-mm-dd hh:mm')).toBe('2026-10-16 12:00');
    expect(formatValue(46311, 'dd/mm/yyyy')).toBe('16/10/2026');
    expect(formatValue(46311, 'mmm d, yyyy')).toBe('Oct 16, 2026');
  });

  it('leaves text alone', () => {
    expect(formatValue('abc', '0.00')).toBe('abc');
    expect(formatValue(true, 'General')).toBe('TRUE');
    expect(formatValue(null, 'General')).toBe('');
  });
});
