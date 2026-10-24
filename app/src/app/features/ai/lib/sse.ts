/** One server-sent event: its name and the decoded JSON of its data. */
export interface SseEvent {
  readonly name: string;
  readonly data: unknown;
}

/**
 * Splits the bytes of an event stream into events. Chunks arrive wherever the network cuts them, so a frame (events
 * end with a blank line) may be spread over several chunks; the incomplete tail is kept for the next call. Comment
 * lines (the keep-alive pings) and frames without data are dropped.
 */
export class SseParser {
  private buffer = '';

  push(chunk: string): SseEvent[] {
    this.buffer += chunk.replace(/\r\n/g, '\n');
    const frames = this.buffer.split('\n\n');
    this.buffer = frames.pop() ?? '';
    const events: SseEvent[] = [];
    for (const frame of frames) {
      const event = parseFrame(frame);
      if (event) {
        events.push(event);
      }
    }
    return events;
  }
}

function parseFrame(frame: string): SseEvent | null {
  let name = 'message';
  const data: string[] = [];
  for (const line of frame.split('\n')) {
    if (line.startsWith(':')) {
      continue;
    }
    if (line.startsWith('event:')) {
      name = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).replace(/^ /, ''));
    }
  }
  if (data.length === 0) {
    return null;
  }
  try {
    return { name, data: JSON.parse(data.join('\n')) };
  } catch {
    return null;
  }
}
