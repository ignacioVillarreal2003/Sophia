import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Comment } from '@core/models/document-workspace';
import { AuthenticationService } from '@core/services/authentication/authentication.service';
import { ToastService } from '@core/services/toast.service';
import { TranslationService } from '@core/services/i18n';
import { CommentService } from '../data/comment.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { EmptyStateComponent } from '@shared/ui/empty-state/empty-state.component';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';

@Component({
  selector: 'app-comment-thread',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    ButtonComponent,
    AvatarComponent,
    IconComponent,
    SpinnerComponent,
    EmptyStateComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './comment-thread.component.html',
  styleUrl: './comment-thread.component.scss',
})
export class CommentThreadComponent implements OnInit {
  private readonly service = inject(CommentService);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly documentWorkspaceId = input.required<string>();
  readonly artifactId = input.required<string>();
  readonly close = output<void>();

  readonly comments = signal<Comment[]>([]);
  readonly loading = signal(true);
  readonly draft = signal('');
  readonly posting = signal(false);
  readonly replyTo = signal<string | null>(null);
  readonly replyDraft = signal('');

  readonly me = this.auth.user;

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.service.list(this.documentWorkspaceId(), this.artifactId()).subscribe({
      next: (list) => {
        this.comments.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  post(): void {
    const body = this.draft().trim();
    if (!body) return;
    this.posting.set(true);
    this.service.create(this.documentWorkspaceId(), this.artifactId(), { body }).subscribe({
      next: (comment) => {
        this.comments.update((list) => [...list, comment]);
        this.draft.set('');
        this.posting.set(false);
      },
      error: () => {
        this.posting.set(false);
        this.toast.error(this.i18n.translate('comments.postError'));
      },
    });
  }

  postReply(parent: Comment): void {
    const body = this.replyDraft().trim();
    if (!body) return;
    this.service
      .create(this.documentWorkspaceId(), this.artifactId(), { body, parentId: parent.id })
      .subscribe({
        next: (reply) => {
          this.comments.update((list) =>
            list.map((c) =>
              c.id === parent.id ? { ...c, replies: [...(c.replies ?? []), reply] } : c,
            ),
          );
          this.replyTo.set(null);
          this.replyDraft.set('');
        },
        error: () => this.toast.error(this.i18n.translate('comments.replyError')),
      });
  }

  toggleResolve(comment: Comment): void {
    const action = comment.resolved
      ? this.service.unresolve(this.documentWorkspaceId(), comment.id)
      : this.service.resolve(this.documentWorkspaceId(), comment.id);
    action.subscribe({
      next: (updated) =>
        this.comments.update((list) => list.map((c) => (c.id === updated.id ? updated : c))),
      error: () => this.toast.error(this.i18n.translate('common.error')),
    });
  }

  remove(comment: Comment): void {
    this.service.remove(this.documentWorkspaceId(), comment.id).subscribe({
      next: () => this.comments.update((list) => list.filter((c) => c.id !== comment.id)),
      error: () => this.toast.error(this.i18n.translate('common.error')),
    });
  }

  startReply(id: string): void {
    this.replyTo.set(id);
    this.replyDraft.set('');
  }
}
