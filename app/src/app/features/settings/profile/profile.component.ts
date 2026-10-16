import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {AuthenticationService} from '@core/services/authentication/authentication.service';
import {AppError} from '@core/models/api.model';
import {ToastService} from '@core/services/toast.service';
import {TranslationService} from '@core/services/i18n';
import {TranslatePipe} from '@core/services/i18n/translate.pipe';
import {CardComponent} from '@shared/ui/card/card.component';
import {AvatarComponent} from '@shared/ui/avatar/avatar.component';
import {BadgeComponent} from '@shared/ui/badge/badge.component';
import {ButtonComponent} from '@shared/ui/button/button.component';
import {IconComponent} from '@shared/ui/icon/icon.component';
import {SpinnerComponent} from '@shared/ui/spinner/spinner.component';
import {TextFieldComponent} from '@shared/ui/form-field/text-field.component';
import {PageHeaderComponent} from '@shared/ui/page-header/page-header.component';
import {RelativeTimePipe} from '@shared/pipes/relative-time.pipe';

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
const ALLOWED_PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CardComponent,
    AvatarComponent,
    BadgeComponent,
    ButtonComponent,
    IconComponent,
    SpinnerComponent,
    TextFieldComponent,
    PageHeaderComponent,
    RelativeTimePipe,
    TranslatePipe,
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthenticationService);
  private readonly toast = inject(ToastService);
  private readonly i18n = inject(TranslationService);
  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');

  readonly user = this.auth.user;
  readonly saving = signal(false);
  readonly uploadingPhoto = signal(false);
  readonly removingPhoto = signal(false);
  readonly resendingVerification = signal(false);
  readonly verificationSent = signal(false);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.maxLength(100)]],
    lastName: ['', [Validators.maxLength(100)]],
  });

  readonly incomplete = signal(false);

  constructor() {
    effect(() => {
      const u = this.user();
      if (u) {
        this.form.patchValue({firstName: u.firstName, lastName: u.lastName}, {emitEvent: false});
        this.incomplete.set(!u.firstName?.trim() && !u.lastName?.trim());
      }
    });
  }

  ngOnInit(): void {
    this.auth.refreshUser().subscribe({
      error: () => {
      }
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const {firstName, lastName} = this.form.getRawValue();
    this.auth.updateProfile({firstName, lastName}).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.i18n.translate('settings.profile.toast.updated'));
      },
      error: (err: AppError) => {
        this.saving.set(false);
        this.toast.error(err.message);
      },
    });
  }

  resendVerification(): void {
    const email = this.user()?.email;
    if (!email) return;
    this.resendingVerification.set(true);
    this.auth.resendVerification({email}).subscribe({
      next: () => {
        this.resendingVerification.set(false);
        this.verificationSent.set(true);
        this.toast.success(this.i18n.translate('settings.profile.toast.verificationSent'));
      },
      error: (err: AppError) => {
        this.resendingVerification.set(false);
        this.toast.error(err.message);
      },
    });
  }

  triggerPhotoUpload(): void {
    this.fileInput()?.nativeElement.click();
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    if (!ALLOWED_PHOTO_TYPES.includes(file.type)) {
      this.toast.error(this.i18n.translate('settings.profile.error.photoType'));
      return;
    }
    if (file.size > MAX_PHOTO_BYTES) {
      this.toast.error(this.i18n.translate('settings.profile.error.photoSize'));
      return;
    }

    this.uploadingPhoto.set(true);
    this.auth.uploadPhoto(file).subscribe({
      next: () => {
        this.uploadingPhoto.set(false);
        this.toast.success(this.i18n.translate('settings.profile.toast.photoUpdated'));
      },
      error: (err: AppError) => {
        this.uploadingPhoto.set(false);
        this.toast.error(err.message);
      },
    });
  }

  removePhoto(): void {
    this.removingPhoto.set(true);
    this.auth.deletePhoto().subscribe({
      next: () => {
        this.removingPhoto.set(false);
        this.toast.success(this.i18n.translate('settings.profile.toast.photoRemoved'));
      },
      error: (err: AppError) => {
        this.removingPhoto.set(false);
        this.toast.error(err.message);
      },
    });
  }
}
