import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  ViewEncapsulation,
  computed,
  inject,
  input,
  output,
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { renderMarkdown, toggleTask } from './markdown-render';

/**
 * Renders Markdown as sanitized HTML (GFM tables, task lists, callouts, wikilinks, tags, highlighted code).
 * Task checkboxes are interactive unless the view is read-only: toggling one emits the updated source.
 */
@Component({
  selector: 'app-markdown-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  template: `<div
    class="md-preview"
    [class.md-preview--readonly]="readonly()"
    [innerHTML]="html()"
    (click)="onClick($event)"
    (change)="onChange($event)"
  ></div>`,
  styleUrl: './markdown-view.component.scss',
})
export class MarkdownViewComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly source = input.required<string>();
  readonly readonly = input(false);

  /** Emits the new source after a task checkbox was toggled in the rendered view. */
  readonly sourceChange = output<string>();
  /** A `[[wikilink]]` was clicked; the payload is its target name. */
  readonly wikilink = output<string>();

  /** The renderer output is sanitized by DOMPurify, so it is safe to trust here. */
  readonly html = computed(() => this.sanitizer.bypassSecurityTrustHtml(renderMarkdown(this.source())));

  onChange(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (!target.classList.contains('md-task')) return;
    if (this.readonly()) {
      target.checked = !target.checked;
      return;
    }
    const index = Number(target.dataset['task']);
    this.sourceChange.emit(toggleTask(this.source(), index));
  }

  onClick(event: MouseEvent): void {
    const element = event.target as HTMLElement;
    const wiki = element.closest<HTMLElement>('.md-wikilink');
    if (wiki) {
      event.preventDefault();
      this.wikilink.emit(wiki.dataset['target'] ?? '');
      return;
    }
    const copy = element.closest<HTMLElement>('.md-copy');
    if (copy) {
      const code = copy.parentElement?.querySelector('code')?.textContent ?? '';
      void navigator.clipboard?.writeText(code);
      copy.textContent = '✓';
      setTimeout(() => (copy.textContent = 'Copy'), 1200);
      return;
    }
    const anchor = element.closest<HTMLAnchorElement>('a[href^="#"]');
    if (anchor && anchor.getAttribute('href') !== '#') {
      event.preventDefault();
      const id = decodeURIComponent(anchor.getAttribute('href')!.slice(1));
      this.host.nativeElement.querySelector(`[id="${CSS.escape(id)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }

  /** Scrolls the rendered heading with this id into view. */
  scrollToHeading(id: string): void {
    this.host.nativeElement.querySelector(`[id="${CSS.escape(id)}"]`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
