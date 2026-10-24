import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { TokenStorageService } from '@core/services/authentication/token-storage.service';
import { AiError, AiService, StreamDone } from './ai.service';

function sse(...frames: string[]): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      frames.forEach((f) => controller.enqueue(encoder.encode(f)));
      controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { 'Content-Type': 'text/event-stream' } });
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('AiService streaming', () => {
  let service: AiService;
  let fetchMock: ReturnType<typeof vi.fn>;
  const refresh = vi.fn(() => of('fresh-token'));

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    refresh.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthenticationService, useValue: { refreshAccessToken: refresh } },
      ],
    });
    TestBed.inject(TokenStorageService).set({ accessToken: 'old-token' } as never);
    service = TestBed.inject(AiService);
  });

  afterEach(() => {
    TestBed.inject(TokenStorageService).clear();
    vi.unstubAllGlobals();
  });

  async function run(start: (handlers: Parameters<AiService['notes']>[1]) => { finished: Promise<void> }) {
    const deltas: string[] = [];
    const events: { meta?: Record<string, unknown>; done?: StreamDone; error?: AiError } = {};
    const stream = start({
      meta: (m) => (events.meta = m),
      delta: (t) => deltas.push(t),
      done: (d) => (events.done = d),
      error: (e) => (events.error = e),
    });
    await stream.finished;
    return { deltas, ...events };
  }

  it('sends the token and the body, and hands the text over as it arrives', async () => {
    fetchMock.mockResolvedValue(
      sse('event:meta\ndata:{"feature":"NOTES"}\n\nevent:delta\ndata:{"t":"Hol"}\n\n', 'event:delta\ndata:{"t":"a"}\n\nevent:done\ndata:{"truncated":false}\n\n'),
    );
    const result = await run((h) => service.notes({ topic: 'x' }, h));

    expect(result.deltas.join('')).toBe('Hola');
    expect(result.meta).toEqual({ feature: 'NOTES' });
    expect(result.done?.truncated).toBe(false);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('/api/v1/ai/notes');
    expect(init.headers['Authorization']).toBe('Bearer old-token');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body).topic).toBe('x');
  });

  it('sends a form without forcing a content type', async () => {
    fetchMock.mockResolvedValue(sse('event:done\ndata:{}\n\n'));
    await run((h) => service.ocr(new File(['x'], 'a.png', { type: 'image/png' }), 'NOTES', 'ws-1', h));
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toContain('/api/v1/ai/ocr');
    expect(init.body).toBeInstanceOf(FormData);
    expect(init.headers['Content-Type']).toBeUndefined();
    expect((init.body as FormData).get('mode')).toBe('NOTES');
    expect((init.body as FormData).get('workspaceId')).toBe('ws-1');
  });

  it('retries once with a fresh token when the first one has expired', async () => {
    fetchMock.mockResolvedValueOnce(json(401, { code: 'UNAUTHORIZED' })).mockResolvedValueOnce(sse('event:delta\ndata:{"t":"ok"}\n\nevent:done\ndata:{}\n\n'));
    const result = await run((h) => service.notes({ topic: 'x' }, h));
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[1][1].headers['Authorization']).toBe('Bearer fresh-token');
    expect(result.deltas).toEqual(['ok']);
  });

  it('reports a refusal from the server with its code', async () => {
    fetchMock.mockResolvedValue(json(429, { code: 'QUOTA_EXCEEDED', message: 'used it all' }));
    const result = await run((h) => service.notes({ topic: 'x' }, h));
    expect(result.error).toEqual({ status: 429, code: 'QUOTA_EXCEEDED', message: 'used it all' });
    expect(result.deltas).toEqual([]);
  });

  it('reports an error event that comes in the middle of the stream', async () => {
    fetchMock.mockResolvedValue(sse('event:delta\ndata:{"t":"a"}\n\nevent:error\ndata:{"code":"UPSTREAM_ERROR","message":"down"}\n\n'));
    const result = await run((h) => service.notes({ topic: 'x' }, h));
    expect(result.error?.code).toBe('UPSTREAM_ERROR');
    expect(result.deltas).toEqual(['a']);
    expect(result.done).toBeUndefined();
  });

  it('says so when the connection drops before the end', async () => {
    fetchMock.mockResolvedValue(sse('event:delta\ndata:{"t":"a"}\n\n'));
    const result = await run((h) => service.notes({ topic: 'x' }, h));
    expect(result.error?.code).toBe('STREAM_INTERRUPTED');
  });

  it('maps a network failure and stays quiet when the person cancelled', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    const failed = await run((h) => service.notes({ topic: 'x' }, h));
    expect(failed.error?.code).toBe('NETWORK');

    fetchMock.mockImplementationOnce((_url: string, init: { signal: AbortSignal }) =>
      new Promise((_resolve, reject) => init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))),
    );
    let called = false;
    const stream = service.notes({ topic: 'x' }, { delta: () => undefined, error: () => (called = true) });
    stream.cancel();
    await stream.finished;
    expect(called).toBe(false);
  });

  it('exposes conversation messages the same way, with citations on the last event', async () => {
    fetchMock.mockResolvedValue(
      sse('event:meta\ndata:{"conversationId":"c1","messageId":"m1"}\n\nevent:delta\ndata:{"t":"Según [1]"}\n\n', 'event:done\ndata:{"messageId":"m2","citations":[{"index":1,"artifactId":"a1","title":"Notas"}]}\n\n'),
    );
    const result = await run((h) => service.sendMessage('c1', 'hola', false, h));
    expect(fetchMock.mock.calls[0][0]).toContain('/ai/conversations/c1/messages');
    expect(result.done?.citations?.[0].title).toBe('Notas');
    expect(result.done?.messageId).toBe('m2');
  });
});
