import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AppError } from '@core/models/api.model';
import { ArtifactType, CreateArtifactRequest } from '@core/models/document-workspace';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { TranslationService } from '@core/services/i18n';
import { ToastService } from '@core/services/toast.service';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { CardComponent } from '@shared/ui/card/card.component';
import { TextFieldComponent } from '@shared/ui/form-field/text-field.component';
import { TextareaComponent } from '@shared/ui/form-field/textarea.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { IconName } from '@shared/ui/icon/icons';
import { PageHeaderComponent } from '@shared/ui/page-header/page-header.component';
import { ArtifactService } from '../data/artifact.service';
import { DocumentWorkspaceService } from '../data/document-workspace.service';
import { ExportImportService } from '../data/export-import.service';
import { UploadManagerService } from '../data/upload-manager.service';
import { WORKSPACE_COLORS } from '../workspace-colors';

type CreatableType = Extract<ArtifactType, 'FOLDER' | 'MARKDOWN_DOCUMENT' | 'CHECKLIST' | 'SPREADSHEET'>;

interface TypeOption {
  readonly type: CreatableType;
  readonly icon: IconName;
}

const TYPES: TypeOption[] = [
  { type: 'FOLDER', icon: 'folder' },
  { type: 'MARKDOWN_DOCUMENT', icon: 'file-text' },
  { type: 'CHECKLIST', icon: 'check-circle' },
  { type: 'SPREADSHEET', icon: 'table' },
];

/** Starting points offered for each document type. The first one is always the blank page. */
export interface Starter {
  readonly id: string;
  /** Builds the initial content from the document name. */
  readonly build: (name: string) => string;
}

function sheet(cells: Record<string, unknown>, extra: Record<string, unknown> = {}): string {
  return JSON.stringify({
    version: 1,
    sheets: [{ id: 's1', name: 'Sheet1', rows: 100, cols: 26, cells, ...extra }],
    activeSheet: 's1',
  });
}

const HEADER = { b: true, bg: '#eef2ff' };

const STARTERS: Record<'MARKDOWN_DOCUMENT' | 'CHECKLIST' | 'SPREADSHEET', Starter[]> = {
  MARKDOWN_DOCUMENT: [
    { id: 'blank', build: (name) => `# ${name}\n\n` },
    {
      id: 'meeting',
      build: (name) =>
        `# ${name}\n\n**Date:** ${new Date().toISOString().slice(0, 10)}  \n**Attendees:** \n\n## Agenda\n\n1. \n2. \n\n## Notes\n\n\n## Decisions\n\n> [!tip] Decisions\n> \n\n## Action items\n\n- [ ] \n- [ ] \n`,
    },
    {
      id: 'project',
      build: (name) =>
        `# ${name}\n\n> [!info] Summary\n> One paragraph about the goal of the project.\n\n## Goals\n\n- \n\n## Scope\n\n| In scope | Out of scope |\n| --- | --- |\n|  |  |\n\n## Milestones\n\n- [ ] Kick-off\n- [ ] First delivery\n- [ ] Launch\n\n## Risks\n\n> [!warning] Risks\n> \n`,
    },
    { id: 'journal', build: (name) => `# ${name}\n\n## ${new Date().toISOString().slice(0, 10)}\n\n### Highlights\n\n- \n\n### Gratitude\n\n- \n\n### Tomorrow\n\n- [ ] \n` },
  ],
  CHECKLIST: [
    { id: 'blank', build: () => '- [ ] \n' },
    { id: 'weekly', build: () => '## Monday\n\n- [ ] \n\n## Tuesday\n\n- [ ] \n\n## Wednesday\n\n- [ ] \n\n## Thursday\n\n- [ ] \n\n## Friday\n\n- [ ] \n' },
    { id: 'shopping', build: () => '## Groceries\n\n- [ ] \n\n## Household\n\n- [ ] \n' },
    { id: 'release', build: () => '## Before release\n\n- [ ] Tests green\n- [ ] Changelog updated\n- [ ] Version bumped\n\n## Release\n\n- [ ] Deploy\n- [ ] Smoke test\n\n## After release\n\n- [ ] Announce\n- [ ] Monitor errors\n' },
  ],
  SPREADSHEET: [
    { id: 'blank', build: () => sheet({}) },
    {
      id: 'budget',
      build: () =>
        sheet(
          {
            A1: { v: 'Item', s: HEADER },
            B1: { v: 'Planned', s: HEADER },
            C1: { v: 'Actual', s: HEADER },
            D1: { v: 'Difference', s: HEADER },
            A2: { v: 'Rent' },
            B2: { v: 1200, s: { nf: '#,##0.00' } },
            C2: { v: 1200, s: { nf: '#,##0.00' } },
            D2: { f: '=B2-C2', s: { nf: '#,##0.00' } },
            A3: { v: 'Food' },
            B3: { v: 400, s: { nf: '#,##0.00' } },
            C3: { v: 455.5, s: { nf: '#,##0.00' } },
            D3: { f: '=B3-C3', s: { nf: '#,##0.00' } },
            A5: { v: 'Total', s: { b: true } },
            B5: { f: '=SUM(B2:B4)', s: { b: true, nf: '#,##0.00' } },
            C5: { f: '=SUM(C2:C4)', s: { b: true, nf: '#,##0.00' } },
            D5: { f: '=SUM(D2:D4)', s: { b: true, nf: '#,##0.00' } },
          },
          { colWidths: { '0': 180, '1': 110, '2': 110, '3': 110 } },
        ),
    },
    {
      id: 'tracker',
      build: () =>
        sheet(
          {
            A1: { v: 'Task', s: HEADER },
            B1: { v: 'Owner', s: HEADER },
            C1: { v: 'Status', s: HEADER },
            D1: { v: 'Due', s: HEADER },
            A2: { v: 'Write the proposal' },
            B2: { v: 'Ana' },
            C2: { v: 'In progress' },
            A3: { v: 'Review budget' },
            B3: { v: 'Luis' },
            C3: { v: 'Done' },
            F1: { v: 'Done', s: { b: true } },
            G1: { f: '=COUNTIF(C2:C100,"Done")' },
            F2: { v: 'Total', s: { b: true } },
            G2: { f: '=COUNTA(A2:A100)' },
            F3: { v: 'Progress', s: { b: true } },
            G3: { f: '=IF(G2=0,0,G1/G2)', s: { nf: '0%' } },
          },
          { colWidths: { '0': 220, '1': 110, '2': 120, '3': 110, '5': 90 } },
        ),
    },
  ],
};

/** "New item" page: pick a type, fill in the data that type needs, create it. */
@Component({
  selector: 'app-new-artifact',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    ButtonComponent,
    CardComponent,
    TextFieldComponent,
    TextareaComponent,
    IconComponent,
    PageHeaderComponent,
    TranslatePipe,
  ],
  templateUrl: './new-artifact.component.html',
  styleUrl: './new-artifact.component.scss',
})
export class NewArtifactComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly artifacts = inject(ArtifactService);
  private readonly workspaces = inject(DocumentWorkspaceService);
  private readonly uploads = inject(UploadManagerService);
  private readonly exportImport = inject(ExportImportService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);

  /** Route param and query params (bound via withComponentInputBinding). */
  readonly documentWorkspaceId = input.required<string>();
  readonly folder = input<string | undefined>(undefined);
  readonly type = input<string | undefined>(undefined);

  readonly types = TYPES;
  readonly swatches = WORKSPACE_COLORS;
  readonly starter = signal('blank');

  readonly selected = signal<CreatableType>('MARKDOWN_DOCUMENT');
  readonly submitting = signal(false);
  readonly busyFrom = signal<'upload' | 'import' | null>(null);
  readonly location = signal('');

  readonly form = this.fb.nonNullable.group({
    name: [''],
    description: [''],
    color: [''],
  });
  readonly draft = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  readonly touched = signal(false);

  readonly option = computed(() => TYPES.find((t) => t.type === this.selected()) ?? TYPES[0]);
  readonly nameError = computed(() => (this.touched() && !this.draft().name?.trim() ? this.i18n.translate('newItem.error.name') : ''));
  /** Starters of the chosen type (folders have none). */
  readonly starters = computed<Starter[]>(() => {
    const type = this.selected();
    return type === 'FOLDER' ? [] : STARTERS[type];
  });

  ngOnInit(): void {
    const requested = this.type();
    if (requested && TYPES.some((t) => t.type === requested)) {
      this.selected.set(requested as CreatableType);
    }
    this.resolveLocation();
  }

  private resolveLocation(): void {
    const root = this.workspaces.active()?.name ?? this.i18n.translate('documents.home');
    this.location.set(root);
    const folderId = this.folder();
    if (!folderId) return;
    const ws = this.documentWorkspaceId();
    this.artifacts.get(ws, folderId).subscribe({
      next: (current) =>
        this.artifacts.breadcrumb(ws, folderId).subscribe({
          next: (ancestors) => {
            const names = ancestors.filter((a) => a.id !== current.id).map((a) => a.name);
            this.location.set([root, ...names, current.name].join(' / '));
          },
        }),
    });
  }

  select(type: CreatableType): void {
    this.selected.set(type);
    this.starter.set('blank');
    this.touched.set(false);
  }

  setColor(color: string): void {
    this.form.controls.color.setValue(this.form.controls.color.value === color ? '' : color);
  }

  // ---- navigation ----
  private backTo(): void {
    const base = ['/document-workspaces', this.documentWorkspaceId()];
    const folderId = this.folder();
    this.router.navigate(folderId ? [...base, 'folder', folderId] : base);
  }

  cancel(): void {
    this.backTo();
  }

  // ---- create ----
  submit(): void {
    this.touched.set(true);
    const v = this.form.getRawValue();
    if (!v.name.trim()) return;

    const type = this.selected();
    const ws = this.documentWorkspaceId();
    const parentId = this.folder() ?? null;
    this.submitting.set(true);

    const starter = this.starters().find((s) => s.id === this.starter());
    const request: CreateArtifactRequest = {
      type,
      name: v.name.trim(),
      parentId,
      ...(v.description.trim() ? { description: v.description.trim() } : {}),
      ...(type === 'FOLDER' && v.color ? { color: v.color } : {}),
      ...(starter ? { content: starter.build(v.name.trim()) } : {}),
    };

    this.artifacts.create(ws, request).subscribe({
      next: (created) => this.finish(created.type === 'FOLDER' ? null : created.id),
      error: (err: AppError) => this.fail(err),
    });
  }

  /** Folders go back to where they were created; everything else opens straight away. */
  private finish(openId: string | null): void {
    this.submitting.set(false);
    this.toast.success(this.i18n.translate('newItem.toast.created'));
    if (openId) {
      this.router.navigate(['/document-workspaces', this.documentWorkspaceId(), 'a', openId]);
    } else {
      this.backTo();
    }
  }

  private fail(err: AppError): void {
    this.submitting.set(false);
    this.toast.error(err.message);
  }

  // ---- from the user's computer ----
  onUploadPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length) return;
    this.busyFrom.set('upload');
    let done = 0;
    this.uploads.upload(this.documentWorkspaceId(), files, this.folder() ?? null, () => {
      if (++done === files.length) {
        this.busyFrom.set(null);
        this.backTo();
      }
    });
  }

  onImportPicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.busyFrom.set('import');
    this.exportImport.import(this.documentWorkspaceId(), file, this.folder() ?? null).subscribe({
      next: () => {
        this.busyFrom.set(null);
        this.toast.success(this.i18n.translate('documents.importSuccess'));
        this.backTo();
      },
      error: (err) => {
        this.busyFrom.set(null);
        this.toast.error(err.message ?? this.i18n.translate('documents.importError'));
      },
    });
  }
}
