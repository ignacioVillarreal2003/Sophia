import {ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal} from '@angular/core';
import {FormBuilder, FormControl, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {Permission, Role} from '@core/models/admin';
import {AppError} from '@core/models/api.model';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {AdminApiService} from '@core/services/admin/admin-api.service';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {TextareaComponent} from '@shared/ui/form-field/textarea.component';
import {ChipSelectComponent, ChipOption} from '@shared/ui/chip-select/chip-select.component';
import {LoadMoreButtonComponent} from '@shared/ui/load-more-button/load-more-button.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {firstErrorKey} from '@shared/utils/form-errors';

const NAME_PATTERN = /^[A-Z][A-Z0-9_]*$/;

const ERROR_KEYS: Record<string, string> = {
  'validation.required': 'validation.required',
  'validation.pattern': 'admin.roles.form.error.pattern',
};

@Component({
  selector: 'app-role-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    ButtonComponent,
    CardComponent,
    TextFieldComponent,
    TextareaComponent,
    ChipSelectComponent,
    LoadMoreButtonComponent,
    SpinnerComponent,
    TranslatePipe,
  ],
  templateUrl: './role-form.component.html',
  styleUrl: './role-form.component.scss',
})
export class RoleFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApiService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly id = input<string>('');

  readonly isEdit = computed(() => !!this.id());
  readonly role = signal<Role | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly availablePermissions = signal<Permission[]>([]);
  readonly permissionsControl = new FormControl<string[]>([], {nonNullable: true});
  readonly permissionsPage = signal(0);
  readonly permissionsLast = signal(true);
  readonly loadingMorePermissions = signal(false);

  readonly permissionChipOptions = computed<ChipOption[]>(() =>
    this.availablePermissions().map((p) => ({
      value: p.name,
      label: p.name,
      hint: p.description ?? '',
    })),
  );

  readonly nameLocked = computed(() => this.isEdit() && !!this.role()?.system);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(NAME_PATTERN)]],
    description: [''],
  });

  ngOnInit(): void {
    this.loadPermissions(0);
    if (this.isEdit()) {
      this.loading.set(true);
      this.api.getRole(this.id()).subscribe({
        next: (role) => {
          this.role.set(role);
          this.form.reset({name: role.name, description: role.description ?? ''});
          this.permissionsControl.setValue([...role.permissions]);
          if (role.system) {
            this.form.controls.name.disable();
          }
          this.loading.set(false);
        },
        error: (err: AppError) => {
          this.loading.set(false);
          this.toast.error(err.message);
          this.router.navigate(['/admin/roles']);
        },
      });
    }
  }

  private loadPermissions(page: number): void {
    this.api.listPermissions({page, size: 20}).subscribe({
      next: (result) => {
        this.availablePermissions.update((list) =>
          page === 0 ? result.content : [...list, ...result.content],
        );
        this.permissionsPage.set(page);
        this.permissionsLast.set(result.last);
        this.loadingMorePermissions.set(false);
      },
      error: () => this.loadingMorePermissions.set(false),
    });
  }

  loadMorePermissions(): void {
    this.loadingMorePermissions.set(true);
    this.loadPermissions(this.permissionsPage() + 1);
  }

  errorOf(control: string): string | null {
    const key = firstErrorKey(this.form.get(control));
    if (!key) return null;
    return this.i18n.translate(ERROR_KEYS[key] ?? 'admin.roles.form.error.invalid');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const request = {...value, permissions: this.permissionsControl.value};
    const result$ = this.isEdit()
      ? this.api.updateRole(this.id(), request)
      : this.api.createRole(request);
    result$.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(
          this.i18n.translate(this.isEdit() ? 'admin.roles.toast.updated' : 'admin.roles.toast.created'),
        );
        this.router.navigate(['/admin/roles']);
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async deleteRole(): Promise<void> {
    const current = this.role();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.roles.confirmDelete.title'),
      message: this.i18n.translate('admin.roles.confirmDelete.message', {name: current.name}),
      confirmLabel: this.i18n.translate('admin.roles.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.api.deleteRole(current.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('admin.roles.toast.deleted'));
        this.router.navigate(['/admin/roles']);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }
}
