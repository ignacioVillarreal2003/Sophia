import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslationService } from '@core/services/i18n';

@Pipe({ name: 'relativeTime', pure: false })
export class RelativeTimePipe implements PipeTransform {
  private readonly i18n = inject(TranslationService);

  transform(value: string | number | Date | null | undefined): string {
    if (!value) return '';
    const date = value instanceof Date ? value : new Date(value);
    const diffMs = date.getTime() - Date.now();
    const abs = Math.abs(diffMs);
    const rtf = new Intl.RelativeTimeFormat(this.i18n.locale(), { numeric: 'auto' });

    const units: [Intl.RelativeTimeFormatUnit, number][] = [
      ['year', 31536000000],
      ['month', 2592000000],
      ['week', 604800000],
      ['day', 86400000],
      ['hour', 3600000],
      ['minute', 60000],
    ];
    for (const [unit, ms] of units) {
      if (abs >= ms) {
        return rtf.format(Math.round(diffMs / ms), unit);
      }
    }
    return rtf.format(Math.round(diffMs / 1000), 'second');
  }
}
