import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { MarkdownViewComponent } from './markdown-view.component';
import { OutlineEntry, extractOutline, markdownStats } from './markdown-render';

export type MarkdownMode = 'edit' | 'split' | 'preview';

/** The selection of the source pane with some of the text around it, for the AI assistant. */
export interface EditorSelection {
  readonly start: number;
  readonly end: number;
  readonly text: string;
  readonly before: string;
  readonly after: string;
}

export interface LinkTarget {
  readonly id: string;
  readonly name: string;
}

interface MenuItem {
  readonly key: string;
  readonly label: string;
  readonly hint: string;
  readonly keywords: string;
  readonly insert: string;
  /** Where the caret lands inside the inserted text, counted from its start. */
  readonly caret?: number;
}

interface PopupState {
  readonly kind: 'slash' | 'wiki';
  /** Offset in the text where the trigger (/ or [[) begins. */
  readonly start: number;
  readonly query: string;
  readonly x: number;
  readonly y: number;
}

const TABLE_TEMPLATE = '| Column 1 | Column 2 | Column 3 |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |\n';

const SLASH_ITEMS: MenuItem[] = [
  { key: 'h1', label: 'Heading 1', hint: '#', keywords: 'title heading h1 titulo', insert: '# ' },
  { key: 'h2', label: 'Heading 2', hint: '##', keywords: 'heading h2 subtitulo', insert: '## ' },
  { key: 'h3', label: 'Heading 3', hint: '###', keywords: 'heading h3', insert: '### ' },
  { key: 'bullet', label: 'Bulleted list', hint: '-', keywords: 'list bullet lista', insert: '- ' },
  { key: 'number', label: 'Numbered list', hint: '1.', keywords: 'list number ordered numerada', insert: '1. ' },
  { key: 'task', label: 'To-do list', hint: '[ ]', keywords: 'todo task checkbox checklist tarea', insert: '- [ ] ' },
  { key: 'quote', label: 'Quote', hint: '>', keywords: 'quote cita blockquote', insert: '> ' },
  { key: 'callout', label: 'Callout', hint: '> [!note]', keywords: 'callout note aviso nota tip warning', insert: '> [!note] Title\n> ', caret: 11 },
  { key: 'code', label: 'Code block', hint: '```', keywords: 'code codigo snippet', insert: '```\n\n```', caret: 4 },
  { key: 'table', label: 'Table', hint: '| |', keywords: 'table tabla grid', insert: TABLE_TEMPLATE },
  { key: 'divider', label: 'Divider', hint: '---', keywords: 'divider line separador hr', insert: '---\n' },
  { key: 'link', label: 'Link', hint: '[ ]( )', keywords: 'link enlace url', insert: '[text](https://)', caret: 1 },
  { key: 'image', label: 'Image', hint: '![ ]( )', keywords: 'image imagen picture', insert: '![alt](https://)', caret: 2 },
  { key: 'wiki', label: 'Link to a note', hint: '[[ ]]', keywords: 'wiki note nota link enlace', insert: '[[]]', caret: 2 },
  { key: 'today', label: 'Today\'s date', hint: 'date', keywords: 'date fecha hoy today', insert: '' },
];

/**
 * Markdown editor in the spirit of Obsidian and Notion: a plain-text source with a formatting toolbar,
 * a live preview (edit / split / preview modes), a "/" command menu, `[[` note links, list continuation,
 * an outline and word/task statistics. Edits go through the browser's own undo stack.
 */
@Component({
  selector: 'app-markdown-editor',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, MarkdownViewComponent, TranslatePipe],
  templateUrl: './markdown-editor.component.html',
  styleUrl: './markdown-editor.component.scss',
})
export class MarkdownEditorComponent {
  readonly value = model.required<string>();
  readonly mode = model<MarkdownMode>('split');
  readonly readonly = input(false);
  /** Notes that can be linked with `[[`. */
  readonly linkTargets = input<LinkTarget[]>([]);

  /** A `[[wikilink]]` was activated in the preview. */
  readonly wikilink = output<string>();
  /** An image was pasted or dropped; the parent uploads it and calls {@link insertText} with the result. */
  readonly imageFile = output<File>();
  /** Ctrl/Cmd+S. */
  readonly saveRequested = output<void>();

  private readonly textarea = viewChild<ElementRef<HTMLTextAreaElement>>('source');
  private readonly previewPane = viewChild<ElementRef<HTMLElement>>('previewPane');
  private readonly markdownView = viewChild(MarkdownViewComponent);

  readonly showOutline = signal(false);
  readonly popup = signal<PopupState | null>(null);
  readonly popupIndex = signal(0);

  readonly outline = computed<OutlineEntry[]>(() => extractOutline(this.value()));
  readonly stats = computed(() => markdownStats(this.value()));
  readonly effectiveMode = computed<MarkdownMode>(() => (this.readonly() ? 'preview' : this.mode()));

  readonly popupItems = computed(() => {
    const popup = this.popup();
    if (!popup) return [];
    const query = popup.query.toLowerCase();
    if (popup.kind === 'slash') {
      return SLASH_ITEMS.filter((item) => !query || `${item.label} ${item.keywords}`.toLowerCase().includes(query)).slice(0, 8);
    }
    return this.linkTargets()
      .filter((target) => !query || target.name.toLowerCase().includes(query))
      .slice(0, 8)
      .map<MenuItem>((target) => ({
        key: target.id,
        label: target.name,
        hint: '[[ ]]',
        keywords: '',
        insert: `${target.name}]]`,
      }));
  });

  constructor() {
    // Keep the textarea in sync when the value is replaced from outside (restore, remote reload).
    effect(() => {
      const element = this.textarea()?.nativeElement;
      const value = this.value();
      if (element && element.value !== value) {
        const position = Math.min(element.selectionStart, value.length);
        element.value = value;
        element.setSelectionRange(position, position);
      }
    });
  }

  // ------------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------------

  focus(): void {
    this.textarea()?.nativeElement.focus();
  }

  /** Inserts text at the caret (used by the parent after an image upload). */
  insertText(text: string): void {
    const element = this.textarea()?.nativeElement;
    if (!element) {
      this.value.update((v) => v + text);
      return;
    }
    this.replace(element.selectionStart, element.selectionEnd, text);
  }

  /**
   * What the assistant works on: the selection, the text around it, and where it is. Null while the source pane is
   * not on screen (preview mode), since there is then no selection to speak of.
   */
  selectionContext(): EditorSelection | null {
    const element = this.textarea()?.nativeElement;
    if (!element) {
      return null;
    }
    const { selectionStart: start, selectionEnd: end, value } = element;
    return {
      start,
      end,
      text: value.slice(start, end),
      before: value.slice(Math.max(0, start - 12000), start),
      after: value.slice(end, end + 4000),
    };
  }

  /**
   * Replaces `[start, end)` with `text` when that range still holds `expected` and is still preceded by `preceding`
   * (the document may have changed while the assistant was writing); returns false, changing nothing, when not.
   */
  replaceChecked(start: number, end: number, expected: string, text: string, preceding = ''): boolean {
    const element = this.textarea()?.nativeElement;
    if (!element || this.readonly() || element.value.slice(start, end) !== expected) {
      return false;
    }
    if (preceding && element.value.slice(Math.max(0, start - preceding.length), start) !== preceding) {
      return false;
    }
    this.replace(start, end, text);
    return true;
  }

  // ------------------------------------------------------------------
  // Text editing primitives
  // ------------------------------------------------------------------

  /** Replaces a range through execCommand so the native undo stack keeps working. */
  private replace(start: number, end: number, text: string, selectStart?: number, selectEnd?: number): void {
    const element = this.textarea()!.nativeElement;
    element.focus();
    element.setSelectionRange(start, end);
    const done = document.execCommand?.('insertText', false, text);
    if (!done) {
      element.setRangeText(text, start, end, 'end');
      element.dispatchEvent(new Event('input', { bubbles: true }));
    }
    const base = start + text.length;
    element.setSelectionRange(selectStart ?? base, selectEnd ?? selectStart ?? base);
    this.value.set(element.value);
  }

  private currentLine(element: HTMLTextAreaElement): { start: number; end: number; text: string } {
    const caret = element.selectionStart;
    const start = element.value.lastIndexOf('\n', caret - 1) + 1;
    let end = element.value.indexOf('\n', caret);
    if (end === -1) end = element.value.length;
    return { start, end, text: element.value.slice(start, end) };
  }

  /** Wraps the selection (or toggles the wrapper off when it is already there). */
  wrap(before: string, after = before, placeholder = ''): void {
    const element = this.textarea()?.nativeElement;
    if (!element || this.readonly()) return;
    const { selectionStart: start, selectionEnd: end, value } = element;
    const selected = value.slice(start, end);
    const outerBefore = value.slice(start - before.length, start);
    const outerAfter = value.slice(end, end + after.length);
    if (selected && outerBefore === before && outerAfter === after) {
      this.replace(start - before.length, end + after.length, selected, start - before.length, start - before.length + selected.length);
      return;
    }
    if (selected.startsWith(before) && selected.endsWith(after) && selected.length >= before.length + after.length) {
      const inner = selected.slice(before.length, selected.length - after.length);
      this.replace(start, end, inner, start, start + inner.length);
      return;
    }
    const text = selected || placeholder;
    this.replace(start, end, `${before}${text}${after}`, start + before.length, start + before.length + text.length);
  }

  /** Toggles a line prefix on every selected line; `exclusive` prefixes replace each other (headings). */
  prefixLines(prefix: string, exclusive: RegExp | null = null): void {
    const element = this.textarea()?.nativeElement;
    if (!element || this.readonly()) return;
    const value = element.value;
    const from = value.lastIndexOf('\n', element.selectionStart - 1) + 1;
    let to = value.indexOf('\n', element.selectionEnd);
    if (to === -1) to = value.length;
    const lines = value.slice(from, to).split('\n');
    const allHave = lines.every((line) => line.startsWith(prefix));
    const changed = lines
      .map((line, index) => {
        if (allHave) return line.slice(prefix.length);
        const stripped = exclusive ? line.replace(exclusive, '') : line;
        return prefix === '1. ' ? `${index + 1}. ${stripped.replace(/^\d+\.\s/, '')}` : `${prefix}${stripped}`;
      })
      .join('\n');
    this.replace(from, to, changed, from, from + changed.length);
  }

  heading(level: number): void {
    this.prefixLines('#'.repeat(level) + ' ', /^#{1,6}\s+/);
  }

  list(kind: 'bullet' | 'number' | 'task'): void {
    const prefix = kind === 'bullet' ? '- ' : kind === 'number' ? '1. ' : '- [ ] ';
    this.prefixLines(prefix, /^(\s*)([-*+]|\d+[.)])\s(\[[ xX]]\s)?/);
  }

  codeBlock(): void {
    const element = this.textarea()?.nativeElement;
    if (!element || this.readonly()) return;
    const selected = element.value.slice(element.selectionStart, element.selectionEnd);
    this.replace(element.selectionStart, element.selectionEnd, `\`\`\`\n${selected}\n\`\`\`\n`, element.selectionStart + 4, element.selectionStart + 4 + selected.length);
  }

  link(): void {
    const element = this.textarea()?.nativeElement;
    if (!element || this.readonly()) return;
    const selected = element.value.slice(element.selectionStart, element.selectionEnd) || 'text';
    const start = element.selectionStart;
    this.replace(element.selectionStart, element.selectionEnd, `[${selected}](https://)`, start + selected.length + 3, start + selected.length + 11);
  }

  insertBlock(text: string, caret?: number): void {
    const element = this.textarea()?.nativeElement;
    if (!element || this.readonly()) return;
    const line = this.currentLine(element);
    const atEmptyLine = line.text.trim() === '';
    const start = atEmptyLine ? line.start : line.end;
    const prefix = atEmptyLine ? '' : '\n';
    this.replace(atEmptyLine ? line.start : line.end, atEmptyLine ? line.end : line.end, prefix + text, caret !== undefined ? start + prefix.length + caret : undefined);
  }

  setMode(mode: MarkdownMode): void {
    this.mode.set(mode);
  }

  // ------------------------------------------------------------------
  // Events
  // ------------------------------------------------------------------

  onInput(event: Event): void {
    const element = event.target as HTMLTextAreaElement;
    this.value.set(element.value);
    this.updatePopup(element);
  }

  onKeydown(event: KeyboardEvent): void {
    const element = event.target as HTMLTextAreaElement;
    const mod = event.ctrlKey || event.metaKey;

    if (this.popup()) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const count = this.popupItems().length;
        if (count) this.popupIndex.update((i) => (i + (event.key === 'ArrowDown' ? 1 : count - 1)) % count);
        return;
      }
      if (event.key === 'Enter' || event.key === 'Tab') {
        const item = this.popupItems()[this.popupIndex()];
        if (item) {
          event.preventDefault();
          this.applyPopupItem(item);
          return;
        }
      }
      if (event.key === 'Escape') {
        event.preventDefault();
        this.popup.set(null);
        return;
      }
    }

    if (mod && !event.shiftKey && !event.altKey) {
      switch (event.key.toLowerCase()) {
        case 's':
          event.preventDefault();
          this.saveRequested.emit();
          return;
        case 'b':
          event.preventDefault();
          this.wrap('**', '**', 'bold');
          return;
        case 'i':
          event.preventDefault();
          this.wrap('*', '*', 'italic');
          return;
        case 'k':
          event.preventDefault();
          this.link();
          return;
        case 'e':
          event.preventDefault();
          this.wrap('`', '`', 'code');
          return;
        default:
      }
    }
    if (mod && event.shiftKey) {
      const key = event.key.toLowerCase();
      if (key === 'x') {
        event.preventDefault();
        this.wrap('~~', '~~', 'text');
        return;
      }
      if (key === 'h') {
        event.preventDefault();
        this.wrap('==', '==', 'text');
        return;
      }
      if (key === 'l') {
        event.preventDefault();
        this.list('task');
        return;
      }
    }

    if (event.key === 'Enter' && !event.shiftKey && !mod && !event.isComposing) {
      if (this.continueList(element)) event.preventDefault();
      return;
    }
    if (event.key === 'Tab' && !mod) {
      event.preventDefault();
      this.indent(element, event.shiftKey);
    }
  }

  onKeyup(event: Event): void {
    const key = (event as KeyboardEvent).key;
    if (event.type === 'click' || ['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(key)) {
      this.updatePopup(event.target as HTMLTextAreaElement);
    }
  }

  onPaste(event: ClipboardEvent): void {
    const file = Array.from(event.clipboardData?.files ?? []).find((f) => f.type.startsWith('image/'));
    if (file) {
      event.preventDefault();
      this.imageFile.emit(file);
    }
  }

  onDrop(event: DragEvent): void {
    const file = Array.from(event.dataTransfer?.files ?? []).find((f) => f.type.startsWith('image/'));
    if (file) {
      event.preventDefault();
      this.imageFile.emit(file);
    }
  }

  /** Keeps list markers going on Enter and removes them when the item is empty. */
  private continueList(element: HTMLTextAreaElement): boolean {
    if (element.selectionStart !== element.selectionEnd) return false;
    const line = this.currentLine(element);
    if (element.selectionStart < line.end) return false;
    const match = /^(\s*)(?:(\d+)([.)])|([-*+]))\s(\[[ xX]]\s)?/.exec(line.text) ?? null;
    const quote = /^(\s*>+\s?)/.exec(line.text);
    if (!match && !quote) return false;

    const marker = match ?? quote!;
    const prefixLength = marker[0].length;
    if (line.text.slice(prefixLength).trim() === '') {
      // Empty item: leave the list.
      this.replace(line.start, line.end, '');
      return true;
    }
    let next: string;
    if (match) {
      const indent = match[1];
      const bullet = match[2] ? `${Number(match[2]) + 1}${match[3]}` : match[4];
      next = `${indent}${bullet} ${match[5] ? '[ ] ' : ''}`;
    } else {
      next = quote![1];
    }
    this.replace(element.selectionStart, element.selectionEnd, `\n${next}`);
    return true;
  }

  private indent(element: HTMLTextAreaElement, outdent: boolean): void {
    const value = element.value;
    const from = value.lastIndexOf('\n', element.selectionStart - 1) + 1;
    let to = value.indexOf('\n', element.selectionEnd);
    if (to === -1) to = value.length;
    const lines = value.slice(from, to).split('\n');
    const isListLine = (line: string) => /^\s*([-*+]|\d+[.)])\s/.test(line);
    if (lines.length === 1 && !outdent && !isListLine(lines[0])) {
      this.replace(element.selectionStart, element.selectionEnd, '  ');
      return;
    }
    const changed = lines
      .map((line) => (outdent ? line.replace(/^( {1,2}|\t)/, '') : `  ${line}`))
      .join('\n');
    this.replace(from, to, changed, from, from + changed.length);
  }

  // ------------------------------------------------------------------
  // "/" commands and [[ note links
  // ------------------------------------------------------------------

  private updatePopup(element: HTMLTextAreaElement): void {
    if (this.readonly()) return;
    const caret = element.selectionStart;
    const before = element.value.slice(0, caret);
    const line = before.slice(before.lastIndexOf('\n') + 1);

    const wiki = /\[\[([^\]\n]*)$/.exec(line);
    if (wiki) {
      this.openPopup('wiki', caret - wiki[0].length, wiki[1], element);
      return;
    }
    const slash = /(?:^|\s)\/([\w-]*)$/.exec(line);
    if (slash) {
      this.openPopup('slash', caret - slash[1].length - 1, slash[1], element);
      return;
    }
    this.popup.set(null);
  }

  private openPopup(kind: 'slash' | 'wiki', start: number, query: string, element: HTMLTextAreaElement): void {
    const point = this.caretPoint(element, start);
    const previous = this.popup();
    this.popup.set({ kind, start, query, x: point.x, y: point.y });
    if (!previous || previous.kind !== kind || previous.query !== query) this.popupIndex.set(0);
  }

  applyPopupItem(item: MenuItem): void {
    const popup = this.popup();
    const element = this.textarea()?.nativeElement;
    if (!popup || !element) return;
    const caret = element.selectionStart;
    this.popup.set(null);

    if (popup.kind === 'wiki') {
      this.replace(popup.start + 2, caret, item.insert);
      return;
    }
    if (item.key === 'today') {
      this.replace(popup.start, caret, new Date().toISOString().slice(0, 10));
      return;
    }
    const lineStart = element.value.lastIndexOf('\n', popup.start - 1) + 1;
    const atLineStart = element.value.slice(lineStart, popup.start).trim() === '';
    const text = !atLineStart && item.insert.startsWith('#') ? `\n${item.insert}` : item.insert;
    this.replace(popup.start, caret, text, item.caret !== undefined ? popup.start + (text.length - item.insert.length) + item.caret : undefined);
    if (item.key === 'wiki') {
      this.updatePopup(element);
    }
  }

  /** Pixel position of a character offset, measured with a hidden mirror of the textarea. */
  private caretPoint(element: HTMLTextAreaElement, offset: number): { x: number; y: number } {
    const style = getComputedStyle(element);
    const mirror = document.createElement('div');
    for (const prop of ['font', 'letterSpacing', 'lineHeight', 'padding', 'border', 'boxSizing', 'whiteSpace', 'wordWrap', 'overflowWrap', 'tabSize'] as const) {
      mirror.style[prop as 'font'] = style[prop as 'font'];
    }
    mirror.style.position = 'absolute';
    mirror.style.visibility = 'hidden';
    mirror.style.width = `${element.clientWidth}px`;
    mirror.style.whiteSpace = 'pre-wrap';
    mirror.textContent = element.value.slice(0, offset);
    const marker = document.createElement('span');
    marker.textContent = '​';
    mirror.appendChild(marker);
    document.body.appendChild(mirror);
    const x = marker.offsetLeft;
    const y = marker.offsetTop;
    mirror.remove();
    const rect = element.getBoundingClientRect();
    const lineHeight = parseFloat(style.lineHeight) || 22;
    return {
      x: Math.min(rect.left + x - element.scrollLeft, window.innerWidth - 300),
      y: Math.min(rect.top + y - element.scrollTop + lineHeight + 4, window.innerHeight - 280),
    };
  }

  // ------------------------------------------------------------------
  // Outline and scrolling
  // ------------------------------------------------------------------

  goTo(entry: OutlineEntry): void {
    const mode = this.effectiveMode();
    if (mode !== 'edit') {
      this.markdownView()?.scrollToHeading(entry.id);
    }
    const element = this.textarea()?.nativeElement;
    if (element && mode !== 'preview') {
      const offset = element.value.split('\n').slice(0, entry.line).reduce((sum, line) => sum + line.length + 1, 0);
      element.focus();
      element.setSelectionRange(offset, offset);
      element.scrollTop = Math.max(0, this.caretPoint(element, offset).y - element.getBoundingClientRect().top - 40);
    }
  }

  /** In split mode the preview follows the source proportionally. */
  onSourceScroll(event: Event): void {
    if (this.effectiveMode() !== 'split') return;
    const source = event.target as HTMLTextAreaElement;
    const preview = this.previewPane()?.nativeElement;
    if (!preview) return;
    const ratio = source.scrollTop / Math.max(1, source.scrollHeight - source.clientHeight);
    preview.scrollTop = ratio * (preview.scrollHeight - preview.clientHeight);
  }

  onPreviewSource(source: string): void {
    this.value.set(source);
  }

}
