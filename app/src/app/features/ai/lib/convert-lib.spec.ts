import { describe, expect, it } from 'vitest';
import { DiagramResult, TableResult } from '@core/models/ai/ai.model';
import { diagramElements } from './diagram-builder';
import { plainTextForSpeech, splitForSpeech } from './speech';
import { cellValue, tableAsMarkdown, tableAsTsv, workbookContent } from './table-builder';
import { fileProblem } from '../tools/scan-tool.component';

describe('plainTextForSpeech', () => {
  it('drops Markdown symbols and turns headings into sentences', () => {
    const text = plainTextForSpeech('# Fotosíntesis\n\nLas **plantas** usan [luz](https://x.org) y `CO2`.\n\n- uno\n- [x] dos\n1. tres');
    expect(text.split('\n')).toEqual(['Fotosíntesis.', 'Las plantas usan luz y CO2.', 'uno', 'dos', 'tres']);
  });

  it('skips code blocks, rules and table separators, and reads table rows as a sentence', () => {
    const text = plainTextForSpeech('```js\nconsole.log(1)\n```\n\n---\n\n| Tema | Año |\n| --- | --- |\n| Mitosis | 1882 |');
    expect(text).toBe('Tema, Año.\nMitosis, 1882.');
  });

  it('keeps callout titles, wikilinks and tags readable', () => {
    const text = plainTextForSpeech('> [!tip] Consejo útil\n> Revisá [[Mitosis|la mitosis]] #biologia');
    expect(text).toBe('Consejo útil\nRevisá la mitosis biologia');
  });

  it('is empty for an empty note', () => {
    expect(plainTextForSpeech('')).toBe('');
    expect(plainTextForSpeech('```\ncode only\n```')).toBe('');
  });
});

describe('splitForSpeech', () => {
  it('cuts at sentence ends and never beyond the limit', () => {
    const sentence = 'Esta es una oración de prueba bastante normal. ';
    const pieces = splitForSpeech(sentence.repeat(30), 200);
    expect(pieces.length).toBeGreaterThan(5);
    expect(pieces.every((p) => p.length <= 200)).toBe(true);
    expect(pieces.join(' ').split('oración').length - 1).toBe(30);
  });

  it('splits a very long sentence without losing words', () => {
    const long = Array.from({ length: 120 }, (_, i) => `palabra${i}`).join(' ');
    const pieces = splitForSpeech(long, 100);
    expect(pieces.every((p) => p.length <= 100)).toBe(true);
    expect(pieces.join(' ')).toBe(long);
  });

  it('treats each line as its own piece and ignores blanks', () => {
    expect(splitForSpeech('Uno.\n\nDos.\n')).toEqual(['Uno.', 'Dos.']);
    expect(splitForSpeech('   ')).toEqual([]);
  });
});

describe('table builder', () => {
  const table: TableResult = {
    title: 'Ventas',
    headers: ['Producto', 'Cantidad', 'Código'],
    rows: [
      { cells: ['Lápiz', '1,200', '007'] },
      { cells: ['Goma | grande', '3.5', ''] },
      { cells: ['Cuaderno', '-4', '12345678901234567890'] },
    ],
  };

  it('keeps clear numbers as numbers and everything else as written', () => {
    expect(cellValue('1,200')).toBe(1200);
    expect(cellValue('3.5')).toBe(3.5);
    expect(cellValue('-4')).toBe(-4);
    expect(cellValue('007')).toBe('007');
    expect(cellValue('12345678901234567890')).toBe('12345678901234567890');
    expect(cellValue('1,2')).toBe('1,2');
    expect(cellValue('abc')).toBe('abc');
  });

  it('builds a workbook the spreadsheet understands', () => {
    const wb = JSON.parse(workbookContent(table)) as {
      version: number;
      activeSheet: string;
      sheets: { cells: Record<string, { v: unknown; s?: { b?: boolean } }>; colWidths: Record<string, number>; frozenRows: number; rows: number; cols: number }[];
    };
    const sheet = wb.sheets[0];
    expect(wb.version).toBe(1);
    expect(sheet.cells['A1']).toEqual({ v: 'Producto', s: { b: true, bg: '#eef2ff' } });
    expect(sheet.cells['B2'].v).toBe(1200);
    expect(sheet.cells['C2'].v).toBe('007');
    expect(sheet.cells['C3']).toBeUndefined(); // an empty cell stays empty
    expect(sheet.frozenRows).toBe(1);
    expect(sheet.colWidths[0]).toBeGreaterThanOrEqual(80);
    expect(sheet.rows).toBeGreaterThanOrEqual(100);
    expect(sheet.cols).toBeGreaterThanOrEqual(26);
  });

  it('copies as tab-separated text and as Markdown, escaping what would break them', () => {
    expect(tableAsTsv({ ...table, rows: [{ cells: ['a\tb', 'c\nd', 'e'] }] })).toBe('Producto\tCantidad\tCódigo\na b\tc d\te');
    const md = tableAsMarkdown(table);
    expect(md.split('\n')[1]).toBe('| --- | --- | --- |');
    expect(md).toContain('| Goma \\| grande | 3.5 |  |');
  });
});

describe('diagram builder', () => {
  const flow: DiagramResult = {
    title: 't',
    kind: 'FLOWCHART',
    nodes: [
      { id: 'a', label: 'Inicio', shape: 'START', detail: '' },
      { id: 'b', label: 'Hacer algo', shape: 'PROCESS', detail: '' },
      { id: 'c', label: '¿Funciona?', shape: 'DECISION', detail: '' },
      { id: 'd', label: 'Terminar', shape: 'END', detail: '' },
      { id: 'e', label: 'Corregir', shape: 'PROCESS', detail: '' },
    ],
    edges: [
      { from: 'a', to: 'b', label: '' },
      { from: 'b', to: 'c', label: '' },
      { from: 'c', to: 'd', label: 'sí' },
      { from: 'c', to: 'e', label: 'no' },
      { from: 'e', to: 'b', label: '' }, // a loop back
    ],
  };

  function noOverlap(shapes: ReturnType<typeof diagramElements>): void {
    for (let i = 0; i < shapes.length; i++) {
      for (let j = i + 1; j < shapes.length; j++) {
        const a = shapes[i];
        const b = shapes[j];
        const overlap = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
        expect(overlap, `${a.text} / ${b.text}`).toBe(false);
      }
    }
  }

  it('draws a flowchart top to bottom with every arrow glued to existing shapes', () => {
    const els = diagramElements(flow);
    const shapes = els.filter((e) => e.type !== 'arrow');
    const arrows = els.filter((e) => e.type === 'arrow');
    const ids = new Set(shapes.map((s) => s.id));
    expect(shapes).toHaveLength(5);
    expect(arrows).toHaveLength(5);
    expect(arrows.every((a) => ids.has(a.from!) && ids.has(a.to!))).toBe(true);
    const y = (text: string) => shapes.find((s) => s.text === text)!.y;
    expect(y('Inicio')).toBeLessThan(y('Hacer algo'));
    expect(y('Hacer algo')).toBeLessThan(y('¿Funciona?'));
    expect(y('¿Funciona?')).toBeLessThan(y('Terminar'));
    expect(y('Corregir')).toBeGreaterThan(y('¿Funciona?'));
    noOverlap(shapes);
  });

  it('draws the loop back as a curve and the branches of a decision as elbows', () => {
    const arrows = diagramElements(flow).filter((e) => e.type === 'arrow');
    expect(arrows.find((a) => a.text === '' && a.route === 'curve')).toBeDefined();
    expect(arrows.filter((a) => a.route === 'elbow').map((a) => a.text).sort()).toEqual(['no', 'sí']);
  });

  it('uses the right shape for each step', () => {
    const shapes = diagramElements(flow).filter((e) => e.type !== 'arrow');
    expect(shapes.find((s) => s.text === '¿Funciona?')!.type).toBe('diamond');
    expect(shapes.find((s) => s.text === 'Inicio')!.type).toBe('ellipse');
    expect(shapes.find((s) => s.text === 'Hacer algo')!.type).toBe('box');
  });

  it('survives a disconnected graph and a graph that is all one cycle', () => {
    const loose: DiagramResult = { ...flow, edges: [{ from: 'a', to: 'b', label: '' }] };
    expect(diagramElements(loose).filter((e) => e.type !== 'arrow')).toHaveLength(5);
    const ring: DiagramResult = {
      ...flow,
      nodes: flow.nodes.slice(0, 3),
      edges: [
        { from: 'a', to: 'b', label: '' },
        { from: 'b', to: 'c', label: '' },
        { from: 'c', to: 'a', label: '' },
      ],
    };
    noOverlap(diagramElements(ring).filter((e) => e.type !== 'arrow'));
  });

  it('lays a timeline along a line, alternating above and below, with the dates', () => {
    const els = diagramElements({
      title: 't', kind: 'TIMELINE', edges: [],
      nodes: ['1789', '1799', '1815', '1830'].map((d, i) => ({ id: `n${i}`, label: `Hecho ${i}`, shape: 'EVENT' as const, detail: d })),
    });
    const boxes = els.filter((e) => e.type === 'box');
    expect(boxes).toHaveLength(4);
    expect(boxes[0].y).toBeLessThan(0);
    expect(boxes[1].y).toBeGreaterThan(0);
    expect(boxes.map((b) => b.x)).toEqual([...boxes.map((b) => b.x)].sort((a, b) => a - b));
    expect(els.filter((e) => e.type === 'text').map((e) => e.text)).toEqual(['1789', '1799', '1815', '1830']);
    expect(els.some((e) => e.type === 'line')).toBe(true);
    noOverlap(els.filter((e) => e.type === 'box'));
  });

  it('places a cycle on a circle without overlaps, closing the loop', () => {
    const nodes = Array.from({ length: 7 }, (_, i) => ({ id: `n${i}`, label: `Etapa número ${i}`, shape: 'PROCESS' as const, detail: '' }));
    const edges = nodes.map((n, i) => ({ from: n.id, to: nodes[(i + 1) % nodes.length].id, label: '' }));
    const els = diagramElements({ title: 't', kind: 'CYCLE', nodes, edges });
    expect(els.filter((e) => e.type === 'arrow')).toHaveLength(7);
    noOverlap(els.filter((e) => e.type !== 'arrow'));
  });
});

describe('fileProblem', () => {
  const file = (name: string, type: string, size: number) => ({ name, type, size });

  it('accepts images and PDFs within their limits', () => {
    expect(fileProblem(file('a.png', 'image/png', 1000))).toBeNull();
    expect(fileProblem(file('scan.JPG', '', 1000))).toBeNull();
    expect(fileProblem(file('book.pdf', 'application/pdf', 10 * 1024 * 1024))).toBeNull();
  });

  it('refuses other types and files over the limit', () => {
    expect(fileProblem(file('a.docx', 'application/msword', 10))).toBe('type');
    expect(fileProblem(file('a.png', 'image/png', 6 * 1024 * 1024))).toBe('size');
    expect(fileProblem(file('a.pdf', 'application/pdf', 16 * 1024 * 1024))).toBe('size');
  });
});
