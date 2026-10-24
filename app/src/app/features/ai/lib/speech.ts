/**
 * Turns a note into what a voice should read. Markdown symbols are noise when spoken ("hash hash heading"), so they
 * are dropped, headings and table rows become sentences, and code is skipped instead of being spelled out.
 */
export function plainTextForSpeech(markdown: string): string {
  const lines: string[] = [];
  let inCode = false;
  for (const raw of markdown.replace(/\r\n/g, '\n').split('\n')) {
    const line = raw.trim();
    if (/^(```|~~~)/.test(line)) {
      inCode = !inCode;
      continue;
    }
    if (inCode || /^([-*_]\s*){3,}$/.test(line) || /^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$/.test(line)) {
      continue;
    }
    let text = line;
    const heading = /^#{1,6}\s+(.*)$/.exec(text);
    if (heading) {
      lines.push(endSentence(clean(heading[1])));
      continue;
    }
    text = text.replace(/^>\s*/, '').replace(/^\[!\w+\]\s*/, '');
    if (text.startsWith('|')) {
      const cells = text.split('|').map((c) => clean(c)).filter(Boolean);
      if (cells.length) {
        lines.push(endSentence(cells.join(', ')));
      }
      continue;
    }
    text = text.replace(/^([-*+]|\d+[.)])\s+(\[[ xX]\]\s*)?/, '');
    text = clean(text);
    if (text) {
      lines.push(text);
    }
  }
  return lines.join('\n').replace(/\n{2,}/g, '\n').trim();
}

function clean(text: string): string {
  return text
    .replace(/!\[[^\]]*]\([^)]*\)/g, '')
    .replace(/\[\[([^\]|]+)(\|([^\]]+))?]]/g, (_m, target: string, _p, label?: string) => label ?? target)
    .replace(/\[([^\]]+)]\([^)]*\)/g, '$1')
    .replace(/<[^>]+>/g, '')
    .replace(/`([^`]*)`/g, '$1')
    .replace(/(\*\*|__)(.*?)\1/g, '$2')
    .replace(/([*_~])(.*?)\1/g, '$2')
    .replace(/(^|\s)#([\p{L}\p{N}_-]+)/gu, '$1$2')
    .replace(/\s+/g, ' ')
    .trim();
}

function endSentence(text: string): string {
  return text && !/[.!?:;…]$/.test(text) ? `${text}.` : text;
}

/**
 * Cuts text into pieces a speech engine can take: at sentence ends when possible, never longer than `max`. Engines
 * (Chrome's above all) stop reading long utterances halfway, so a long text is spoken as a queue of short ones.
 */
export function splitForSpeech(text: string, max = 220): string[] {
  const pieces: string[] = [];
  for (const paragraph of text.split(/\n+/)) {
    const sentences = paragraph.match(/[^.!?…]+[.!?…]+["')\]]*\s*|[^.!?…]+$/g) ?? [paragraph];
    let current = '';
    for (const sentence of sentences) {
      for (const part of hardSplit(sentence.trim(), max)) {
        if (!part) {
          continue;
        }
        if (current && current.length + part.length + 1 > max) {
          pieces.push(current);
          current = part;
        } else {
          current = current ? `${current} ${part}` : part;
        }
      }
    }
    if (current) {
      pieces.push(current);
    }
  }
  return pieces;
}

function hardSplit(sentence: string, max: number): string[] {
  if (sentence.length <= max) {
    return [sentence];
  }
  const out: string[] = [];
  let rest = sentence;
  while (rest.length > max) {
    let cut = Math.max(rest.lastIndexOf(', ', max), rest.lastIndexOf('; ', max), rest.lastIndexOf(' ', max));
    if (cut < max / 3) {
      cut = max;
    }
    out.push(rest.slice(0, cut + 1).trim());
    rest = rest.slice(cut + 1).trim();
  }
  if (rest) {
    out.push(rest);
  }
  return out;
}
