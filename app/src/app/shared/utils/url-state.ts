import { WritableSignal, effect, inject, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime } from 'rxjs';

/** One piece of a page's state that lives in the address: a filter, the search text, the order, the page. */
export interface UrlField {
  /** The state itself; its type (text, number or true/false) says how the address is read back. */
  readonly signal: WritableSignal<any>;
  /** The name in the address. */
  readonly param: string;
  /** Pages count from 0 inside and from 1 in the address. */
  readonly page?: boolean;
  /** The values it may take; anything else in the address is ignored. */
  readonly allowed?: readonly string[];
}

/**
 * Keeps the state of a page and its address the same thing: the address is read once when the page opens (before
 * anything is loaded) and rewritten, without adding history entries, each time the state changes. A value left at
 * what it was on opening is not written, so a plain page keeps a clean address. Call it from a field initializer or
 * the constructor, after the signals it names exist.
 */
export function bindUrlState(fields: readonly UrlField[]): void {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const defaults = new Map<UrlField, unknown>(fields.map((f) => [f, f.signal()]));
  const params = route.snapshot.queryParamMap;

  for (const field of fields) {
    const raw = params.get(field.param);
    if (raw === null) continue;
    const fallback = defaults.get(field);
    if (typeof fallback === 'number') {
      const n = Number(raw);
      if (Number.isFinite(n)) field.signal.set(field.page ? Math.max(0, Math.floor(n) - 1) : n);
    } else if (typeof fallback === 'boolean') {
      field.signal.set(raw === 'true');
    } else if (!field.allowed || field.allowed.includes(raw)) {
      field.signal.set(raw);
    }
  }

  effect(() => {
    const query: Record<string, string | null> = {};
    for (const field of fields) {
      const value = field.signal();
      const same = value === defaults.get(field);
      query[field.param] = same ? null : String(field.page ? Number(value) + 1 : value);
    }
    untracked(() => void router.navigate([], { relativeTo: route, queryParams: query, queryParamsHandling: 'merge', replaceUrl: true }));
  });
}

/** The same for form fields: their value is read from the address on opening and written back as it changes. */
export function bindUrlControls(fields: readonly { readonly control: FormControl<string>; readonly param: string }[]): void {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const params = route.snapshot.queryParamMap;
  for (const { control, param } of fields) {
    const raw = params.get(param);
    if (raw !== null) control.setValue(raw, { emitEvent: false });
    control.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) => {
      void router.navigate([], { relativeTo: route, queryParams: { [param]: value || null }, queryParamsHandling: 'merge', replaceUrl: true });
    });
  }
}
