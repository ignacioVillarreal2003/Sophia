import {ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {Permission} from '@core/models/admin';
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
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {DialogService} from '@shared/ui/dialog/dialog.service';
import {firstErrorKey} from '@shared/utils/form-errors';

const NAME_PATTERN = /^[A-Z][A-Z0-9_]*$/;

const ERROR_KEYS: Record<string, string> = {
  'validation.required': 'validation.required',
  'validation.pattern': 'admin.permissions.form.error.pattern',
};

@Component({
  selector: 'app-permission-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    ButtonComponent,
    CardComponent,
    TextFieldComponent,
    TextareaComponent,
    SpinnerComponent,
    TranslatePipe,
  ],
  templateUrl: './permission-form.component.html',
  styleUrl: './permission-form.component.scss',
})
export class PermissionFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApiService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly id = input<string>('');

  readonly isEdit = computed(() => !!this.id());
  readonly permission = signal<Permission | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(NAME_PATTERN)]],
    description: [''],
    category: [''],
  });

  ngOnInit(): void {
    if (this.isEdit()) {
      this.loading.set(true);
      this.api.getPermission(this.id()).subscribe({
        next: (permission) => {
          this.permission.set(permission);
          this.form.reset({
            name: permission.name,
            description: permission.description ?? '',
            category: permission.category ?? '',
          });
          this.loading.set(false);
        },
        error: (err: AppError) => {
          this.loading.set(false);
          this.toast.error(err.message);
          this.router.navigate(['/admin/permissions']);
        },
      });
    }
  }

  errorOf(control: string): string | null {
    const key = firstErrorKey(this.form.get(control));
    if (!key) return null;
    return this.i18n.translate(ERROR_KEYS[key] ?? 'admin.permissions.form.error.invalid');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const result$ = this.isEdit()
      ? this.api.updatePermission(this.id(), value)
      : this.api.createPermission(value);
    result$.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(
          this.i18n.translate(this.isEdit() ? 'admin.permissions.toast.updated' : 'admin.permissions.toast.created'),
        );
        this.router.navigate(['/admin/permissions']);
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async deletePermission(): Promise<void> {
    const current = this.permission();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.permissions.confirmDelete.title'),
      message: this.i18n.translate('admin.permissions.confirmDelete.message', {name: current.name}),
      confirmLabel: this.i18n.translate('admin.permissions.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.api.deletePermission(current.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('admin.permissions.toast.deleted'));
        this.router.navigate(['/admin/permissions']);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }
}
