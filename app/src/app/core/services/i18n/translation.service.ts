import { Injectable, computed, effect, signal } from '@angular/core';
import { environment } from '@env/environment';
import { TRANSLATIONS, TranslationDict } from './translations';

const LOCALE_STORAGE_KEY = 'sophia.locale';

@Injectable({ providedIn: 'root' })
export class TranslationService {
  private readonly _locale = signal<string>(this.resolveInitialLocale());

  readonly locale = this._locale.asReadonly();
  readonly supported = environment.supportedLocales;

  private readonly dict = computed<TranslationDict>(
    () => TRANSLATIONS[this._locale()] ?? TRANSLATIONS[environment.defaultLocale],
  );

  constructor() {
    effect(() => {
      const locale = this._locale();
      localStorage.setItem(LOCALE_STORAGE_KEY, locale);
      document.documentElement.lang = locale;
    });
  }

  translate = (key: string, params?: Record<string, string | number>): string => {
    const dict = this.dict();
    const fallback = TRANSLATIONS[environment.defaultLocale];
    const template = dict[key] ?? fallback[key] ?? key;
    return params ? this.interpolate(template, params) : template;
  };

  setLocale(locale: string): void {
    if (this.supported.includes(locale)) {
      this._locale.set(locale);
    }
  }

  private interpolate(template: string, params: Record<string, string | number>): string {
    return template.replace(/\{(\w+)\}/g, (_, name: string) =>
      params[name] !== undefined ? String(params[name]) : `{${name}}`,
    );
  }

  private resolveInitialLocale(): string {
    const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
    if (stored && environment.supportedLocales.includes(stored)) {
      return stored;
    }
    const browser = navigator.language?.split('-')[0];
    if (browser && environment.supportedLocales.includes(browser)) {
      return browser;
    }
    return environment.defaultLocale;
  }
}
