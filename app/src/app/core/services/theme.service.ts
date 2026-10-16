import { Injectable, signal } from '@angular/core';

export type VisualTheme =
  | 'claude'
  | 'violet-bloom'
  | 'vercel'
  | 'twitter'
  | 'tangerine'
  | 'sage-garden'
  | 'pastel-dreams'
  | 'notebook'
  | 'nature'
  | 'modern-minimal'
  | 'darkmatter'
  | 'cosmic-night'
  | 'claymorphism'
  | 'caffeine';

export type ThemeMode = 'light' | 'dark';

export interface ThemeDefinition {
  readonly id: VisualTheme;
  readonly labelKey: string;
}

export const AVAILABLE_THEMES: ThemeDefinition[] = [
  { id: 'claude', labelKey: 'theme.claude' },
  { id: 'violet-bloom', labelKey: 'theme.violetBloom' },
  { id: 'vercel', labelKey: 'theme.vercel' },
  { id: 'twitter', labelKey: 'theme.twitter' },
  { id: 'tangerine', labelKey: 'theme.tangerine' },
  { id: 'sage-garden', labelKey: 'theme.sageGarden' },
  { id: 'pastel-dreams', labelKey: 'theme.pastelDreams' },
  { id: 'notebook', labelKey: 'theme.notebook' },
  { id: 'nature', labelKey: 'theme.nature' },
  { id: 'modern-minimal', labelKey: 'theme.modernMinimal' },
  { id: 'darkmatter', labelKey: 'theme.darkmatter' },
  { id: 'cosmic-night', labelKey: 'theme.cosmicNight' },
  { id: 'claymorphism', labelKey: 'theme.claymorphism' },
  { id: 'caffeine', labelKey: 'theme.caffeine' },
];

const DEFAULT_THEME: VisualTheme = 'claude';
const DEFAULT_MODE: ThemeMode = 'light';
const THEME_STORAGE_KEY = 'sophia.visualTheme';
const MODE_STORAGE_KEY = 'sophia.themeMode';

export function themeToApi(theme: VisualTheme): string {
  return theme.toUpperCase().replace(/-/g, '_');
}

export function themeFromApi(value: string | null | undefined): VisualTheme {
  const kebab = (value ?? '').toLowerCase().replace(/_/g, '-');
  return AVAILABLE_THEMES.some((t) => t.id === kebab) ? (kebab as VisualTheme) : DEFAULT_THEME;
}

export function modeToApi(mode: ThemeMode): string {
  return mode.toUpperCase();
}

export function modeFromApi(value: string | null | undefined): ThemeMode {
  const lower = (value ?? '').toLowerCase();
  return lower === 'light' || lower === 'dark' ? (lower as ThemeMode) : DEFAULT_MODE;
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly _theme = signal<VisualTheme>(this.readStoredTheme());
  private readonly _mode = signal<ThemeMode>(this.readStoredMode());

  readonly theme = this._theme.asReadonly();
  readonly mode = this._mode.asReadonly();

  constructor() {
    this.applyTheme(this._theme());
    this.applyMode(this._mode());
  }

  set(theme: VisualTheme): void {
    this._theme.set(theme);
    this.applyTheme(theme);
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  }

  setMode(mode: ThemeMode): void {
    this._mode.set(mode);
    this.applyMode(mode);
    localStorage.setItem(MODE_STORAGE_KEY, mode);
  }

  applyFromAccount(theme: string | null | undefined, mode: string | null | undefined): void {
    this.set(themeFromApi(theme));
    this.setMode(modeFromApi(mode));
  }

  private applyTheme(theme: VisualTheme): void {
    document.documentElement.setAttribute('data-palette', theme);
  }

  private applyMode(mode: ThemeMode): void {
    document.documentElement.classList.toggle('dark', mode === 'dark');
  }

  private readStoredTheme(): VisualTheme {
    const stored = localStorage.getItem(THEME_STORAGE_KEY);
    return AVAILABLE_THEMES.some((t) => t.id === stored) ? (stored as VisualTheme) : DEFAULT_THEME;
  }

  private readStoredMode(): ThemeMode {
    const stored = localStorage.getItem(MODE_STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : DEFAULT_MODE;
  }
}
