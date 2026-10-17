import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { environment } from '@env/environment';
import { Artifact } from '@core/models/document-workspace';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpinnerComponent } from '@shared/ui/spinner/spinner.component';
import { FileSizePipe } from '@shared/pipes/file-size.pipe';
import { ExportImportService } from '../data/export-import.service';

/**
 * Viewer for uploaded files: PDFs, images, video, audio and text render inline; Word, Excel and PowerPoint
 * files are stored as they are and offer download plus conversion to an editable note or spreadsheet.
 */
@Component({
  selector: 'app-file-viewer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, SpinnerComponent, TranslatePipe, FileSizePipe],
  template: `
    <div class="fv">
      <header class="fv__bar">
        <div class="fv__meta">
          <app-icon [name]="icon()" [size]="20" />
          <div>
            <p class="fv__name">{{ artifact().originalFilename || artifact().name }}</p>
            <p class="fv__sub">{{ artifact().mimeType }} · {{ artifact().sizeBytes | fileSize }}</p>
          </div>
        </div>
        <div class="fv__actions">
          @if (convertible()) {
            <app-button variant="secondary" size="sm" [loading]="converting()" (click)="convert()">
              {{ convertLabel() | t }}
            </app-button>
          }
          @if (url()) {
            <a [href]="url()!" target="_blank" rel="noopener" class="fv__link">
              <app-button variant="text" size="sm" icon="eye">{{ 'file.open' | t }}</app-button>
            </a>
            <a [href]="url()!" [attr.download]="artifact().originalFilename || artifact().name" class="fv__link">
              <app-button variant="primary" size="sm" icon="download">{{ 'documentView.download' | t }}</app-button>
            </a>
          }
        </div>
      </header>

      <div class="fv__body">
        @if (loading()) {
          <div class="fv__center"><app-spinner /></div>
        } @else if (failed()) {
          <div class="fv__center fv__muted">{{ 'file.unavailable' | t }}</div>
        } @else {
          @switch (kind()) {
            @case ('image') {
              <img class="fv__image" [src]="url()!" [alt]="artifact().name" />
            }
            @case ('pdf') {
              <iframe class="fv__pdf" [src]="safeUrl()!" [title]="artifact().name"></iframe>
            }
            @case ('video') {
              <video class="fv__media" [src]="url()!" controls></video>
            }
            @case ('audio') {
              <audio class="fv__audio" [src]="url()!" controls></audio>
            }
            @case ('text') {
              <pre class="fv__text">{{ text() }}</pre>
            }
            @default {
              <div class="fv__center fv__muted">
                <app-icon [name]="icon()" [size]="40" />
                <p>{{ 'file.noPreview' | t }}</p>
              </div>
            }
          }
        }
      </div>
    </div>
  `,
  styles: `
    :host { display: block; height: 100%; min-height: 0; }
    .fv { display: flex; flex-direction: column; height: 100%; min-height: 0; }
    .fv__bar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 12px var(--space-lg); border-bottom: 1px solid var(--divider); }
    .fv__meta { display: flex; align-items: center; gap: 12px; color: var(--text-muted); }
    .fv__name { margin: 0; font-weight: 600; color: var(--text); }
    .fv__sub { margin: 0; font-size: 12px; color: var(--text-subtle); }
    .fv__actions { display: flex; align-items: center; gap: 8px; }
    .fv__link { text-decoration: none; }
    .fv__body { flex: 1; min-height: 0; overflow: auto; background: var(--surface-muted); display: flex; justify-content: center; }
    .fv__center { display: grid; place-items: center; align-content: center; gap: 12px; width: 100%; min-height: 40vh; text-align: center; }
    .fv__muted { color: var(--text-subtle); }
    .fv__image { max-width: 100%; max-height: 100%; object-fit: contain; margin: auto; padding: var(--space-lg); }
    .fv__pdf { width: 100%; height: 100%; border: none; background: #fff; }
    .fv__media { max-width: 100%; max-height: 100%; margin: auto; }
    .fv__audio { margin: auto; width: min(560px, 90%); }
    .fv__text { width: min(900px, 100%); margin: 0; padding: var(--space-lg); background: var(--surface); white-space: pre-wrap; overflow-wrap: anywhere; font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 13px; }
  `,
})
export class FileViewerComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly exportImport = inject(ExportImportService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  readonly documentWorkspaceId = input.required<string>();
  readonly artifact = input.required<Artifact>();

  readonly url = signal<string | null>(null);
  private blob: Blob | null = null;
  readonly text = signal('');
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly converting = signal(false);

  readonly kind = computed<'image' | 'pdf' | 'video' | 'audio' | 'text' | 'other'>(() => {
    switch (this.artifact().type) {
      case 'IMAGE':
        return 'image';
      case 'PDF':
        return 'pdf';
      case 'VIDEO':
        return 'video';
      case 'AUDIO':
        return 'audio';
      case 'TXT':
        return 'text';
      default:
        return 'other';
    }
  });
  readonly convertible = computed(() => ['DOCX', 'XLSX', 'PPTX', 'PDF'].includes(this.artifact().type));
  readonly convertLabel = computed(() => {
    const type = this.artifact().type;
    return type === 'XLSX' ? 'file.convert.sheet' : type === 'PPTX' ? 'file.convert.deck' : 'file.convert.note';
  });
  readonly icon = computed(() => (this.artifact().type === 'XLSX' ? 'table' : this.artifact().type === 'IMAGE' ? 'image' : 'file-text') as 'table' | 'image' | 'file-text');
  readonly safeUrl = computed<SafeResourceUrl | null>(() => {
    const url = this.url();
    return url ? this.sanitizer.bypassSecurityTrustResourceUrl(url) : null;
  });

  ngOnInit(): void {
    // The bytes come through the API (authenticated), never from the object store directly.
    this.destroyRef.onDestroy(() => {
      const url = this.url();
      if (url) URL.revokeObjectURL(url);
    });
    const endpoint = `${environment.documentWorkspaceApiUrl}/api/v1/workspaces/${this.documentWorkspaceId()}/files/${this.artifact().id}/content`;
    this.http.get(endpoint, { responseType: 'blob' }).subscribe({
      next: async (blob) => {
        this.blob = blob;
        const typed = blob.type ? blob : new Blob([blob], { type: this.artifact().mimeType ?? 'application/octet-stream' });
        this.url.set(URL.createObjectURL(typed));
        if (this.kind() === 'text') this.text.set((await blob.text()).slice(0, 500_000));
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  /** Imports the stored file as an editable note (DOCX) or spreadsheet (XLSX) next to the original. */
  convert(): void {
    const blob = this.blob;
    const artifact = this.artifact();
    if (!blob || this.converting()) return;
    this.converting.set(true);
    const file = new File([blob], artifact.originalFilename || artifact.name, { type: blob.type });
    this.exportImport.import(this.documentWorkspaceId(), file, artifact.parentId).subscribe({
      next: (result) => {
        this.converting.set(false);
        const id = Object.values(result).flat()[0];
        this.toast.success(this.i18n.translate('file.converted'));
        if (id) void this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'a', id]);
      },
      error: (err) => {
        this.converting.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
