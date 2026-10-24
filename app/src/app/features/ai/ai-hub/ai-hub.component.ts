import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input } from '@angular/core';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { RouterLink } from '@angular/router';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { IconName } from '@shared/ui/icon/icons';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { AiService } from '../data/ai.service';
import { AnalysisToolComponent } from '../tools/analysis-tool.component';
import { ChatToolComponent } from '../tools/chat-tool.component';
import { DeckToolComponent } from '../tools/deck-tool.component';
import { DiagramToolComponent } from '../tools/diagram-tool.component';
import { ListenToolComponent } from '../tools/listen-tool.component';
import { ScanToolComponent } from '../tools/scan-tool.component';
import { TableToolComponent } from '../tools/table-tool.component';
import { MapToolComponent } from '../tools/map-tool.component';
import { PlanToolComponent } from '../tools/plan-tool.component';
import { QuizToolComponent } from '../tools/quiz-tool.component';
import { TextToolComponent } from '../tools/text-tool.component';

export type ToolId =
  | 'notes'
  | 'cards'
  | 'map'
  | 'quiz'
  | 'plan'
  | 'review'
  | 'perspectives'
  | 'debate'
  | 'gaps'
  | 'scan'
  | 'table'
  | 'diagram'
  | 'listen'
  | 'chat';

interface Tool {
  readonly id: ToolId;
  readonly icon: IconName;
}

interface Group {
  readonly key: string;
  readonly tools: Tool[];
}

const GROUPS: Group[] = [
  {
    key: 'create',
    tools: [
      { id: 'notes', icon: 'file-text' },
      { id: 'cards', icon: 'layout-cards' },
      { id: 'map', icon: 'map' },
    ],
  },
  {
    key: 'study',
    tools: [
      { id: 'quiz', icon: 'target' },
      { id: 'plan', icon: 'clock' },
      { id: 'review', icon: 'book' },
    ],
  },
  {
    key: 'analyze',
    tools: [
      { id: 'perspectives', icon: 'eye' },
      { id: 'debate', icon: 'swap' },
      { id: 'gaps', icon: 'search' },
    ],
  },
  {
    key: 'convert',
    tools: [
      { id: 'scan', icon: 'camera' },
      { id: 'table', icon: 'table' },
      { id: 'diagram', icon: 'share' },
      { id: 'listen', icon: 'volume' },
    ],
  },
  { key: 'talk', tools: [{ id: 'chat', icon: 'message-circle' }] },
];

const ALL = GROUPS.flatMap((g) => g.tools);

/** The workspace's study studio: pick a tool on the left, work in the middle. The URL keeps the tool (`?tool=quiz`). */
@Component({
  selector: 'app-ai-hub',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    AnalysisToolComponent,
    ChatToolComponent,
    DeckToolComponent,
    DiagramToolComponent,
    ListenToolComponent,
    ScanToolComponent,
    TableToolComponent,
    IconComponent,
    MapToolComponent,
    PageHeaderComponent,
    PlanToolComponent,
    QuizToolComponent,
    RouterLink,
    TextToolComponent,
    TranslatePipe,
  ],
  templateUrl: './ai-hub.component.html',
  styleUrl: './ai-hub.component.scss',
})
export class AiHubComponent implements OnInit {
  protected readonly ai = inject(AiService);
  private readonly auth = inject(AuthenticationService);

  /** Route param. */
  readonly documentWorkspaceId = input.required<string>();
  /** Query param. */
  readonly tool = input<string>();

  /** The tools used most, always on show; the rest wait under "More". */
  readonly main: ToolId[] = ['chat', 'notes', 'quiz', 'cards', 'map', 'plan'];
  readonly others: ToolId[] = ALL.map((t) => t.id).filter((id) => !this.main.includes(id));
  readonly active = computed<ToolId>(() => ALL.find((t) => t.id === this.tool())?.id ?? 'chat');
  readonly isMore = computed(() => this.others.includes(this.active()));

  iconOf(id: ToolId): IconName {
    return ALL.find((t) => t.id === id)?.icon ?? 'sparkles';
  }

  /** Why the tools cannot be used right now, or null when they can. */
  readonly blocked = computed<'disabled' | 'unconfigured' | 'quota' | 'down' | null>(() => {
    if (this.ai.unreachable()) {
      return 'down';
    }
    const s = this.status();
    if (!s) {
      return null;
    }
    if (!s.enabled) {
      return 'disabled';
    }
    if (!s.configured) {
      return 'unconfigured';
    }
    const q = s.quota;
    return (q.userLimit > 0 && q.userUsed >= q.userLimit) || (q.workspaceLimit > 0 && q.workspaceUsed >= q.workspaceLimit)
      ? 'quota'
      : null;
  });

  /** What to tell the person when the tools cannot be used (a text key), or null. */
  readonly notice = computed<string | null>(() => {
    const why = this.blocked();
    return why ? `ai.blocked.${why}` : this.status()?.mock ? 'ai.mock' : null;
  });
  readonly status = this.ai.status;

  readonly canManage = computed(() => this.auth.permissions().has('WORKSPACE_MANAGE'));

  /** The reason shown with the usage meter: the service being down has its own banner. */
  readonly reason = computed(() => (this.blocked() === 'down' ? null : this.blocked()));

  readonly usedPercent = computed(() => {
    const q = this.status()?.quota;
    return q && q.userLimit > 0 ? Math.min(100, Math.round((q.userUsed / q.userLimit) * 100)) : 0;
  });

  ngOnInit(): void {
    this.ai.loadStatus(this.documentWorkspaceId()).subscribe({ error: () => undefined });
  }

  /** 12 345 -> "12.3k" */
  compact(n: number): string {
    return n >= 1_000_000 ? `${(n / 1_000_000).toFixed(1)}M` : n >= 1000 ? `${(n / 1000).toFixed(n >= 100_000 ? 0 : 1)}k` : String(n);
  }
}
