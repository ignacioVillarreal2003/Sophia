import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { DiagramKind, DiagramResult } from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { BoardElement } from '../../document-workspace/whiteboard/board.model';
import { diagramContent, diagramElements } from '../lib/diagram-builder';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

const KINDS: DiagramKind[] = ['FLOWCHART', 'TIMELINE', 'CYCLE'];

/** A flowchart, a timeline or a cycle, previewed and then saved as a whiteboard. */
@Component({
  selector: 'app-ai-diagram-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.diagram.intro' | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        <div class="ait__field">
          <span class="ait__label">{{ 'ai.diagram.kind' | t }}</span>
          <div class="ait__chips" role="radiogroup" [attr.aria-label]="'ai.diagram.kind' | t">
            @for (k of kinds; track k) {
              <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="kind() === k" [attr.aria-checked]="kind() === k" (click)="kind.set(k)">{{ 'ai.diagram.kind.' + k | t }}</button>
            }
          </div>
          <span class="ait__hint">{{ 'ai.diagram.kind.' + kind() + '.hint' | t }}</span>
        </div>

        <div class="ait__field">
          <label class="ait__label" for="diagram-topic">{{ 'ai.field.topic' | t }}</label>
          <input id="diagram-topic" class="ait__input" maxlength="500" [placeholder]="'ai.diagram.topicPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
        </div>
        <div class="ait__field">
          <label class="ait__label" for="diagram-text">{{ 'ai.diagram.text' | t }}</label>
          <textarea id="diagram-text" class="ait__textarea" rows="4" maxlength="30000" [placeholder]="'ai.diagram.textPlaceholder' | t" [value]="text()" (input)="text.set($any($event.target).value)"></textarea>
        </div>

        <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }
        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">{{ 'ai.diagram.generate' | t }}</app-button>
        </div>
      </form>

      @if (result(); as d) {
        <section class="ait__result">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ d.title }}</h3>
            <span class="ait__live">{{ 'ai.map.nodes' | t: { count: d.nodes.length } }}</span>
          </div>
          <div class="dg__stage">
            <svg [attr.viewBox]="viewBox()" role="img" [attr.aria-label]="d.title" preserveAspectRatio="xMidYMid meet">
              <defs>
                <marker id="dg-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#64748b" /></marker>
              </defs>
              @for (edge of edges(); track $index) {
                <path [attr.d]="edge.d" class="dg__edge" [attr.marker-end]="edge.arrow ? 'url(#dg-arrow)' : null" />
                @if (edge.label) {
                  <text [attr.x]="edge.lx" [attr.y]="edge.ly" class="dg__edge-label" text-anchor="middle">{{ edge.label }}</text>
                }
              }
              @for (s of shapes(); track s.id) {
                @switch (s.type) {
                  @case ('ellipse') { <ellipse [attr.cx]="s.x + s.w / 2" [attr.cy]="s.y + s.h / 2" [attr.rx]="s.w / 2" [attr.ry]="s.h / 2" [attr.fill]="s.fill" [attr.stroke]="s.stroke" stroke-width="2" /> }
                  @case ('diamond') { <polygon [attr.points]="diamond(s)" [attr.fill]="s.fill" [attr.stroke]="s.stroke" stroke-width="2" /> }
                  @case ('text') { }
                  @default { <rect [attr.x]="s.x" [attr.y]="s.y" [attr.width]="s.w" [attr.height]="s.h" rx="12" [attr.fill]="s.fill" [attr.stroke]="s.stroke" stroke-width="2" /> }
                }
                <text [attr.x]="s.x + s.w / 2" [attr.y]="s.y + s.h / 2" class="dg__label" text-anchor="middle" dominant-baseline="central">{{ s.text }}</text>
              }
            </svg>
          </div>
          <div class="ait__actions">
            <app-button variant="dark" icon="pencil" [loading]="saving()" (click)="save()">{{ 'ai.map.create' | t }}</app-button>
            <app-button variant="text" icon="sparkles" (click)="generate()" [disabled]="!canGenerate()">{{ 'ai.regenerate' | t }}</app-button>
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .dg__stage { overflow: hidden; background: var(--surface-sunken); border-radius: var(--radius-md); svg { display: block; width: 100%; height: min(62vh, 560px); } }
    .dg__edge { fill: none; stroke: #64748b; stroke-width: 1.6; }
    .dg__edge-label { font-size: 12px; fill: var(--text-muted); paint-order: stroke; stroke: var(--surface-sunken); stroke-width: 4px; }
    .dg__label { font-size: 14px; fill: #1e293b; pointer-events: none; }
  `,
})
export class DiagramToolComponent extends AiToolBase {
  readonly kinds = KINDS;
  readonly kind = signal<DiagramKind>('FLOWCHART');
  readonly topic = signal('');
  readonly text = signal('');
  readonly sources = signal<string[]>([]);
  readonly result = signal<DiagramResult | null>(null);

  readonly canGenerate = computed(
    () => !this.busy() && (this.topic().trim().length > 0 || this.text().trim().length > 0 || this.sources().length > 0),
  );

  /** The elements the saved board will hold, so the preview is what you get. */
  private readonly elements = computed<BoardElement[]>(() => {
    const d = this.result();
    return d ? diagramElements(d) : [];
  });
  readonly shapes = computed(() =>
    this.elements().filter((e) => e.type !== 'arrow' && e.type !== 'line').map((e) => ({ ...e, fill: e.fill ?? '#fff', stroke: e.stroke ?? '#475569' })),
  );
  readonly edges = computed(() => {
    const els = this.elements();
    const byId = new Map(els.map((e) => [e.id, e]));
    const out: { d: string; label: string; lx: number; ly: number; arrow: boolean }[] = [];
    for (const e of els) {
      if (e.type === 'line') {
        out.push({ d: `M ${e.x} ${e.y} L ${e.x2} ${e.y2}`, label: '', lx: 0, ly: 0, arrow: false });
      } else if (e.type === 'arrow' && e.from && e.to) {
        const a = byId.get(e.from);
        const b = byId.get(e.to);
        if (!a || !b) {
          continue;
        }
        const [ax, ay] = anchor(a, b);
        const [bx, by] = anchor(b, a);
        const curved = e.route === 'curve';
        const elbow = e.route === 'elbow';
        const midY = (ay + by) / 2;
        const d = curved
          ? `M ${ax} ${ay} Q ${(ax + bx) / 2 + (by - ay) * 0.25} ${(ay + by) / 2 - (bx - ax) * 0.25} ${bx} ${by}`
          : elbow
            ? `M ${ax} ${ay} L ${ax} ${midY} L ${bx} ${midY} L ${bx} ${by}`
            : `M ${ax} ${ay} L ${bx} ${by}`;
        out.push({ d, label: e.text, lx: (ax + bx) / 2, ly: elbow ? midY - 4 : (ay + by) / 2 - 4, arrow: e.endHead !== 'none' });
      }
    }
    return out;
  });
  readonly viewBox = computed(() => {
    const shapes = this.shapes();
    if (shapes.length === 0) {
      return '0 0 100 100';
    }
    const minX = Math.min(...shapes.map((s) => s.x)) - 40;
    const minY = Math.min(...shapes.map((s) => s.y)) - 40;
    const maxX = Math.max(...shapes.map((s) => s.x + s.w)) + 40;
    const maxY = Math.max(...shapes.map((s) => s.y + s.h)) + 40;
    return `${minX} ${minY} ${maxX - minX} ${maxY - minY}`;
  });

  diamond(s: { x: number; y: number; w: number; h: number }): string {
    return `${s.x + s.w / 2},${s.y} ${s.x + s.w},${s.y + s.h / 2} ${s.x + s.w / 2},${s.y + s.h} ${s.x},${s.y + s.h / 2}`;
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    this.ai
      .diagram({
        workspaceId: this.workspaceId(),
        kind: this.kind(),
        topic: this.topic().trim() || undefined,
        text: this.text().trim() || undefined,
        sources: this.sources().map((artifactId) => ({ artifactId })),
      })
      .subscribe({
        next: (d) => {
          this.result.set(d);
          this.busy.set(false);
          this.refreshStatus();
        },
        error: (err) => this.fail(err),
      });
  }

  save(): void {
    const d = this.result();
    if (d) {
      this.keep({ name: this.nameOf(d.title, this.topic()), type: 'WHITEBOARD', content: diagramContent(d) });
    }
  }
}

/** Where a connector leaves a shape: the middle of the side that faces the other shape. */
function anchor(from: BoardElement, to: BoardElement): [number, number] {
  const fx = from.x + from.w / 2;
  const fy = from.y + from.h / 2;
  const dx = to.x + to.w / 2 - fx;
  const dy = to.y + to.h / 2 - fy;
  if (Math.abs(dy) * from.w >= Math.abs(dx) * from.h) {
    return [fx, dy >= 0 ? from.y + from.h : from.y];
  }
  return [dx >= 0 ? from.x + from.w : from.x, fy];
}
