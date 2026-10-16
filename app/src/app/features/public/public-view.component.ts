import { HttpClient, HttpContext } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { environment } from '@env/environment';
import { ApiResponse } from '@core/models/api.model';
import { SKIP_AUTH } from '@core/services/http-context';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { MarkdownViewComponent } from '../document-workspace/markdown/markdown-view.component';

interface PublicView {
  readonly name: string;
  readonly type: string;
  readonly workspaceName: string;
  readonly updatedAt: string;
  readonly markdown: string;
}

/** Read-only page behind a public link. It lives outside the app shell and needs no account. */
@Component({
  selector: 'app-public-view',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, IconComponent, SpinnerComponent, TranslatePipe, MarkdownViewComponent],
  template: `
    <main class="pv">
      <header class="pv__top">
        <span class="pv__brand"><img src="/sophia.png" alt="" width="20" height="20" /> Sophia</span>
        <span class="pv__badge"><app-icon name="eye" [size]="14" /> {{ 'public.readOnly' | t }}</span>
      </header>

      @if (loading()) {
        <div class="pv__state"><app-spinner /></div>
      } @else if (view(); as v) {
        <article class="pv__doc">
          <h1 class="pv__title">{{ v.name }}</h1>
          <p class="pv__meta">{{ v.workspaceName }} · {{ v.updatedAt | date: 'mediumDate' }}</p>
          <div class="pv__body"><app-markdown-view [source]="body()" [readonly]="true" /></div>
        </article>
      } @else {
        <div class="pv__state pv__state--missing">
          <span class="pv__missing-icon"><app-icon name="lock" [size]="26" /></span>
          <h1>{{ 'public.notFound.title' | t }}</h1>
          <p>{{ 'public.notFound.desc' | t }}</p>
        </div>
      }
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        background: var(--canvas, var(--surface));
        color: var(--text);
      }
      .pv {
        max-width: 760px;
        margin-inline: auto;
        padding: var(--space-xl) var(--space-lg) var(--space-section);
      }
      .pv__top {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: var(--space-xl);
        padding-bottom: var(--space-md);
        border-bottom: 1px solid var(--divider);
      }
      .pv__brand {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        font-weight: 600;
      }
      .pv__badge {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 4px 10px;
        border-radius: var(--radius-pill);
        background: var(--surface-sunken);
        box-shadow: inset 0 0 0 1px var(--border);
        font-size: 12px;
        font-weight: 600;
        color: var(--text-muted);
      }
      .pv__title {
        margin: 0 0 6px;
        font-size: 2.2rem;
        font-weight: 600;
        letter-spacing: -0.02em;
        line-height: 1.15;
      }
      .pv__meta {
        margin: 0 0 var(--space-lg);
        font-size: 13px;
        color: var(--text-subtle);
      }
      .pv__state {
        display: grid;
        justify-items: center;
        gap: 8px;
        padding: var(--space-section) 0;
        text-align: center;

        h1 { font-size: var(--type-tagline-size); font-weight: 600; }
        p { margin: 0; max-width: 380px; color: var(--text-subtle); line-height: 1.5; }
      }
      .pv__missing-icon {
        display: grid;
        place-items: center;
        width: 56px;
        height: 56px;
        border-radius: var(--radius-lg);
        background: var(--surface-sunken);
        box-shadow: inset 0 0 0 1px var(--border);
        color: var(--text-subtle);
      }
      .pv__body {
        line-height: 1.7;
        font-size: 16px;
        overflow-wrap: anywhere;
      }
      .pv__body :is(h1, h2, h3, h4) { margin: 1.6em 0 0.5em; line-height: 1.25; font-weight: 600; }
      .pv__body h1 { margin-top: 0; font-size: 2rem; letter-spacing: -0.02em; }
      .pv__body h2 { font-size: 1.5rem; }
      .pv__body h3 { font-size: 1.2rem; }
      .pv__body p { margin: 0 0 1em; }
      .pv__body a { color: var(--accent); text-decoration: underline; }
      .pv__body code {
        padding: 2px 6px;
        border-radius: 6px;
        background: var(--surface-sunken);
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
        font-size: 0.9em;
      }
      .pv__body pre {
        margin: 0 0 1em;
        padding: 14px 16px;
        overflow-x: auto;
        border-radius: var(--radius-md);
        background: var(--surface-sunken);
        box-shadow: inset 0 0 0 1px var(--border);
      }
      .pv__body pre code { padding: 0; background: none; }
      .pv__body blockquote {
        margin: 0 0 1em;
        padding: 4px 16px;
        border-left: 3px solid var(--border-strong, var(--border));
        color: var(--text-muted);
      }
      .pv__body :is(ul, ol) { margin: 0 0 1em; padding-left: 1.4em; }
      .pv__body hr { margin: 1.6em 0; border: 0; border-top: 1px solid var(--divider); }
      .pv__body table {
        width: 100%;
        margin: 0 0 1em;
        border-collapse: collapse;
        font-size: 14px;
      }
      .pv__body :is(th, td) { padding: 8px 12px; border: 1px solid var(--border); text-align: left; }
      .pv__body th { background: var(--surface-sunken); font-weight: 600; }
    `,
  ],
})
export class PublicViewComponent implements OnInit {
  private readonly http = inject(HttpClient);

  /** Route param. */
  readonly token = input.required<string>();

  readonly view = signal<PublicView | null>(null);
  readonly loading = signal(true);
  /** Documents come with their own "# title" first line; the page shows the title above, so drop the duplicate. */
  readonly body = computed(() => {
    const v = this.view();
    if (!v) return '';
    const duplicate = `# ${v.name}`;
    const body = v.markdown.startsWith(duplicate) ? v.markdown.slice(duplicate.length) : v.markdown;
    return body.replace(/^\s+/, '');
  });

  ngOnInit(): void {
    this.http
      .get<ApiResponse<PublicView>>(`${environment.documentWorkspaceApiUrl}/api/v1/public/${encodeURIComponent(this.token())}`, {
        context: new HttpContext().set(SKIP_AUTH, true),
      })
      .subscribe({
        next: (res) => {
          this.view.set(res.data);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
