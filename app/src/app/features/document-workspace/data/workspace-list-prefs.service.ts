import { Injectable, signal } from '@angular/core';

export type WorkspaceViewMode = 'list' | 'cards';

const STORAGE_KEY = 'sophia.workspaces.view';

/** How the workspace list is shown; the sidebar switches it, the list pages follow it, and it survives reloads. */
@Injectable({ providedIn: 'root' })
export class WorkspaceListPrefsService {
  private readonly _view = signal<WorkspaceViewMode>(this.read());
  readonly view = this._view.asReadonly();

  setView(mode: WorkspaceViewMode): void {
    this._view.set(mode);
    try {
      localStorage.setItem(STORAGE_KEY, mode);
    } catch {
      // Storage can be unavailable (private mode); the choice still holds for this session.
    }
  }

  private read(): WorkspaceViewMode {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'cards' ? 'cards' : 'list';
    } catch {
      return 'list';
    }
  }
}
