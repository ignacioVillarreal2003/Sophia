import {ChangeDetectionStrategy, Component, OnInit, inject, signal} from '@angular/core';
import {FormBuilder, FormControl, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {Role} from '@core/models/admin';
import {AppError} from '@core/models/api.model';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {AdminApiService} from '@core/services/admin/admin-api.service';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {CardComponent} from '@shared/ui/card/card.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {ChipSelectComponent, ChipOption} from '@shared/ui/chip-select/chip-select.component';
import {CheckboxComponent} from '@shared/ui/checkbox/checkbox.component';
import {firstErrorKey, passwordStrengthValidator} from '@shared/utils/form-errors';

const ERROR_KEYS: Record<string, string> = {
  'validation.required': 'validation.required',
  'validation.email': 'validation.email',
  'validation.password': 'validation.password',
};

@Component({
  selector: 'app-user-create',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    ButtonComponent,
    CardComponent,
    TextFieldComponent,
    ChipSelectComponent,
    CheckboxComponent,
    TranslatePipe,
  ],
  templateUrl: './user-create.component.html',
  styleUrl: './user-create.component.scss',
})
export class UserCreateComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApiService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly saving = signal(false);
  readonly availableRoles = signal<Role[]>([]);
  readonly rolesControl = new FormControl<string[]>(['USER'], {nonNullable: true});

  readonly roleChipOptions = signal<ChipOption[]>([]);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, passwordStrengthValidator]],
    active: true,
  });

  ngOnInit(): void {
    this.api.listRoles({size: 200}).subscribe({
      next: (page) => {
        this.availableRoles.set(page.content);
        this.roleChipOptions.set(page.content.map((r) => ({value: r.name, label: r.name})));
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  errorOf(control: string): string | null {
    const key = firstErrorKey(this.form.get(control));
    if (!key) return null;
    return this.i18n.translate(ERROR_KEYS[key] ?? 'admin.users.form.error.invalid');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    this.api
      .createUser({...value, roles: this.rolesControl.value})
      .subscribe({
        next: (user) => {
          this.saving.set(false);
          this.toast.success(this.i18n.translate('admin.users.toast.created'));
          this.router.navigate(['/admin/users', user.id]);
        },
        error: (err: AppError) => {
          this.saving.set(false);
          this.toast.error(err.message);
        },
      });
  }
}
