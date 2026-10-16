import { Lexer, Marked, Tokens } from 'marked';
import DOMPurify from 'dompurify';
import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import css from 'highlight.js/lib/languages/css';
import diff from 'highlight.js/lib/languages/diff';
import go from 'highlight.js/lib/languages/go';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import markdown from 'highlight.js/lib/languages/markdown';
import python from 'highlight.js/lib/languages/python';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import yaml from 'highlight.js/lib/languages/yaml';

hljs.registerLanguage('bash', bash);
hljs.registerLanguage('sh', bash);
hljs.registerLanguage('shell', bash);
hljs.registerLanguage('css', css);
hljs.registerLanguage('diff', diff);
hljs.registerLanguage('go', go);
hljs.registerLanguage('java', java);
hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('js', javascript);
hljs.registerLanguage('json', json);
hljs.registerLanguage('markdown', markdown);
hljs.registerLanguage('md', markdown);
hljs.registerLanguage('python', python);
hljs.registerLanguage('py', python);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('ts', typescript);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('html', xml);
hljs.registerLanguage('yaml', yaml);
hljs.registerLanguage('yml', yaml);

export interface OutlineEntry {
  readonly level: number;
  readonly text: string;
  readonly id: string;
  /** Zero-based line of the heading in the source. */
  readonly line: number;
}

export interface MarkdownStats {
  readonly words: number;
  readonly characters: number;
  readonly readingMinutes: number;
  readonly tasksDone: number;
  readonly tasksTotal: number;
}

const CALLOUT_ICONS: Record<string, string> = {
  note: 'ℹ️',
  info: 'ℹ️',
  tip: '💡',
  success: '✅',
  warning: '⚠️',
  danger: '⛔',
  quote: '❝',
};

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function slugify(text: string): string {
  const slug = text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+|-+$/g, '');
  return slug || 'section';
}

/** Per-render state: task checkbox numbering and unique heading ids. */
interface RenderState {
  task: number;
  headings: Map<string, number>;
}

let state: RenderState = { task: 0, headings: new Map() };

const md = new Marked({ gfm: true, breaks: false });

md.use({
  extensions: [
    {
      name: 'wikilink',
      level: 'inline',
      start: (src: string) => src.indexOf('[['),
      tokenizer(src: string) {
        const match = /^\[\[([^\]|\n]+)(?:\|([^\]\n]+))?\]\]/.exec(src);
        if (!match) return undefined;
        return { type: 'wikilink', raw: match[0], target: match[1].trim(), alias: (match[2] ?? match[1]).trim() };
      },
      renderer(token) {
        const t = token as unknown as { target: string; alias: string };
        return `<a class="md-wikilink" data-target="${escapeHtml(t.target)}" href="#">${escapeHtml(t.alias)}</a>`;
      },
    },
    {
      name: 'mdtag',
      level: 'inline',
      start(src: string) {
        const match = /(?:^|\s)#(?=[\p{L}_])/u.exec(src);
        return match ? match.index + match[0].length - 1 : undefined;
      },
      tokenizer(src: string) {
        const match = /^#([\p{L}\p{N}_/-]+)/u.exec(src);
        return match ? { type: 'mdtag', raw: match[0], tag: match[1] } : undefined;
      },
      renderer(token) {
        const t = token as unknown as { tag: string };
        return `<span class="md-tag" data-tag="${escapeHtml(t.tag)}">#${escapeHtml(t.tag)}</span>`;
      },
    },
    {
      name: 'mdmark',
      level: 'inline',
      start: (src: string) => src.indexOf('=='),
      tokenizer(src: string) {
        const match = /^==([^=\n][^\n]*?)==/.exec(src);
        return match ? { type: 'mdmark', raw: match[0], text: match[1] } : undefined;
      },
      renderer(token) {
        return `<mark>${escapeHtml((token as unknown as { text: string }).text)}</mark>`;
      },
    },
  ],
  renderer: {
    heading({ tokens, depth }: Tokens.Heading): string {
      const inner = this.parser.parseInline(tokens);
      const base = slugify(inner.replace(/<[^>]+>/g, ''));
      const seen = state.headings.get(base) ?? 0;
      state.headings.set(base, seen + 1);
      const id = seen === 0 ? base : `${base}-${seen}`;
      return `<h${depth} id="${id}">${inner}</h${depth}>`;
    },
    code({ text, lang }: Tokens.Code): string {
      const language = (lang ?? '').trim().split(/\s+/)[0];
      let body: string;
      if (language && hljs.getLanguage(language)) {
        body = hljs.highlight(text, { language, ignoreIllegals: true }).value;
      } else {
        body = escapeHtml(text);
      }
      const label = language ? `<span class="md-code-lang">${escapeHtml(language)}</span>` : '';
      return `<div class="md-code">${label}<button type="button" class="md-copy" aria-label="copy">Copy</button><pre><code class="hljs">${body}</code></pre></div>`;
    },
    blockquote({ tokens }: Tokens.Blockquote): string {
      const first = tokens[0];
      if (first && first.type === 'paragraph') {
        const match = /^\[!(\w+)\][+-]?[ \t]*([^\n]*)\n?([\s\S]*)$/.exec((first as Tokens.Paragraph).text);
        if (match) {
          const kind = match[1].toLowerCase();
          const title = match[2].trim() || kind.charAt(0).toUpperCase() + kind.slice(1);
          const rest = [...(match[3] ? new Lexer().lex(match[3]) : []), ...tokens.slice(1)];
          const body = this.parser.parse(rest);
          const icon = CALLOUT_ICONS[kind] ?? 'ℹ️';
          return `<div class="md-callout md-callout--${escapeHtml(kind)}"><div class="md-callout__title"><span>${icon}</span>${escapeHtml(title)}</div><div class="md-callout__body">${body}</div></div>`;
        }
      }
      return `<blockquote>${this.parser.parse(tokens)}</blockquote>\n`;
    },
    checkbox({ checked }: Tokens.Checkbox): string {
      return `<input type="checkbox" class="md-task" data-task="${state.task++}"${checked ? ' checked' : ''}> `;
    },
    link({ href, title, tokens }: Tokens.Link): string {
      const text = this.parser.parseInline(tokens);
      const external = /^https?:\/\//i.test(href);
      const attrs = external ? ' target="_blank" rel="noopener noreferrer"' : '';
      return `<a href="${escapeHtml(href)}"${title ? ` title="${escapeHtml(title)}"` : ''}${attrs}>${text}</a>`;
    },
  },
});

DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A' && node.getAttribute('target') === '_blank') {
    node.setAttribute('rel', 'noopener noreferrer');
  }
});

/** Renders Markdown to sanitized HTML. */
export function renderMarkdown(source: string): string {
  state = { task: 0, headings: new Map() };
  const html = md.parse(source ?? '', { async: false }) as string;
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['data-task', 'data-target', 'data-tag', 'checked', 'target'],
    ADD_TAGS: ['input'],
  });
}

/** Headings of a document, with the ids the renderer gives them. */
export function extractOutline(source: string): OutlineEntry[] {
  const entries: OutlineEntry[] = [];
  const seen = new Map<string, number>();
  const lines = (source ?? '').split('\n');
  let fenced = false;
  lines.forEach((line, index) => {
    if (/^\s{0,3}(```|~~~)/.test(line)) {
      fenced = !fenced;
      return;
    }
    if (fenced) return;
    const match = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (!match) return;
    const text = match[2].replace(/[*_`~=]|\[\[|\]\]/g, '').replace(/\[([^\]]*)]\([^)]*\)/g, '$1');
    const base = slugify(text);
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    entries.push({ level: match[1].length, text, id: count === 0 ? base : `${base}-${count}`, line: index });
  });
  return entries;
}

export function markdownStats(source: string): MarkdownStats {
  const text = source ?? '';
  const words = (text.match(/[\p{L}\p{N}][\p{L}\p{N}'’-]*/gu) ?? []).length;
  const tasks = taskLines(text);
  return {
    words,
    characters: text.length,
    readingMinutes: Math.max(1, Math.round(words / 200)),
    tasksDone: tasks.filter((t) => t.checked).length,
    tasksTotal: tasks.length,
  };
}

interface TaskLine {
  readonly line: number;
  readonly checked: boolean;
}

/** Task-list items of the source in document order (code fences are skipped). */
function taskLines(source: string): TaskLine[] {
  const tasks: TaskLine[] = [];
  let fenced = false;
  (source ?? '').split('\n').forEach((line, index) => {
    if (/^\s{0,3}(```|~~~)/.test(line)) {
      fenced = !fenced;
      return;
    }
    if (fenced) return;
    const match = /^\s*(?:[-*+]|\d+[.)])\s+\[([ xX])]\s/.exec(line);
    if (match) tasks.push({ line: index, checked: match[1] !== ' ' });
  });
  return tasks;
}

/** Flips the n-th task item of the source (the same order the preview numbers its checkboxes). */
export function toggleTask(source: string, index: number): string {
  const tasks = taskLines(source);
  const target = tasks[index];
  if (!target) return source;
  const lines = source.split('\n');
  lines[target.line] = lines[target.line].replace(/\[([ xX])]/, target.checked ? '[ ]' : '[x]');
  return lines.join('\n');
}
