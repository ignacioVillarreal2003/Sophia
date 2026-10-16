import { Injectable, inject } from '@angular/core';
import { Dialog, DialogConfig, DialogRef } from '@angular/cdk/dialog';
import { ComponentType } from '@angular/cdk/portal';
import { firstValueFrom } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class DialogService {
  private readonly dialog = inject(Dialog);

  open<T, D = unknown, R = unknown>(
    component: ComponentType<T>,
    config?: DialogConfig<D>,
  ): DialogRef<R, T> {
    return this.dialog.open<R, D, T>(component, {
      panelClass: 'app-dialog-panel',
      backdropClass: 'cdk-overlay-dark-backdrop',
      hasBackdrop: true,
      autoFocus: 'first-tabbable',
      ...config,
    } as DialogConfig<D, DialogRef<R, T>>);
  }

  async confirm(data: ConfirmDialogData): Promise<boolean> {
    const ref = this.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
      ConfirmDialogComponent,
      { data },
    );
    return (await firstValueFrom(ref.closed)) ?? false;
  }

  /** A single-button informational modal — nothing to confirm, just to read. */
  async info(data: Omit<ConfirmDialogData, 'hideCancel' | 'danger'>): Promise<void> {
    const ref = this.open<ConfirmDialogComponent, ConfirmDialogData, boolean>(
      ConfirmDialogComponent,
      { data: { ...data, hideCancel: true } },
    );
    await firstValueFrom(ref.closed);
  }
}
