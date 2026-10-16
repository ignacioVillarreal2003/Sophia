import {describe, expect, it} from 'vitest';
import {MODULES, visibleModules} from './module-catalog';

/**
 * `hiddenModules` is a deny list. The point of these tests is the default: a
 * user who has never touched the setting, and a module added to the catalogue
 * after they last saved, both have to end up visible.
 */
describe('visibleModules', () => {
  it('shows everything when nothing is hidden', () => {
    expect(visibleModules([])).toEqual(MODULES);
  });

  it('shows everything when the user has no preference yet', () => {
    expect(visibleModules(undefined)).toEqual(MODULES);
    expect(visibleModules(null)).toEqual(MODULES);
  });

  it('drops only the hidden slugs', () => {
    const [first] = MODULES;

    const visible = visibleModules([first.slug]);

    expect(visible.map((m) => m.slug)).not.toContain(first.slug);
    expect(visible).toHaveLength(MODULES.length - 1);
  });

  it('keeps a module that was added after the preference was saved', () => {
    // The stored list names something the catalogue no longer has; that must not
    // shift the filter onto a real module.
    const visible = visibleModules(['a-module-that-shipped-and-was-removed']);

    expect(visible).toEqual(MODULES);
  });

  it('returns an empty list only when every module is hidden', () => {
    expect(visibleModules(MODULES.map((m) => m.slug))).toEqual([]);
  });
});
