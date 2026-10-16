import {ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal} from '@angular/core';
import {FormBuilder, FormControl, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {firstValueFrom} from 'rxjs';
import {Permission, ServiceClient} from '@core/models/admin';
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
import {
  ServiceClientSecretDialogComponent,
  ServiceClientSecretData,
} from './service-client-secret-dialog.component';

@Component({
  selector: 'app-service-client-form',
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
  templateUrl: './service-client-form.component.html',
  styleUrl: './service-client-form.component.scss',
})
export class ServiceClientFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AdminApiService);
  private readonly toast = inject(ToastService);
  private readonly dialog = inject(DialogService);
  private readonly router = inject(Router);
  private readonly i18n = inject(TranslationService);

  readonly id = input<string>('');

  readonly isEdit = computed(() => !!this.id());
  readonly client = signal<ServiceClient | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);

  readonly availablePermissions = signal<Permission[]>([]);
  readonly scopesControl = new FormControl<string[]>([], {nonNullable: true});
  readonly scopesPage = signal(0);
  readonly scopesLast = signal(true);
  readonly loadingMoreScopes = signal(false);

  readonly scopeOptions = computed<ChipOption[]>(() =>
    this.availablePermissions().map((p) => ({value: p.name, label: p.name})),
  );

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
    description: [''],
  });

  ngOnInit(): void {
    this.loadScopes(0);

    if (this.isEdit()) {
      this.loading.set(true);
      this.api.getServiceClient(this.id()).subscribe({
        next: (client) => {
          this.client.set(client);
          this.form.reset({name: client.name, description: client.description ?? ''});
          this.scopesControl.setValue([...client.scopes]);
          this.loading.set(false);
        },
        error: (err: AppError) => {
          this.loading.set(false);
          this.toast.error(err.message);
          this.router.navigate(['/admin/service-clients']);
        },
      });
    }
  }

  private loadScopes(page: number): void {
    this.api.listPermissions({page, size: 20}).subscribe({
      next: (result) => {
        this.availablePermissions.update((list) =>
          page === 0 ? result.content : [...list, ...result.content],
        );
        this.scopesPage.set(page);
        this.scopesLast.set(result.last);
        this.loadingMoreScopes.set(false);
      },
      error: () => this.loadingMoreScopes.set(false),
    });
  }

  loadMoreScopes(): void {
    this.loadingMoreScopes.set(true);
    this.loadScopes(this.scopesPage() + 1);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const request = {
      ...value,
      scopes: this.scopesControl.value,
      active: this.client()?.active ?? true,
    };
    const result$ = this.isEdit()
      ? this.api.updateServiceClient(this.id(), request)
      : this.api.createServiceClient(request);
    result$.subscribe({
      next: async (client) => {
        this.saving.set(false);
        if (this.isEdit()) {
          this.toast.success(this.i18n.translate('admin.serviceClients.toast.updated'));
        } else {
          this.toast.success(this.i18n.translate('admin.serviceClients.toast.created'));
          await this.revealSecret(client);
        }
        this.router.navigate(['/admin/service-clients']);
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  async rotateSecret(): Promise<void> {
    const current = this.client();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.serviceClients.confirmRotate.title'),
      message: this.i18n.translate('admin.serviceClients.confirmRotate.message', {name: current.name}),
      confirmLabel: this.i18n.translate('admin.serviceClients.confirmRotate.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.api.rotateServiceClientSecret(current.id).subscribe({
      next: (updated) => {
        this.client.set(updated);
        this.revealSecret(updated);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  async deleteClient(): Promise<void> {
    const current = this.client();
    if (!current) return;
    const ok = await this.dialog.confirm({
      title: this.i18n.translate('admin.serviceClients.confirmDelete.title'),
      message: this.i18n.translate('admin.serviceClients.confirmDelete.message', {name: current.name}),
      confirmLabel: this.i18n.translate('admin.serviceClients.confirmDelete.confirmLabel'),
      danger: true,
    });
    if (!ok) return;
    this.api.deleteServiceClient(current.id).subscribe({
      next: () => {
        this.toast.success(this.i18n.translate('admin.serviceClients.toast.deleted'));
        this.router.navigate(['/admin/service-clients']);
      },
      error: (err: AppError) => this.toast.error(err.message),
    });
  }

  private async revealSecret(client: ServiceClient): Promise<void> {
    if (!client.clientSecret) return;
    const data: ServiceClientSecretData = {name: client.name, secret: client.clientSecret};
    const ref = this.dialog.open(ServiceClientSecretDialogComponent, {data});
    await firstValueFrom(ref.closed);
  }
}
