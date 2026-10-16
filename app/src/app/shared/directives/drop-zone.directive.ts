import { Directive, output, signal } from '@angular/core';

@Directive({
  selector: '[appDropZone]',
  exportAs: 'dropZone',
  host: {
    '(dragenter)': 'onDragEnter($event)',
    '(dragover)': 'onDragOver($event)',
    '(dragleave)': 'onDragLeave($event)',
    '(drop)': 'onDrop($event)',
  },
})
export class DropZoneDirective {
  readonly filesDropped = output<File[]>();
  readonly dragging = signal(false);

  private depth = 0;

  /** Only drags that carry files are uploads; moving an in-page item around must pass through untouched. */
  private hasFiles(event: DragEvent): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes('Files');
  }

  onDragEnter(event: DragEvent): void {
    if (!this.hasFiles(event)) return;
    event.preventDefault();
    event.stopPropagation();
    this.depth++;
    this.dragging.set(true);
  }

  onDragOver(event: DragEvent): void {
    if (!this.hasFiles(event)) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'copy';
    }
  }

  onDragLeave(event: DragEvent): void {
    if (!this.hasFiles(event)) return;
    event.preventDefault();
    event.stopPropagation();
    this.depth = Math.max(0, this.depth - 1);
    if (this.depth === 0) {
      this.dragging.set(false);
    }
  }

  onDrop(event: DragEvent): void {
    if (!this.hasFiles(event)) return;
    event.preventDefault();
    event.stopPropagation();
    this.depth = 0;
    this.dragging.set(false);
    const files = Array.from(event.dataTransfer?.files ?? []);
    if (files.length) {
      this.filesDropped.emit(files);
    }
  }
}
