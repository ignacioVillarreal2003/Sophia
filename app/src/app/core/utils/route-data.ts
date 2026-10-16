import { ActivatedRouteSnapshot } from '@angular/router';

/**
 * Looks up `key` across the whole active route chain, not just the leaf.
 * Route data lives on whichever segment declared it (e.g. a top-level app
 * route), and Angular does not bubble it down to descendants on its own, so a
 * plain `snapshot.data[key]` on the deepest route would miss it.
 */
export function findRouteData<T>(root: ActivatedRouteSnapshot, key: string): T | undefined {
  let snapshot: ActivatedRouteSnapshot | null = root;
  let found: T | undefined;
  while (snapshot) {
    if (key in snapshot.data) {
      found = snapshot.data[key] as T;
    }
    snapshot = snapshot.firstChild;
  }
  return found;
}
