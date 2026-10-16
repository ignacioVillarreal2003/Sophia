import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {
  ActivatedRouteSnapshot,
  NavigationEnd,
  Router,
  RouterOutlet,
} from '@angular/router';
import {CdkScrollable} from '@angular/cdk/scrolling';
import {filter} from 'rxjs';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {findRouteData} from '@core/utils/route-data';
import {DocumentWorkspaceService} from '@features/document-workspace/data/document-workspace.service';
import {WorkspaceLogoService} from '@features/document-workspace/data/workspace-logo.service';
import {NotificationService} from '@features/notifications/data/notification.service';
import {RealtimeService} from '@features/document-workspace/data/realtime.service';
import {AppHeaderComponent} from './header/app-header.component';
import {IconRailComponent, RailItem} from '@shared/ui/icon-rail/icon-rail.component';
import {UploadProgressComponent} from '@features/document-workspace/ui/upload-progress.component';

type WorkspaceKind = 'document';

interface SidebarConfig {
  readonly title?: string;
  readonly items: RailItem[];
  readonly footItems?: RailItem[];
}

/** `/document-workspaces/<uuid>` and anything below it — a specific workspace, not the list/new/trash pages. */
const WORKSPACE_URL = /^\/document-workspaces\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:[/?#]|$)/i;

/** Every section's outer sidebar shares this key: it's one generic rail, not one per section. */
const SIDEBAR_STORAGE_KEY = 'sophia.appSidebar.railPinned';

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    CdkScrollable,
    AppHeaderComponent,
    IconRailComponent,
    UploadProgressComponent,
    TranslatePipe,
  ],
  template: `
    <div class="shell">
      <app-header/>

      <div class="shell__body" [class.shell__body--no-sidebar]="hideSidebar()">
        @if (sidebarConfig(); as sidebar) {
          <app-icon-rail
            [items]="sidebar.items"
            [footItems]="sidebar.footItems ?? []"
            [title]="sidebar.title ?? ''"
            [titleImage]="sidebar.title ? logoSrc() : null"
            [expandLabel]="'admin.rail.pin' | t"
            [collapseLabel]="'admin.rail.unpin' | t"
            [storageKey]="sidebarStorageKey"
            [(expanded)]="sidebarExpanded"
          />
        }

        <!--
          cdkScrollable registers this container with the CDK ScrollDispatcher.
          The window never scrolls here, so without it overlays (select menus,
          dropdowns, tooltips) never hear about scrolling and stay pinned where
          they opened instead of following their trigger.
        -->
        <main cdkScrollable class="shell__main" [class.shell__main--expanded]="sidebarExpanded()">
          <router-outlet/>
        </main>
      </div>

      <app-upload-progress/>
    </div>
  `,
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit, OnDestroy {
  private readonly documentWorkspaces = inject(DocumentWorkspaceService);
  private readonly logos = inject(WorkspaceLogoService);
  private readonly notifications = inject(NotificationService);
  private readonly realtime = inject(RealtimeService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly sidebarStorageKey = SIDEBAR_STORAGE_KEY;
  readonly hideSidebar = signal(this.computeHideSidebar());
  readonly sidebarExpanded = signal(false);
  /** Object URL of the active workspace's logo, shown next to its name in the sidebar. */
  readonly logoSrc = signal<string | null>(null);
  private readonly workspaceKind = signal(this.computeWorkspaceKind());
  private readonly workspaceId = signal(this.computeWorkspaceId());

  readonly sidebarConfig = computed<SidebarConfig | null>(() => {
    switch (this.workspaceKind()) {
      case 'document': {
        const id = this.workspaceId();
        return id ? this.workspaceSidebar(id) : {items: this.documentSidebarItems()};
      }
      default:
        return null;
    }
  });

  constructor() {
    effect((onCleanup) => {
      const url = this.documentWorkspaces.active()?.logoUrl;
      if (!url) {
        this.logoSrc.set(null);
        return;
      }
      const subscription = this.logos.resolve(url).subscribe((src) => this.logoSrc.set(src));
      onCleanup(() => subscription.unsubscribe());
    });

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.hideSidebar.set(this.computeHideSidebar());
        this.workspaceKind.set(this.computeWorkspaceKind());
        this.workspaceId.set(this.computeWorkspaceId());
      });

    this.notifications.incoming$.pipe(takeUntilDestroyed()).subscribe((notification) => {
      this.toast.show(notification.title, 'info');
    });
  }

  private computeHideSidebar(): boolean {
    let snapshot: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    while (snapshot) {
      if (snapshot.data['hideSidebar']) {
        return true;
      }
      snapshot = snapshot.firstChild;
    }
    return false;
  }

  private computeWorkspaceKind(): WorkspaceKind | undefined {
    return findRouteData<WorkspaceKind>(this.router.routerState.snapshot.root, 'workspaceKind');
  }

  private computeWorkspaceId(): string | null {
    return WORKSPACE_URL.exec(this.router.url)?.[1] ?? null;
  }

  /** Sidebar for one workspace: its name as the header, Home / Members / Settings, and a way back to the list. */
  private workspaceSidebar(id: string): SidebarConfig {
    const base = `/document-workspaces/${id}`;
    return {
      title: this.documentWorkspaces.active()?.name || this.i18n.translate('documentWorkspace.fallbackName'),
      items: [
        {label: this.i18n.translate('nav.dashboard'), icon: 'home', link: base, exact: true},
        {label: this.i18n.translate('documentWorkspace.starred'), icon: 'star', link: `${base}/starred`},
        {label: this.i18n.translate('documentWorkspace.members'), icon: 'users', link: `${base}/members`},
        {label: this.i18n.translate('documentWorkspace.activity'), icon: 'clock', link: `${base}/activity`},
        {label: this.i18n.translate('nav.trash'), icon: 'trash', link: `${base}/trash`},
        {label: this.i18n.translate('documentWorkspace.settings'), icon: 'settings', link: `${base}/settings`},
      ],
      footItems: [
        {
          label: this.i18n.translate('common.back'),
          icon: 'arrow-left',
          link: '/document-workspaces',
          onClick: () => this.documentWorkspaces.setActive(null),
        },
      ],
    };
  }

  private documentSidebarItems(): RailItem[] {
    const items: RailItem[] = [];
    if (this.documentWorkspaces.active()) {
      items.push({
        label: this.i18n.translate('nav.exitDocumentWorkspace'),
        icon: 'arrow-left',
        link: '/',
        onClick: () => this.documentWorkspaces.setActive(null),
      });
    }
    items.push(
      {
        label: this.i18n.translate('nav.documentWorkspaces'),
        icon: 'grid',
        link: '/document-workspaces',
        exact: true,
      },
      {
        label: this.i18n.translate('nav.trash'),
        icon: 'trash',
        link: '/document-workspaces/trash',
      },
    );
    return items;
  }

  ngOnInit(): void {
    if (!this.documentWorkspaces.loaded()) {
      this.documentWorkspaces.loadAll().subscribe();
    }
    this.notifications.refreshUnread().subscribe();
    this.notifications.connectStream();

    this.realtime.connect();
  }

  ngOnDestroy(): void {
    this.notifications.disconnectStream();
    this.realtime.disconnect();
  }
}
