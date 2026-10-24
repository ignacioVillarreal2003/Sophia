import { ChangeDetectionStrategy, Component, OnInit, computed, signal } from '@angular/core';
import { MapKind, MindMapResult } from '@core/models/ai/ai.model';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { BoardElement } from '../../document-workspace/whiteboard/board.model';
import { boardContent, boardFromMap } from '../lib/board-builder';
import { SourcePickerComponent } from '../ui/source-picker.component';
import { AiToolBase } from './tool-base';

/** A mind map or a concept map, previewed and then saved as an ordinary whiteboard. */
@Component({
  selector: 'app-ai-map-tool',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SourcePickerComponent, TranslatePipe],
  styleUrl: '../ai-tools.scss',
  template: `
    <div class="ait">
      <p class="ait__intro">{{ 'ai.map.intro' | t }}</p>

      <form class="ait__form" (submit)="$event.preventDefault(); generate()">
        <div class="ait__field">
          <span class="ait__label">{{ 'ai.map.kind' | t }}</span>
          <div class="ait__chips" role="radiogroup" [attr.aria-label]="'ai.map.kind' | t">
            @for (k of kinds; track k) {
              <button type="button" role="radio" class="ait__chip" [class.ait__chip--on]="kind() === k" [attr.aria-checked]="kind() === k" (click)="kind.set(k)">
                {{ 'ai.map.kind.' + k | t }}
              </button>
            }
          </div>
          <span class="ait__hint">{{ 'ai.map.kind.' + kind() + '.hint' | t }}</span>
        </div>

        <div class="ait__field">
          <label class="ait__label" for="map-topic">{{ 'ai.field.topic' | t }}</label>
          <input id="map-topic" class="ait__input" maxlength="500" [placeholder]="'ai.map.topicPlaceholder' | t" [value]="topic()" (input)="topic.set($any($event.target).value)" />
        </div>

        @if (kind() === 'MINDMAP') {
          <div class="ait__field">
            <label class="ait__label" for="map-depth">{{ 'ai.map.depth' | t }}</label>
            <select id="map-depth" class="ait__select" [value]="depth()" (change)="depth.set(+$any($event.target).value)">
              <option value="2">{{ 'ai.map.depth.2' | t }}</option>
              <option value="3">{{ 'ai.map.depth.3' | t }}</option>
              <option value="4">{{ 'ai.map.depth.4' | t }}</option>
            </select>
          </div>
        }

        <app-ai-sources [workspaceId]="workspaceId()" [(selected)]="sources" />

        @if (error()) {
          <div class="ait__error" role="alert"><app-icon name="alert-circle" [size]="16" />{{ error() }}</div>
        }

        <div class="ait__actions">
          <app-button type="submit" variant="dark" [loading]="busy()" [disabled]="!canGenerate()">{{ 'ai.map.generate' | t }}</app-button>
        </div>
      </form>

      @if (result(); as map) {
        <section class="ait__result">
          <div class="ait__result-head">
            <h3 class="ait__result-title">{{ map.title }}</h3>
            <span class="ait__live">{{ 'ai.map.nodes' | t: { count: map.nodes.length } }}</span>
          </div>
          <div class="map__stage">
            <svg [attr.viewBox]="viewBox()" role="img" [attr.aria-label]="map.title" preserveAspectRatio="xMidYMid meet">
              @for (edge of edges(); track $index) {
                <path [attr.d]="edge.d" class="map__edge" />
                @if (edge.label) {
                  <text [attr.x]="edge.lx" [attr.y]="edge.ly" class="map__edge-label" text-anchor="middle">{{ edge.label }}</text>
                }
              }
              @for (node of shapes(); track node.id) {
                <g>
                  <rect [attr.x]="node.x" [attr.y]="node.y" [attr.width]="node.w" [attr.height]="node.h" [attr.rx]="node.type === 'ellipse' ? node.h / 2 : 12" [attr.fill]="node.fill" [attr.stroke]="node.stroke" stroke-width="2" />
                  <text [attr.x]="node.x + node.w / 2" [attr.y]="node.y + node.h / 2" class="map__label" text-anchor="middle" dominant-baseline="central">{{ node.text }}</text>
                </g>
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
    .map__stage {
      overflow: hidden; background: var(--surface-sunken); border-radius: var(--radius-md);
      svg { display: block; width: 100%; height: min(60vh, 520px); }
    }
    .map__edge { fill: none; stroke: var(--text-subtle); stroke-width: 1.6; }
    .map__edge-label { font-size: 12px; fill: var(--text-muted); paint-order: stroke; stroke: var(--surface-sunken); stroke-width: 4px; }
    .map__label { font-size: 15px; fill: #1e293b; pointer-events: none; }
  `,
})
export class MapToolComponent extends AiToolBase implements OnInit {
  readonly kinds: MapKind[] = ['MINDMAP', 'CONCEPT_MAP'];

  readonly kind = signal<MapKind>('MINDMAP');
  readonly topic = signal('');
  readonly depth = signal(3);
  readonly sources = signal<string[]>([]);

  readonly result = signal<MindMapResult | null>(null);

  readonly canGenerate = computed(() => !this.busy() && (this.topic().trim().length > 0 || this.sources().length > 0));

  /** The same elements the saved whiteboard will have, so the preview is what you get. */
  private readonly elements = computed<BoardElement[]>(() => {
    const map = this.result();
    return map ? boardFromMap(map).elements : [];
  });
  readonly shapes = computed(() =>
    this.elements()
      .filter((e) => e.type !== 'arrow')
      .map((e) => ({ ...e, fill: e.fill ?? '#fff', stroke: e.stroke ?? '#475569' })),
  );
  readonly edges = computed(() => {
    const byId = new Map(this.elements().map((e) => [e.id, e]));
    return this.elements()
      .filter((e) => e.type === 'arrow')
      .map((arrow) => {
        const a = byId.get(arrow.from!)!;
        const b = byId.get(arrow.to!)!;
        const ax = a.x + a.w / 2;
        const ay = a.y + a.h / 2;
        const bx = b.x + b.w / 2;
        const by = b.y + b.h / 2;
        const mx = (ax + bx) / 2;
        const curved = arrow.route === 'curve';
        const d = curved ? `M ${ax} ${ay} C ${mx} ${ay}, ${mx} ${by}, ${bx} ${by}` : `M ${ax} ${ay} L ${bx} ${by}`;
        return { d, label: arrow.text, lx: mx, ly: (ay + by) / 2 - 4 };
      });
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

  ngOnInit(): void {
    const handoff = this.ai.handoff();
    if (handoff?.tool === 'map') {
      if (handoff.sources) this.sources.set(handoff.sources);
      if (handoff.topic) this.topic.set(handoff.topic);
      this.ai.handoff.set(null);
    }
  }

  generate(): void {
    if (!this.canGenerate()) {
      return;
    }
    this.begin();
    this.ai
      .mindMap({
        workspaceId: this.workspaceId(),
        kind: this.kind(),
        topic: this.topic().trim() || undefined,
        sources: this.sources().map((artifactId) => ({ artifactId })),
        depth: this.kind() === 'MINDMAP' ? this.depth() : undefined,
      })
      .subscribe({
        next: (map) => {
          this.result.set(map);
          this.busy.set(false);
          this.refreshStatus();
        },
        error: (err) => this.fail(err),
      });
  }

  save(): void {
    const map = this.result();
    if (!map) {
      return;
    }
    this.keep({ name: this.nameOf(map.title, this.topic()), type: 'WHITEBOARD', content: boardContent(map) });
  }
}
