import { TableResult } from '@core/models/ai/ai.model';

const HEADER_STYLE = { b: true, bg: '#eef2ff' };
const NUMBER = /^[-+]?(\d+|\d{1,3}(,\d{3})+)(\.\d+)?$/;
const MIN_WIDTH = 80;
const MAX_WIDTH = 360;

/** What a cell holds: a number when it clearly is one ("1,200.50"), otherwise the text exactly as it is. */
export function cellValue(text: string): string | number {
  const value = text.trim();
  // "007" and phone numbers are labels, not quantities; 16+ digits would lose precision as a number.
  if (!NUMBER.test(value) || /^[-+]?0\d/.test(value) || value.replace(/\D/g, '').length > 15) {
    return text;
  }
  const number = Number(value.replace(/,/g, ''));
  return Number.isFinite(number) ? number : text;
}

/** The table as a workbook with one sheet: a styled header row, numbers as numbers, columns sized to their content. */
export function workbookContent(table: TableResult, sheetName = 'Sheet1'): string {
  const cells: Record<string, { v: string | number; s?: Record<string, unknown> }> = {};
  const colWidths: Record<string, number> = {};
  const letters = (col: number): string => {
    let n = col + 1;
    let out = '';
    while (n > 0) {
      out = String.fromCharCode(65 + ((n - 1) % 26)) + out;
      n = Math.floor((n - 1) / 26);
    }
    return out;
  };
  table.headers.forEach((header, col) => {
    cells[`${letters(col)}1`] = { v: header, s: HEADER_STYLE };
    colWidths[col] = width(header);
  });
  table.rows.forEach((row, r) => {
    row.cells.forEach((text, col) => {
      if (text === '') {
        return;
      }
      cells[`${letters(col)}${r + 2}`] = { v: cellValue(text) };
      colWidths[col] = Math.max(colWidths[col] ?? MIN_WIDTH, width(text));
    });
  });
  const rows = Math.max(100, table.rows.length + 20);
  const cols = Math.max(26, table.headers.length + 4);
  return JSON.stringify({
    version: 1,
    sheets: [{ id: 's1', name: sheetName, rows, cols, cells, colWidths, frozenRows: 1 }],
    activeSheet: 's1',
  });
}

function width(text: string): number {
  return Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, 24 + text.length * 7));
}

/** Tab-separated, which pastes straight into any spreadsheet. */
export function tableAsTsv(table: TableResult): string {
  const clean = (s: string) => s.replace(/[\t\r\n]+/g, ' ');
  return [table.headers.map(clean).join('\t'), ...table.rows.map((r) => r.cells.map(clean).join('\t'))].join('\n');
}

export function tableAsMarkdown(table: TableResult): string {
  const clean = (s: string) => s.replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
  const line = (cells: string[]) => `| ${cells.map(clean).join(' | ')} |`;
  return [line(table.headers), line(table.headers.map(() => '---')), ...table.rows.map((r) => line(r.cells))].join('\n') + '\n';
}
