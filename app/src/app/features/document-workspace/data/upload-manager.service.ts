import { Injectable, computed, inject, signal } from '@angular/core';
import { Artifact } from '@core/models/document-workspace';
import { ToastService } from '@core/services/toast.service';
import { FileService } from './file.service';

export interface UploadTask {
  id: number;
  name: string;
  percent: number;
  status: 'uploading' | 'done' | 'error';
}

/**
 * Tracks in-flight uploads as signals so a global progress panel can render
 * them and features can react when a file finishes (to refresh their list).
 */
@Injectable({ providedIn: 'root' })
export class UploadManagerService {
  private readonly files = inject(FileService);
  private readonly toast = inject(ToastService);

  private seq = 0;
  private readonly _tasks = signal<UploadTask[]>([]);
  readonly tasks = this._tasks.asReadonly();
  readonly active = computed(() => this._tasks().some((t) => t.status === 'uploading'));

  /** Uploads a batch; invokes `onDone` for each completed artifact. */
  upload(
    documentWorkspaceId: string,
    files: File[],
    parentId: string | null,
    onDone: (artifact: Artifact) => void,
  ): void {
    for (const file of files) {
      const id = ++this.seq;
      this._tasks.update((list) => [
        ...list,
        { id, name: file.name, percent: 0, status: 'uploading' },
      ]);

      this.files.upload(documentWorkspaceId, file, parentId).subscribe({
        next: (event) => {
          if (event.type === 'progress') {
            this.patch(id, { percent: event.percent });
          } else {
            this.patch(id, { percent: 100, status: 'done' });
            onDone(event.artifact);
            setTimeout(() => this.remove(id), 2500);
          }
        },
        error: () => {
          this.patch(id, { status: 'error' });
          this.toast.error(`No se pudo subir ${file.name}`);
          setTimeout(() => this.remove(id), 4000);
        },
      });
    }
  }

  dismiss(id: number): void {
    this.remove(id);
  }

  private patch(id: number, partial: Partial<UploadTask>): void {
    this._tasks.update((list) => list.map((t) => (t.id === id ? { ...t, ...partial } : t)));
  }

  private remove(id: number): void {
    this._tasks.update((list) => list.filter((t) => t.id !== id));
  }
}
