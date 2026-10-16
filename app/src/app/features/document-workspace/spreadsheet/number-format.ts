/**
 * Excel-style number and date formats: General, 0.00, #,##0.00, 0%, "$"#,##0.00, yyyy-mm-dd, dd/mm/yyyy, h:mm, @ ...
 * Dates are Excel serial numbers (1900 system, epoch 1899-12-30).
 */

const MS_PER_DAY = 86_400_000;
const EPOCH = Date.UTC(1899, 11, 30);

export function serialToDate(serial: number): Date {
  return new Date(EPOCH + Math.round(serial * MS_PER_DAY));
}

export function dateToSerial(date: Date): number {
  return (Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes(), date.getSeconds()) - EPOCH) / MS_PER_DAY;
}

const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const DAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

function pad(value: number, length = 2): string {
  return String(value).padStart(length, '0');
}

/** True when the pattern is made of date/time tokens rather than digit placeholders. */
export function isDateFormat(format: string): boolean {
  const cleaned = format.replace(/"[^"]*"|\[[^\]]*]|\\./g, '');
  return /[ymdhs]/i.test(cleaned) && !/[0#?]/.test(cleaned);
}

function formatDate(serial: number, format: string): string {
  const date = serialToDate(serial);
  const y = date.getUTCFullYear();
  const mo = date.getUTCMonth();
  const d = date.getUTCDate();
  const h = date.getUTCHours();
  const mi = date.getUTCMinutes();
  const s = date.getUTCSeconds();
  const twelve = /am\/pm|a\/p/i.test(format);
  let out = '';
  let i = 0;
  let lastWasHour = false;
  while (i < format.length) {
    const rest = format.slice(i);
    let m: RegExpExecArray | null;
    if ((m = /^"([^"]*)"/.exec(rest))) {
      out += m[1];
      i += m[0].length;
    } else if ((m = /^\\(.)/.exec(rest))) {
      out += m[1];
      i += 2;
    } else if ((m = /^(yyyy|yy)/i.exec(rest))) {
      out += m[1].length === 4 ? pad(y, 4) : pad(y % 100);
      i += m[0].length;
    } else if ((m = /^(mmmm|mmm|mm|m)/i.exec(rest))) {
      // "m" right after an hour or before seconds means minutes.
      const nextIsSeconds = /^[^a-z]*s/i.test(rest.slice(m[0].length));
      if (lastWasHour || nextIsSeconds) {
        out += m[1].length >= 2 ? pad(mi) : String(mi);
      } else if (m[1].length >= 4) out += MONTHS[mo];
      else if (m[1].length === 3) out += MONTHS[mo].slice(0, 3);
      else out += m[1].length === 2 ? pad(mo + 1) : String(mo + 1);
      i += m[0].length;
      lastWasHour = false;
    } else if ((m = /^(dddd|ddd|dd|d)/i.exec(rest))) {
      const weekday = date.getUTCDay();
      if (m[1].length >= 4) out += DAYS[weekday];
      else if (m[1].length === 3) out += DAYS[weekday].slice(0, 3);
      else out += m[1].length === 2 ? pad(d) : String(d);
      i += m[0].length;
    } else if ((m = /^(hh|h)/i.exec(rest))) {
      const hour = twelve ? h % 12 || 12 : h;
      out += m[1].length === 2 ? pad(hour) : String(hour);
      i += m[0].length;
      lastWasHour = true;
      continue;
    } else if ((m = /^(ss|s)/i.exec(rest))) {
      out += m[1].length === 2 ? pad(s) : String(s);
      i += m[0].length;
    } else if ((m = /^(am\/pm|a\/p)/i.exec(rest))) {
      out += h >= 12 ? 'PM' : 'AM';
      i += m[0].length;
    } else {
      out += format[i];
      i++;
    }
    lastWasHour = lastWasHour && format[i - 1] === ':';
  }
  return out;
}

function groupThousands(integer: string): string {
  return integer.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function formatNumeric(value: number, section: string): string {
  const percent = section.includes('%');
  let n = percent ? value * 100 : value;
  const literalFree = section.replace(/"([^"]*)"|\\(.)/g, (_all, quoted, escaped) => `\u0000${quoted ?? escaped}\u0001`);
  const start = literalFree.search(/[0#?]/);
  if (start === -1) return section.replace(/"([^"]*)"|\\(.)/g, '$1$2');
  const end = Math.max(literalFree.lastIndexOf('0'), literalFree.lastIndexOf('#'), literalFree.lastIndexOf('?')) + 1;
  const prefix = literalFree.slice(0, start).replace(/\u0000|\u0001/g, '');
  const suffix = literalFree.slice(end).replace(/\u0000|\u0001/g, '');
  const pattern = literalFree.slice(start, end);

  const decimalsPattern = pattern.includes('.') ? pattern.split('.')[1] : '';
  const decimals = (decimalsPattern.match(/[0#?]/g) ?? []).length;
  const minDecimals = (decimalsPattern.match(/0/g) ?? []).length;
  const thousands = /,/.test(pattern.split('.')[0]);
  const integerPattern = pattern.split('.')[0].replace(/,/g, '');
  const minInteger = (integerPattern.match(/0/g) ?? []).length;

  const negative = n < 0;
  n = Math.abs(n);
  const fixed = n.toFixed(decimals);
  let [integer, fraction = ''] = fixed.split('.');
  while (fraction.length > minDecimals && fraction.endsWith('0')) fraction = fraction.slice(0, -1);
  if (integer.length < minInteger) integer = integer.padStart(minInteger, '0');
  if (integer === '0' && minInteger === 0 && fraction.length === 0 && decimals === 0 && n === 0) integer = '0';
  if (thousands) integer = groupThousands(integer);
  const body = fraction ? `${integer}.${fraction}` : integer;
  const sign = negative && Number(fixed) !== 0 ? '-' : '';
  return `${sign}${prefix}${body}${suffix}`;
}

/** Display text of a value under an Excel format pattern. */
export function formatValue(value: string | number | boolean | null, format?: string | null): string {
  if (value === null || value === undefined || value === '') return '';
  if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
  if (typeof value === 'string') {
    return format && format.includes('@') ? format.replace(/@/g, value).replace(/"/g, '') : value;
  }
  if (!Number.isFinite(value)) return '#NUM!';
  if (!format || format.toLowerCase() === 'general') return generalNumber(value);
  const sections = format.split(';');
  let section = sections[0];
  if (value < 0 && sections.length > 1) section = sections[1];
  else if (value === 0 && sections.length > 2) section = sections[2];
  const sectionValue = value < 0 && sections.length > 1 ? Math.abs(value) : value;
  if (isDateFormat(section)) return formatDate(value, section);
  return formatNumeric(sectionValue, section);
}

/** Excel "General": up to 10 significant digits, scientific for very large or small values. */
export function generalNumber(value: number): string {
  if (Number.isInteger(value) && Math.abs(value) < 1e11) return String(value);
  const abs = Math.abs(value);
  if (abs !== 0 && (abs >= 1e11 || abs < 1e-4)) return value.toExponential(5).replace(/\.?0+e/, 'e').replace('e+', 'E+').replace('e-', 'E-');
  return String(Number(value.toPrecision(10)));
}

export const NUMBER_FORMATS: readonly { id: string; label: string; format: string; sample: string }[] = [
  { id: 'general', label: 'Automatic', format: 'General', sample: '1234.5' },
  { id: 'number', label: 'Number', format: '#,##0.00', sample: '1,234.50' },
  { id: 'integer', label: 'Integer', format: '0', sample: '1235' },
  { id: 'percent', label: 'Percent', format: '0.00%', sample: '12.34%' },
  { id: 'currency', label: 'Currency', format: '"$"#,##0.00', sample: '$1,234.50' },
  { id: 'date', label: 'Date', format: 'yyyy-mm-dd', sample: '2026-10-16' },
  { id: 'datetime', label: 'Date and time', format: 'yyyy-mm-dd hh:mm', sample: '2026-10-16 14:30' },
  { id: 'time', label: 'Time', format: 'hh:mm:ss', sample: '14:30:00' },
  { id: 'text', label: 'Plain text', format: '@', sample: 'abc' },
];
