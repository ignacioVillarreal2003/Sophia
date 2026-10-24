import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, map, tap } from 'rxjs';
import { ApiResponse } from '@core/models/api.model';
import {
  AiSettings,
  AiSettingsUpdate,
  AiStatus,
  AiUsage,
  Citation,
  ContinueRequest,
  ConversationSummary,
  ConversationView,
  CreateConversationRequest,
  DebateRequest,
  DebateResult,
  DiagramRequest,
  DiagramResult,
  ListenRequest,
  OcrMode,
  TableRequest,
  TableResult,
  TagsRequest,
  TagsResult,
  DeckRequest,
  DeckResult,
  GapsRequest,
  GapsResult,
  GradeRequest,
  GradeResult,
  MindMapRequest,
  MindMapResult,
  NotesRequest,
  PerspectivesRequest,
  PerspectivesResult,
  QuizRequest,
  QuizResult,
  ReviewSummaryRequest,
  RewriteRequest,
  StudyPlanRequest,
  StudyPlanResult,
  UpdateConversationRequest,
} from '@core/models/ai/ai.model';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { TokenStorageService } from '@core/services/authentication/token-storage.service';
import { TranslationService } from '@core/services/i18n';
import { silentErrors } from '@core/services/http-context';
import { firstValueFrom } from 'rxjs';
import { SseParser } from '../lib/sse';

/** What a failed AI call looks like to the screens. */
export interface AiError {
  readonly status: number;
  readonly code: string;
  readonly message: string;
}

export interface StreamHandlers {
  /** The first event: which feature, and for conversations the ids involved. */
  meta?: (data: Record<string, unknown>) => void;
  /** A piece of the answer. */
  delta: (text: string) => void;
  /** The answer is complete. `data` carries the usage and, for conversations, the saved message and its citations. */
  done?: (data: StreamDone) => void;
  error?: (error: AiError) => void;
}

export interface StreamDone {
  truncated?: boolean;
  usage?: { input: number; output: number };
  messageId?: string;
  citations?: Citation[];
}

/** A running stream; cancelling it closes the connection, which stops the generation on the server. */
export interface AiStream {
  cancel(): void;
  readonly finished: Promise<void>;
}

export interface AiHandoff {
  readonly tool: string;
  readonly sources?: string[];
  readonly topic?: string;
  readonly weakTopics?: string[];
  readonly instructions?: string;
}

/**
 * Everything the AI screens say to the AI service. Generation results are drafts: the screens decide what to turn
 * them into, and the documents are created through the document service with the person's own token.
 */
@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly http = inject(HttpClient);
  private readonly tokens = inject(TokenStorageService);
  private readonly auth = inject(AuthenticationService);
  private readonly i18n = inject(TranslationService);
  private readonly base = `${environment.aiApiUrl}/api/v1`;

  private readonly _status = signal<AiStatus | null>(null);
  readonly status = this._status.asReadonly();
  private readonly _unreachable = signal(false);
  /** True when the last status check failed because the service is down, not because of anything the person did. */
  readonly unreachable = this._unreachable.asReadonly();
  /** True while the service is on and has a provider; false when the screens should explain why not. */
  readonly available = computed(() => {
    const status = this._status();
    return !!status && status.enabled && status.configured;
  });

  /** Passes work from one tool to the next (a quiz hands its missed topics to the review summary). */
  readonly handoff = signal<AiHandoff | null>(null);

  /** The language the results should be written in: the interface's. */
  get language(): string {
    return this.i18n.locale();
  }

  // ---- status and administration --------------------------------------------------------------------------

  loadStatus(workspaceId?: string): Observable<AiStatus> {
    return this.get<AiStatus>('/ai/status', workspaceId ? { workspaceId } : undefined).pipe(
      tap({
        next: (status) => {
          this._status.set(status);
          this._unreachable.set(false);
        },
        // A 4xx would be our own mistake; only a service that cannot be reached (or is failing) locks the tools.
        error: (err: { status?: number }) => this._unreachable.set(!err?.status || err.status >= 500),
      }),
    );
  }

  settings(): Observable<AiSettings> {
    return this.get<AiSettings>('/admin/ai/settings');
  }

  updateSettings(update: AiSettingsUpdate): Observable<AiSettings> {
    return this.http
      .put<ApiResponse<AiSettings>>(`${this.base}/admin/ai/settings`, update, { context: this.quiet() })
      .pipe(map((r) => r.data));
  }

  usage(days: number): Observable<AiUsage> {
    return this.get<AiUsage>('/admin/ai/usage', { days });
  }

  // ---- streamed text --------------------------------------------------------------------------------------

  notes(request: NotesRequest, handlers: StreamHandlers): AiStream {
    return this.stream('/ai/notes', { language: this.language, ...request }, handlers);
  }

  rewrite(request: RewriteRequest, handlers: StreamHandlers): AiStream {
    return this.stream('/ai/rewrite', { language: this.language, ...request }, handlers);
  }

  continueText(request: ContinueRequest, handlers: StreamHandlers): AiStream {
    return this.stream('/ai/continue', { language: this.language, ...request }, handlers);
  }

  reviewSummary(request: ReviewSummaryRequest, handlers: StreamHandlers): AiStream {
    return this.stream('/ai/review-summary', { language: this.language, ...request }, handlers);
  }

  sendMessage(conversationId: string, content: string, finish: boolean, handlers: StreamHandlers): AiStream {
    return this.stream(`/ai/conversations/${conversationId}/messages`, { content, finish }, handlers);
  }

  // ---- structured results ---------------------------------------------------------------------------------

  deck(request: DeckRequest): Observable<DeckResult> {
    return this.post('/ai/deck', { language: this.language, ...request });
  }

  mindMap(request: MindMapRequest): Observable<MindMapResult> {
    return this.post('/ai/mindmap', { language: this.language, ...request });
  }

  quiz(request: QuizRequest): Observable<QuizResult> {
    return this.post('/ai/quiz', { language: this.language, ...request });
  }

  grade(request: GradeRequest): Observable<GradeResult> {
    return this.post('/ai/quiz/grade', { language: this.language, ...request });
  }

  studyPlan(request: StudyPlanRequest): Observable<StudyPlanResult> {
    return this.post('/ai/study-plan', { language: this.language, ...request });
  }

  perspectives(request: PerspectivesRequest): Observable<PerspectivesResult> {
    return this.post('/ai/perspectives', { language: this.language, ...request });
  }

  debate(request: DebateRequest): Observable<DebateResult> {
    return this.post('/ai/debate', { language: this.language, ...request });
  }

  gaps(request: GapsRequest): Observable<GapsResult> {
    return this.post('/ai/gaps', { language: this.language, ...request });
  }

  // ---- conversions ----------------------------------------------------------------------------------------

  /** Transcribes a scan, a photo or a PDF; the Markdown arrives as it is written. */
  ocr(file: File, mode: OcrMode, workspaceId: string | undefined, handlers: StreamHandlers): AiStream {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('mode', mode);
    form.append('language', this.language);
    if (workspaceId) {
      form.append('workspaceId', workspaceId);
    }
    return this.stream('/ai/ocr', form, handlers);
  }

  listen(request: ListenRequest, handlers: StreamHandlers): AiStream {
    return this.stream('/ai/listen', { language: this.language, ...request }, handlers);
  }

  table(request: TableRequest): Observable<TableResult> {
    return this.post('/ai/tables', { language: this.language, ...request });
  }

  tableFromFile(file: File, workspaceId: string | undefined, instructions: string | undefined): Observable<TableResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('language', this.language);
    if (workspaceId) {
      form.append('workspaceId', workspaceId);
    }
    if (instructions) {
      form.append('instructions', instructions);
    }
    return this.post('/ai/tables/file', form);
  }

  diagram(request: DiagramRequest): Observable<DiagramResult> {
    return this.post('/ai/diagrams', { language: this.language, ...request });
  }

  tags(request: TagsRequest): Observable<TagsResult> {
    return this.post('/ai/tags', { language: this.language, ...request });
  }

  // ---- conversations --------------------------------------------------------------------------------------

  conversations(workspaceId?: string): Observable<ConversationSummary[]> {
    return this.get<ConversationSummary[]>('/ai/conversations', workspaceId ? { workspaceId } : undefined);
  }

  createConversation(request: CreateConversationRequest): Observable<ConversationView> {
    return this.post('/ai/conversations', { language: this.language, ...request });
  }

  conversation(id: string): Observable<ConversationView> {
    return this.get<ConversationView>(`/ai/conversations/${id}`);
  }

  updateConversation(id: string, request: UpdateConversationRequest): Observable<ConversationSummary> {
    return this.http
      .patch<ApiResponse<ConversationSummary>>(`${this.base}/ai/conversations/${id}`, request, { context: this.quiet() })
      .pipe(map((r) => r.data));
  }

  deleteAllConversations(): Observable<number> {
    return this.http
      .delete<ApiResponse<number>>(`${this.base}/ai/conversations`, { context: this.quiet() })
      .pipe(map((r) => r.data));
  }

  deleteConversation(id: string): Observable<void> {
    return this.http
      .delete<void>(`${this.base}/ai/conversations/${id}`, { context: this.quiet() })
      .pipe(map(() => undefined));
  }

  // ---- plumbing -------------------------------------------------------------------------------------------

  private quiet(): HttpContext {
    return silentErrors();
  }

  private get<T>(path: string, params?: Record<string, string | number>): Observable<T> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params ?? {})) {
      httpParams = httpParams.set(key, String(value));
    }
    return this.http
      .get<ApiResponse<T>>(`${this.base}${path}`, { params: httpParams, context: this.quiet() })
      .pipe(map((r) => r.data));
  }

  private post<T>(path: string, body: unknown): Observable<T> {
    return this.http
      .post<ApiResponse<T>>(`${this.base}${path}`, body, { context: this.quiet() })
      .pipe(map((r) => r.data));
  }

  /**
   * POST that answers with server-sent events. EventSource cannot send a body or an Authorization header, so this
   * reads the response with fetch (the same way the notification stream does) and retries once with a fresh token
   * when the first one has expired.
   */
  private stream(path: string, body: unknown, handlers: StreamHandlers): AiStream {
    const controller = new AbortController();
    const finished = this.run(path, body, handlers, controller.signal).catch((error: unknown) => {
      if (controller.signal.aborted) {
        return;
      }
      handlers.error?.(toAiError(error));
    });
    return { cancel: () => controller.abort(), finished };
  }

  private async run(path: string, body: unknown, handlers: StreamHandlers, signal: AbortSignal): Promise<void> {
    let response = await this.open(path, body, this.tokens.accessToken, signal);
    if (response.status === 401) {
      const fresh = await firstValueFrom(this.auth.refreshAccessToken());
      response = await this.open(path, body, fresh, signal);
    }
    if (!response.ok || !response.body) {
      throw await readError(response);
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    const parser = new SseParser();
    let ended = false;
    while (!ended) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      for (const event of parser.push(decoder.decode(value, { stream: true }))) {
        const data = event.data as Record<string, unknown>;
        switch (event.name) {
          case 'meta':
            handlers.meta?.(data);
            break;
          case 'delta':
            handlers.delta(String(data['t'] ?? ''));
            break;
          case 'done':
            handlers.done?.(data as StreamDone);
            ended = true;
            break;
          case 'error':
            handlers.error?.({
              status: 0,
              code: String(data['code'] ?? 'GENERATION_FAILED'),
              message: String(data['message'] ?? ''),
            });
            ended = true;
            break;
        }
      }
    }
    await reader.cancel().catch(() => undefined);
    if (!ended) {
      throw { status: 0, code: 'STREAM_INTERRUPTED', message: '' } satisfies AiError;
    }
  }

  private open(path: string, body: unknown, token: string | null, signal: AbortSignal): Promise<Response> {
    return fetch(`${this.base}${path}`, {
      method: 'POST',
      headers: {
        // A form sets its own content type (with the boundary); JSON has to say it.
        ...(body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body instanceof FormData ? body : JSON.stringify(body),
      signal,
    });
  }
}

async function readError(response: Response): Promise<AiError> {
  try {
    const body = (await response.json()) as { code?: string; message?: string };
    return { status: response.status, code: body.code ?? `HTTP_${response.status}`, message: body.message ?? '' };
  } catch {
    return { status: response.status, code: `HTTP_${response.status}`, message: '' };
  }
}

function toAiError(error: unknown): AiError {
  if (error && typeof error === 'object' && 'code' in error) {
    const e = error as Partial<AiError>;
    return { status: e.status ?? 0, code: e.code ?? 'GENERATION_FAILED', message: e.message ?? '' };
  }
  return { status: 0, code: 'NETWORK', message: '' };
}
