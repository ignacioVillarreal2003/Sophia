import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AccessPreview } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { RelativeTimePipe } from '@shared/pipes/relative-time.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { WorkspaceRequestsService } from '../data/workspace-extras.service';

/** Where a person lands when they open a workspace they do not belong to: they can ask its admins for access. */
@Component({
  selector: 'app-request-access',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ButtonComponent, CardComponent, IconComponent, SpinnerComponent, TextareaComponent, RelativeTimePipe, TranslatePipe],
  template: `
    <div class="ra app-container">
      @if (loading()) {
        <div class="ra__center"><app-spinner /></div>
      } @else {
        <app-card class="ra__card">
          @if (preview(); as p) {
            <div class="ra__head" [style.--ws-color]="p.color || 'var(--accent)'">
              <span class="ra__icon"><app-icon [name]="p.requestStatus === 'PENDING' ? 'clock' : 'lock'" [size]="22" /></span>
              <h1 class="ra__title">{{ p.name }}</h1>
            </div>

            @if (p.requestStatus === 'PENDING') {
              <p class="ra__text">{{ 'workspaces.access.pending' | t }}</p>
              @if (p.requestedAt) {
                <p class="ra__hint">{{ 'workspaces.access.sentAt' | t: { time: (p.requestedAt | relativeTime) } }}</p>
              }
            } @else {
              <p class="ra__text">{{ 'workspaces.access.intro' | t }}</p>
              @if (p.requestStatus === 'DENIED') {
                <p class="ra__hint ra__hint--warn">{{ 'workspaces.access.denied' | t }}</p>
              }
              <app-textarea [label]="'workspaces.access.message' | t" [rows]="3" [formControl]="message" [placeholder]="'workspaces.access.messagePlaceholder' | t" />
            }
          } @else {
            <div class="ra__head">
              <span class="ra__icon"><app-icon name="ban" [size]="22" /></span>
              <h1 class="ra__title">{{ 'workspaces.access.notFound' | t }}</h1>
            </div>
            <p class="ra__text">{{ 'workspaces.access.notFoundHint' | t }}</p>
          }
          <div class="ra__actions">
            @if (preview(); as p) {
              @if (p.requestStatus !== 'PENDING') {
                <app-button variant="primary" icon="send" [loading]="busy()" [disabled]="message.invalid || busy()" (click)="send()">
                  {{ 'workspaces.access.send' | t }}
                </app-button>
              }
            }
            <a class="ra__back" routerLink="/document-workspaces">{{ 'workspaces.access.back' | t }}</a>
          </div>
        </app-card>
      }
    </div>
  `,
  styles: [
    `
      .ra { padding-block: var(--space-section); display: flex; justify-content: center; }
      .ra__center { display: grid; place-items: center; padding: var(--space-section); }
      .ra__card { width: min(100%, 520px); display: block; }
      .ra__head { display: flex; align-items: center; gap: 14px; margin-bottom: var(--space-md); }
      .ra__icon {
        display: grid; place-items: center; width: 44px; height: 44px; border-radius: var(--radius-md);
        background: color-mix(in srgb, var(--ws-color, var(--accent)) 16%, var(--surface));
        color: color-mix(in srgb, var(--ws-color, var(--accent)) 70%, var(--text));
      }
      .ra__title { font-size: var(--type-tagline-size); font-weight: 600; margin: 0; min-width: 0; overflow-wrap: anywhere; }
      .ra__text { color: var(--text-muted); line-height: 1.5; margin: 0 0 var(--space-md); }
      .ra__hint { color: var(--text-subtle); font-size: var(--type-caption-size); margin: 0 0 var(--space-md); }
      .ra__hint--warn { color: var(--warning); }
      .ra__actions { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-md); margin-top: var(--space-md); }
      .ra__back { display: inline-block; color: var(--accent); font-weight: 600; text-decoration: none; }
      .ra__back:hover { text-decoration: underline; }
      app-textarea { display: block; margin-bottom: var(--space-md); }
    `,
  ],
})
export class RequestAccessComponent implements OnInit {
  private readonly requests = inject(WorkspaceRequestsService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly documentWorkspaceId = input.required<string>();

  readonly loading = signal(true);
  readonly busy = signal(false);
  private readonly _preview = signal<AccessPreview | null>(null);
  readonly preview = computed(() => this._preview());
  readonly message = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] });

  ngOnInit(): void {
    this.requests.accessPreview(this.documentWorkspaceId()).subscribe({
      next: (preview) => {
        if (preview.member) {
          this.router.navigate(['/document-workspaces', preview.workspaceId], { replaceUrl: true });
          return;
        }
        this._preview.set(preview);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  send(): void {
    if (this.message.invalid || this.busy()) return;
    this.busy.set(true);
    this.requests.requestAccess(this.documentWorkspaceId(), this.message.value.trim()).subscribe({
      next: (request) => {
        this.busy.set(false);
        const current = this._preview();
        if (current) {
          this._preview.set({ ...current, requestStatus: request.status, requestedAt: request.createdAt });
        }
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
