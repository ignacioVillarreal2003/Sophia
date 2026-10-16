import { AbstractControl } from '@angular/forms';

export function firstErrorKey(control: AbstractControl | null): string | null {
  if (!control || !control.errors || !(control.touched || control.dirty)) {
    return null;
  }
  const errors = control.errors;
  if (errors['required']) return 'validation.required';
  if (errors['email']) return 'validation.email';
  if (errors['minlength']) return 'validation.minlength';
  if (errors['maxlength']) return 'validation.maxlength';
  if (errors['passwordStrength']) return 'validation.password';
  if (errors['mismatch']) return 'auth.error.mismatch';
  const first = Object.keys(errors)[0];
  return `validation.${first}`;
}

export function passwordStrengthValidator(control: AbstractControl) {
  const value = String(control.value ?? '');
  if (!value) return null;
  const ok =
    value.length >= 8 &&
    value.length <= 128 &&
    /[a-z]/.test(value) &&
    /[A-Z]/.test(value) &&
    /[0-9]/.test(value) &&
    /[^A-Za-z0-9]/.test(value);
  return ok ? null : { passwordStrength: true };
}
