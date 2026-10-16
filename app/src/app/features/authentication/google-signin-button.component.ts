import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  inject,
  input,
  output,
  viewChild,
} from '@angular/core';
import {GoogleButtonText, GoogleIdentityService} from '@core/services/authentication/google-identity.service';

@Component({
  selector: 'app-google-signin-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div #container class="gsb"></div>`,
  styles: [
    `
      :host {
        display: block;
        width: 100%;
      }

      .gsb {
        display: flex;
        justify-content: center;
        width: 100%;
      }
    `,
  ],
})
export class GoogleSigninButtonComponent implements AfterViewInit, OnDestroy {
  private readonly google = inject(GoogleIdentityService);
  private readonly container = viewChild.required<ElementRef<HTMLDivElement>>('container');

  readonly text = input<GoogleButtonText>('continue_with');
  readonly height = input<number>(40);
  readonly credential = output<string>();

  private resizeObserver: ResizeObserver | null = null;
  private resizeTimeout: ReturnType<typeof setTimeout> | null = null;
  private lastWidth = 0;

  ngAfterViewInit(): void {
    const element = this.container().nativeElement;

    this.resizeObserver = new ResizeObserver((entries) => {
      const width = Math.round(entries[0].contentRect.width);
      if (width <= 0 || Math.abs(width - this.lastWidth) < 4) {
        return;
      }
      if (this.resizeTimeout) {
        clearTimeout(this.resizeTimeout);
      }
      const delay = this.lastWidth === 0 ? 0 : 150;
      this.resizeTimeout = setTimeout(() => this.render(element, width), delay);
    });
    this.resizeObserver.observe(element);
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    if (this.resizeTimeout) {
      clearTimeout(this.resizeTimeout);
    }
  }

  private render(element: HTMLDivElement, width: number): void {
    this.lastWidth = width;
    this.google
      .renderButton(element, (idToken) => this.credential.emit(idToken), {
        text: this.text(),
        width,
        height: this.height(),
      })
      .catch((err: unknown) => {
        console.error('Google sign-in button failed to render', err);
      });
  }
}
