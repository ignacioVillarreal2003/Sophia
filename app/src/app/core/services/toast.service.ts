import { Injectable, signal } from '@angular/core';

export type ToastVariant = 'info' | 'success' | 'warning' | 'danger';

export interface Toast {
  readonly id: number;
  readonly variant: ToastVariant;
  readonly message: string;
  readonly action?: { label: string; run: () => void };
  readonly duration: number;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  private readonly _toasts = signal<Toast[]>([]);
  readonly toasts = this._toasts.asReadonly();

  show(
    message: string,
    variant: ToastVariant = 'info',
    options: { duration?: number; action?: Toast['action'] } = {},
  ): number {
    const id = ++this.seq;
    const duration = options.duration ?? (variant === 'danger' ? 6000 : 4000);
    const toast: Toast = { id, message, variant, duration, action: options.action };
    this._toasts.update((list) => [...list, toast]);
    if (duration > 0) {
      setTimeout(() => this.dismiss(id), duration);
    }
    return id;
  }

  success(message: string, options?: { duration?: number; action?: Toast['action'] }): number {
    return this.show(message, 'success', options);
  }

  error(message: string, options?: { duration?: number; action?: Toast['action'] }): number {
    return this.show(message, 'danger', options);
  }

  info(message: string, options?: { duration?: number; action?: Toast['action'] }): number {
    return this.show(message, 'info', options);
  }

  warning(message: string, options?: { duration?: number; action?: Toast['action'] }): number {
    return this.show(message, 'warning', options);
  }

  dismiss(id: number): void {
    this._toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
