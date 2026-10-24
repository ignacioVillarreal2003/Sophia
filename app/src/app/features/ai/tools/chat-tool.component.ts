import {
  AfterViewChecked,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  ChatMessageView,
  ChatMode,
  ConversationSummary,
  Stance,
} from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { IconName } from '@shared/ui/icon/icons';
import { MarkdownViewComponent } from '../../document-workspace/markdown/markdown-view.component';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

const MODES: { mode: ChatMode; icon: IconName }[] = [
  { mode: 'DOCUMENTS', icon: 'message-circle' },
  { mode: 'SOCRATIC', icon: 'brain' },
  { mode: 'DEBATE', icon: 'swap' },
];

/**
 * Conversations: ask questions about the workspace's documents (answers cite them), learn with a tutor who asks
 * instead of tells, or argue a motion against a sparring partner. Conversations are saved and only their owner can
 * open them; the documents are read again with the person's own permissions on every message.
 */
@Component({
  selector: 'app-ai-chat-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, MarkdownViewComponent, RelativeTimePipe, RouterLink, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="chat" [class.chat--thread]="view() || creating()">
      <!-- conversation list -->
      <aside class="chat__list" [attr.aria-label]="'ai.chat.conversations' | t">
        <app-button variant="dark" size="sm" icon="plus" block (click)="startCreating()">{{ 'ai.chat.new' | t }}</app-button>
        @if (loadingList()) {
          <p class="chat__note">{{ 'ai.sources.loading' | t }}</p>
        } @else if (list().length === 0) {
          <p class="chat__note">{{ 'ai.chat.noConversations' | t }}</p>
        } @else {
          <ul class="chat__items">
            @for (c of list(); track c.id) {
              <li>
                <button type="button" class="chat__item" [class.chat__item--on]="view()?.conversation?.id === c.id" (click)="open(c.id)">
                  <app-icon [name]="iconOf(c.mode)" [size]="15" />
                  <span class="chat__item-text">
                    <span class="chat__item-title">{{ c.title || ('ai.chat.untitled' | t) }}</span>
                    <span class="chat__item-sub">{{ 'ai.chat.mode.' + c.mode | t }} · {{ c.updatedAt | relativeTime }}</span>
                  </span>
                </button>
              </li>
            }
          </ul>
        }
        @if (list().length > 0) {
          @if (confirmingDeleteAll()) {
            <div class="chat__wipe" role="alert">
              <span>{{ 'ai.chat.deleteAllConfirm' | t }}</span>
              <div class="ait__actions">
                <app-button variant="danger" size="sm" (click)="removeAll()">{{ 'ai.chat.deleteAllYes' | t }}</app-button>
                <app-button variant="text" size="sm" (click)="confirmingDeleteAll.set(false)">{{ 'common.cancel' | t }}</app-button>
              </div>
            </div>
          } @else {
            <button type="button" class="chat__wipe-link" (click)="confirmingDeleteAll.set(true)">{{ 'ai.chat.deleteAll' | t }}</button>
          }
        }
      </aside>

      <!-- thread -->
      <section class="chat__main">
        @if (creating()) {
          <form class="ait__form chat__new" (submit)="$event.preventDefault(); create()">
            <button type="button" class="chat__back" (click)="creating.set(false)"><app-icon name="arrow-left" [size]="15" />{{ 'common.back' | t }}</button>
            <h3 class="ait__result-title">{{ 'ai.chat.new' | t }}</h3>
            <div class="ait__field">
              <span class="ait__label">{{ 'ai.chat.kind' | t }}</span>
              <div class="chat__modes" role="radiogroup" [attr.aria-label]="'ai.chat.kind' | t">
                @for (m of modes; track m.mode) {
                  <button type="button" role="radio" class="chat__mode" [class.chat__mode--on]="newMode() === m.mode" [attr.aria-checked]="newMode() === m.mode" (click)="newMode.set(m.mode)">
                    <app-icon [name]="m.icon" [size]="18" />
                    <strong>{{ 'ai.chat.mode.' + m.mode | t }}</strong>
                    <span>{{ 'ai.chat.mode.' + m.mode + '.hint' | t }}</span>
                  </button>
                }
              </div>
            </div>

            @if (newMode() === 'DEBATE') {
              <div class="ait__field">
                <label class="ait__label" for="chat-motion">{{ 'ai.debate.motion' | t }}</label>
                <input id="chat-motion" class="ait__input" maxlength="500" [placeholder]="'ai.debate.motionPlaceholder' | t" [value]="newTopic()" (input)="newTopic.set($any($event.target).value)" />
              </div>
              <div class="ait__field">
                <span class="ait__label">{{ 'ai.chat.stance' | t }}</span>
                <div class="ait__chips" role="radiogroup" [attr.aria-label]="'ai.chat.stance' | t">
                  @for (s of stances; track s) {
                    <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="newStance() === s" [attr.aria-checked]="newStance() === s" (click)="newStance.set(s)">{{ 'ai.chat.stance.' + s | t }}</button>
                  }
                </div>
              </div>
            } @else if (newMode() === 'SOCRATIC') {
              <div class="ait__field">
                <label class="ait__label" for="chat-topic">{{ 'ai.chat.learn' | t }}</label>
                <input id="chat-topic" class="ait__input" maxlength="500" [placeholder]="'ai.chat.learnPlaceholder' | t" [value]="newTopic()" (input)="newTopic.set($any($event.target).value)" />
              </div>
            }

            <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="newSources" />

            @if (error()) {
              <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
            }
            <div class="ait__actions">
              <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canCreate()">{{ 'ai.chat.start' | t }}</app-button>
            </div>
          </form>
        } @else if (view(); as v) {
          <header class="chat__head">
            <button type="button" class="chat__back" (click)="close()"><app-icon name="arrow-left" [size]="15" />{{ 'ai.chat.conversations' | t }}</button>
            <input class="chat__title" [value]="v.conversation.title" [placeholder]="'ai.chat.untitled' | t" [attr.aria-label]="'ai.chat.rename' | t" maxlength="160" (blur)="rename($any($event.target).value)" (keydown.enter)="$any($event.target).blur()" />
            <app-button variant="text" size="sm" icon="file-text" iconOnly [attr.aria-label]="'ai.chat.documents' | t" (click)="editingSources.set(!editingSources())" />
            @if (confirmingDelete()) {
              <app-button variant="danger" size="sm" (click)="remove()">{{ 'ai.chat.deleteConfirm' | t }}</app-button>
              <app-button variant="text" size="sm" (click)="confirmingDelete.set(false)">{{ 'common.cancel' | t }}</app-button>
            } @else {
              <app-button variant="text" size="sm" icon="trash" iconOnly [attr.aria-label]="'ai.chat.delete' | t" (click)="confirmingDelete.set(true)" />
            }
          </header>

          @if (v.conversation.mode === 'DEBATE') {
            <p class="chat__context"><app-icon name="swap" [size]="14" /> {{ v.conversation.topic }} · {{ 'ai.chat.youArgue.' + v.conversation.stance | t }}</p>
          } @else if (v.conversation.mode === 'SOCRATIC' && v.conversation.topic) {
            <p class="chat__context"><app-icon name="brain" [size]="14" /> {{ v.conversation.topic }}</p>
          }

          @if (editingSources()) {
            <div class="chat__sources">
              <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="draftSources" />
              <div class="ait__actions">
                <app-button variant="dark" size="sm" [loading]="busy()" (click)="saveSources()">{{ 'common.save' | t }}</app-button>
                <app-button variant="text" size="sm" (click)="editingSources.set(false)">{{ 'common.cancel' | t }}</app-button>
              </div>
            </div>
          } @else if (v.conversation.sources.length > 0) {
            <div class="chat__docs">
              @for (s of v.conversation.sources; track s.artifactId) {
                <a class="chat__doc" [routerLink]="['/document-workspaces', workspaceId(), 'a', s.artifactId]"><app-icon name="file-text" [size]="12" />{{ s.title }}</a>
              }
            </div>
          }

          <div class="chat__scroll" #scroller aria-live="polite">
            @if (messages().length === 0) {
              <div class="ait__empty">
                <app-icon [name]="iconOf(v.conversation.mode)" [size]="26" />
                <strong>{{ 'ai.chat.empty.' + v.conversation.mode | t }}</strong>
                <div class="ait__chips">
                  @for (s of suggestions(v.conversation.mode); track s) {
                    <button type="button" class="ait__chip" (click)="send(s)">{{ s }}</button>
                  }
                </div>
              </div>
            }
            @for (m of messages(); track $index) {
              <article class="chat__msg" [class.chat__msg--user]="m.role === 'USER'">
                @if (m.role === 'USER') {
                  <p class="chat__bubble chat__bubble--user">{{ m.content }}</p>
                } @else {
                  <div class="chat__bubble">
                    @if (m.content) {
                      <app-markdown-view [source]="m.content" [readonly]="true" />
                    } @else {
                      <span class="chat__typing" aria-label="…"><i></i><i></i><i></i></span>
                    }
                    @if (m.citations.length > 0) {
                      <div class="chat__cites">
                        <span>{{ 'ai.chat.sources' | t }}</span>
                        @for (c of m.citations; track c.index) {
                          <a class="chat__cite" [routerLink]="['/document-workspaces', workspaceId(), 'a', c.artifactId]">[{{ c.index }}] {{ c.title }}</a>
                        }
                      </div>
                    }
                  </div>
                }
              </article>
            }
          </div>

          @if (error()) {
            <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
          }

          <form class="chat__composer" (submit)="$event.preventDefault(); send(draft())">
            <textarea
              class="ait__textarea chat__input"
              rows="2"
              maxlength="4000"
              [attr.aria-label]="'ai.chat.message' | t"
              [placeholder]="'ai.chat.placeholder.' + v.conversation.mode | t"
              [value]="draft()"
              [disabled]="streaming()"
              (input)="draft.set($any($event.target).value)"
              (keydown)="onKey($event)"
            ></textarea>
            <div class="chat__buttons">
              @if (streaming()) {
                <app-button variant="secondary" icon="pause" (click)="stop()">{{ 'ai.stop' | t }}</app-button>
              } @else {
                <app-button type="submit" variant="dark" icon="send" [disabled]="!draft().trim()">{{ 'ai.chat.send' | t }}</app-button>
                @if (v.conversation.mode !== 'DOCUMENTS' && messages().length > 1) {
                  <app-button variant="text" (click)="finishSession()">{{ 'ai.chat.finish' | t }}</app-button>
                }
              }
            </div>
          </form>
        } @else {
          <div class="ait__empty chat__welcome">
            <app-icon name="message-circle" [size]="30" />
            <strong>{{ 'ai.chat.welcome' | t }}</strong>
            <span>{{ 'ai.chat.welcomeHint' | t }}</span>
            <app-button variant="dark" icon="plus" (click)="startCreating()">{{ 'ai.chat.new' | t }}</app-button>
          </div>
        }
      </section>
    </div>
  `,
  styles: `
    .chat { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: var(--space-lg); align-items: start; }
    .chat__list { display: flex; flex-direction: column; gap: var(--space-sm); }
    .chat__note { margin: 0; padding: var(--space-md); font-size: var(--type-caption-size); color: var(--text-subtle); text-align: center; }
    .chat__items { display: flex; flex-direction: column; gap: 2px; margin: 0; padding: 0; list-style: none; max-height: 560px; overflow: auto; }
    .chat__item {
      display: flex; align-items: flex-start; gap: 10px; width: 100%; padding: 9px 10px; font: inherit; color: var(--text-muted);
      text-align: left; background: transparent; border: 0; border-radius: var(--radius-md); cursor: pointer;
      &:hover { background: var(--surface-muted); }
      &:focus-visible { outline: 2px solid var(--accent-focus); outline-offset: -2px; }
      &--on { color: var(--text); background: var(--surface-sunken); }
      app-icon { margin-top: 2px; flex-shrink: 0; }
    }
    .chat__item-text { display: flex; flex-direction: column; min-width: 0; }
    .chat__item-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--type-caption-size); color: var(--text); }
    .chat__item-sub { font-size: var(--type-fine-size); color: var(--text-subtle); }
    .chat__wipe { display: flex; flex-direction: column; gap: 8px; padding: 10px; font-size: var(--type-fine-size); color: var(--danger); background: var(--danger-surface); border-radius: var(--radius-md); }
    .chat__wipe-link { align-self: flex-start; padding: 4px 6px; font: inherit; font-size: var(--type-fine-size); color: var(--text-subtle); text-decoration: underline; background: none; border: 0; cursor: pointer; &:hover { color: var(--danger); } &:focus-visible { outline: 2px solid var(--accent-focus); } }
    .chat__main { display: flex; flex-direction: column; gap: var(--space-md); min-width: 0; padding: var(--space-lg); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); }
    .chat__head { display: flex; align-items: center; gap: var(--space-sm); }
    .chat__back { display: none; align-items: center; gap: 6px; padding: 4px 8px; font: inherit; font-size: var(--type-caption-size); color: var(--text-muted); background: transparent; border: 0; border-radius: var(--radius-md); cursor: pointer; &:hover { background: var(--surface-muted); } }
    .chat__title { flex: 1; min-width: 0; padding: 6px 8px; font: inherit; font-weight: 600; color: var(--text); background: transparent; border: 1px solid transparent; border-radius: var(--radius-md); &:hover { border-color: var(--border); } &:focus-visible { outline: 2px solid var(--accent-focus); background: var(--surface); } }
    .chat__context { display: flex; align-items: center; gap: 6px; margin: 0; font-size: var(--type-caption-size); color: var(--text-muted); }
    .chat__docs { display: flex; flex-wrap: wrap; gap: 6px; }
    .chat__doc { display: inline-flex; align-items: center; gap: 5px; max-width: 240px; padding: 3px 10px; overflow: hidden; font-size: var(--type-fine-size); color: var(--text-muted); text-overflow: ellipsis; white-space: nowrap; text-decoration: none; background: var(--surface-sunken); border-radius: var(--radius-full); &:hover { color: var(--text); } }
    .chat__sources { display: flex; flex-direction: column; gap: var(--space-sm); padding: var(--space-md); background: var(--surface-sunken); border-radius: var(--radius-md); }
    .chat__scroll { display: flex; flex-direction: column; gap: var(--space-md); min-height: 260px; max-height: 58vh; overflow: auto; padding: 2px; scroll-behavior: smooth; }
    .chat__msg { display: flex; &--user { justify-content: flex-end; } }
    .chat__bubble { max-width: min(760px, 92%); padding: 10px 14px; font-size: var(--type-caption-size); line-height: 1.55; color: var(--text); background: var(--surface); box-shadow: inset 0 0 0 1px var(--border); border-radius: var(--radius-md); overflow-wrap: anywhere;
      &--user { margin: 0; color: var(--text); white-space: pre-wrap; background: var(--surface-sunken); }
    }
    .chat__cites { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 8px; padding-top: 8px; font-size: var(--type-fine-size); color: var(--text-subtle); border-top: 1px solid var(--divider); }
    .chat__cite { padding: 2px 8px; color: var(--accent); text-decoration: none; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-full); max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; &:hover { background: var(--surface-muted); } }
    .chat__typing { display: inline-flex; gap: 4px; i { width: 6px; height: 6px; background: var(--text-subtle); border-radius: 50%; animation: chat-dot 1.2s infinite ease-in-out; &:nth-child(2) { animation-delay: 0.15s; } &:nth-child(3) { animation-delay: 0.3s; } } }
    @keyframes chat-dot { 0%, 80%, 100% { opacity: 0.25; } 40% { opacity: 1; } }
    @media (prefers-reduced-motion: reduce) { .chat__typing i { animation: none; opacity: 0.6; } .chat__scroll { scroll-behavior: auto; } }
    .chat__composer { display: flex; gap: var(--space-sm); align-items: flex-end; }
    .chat__input { flex: 1; min-height: 56px; max-height: 160px; }
    .chat__buttons { display: flex; flex-direction: column; gap: 4px; align-items: stretch; }
    .chat__modes { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: var(--space-sm); }
    .chat__mode { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; padding: 12px 14px; font: inherit; text-align: left; color: var(--text-muted); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); cursor: pointer;
      strong { color: var(--text); font-size: var(--type-caption-size); }
      span { font-size: var(--type-fine-size); line-height: 1.4; }
      &:hover { background: var(--surface-muted); }
      &:focus-visible { outline: 2px solid var(--accent-focus); outline-offset: 1px; }
      &--on { border-color: var(--text); background: var(--surface-sunken); color: var(--text); }
    }
    .chat__welcome { padding: var(--space-section) var(--space-lg); }
    @media (max-width: 860px) {
      .chat { grid-template-columns: minmax(0, 1fr); }
      .chat--thread .chat__list { display: none; }
      .chat:not(.chat--thread) .chat__main { display: none; }
      .chat__back { display: inline-flex; }
      .chat__composer { flex-direction: column; align-items: stretch; }
      .chat__buttons { flex-direction: row; justify-content: flex-end; }
    }
  `,
})
export class ChatToolComponent extends AiToolBase implements OnInit, AfterViewChecked {
  private readonly destroyRef = inject(DestroyRef);
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  readonly modes = MODES;
  readonly stances: Stance[] = ['PRO', 'CON'];

  readonly list = signal<ConversationSummary[]>([]);
  readonly loadingList = signal(true);
  readonly view = signal<{ conversation: ConversationSummary } | null>(null);
  readonly messages = signal<ChatMessageView[]>([]);

  readonly creating = signal(false);
  readonly newMode = signal<ChatMode>('DOCUMENTS');
  readonly newTopic = signal('');
  readonly newStance = signal<Stance>('PRO');
  readonly newSources = signal<string[]>([]);

  readonly editingSources = signal(false);
  readonly draftSources = signal<string[]>([]);
  readonly confirmingDelete = signal(false);
  readonly confirmingDeleteAll = signal(false);

  readonly draft = signal('');
  readonly streaming = signal(false);
  private stickToBottom = true;

  readonly canCreate = computed(() => !this.busy() && (this.newMode() !== 'DEBATE' || this.newTopic().trim().length > 0));

  constructor() {
    super();
    this.destroyRef.onDestroy(() => this.stream?.cancel());
  }

  ngOnInit(): void {
    this.refreshList();
  }

  ngAfterViewChecked(): void {
    const el = this.scroller()?.nativeElement;
    if (el && this.stickToBottom) {
      el.scrollTop = el.scrollHeight;
    }
  }

  iconOf(mode: ChatMode): IconName {
    return MODES.find((m) => m.mode === mode)?.icon ?? 'message-circle';
  }

  suggestions(mode: ChatMode): string[] {
    return [1, 2, 3].map((n) => this.t(`ai.chat.suggest.${mode}.${n}`));
  }

  // ---- list and selection ---------------------------------------------------------------------------------

  private refreshList(): void {
    this.ai.conversations(this.workspaceId()).subscribe({
      next: (list) => {
        this.list.set(list);
        this.loadingList.set(false);
      },
      error: () => this.loadingList.set(false),
    });
  }

  open(id: string): void {
    this.cancelStream();
    this.creating.set(false);
    this.error.set('');
    this.editingSources.set(false);
    this.confirmingDelete.set(false);
    this.ai.conversation(id).subscribe({
      next: (v) => {
        this.view.set({ conversation: v.conversation });
        this.messages.set(v.messages);
        this.draftSources.set(v.conversation.sources.map((s) => s.artifactId));
        this.stickToBottom = true;
      },
      error: (err) => this.fail(err),
    });
  }

  close(): void {
    this.cancelStream();
    this.view.set(null);
    this.messages.set([]);
  }

  startCreating(): void {
    this.cancelStream();
    this.view.set(null);
    this.creating.set(true);
    this.error.set('');
  }

  create(): void {
    if (!this.canCreate()) {
      return;
    }
    this.begin();
    const mode = this.newMode();
    this.ai
      .createConversation({
        workspaceId: this.workspaceId(),
        mode,
        topic: this.newTopic().trim() || undefined,
        stance: mode === 'DEBATE' ? this.newStance() : undefined,
        sources: this.newSources().map((artifactId) => ({ artifactId })),
      })
      .subscribe({
        next: (v) => {
          this.busy.set(false);
          this.creating.set(false);
          this.newTopic.set('');
          this.list.update((list) => [v.conversation, ...list]);
          this.view.set({ conversation: v.conversation });
          this.messages.set([]);
          this.draftSources.set(v.conversation.sources.map((s) => s.artifactId));
        },
        error: (err) => this.fail(err),
      });
  }

  rename(value: string): void {
    const v = this.view();
    const title = value.trim();
    if (!v || !title || title === v.conversation.title) {
      return;
    }
    this.ai.updateConversation(v.conversation.id, { title }).subscribe({
      next: (updated) => this.applySummary(updated),
      error: (err) => this.fail(err),
    });
  }

  saveSources(): void {
    const v = this.view();
    if (!v) {
      return;
    }
    this.begin();
    this.ai
      .updateConversation(v.conversation.id, { sources: this.draftSources().map((artifactId) => ({ artifactId })) })
      .subscribe({
        next: (updated) => {
          this.busy.set(false);
          this.editingSources.set(false);
          this.applySummary(updated);
        },
        error: (err) => this.fail(err),
      });
  }

  removeAll(): void {
    this.ai.deleteAllConversations().subscribe({
      next: () => {
        this.confirmingDeleteAll.set(false);
        this.list.set([]);
        this.close();
      },
      error: (err) => this.fail(err),
    });
  }

  remove(): void {
    const v = this.view();
    if (!v) {
      return;
    }
    this.ai.deleteConversation(v.conversation.id).subscribe({
      next: () => {
        this.list.update((list) => list.filter((c) => c.id !== v.conversation.id));
        this.confirmingDelete.set(false);
        this.close();
      },
      error: (err) => this.fail(err),
    });
  }

  private applySummary(updated: ConversationSummary): void {
    this.view.set({ conversation: updated });
    this.list.update((list) => list.map((c) => (c.id === updated.id ? updated : c)));
  }

  // ---- messages -------------------------------------------------------------------------------------------

  onKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.send(this.draft());
    }
  }

  finishSession(): void {
    this.send(this.t('ai.chat.finishMessage'), true);
  }

  send(content: string, finish = false): void {
    const v = this.view();
    const text = content.trim();
    if (!v || !text || this.streaming()) {
      return;
    }
    this.error.set('');
    this.draft.set('');
    this.streaming.set(true);
    this.stickToBottom = true;
    const now = new Date().toISOString();
    this.messages.update((list) => [
      ...list,
      { id: '', role: 'USER', content: text, citations: [], createdAt: now },
      { id: '', role: 'ASSISTANT', content: '', citations: [], createdAt: now },
    ]);
    const patchLast = (patch: (m: ChatMessageView) => ChatMessageView) =>
      this.messages.update((list) => list.map((m, i) => (i === list.length - 1 ? patch(m) : m)));

    this.stream = this.ai.sendMessage(v.conversation.id, text, finish, {
      delta: (piece) => patchLast((m) => ({ ...m, content: m.content + piece })),
      done: (data) => {
        patchLast((m) => ({ ...m, id: data.messageId ?? m.id, citations: data.citations ?? [] }));
        this.streaming.set(false);
        this.touch(v.conversation.id, text);
        this.refreshStatus();
      },
      error: (err) => {
        // Nothing was written: take the empty answer back out and give the question back to the box.
        this.messages.update((list) => {
          const last = list[list.length - 1];
          return last && last.role === 'ASSISTANT' && !last.content ? list.slice(0, -2) : list;
        });
        if (!this.messages().some((m) => m.content === text && m.role === 'USER')) {
          this.draft.set(text);
        }
        this.streaming.set(false);
        this.fail(err);
      },
    });
  }

  /** The first message names an untitled conversation on the server; mirror that and move it to the top. */
  private touch(id: string, firstText: string): void {
    this.list.update((list) => {
      const found = list.find((c) => c.id === id);
      if (!found) {
        return list;
      }
      const title = found.title || firstText.split('\n')[0].slice(0, 60);
      const updated = { ...found, title, updatedAt: new Date().toISOString() };
      this.view.update((v) => (v && v.conversation.id === id ? { conversation: updated } : v));
      return [updated, ...list.filter((c) => c.id !== id)];
    });
  }

  stop(): void {
    // The server keeps whatever was written before the connection closed; what is on screen is already that.
    this.cancelStream();
  }

  private cancelStream(): void {
    this.stream?.cancel();
    this.stream = null;
    this.streaming.set(false);
  }
}
