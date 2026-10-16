import { Pipe, PipeTransform, inject } from '@angular/core';
import { DocumentWorkspaceService } from '../data/document-workspace.service';

/**
 * Formats a date with the active workspace's time zone and date format
 * (`yyyy-MM-dd`, `dd/MM/yyyy` or `MM/dd/yyyy`). Falls back to UTC and ISO order when none is loaded.
 */
@Pipe({ name: 'wsDate', pure: false })
export class WorkspaceDatePipe implements PipeTransform {
  private readonly workspaces = inject(DocumentWorkspaceService);

  transform(value: string | number | Date | null | undefined, mode: 'date' | 'datetime' = 'datetime'): string {
    if (!value) return '';
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) return '';

    const active = this.workspaces.active();
    const timeZone = active?.timezone || 'UTC';
    const pattern = active?.dateFormat || 'yyyy-MM-dd';

    let parts: Intl.DateTimeFormatPart[];
    try {
      parts = new Intl.DateTimeFormat('en-GB', {
        timeZone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      }).formatToParts(date);
    } catch {
      return date.toISOString();
    }
    const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
    const day = `${get('day')}`;
    const month = `${get('month')}`;
    const year = `${get('year')}`;
    const datePart =
      pattern === 'dd/MM/yyyy' ? `${day}/${month}/${year}` : pattern === 'MM/dd/yyyy' ? `${month}/${day}/${year}` : `${year}-${month}-${day}`;
    return mode === 'date' ? datePart : `${datePart} ${get('hour')}:${get('minute')}`;
  }
}
