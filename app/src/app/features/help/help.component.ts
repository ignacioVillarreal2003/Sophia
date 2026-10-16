import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { CardComponent } from '@shared/ui/card/card.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { IconName } from '@shared/ui/icon/icons';

interface HelpTopic {
  titleKey: string;
  descKey: string;
  icon: IconName;
}

interface FaqItem {
  qKey: string;
  aKey: string;
  open?: boolean;
}

@Component({
  selector: 'app-help',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CardComponent, IconComponent, PageHeaderComponent, TranslatePipe],
  template: `
    <div class="help app-container">
      <app-page-header [title]="'nav.help' | t" [subtitle]="'help.subtitle' | t" />

      <div class="help__cards">
        @for (topic of topics; track topic.titleKey) {
          <app-card class="help__topic">
            <span class="help__topic-icon"><app-icon [name]="topic.icon" [size]="20" /></span>
            <h3 class="help__topic-title">{{ topic.titleKey | t }}</h3>
            <p class="help__topic-desc">{{ topic.descKey | t }}</p>
          </app-card>
        }
      </div>

      <h2 class="help__section">{{ 'help.faqTitle' | t }}</h2>
      <div class="help__faq">
        @for (item of faqs(); track item.qKey; let i = $index) {
          <button class="help__faq-item" [attr.aria-expanded]="!!item.open" (click)="toggle(i)">
            <div class="help__faq-q">
              <span>{{ item.qKey | t }}</span>
              <app-icon [name]="item.open ? 'chevron-down' : 'chevron-right'" [size]="16" />
            </div>
            @if (item.open) {
              <p class="help__faq-a">{{ item.aKey | t }}</p>
            }
          </button>
        }
      </div>
    </div>
  `,
  styleUrl: './help.component.scss',
})
export class HelpComponent {
  readonly topics: HelpTopic[] = [
    { titleKey: 'help.topics.gettingStarted.title', descKey: 'help.topics.gettingStarted.desc', icon: 'home' },
    { titleKey: 'help.topics.editor.title', descKey: 'help.topics.editor.desc', icon: 'edit' },
    { titleKey: 'help.topics.collaboration.title', descKey: 'help.topics.collaboration.desc', icon: 'users' },
    { titleKey: 'help.topics.security.title', descKey: 'help.topics.security.desc', icon: 'shield' },
  ];

  readonly faqs = signal<FaqItem[]>([
    { qKey: 'help.faq.invite.q', aKey: 'help.faq.invite.a' },
    { qKey: 'help.faq.restore.q', aKey: 'help.faq.restore.a' },
    { qKey: 'help.faq.theme.q', aKey: 'help.faq.theme.a' },
  ]);

  toggle(index: number): void {
    this.faqs.update((list) =>
      list.map((item, i) => (i === index ? { ...item, open: !item.open } : item)),
    );
  }
}
