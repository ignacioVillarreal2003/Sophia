import { ChangeDetectionStrategy, Component, OnInit, computed, input, model, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { IconName } from '../icon/icons';

export interface RailItem {
  label: string;
  link: string;
  icon: IconName;
  exact?: boolean;
  onClick?: () => void;
  /** Optional section header; consecutive items sharing one are grouped under
   *  it. Omit on every item to keep the flat list this component started with. */
  group?: string;
  /** Renders a button that only runs `onClick` (a setting, not a destination); `link` is then just a unique key. */
  action?: boolean;
  /** Highlights an action item, e.g. the selected view mode. */
  active?: boolean;
}

interface RailSection {
  label: string | null;
  items: RailItem[];
}

@Component({
  selector: 'app-icon-rail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, IconComponent],
  template: `
    <aside class="rail" [class.rail--expanded]="expanded()">
      <div class="rail__top">
        @if (title() && expanded()) {
          @if (titleImage(); as image) {
            <img class="rail__title-image" [src]="image" alt="" width="24" height="24" />
          }
          <span class="rail__title">{{ title() }}</span>
        }
        <button
          type="button"
          class="rail__toggle"
          (click)="toggleExpanded()"
          [attr.aria-label]="expanded() ? collapseLabel() : expandLabel()"
          [attr.aria-pressed]="expanded()"
          [attr.title]="expanded() ? collapseLabel() : expandLabel()"
        >
          <app-icon [name]="expanded() ? 'chevron-left' : 'chevron-right'" [size]="14" />
        </button>
      </div>

      <nav class="rail__nav">
        @for (section of sections(); track $index) {
          <div class="rail__group">
            @if (section.label) {
              <button
                type="button"
                class="rail__group-label"
                [attr.aria-expanded]="!isCollapsed(section)"
                (click)="toggleGroup(section)"
              >
                <span class="rail__group-label-text">{{ section.label }}</span>
                @if (collapsibleGroups()) {
                  <app-icon
                    name="chevron-down"
                    [size]="12"
                    class="rail__group-chevron"
                    [class.rail__group-chevron--open]="!isCollapsed(section)"
                  />
                }
              </button>
            }
            @if (!section.label || !isCollapsed(section)) {
              @for (item of section.items; track item.link) {
                @if (item.action) {
                  <button
                    type="button"
                    class="rail__link"
                    [class.rail__link--active]="item.active"
                    [attr.aria-pressed]="item.active ?? false"
                    [attr.title]="expanded() ? null : item.label"
                    (click)="item.onClick?.()"
                  >
                    <app-icon [name]="item.icon" [size]="18" />
                    <span class="rail__label">{{ item.label }}</span>
                  </button>
                } @else {
                  <a
                    [routerLink]="item.link"
                    routerLinkActive="rail__link--active"
                    [routerLinkActiveOptions]="item.exact ? exactMatch : subsetMatch"
                    class="rail__link"
                    [attr.title]="expanded() ? null : item.label"
                    (click)="onLinkClick(item, section)"
                  >
                    <app-icon [name]="item.icon" [size]="18" />
                    <span class="rail__label">{{ item.label }}</span>
                  </a>
                }
              }
            }
          </div>
        }
      </nav>

      <div class="rail__foot">
        @for (item of footItems(); track item.link) {
          <a
            [routerLink]="item.link"
            class="rail__link rail__link--foot"
            [attr.title]="expanded() ? null : item.label"
            (click)="item.onClick?.()"
          >
            <app-icon [name]="item.icon" [size]="18" />
            <span class="rail__label">{{ item.label }}</span>
          </a>
        }
        <ng-content select="[rail-foot]" />
      </div>
    </aside>
  `,
  styleUrl: './icon-rail.component.scss',
})
export class IconRailComponent implements OnInit {
  readonly items = input.required<RailItem[]>();
  /** Exact path match, but ?query params (list filters, paging) must not deactivate the item. */
  protected readonly exactMatch = { paths: 'exact', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored' } as const;
  protected readonly subsetMatch = { paths: 'subset', queryParams: 'subset', matrixParams: 'ignored', fragment: 'ignored' } as const;

  /** Links pinned to the bottom of the rail (e.g. a way back out of a section). */
  readonly footItems = input<RailItem[]>([]);

  readonly title = input<string>('');
  /** Small image shown before the title (e.g. a workspace logo). */
  readonly titleImage = input<string | null>(null);
  readonly expandLabel = input<string>('Expand');
  readonly collapseLabel = input<string>('Collapse');
  readonly storageKey = input.required<string>();
  /** When true, grouped sections become a strict accordion: at most one group
   *  is open at a time, and clicking any link (grouped or not) closes every
   *  group. Defaults to all collapsed until the user opens one. */
  readonly collapsibleGroups = input(false);

  readonly expanded = model<boolean>(false);

  private readonly collapsedOverrides = signal(new Map<string, boolean>());

  /** Consecutive items sharing a `group` collapse into one section; items with
   *  no `group` at all (the common case) come back as one unlabeled section,
   *  identical to the old flat rendering. */
  readonly sections = computed<RailSection[]>(() => {
    const sections: RailSection[] = [];
    for (const item of this.items()) {
      const label = item.group ?? null;
      const current = sections.at(-1);
      if (current && current.label === label) {
        current.items.push(item);
      } else {
        sections.push({ label, items: [item] });
      }
    }
    return sections;
  });

  ngOnInit(): void {
    const stored = localStorage.getItem(this.storageKey());
    if (stored !== null) {
      this.expanded.set(stored === 'true');
    }
  }

  toggleExpanded(): void {
    this.expanded.update((v) => !v);
    localStorage.setItem(this.storageKey(), String(this.expanded()));
  }

  isCollapsed(section: RailSection): boolean {
    if (!this.collapsibleGroups() || !section.label || !this.expanded()) {
      // Icon-only mode has no room for group headers, so every icon stays
      // flat and visible there — collapsing only makes sense once labels
      // (and therefore a clickable header) are actually shown.
      return false;
    }
    const overrides = this.collapsedOverrides();
    if (overrides.has(section.label)) {
      return overrides.get(section.label)!;
    }
    const stored = this.readGroupState(section.label);
    return stored !== null ? stored === 'true' : true;
  }

  /** Clicking a link closes every other group. A link with no group closes
   *  all of them; a link inside a group keeps that one group open, since
   *  it's now the active section, and closes the rest. */
  onLinkClick(item: RailItem, section: RailSection): void {
    item.onClick?.();
    this.collapseAllGroupsExcept(section.label);
  }

  toggleGroup(section: RailSection): void {
    if (!this.collapsibleGroups() || !section.label) {
      return;
    }
    const wasCollapsed = this.isCollapsed(section);
    this.collapseAllGroupsExcept(wasCollapsed ? section.label : null);
  }

  private collapseAllGroupsExcept(openLabel: string | null): void {
    if (!this.collapsibleGroups()) {
      return;
    }
    for (const section of this.sections()) {
      if (section.label) {
        this.setGroupCollapsed(section.label, section.label !== openLabel);
      }
    }
  }

  private setGroupCollapsed(label: string, collapsed: boolean): void {
    this.collapsedOverrides.update((map) => new Map(map).set(label, collapsed));
    this.writeGroupState(label, collapsed);
  }

  private groupStorageKey(label: string): string {
    return `${this.storageKey()}.group.${label}`;
  }

  private readGroupState(label: string): string | null {
    try {
      return localStorage.getItem(this.groupStorageKey(label));
    } catch {
      return null;
    }
  }

  private writeGroupState(label: string, collapsed: boolean): void {
    try {
      localStorage.setItem(this.groupStorageKey(label), String(collapsed));
    } catch {
      // Ignore storage failures (private browsing, quota, etc.) — state still
      // lives in collapsedOverrides for the rest of this session.
    }
  }
}
