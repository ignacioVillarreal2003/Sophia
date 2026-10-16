import {
  AfterViewInit,
  Directive,
  ElementRef,
  booleanAttribute,
  inject,
  input,
} from '@angular/core';

@Directive({ selector: '[appAutofocus]' })
export class AutofocusDirective implements AfterViewInit {
  private readonly el = inject(ElementRef<HTMLElement>);
  readonly enabled = input(true, { alias: 'appAutofocus', transform: booleanAttribute });

  ngAfterViewInit(): void {
    if (this.enabled()) {
      queueMicrotask(() => {
        const node = this.el.nativeElement;
        const selector = 'input,textarea,select,button,[tabindex]';
        const target: HTMLElement | null = node.matches(selector)
          ? node
          : node.querySelector(selector);
        target?.focus();
      });
    }
  }
}
