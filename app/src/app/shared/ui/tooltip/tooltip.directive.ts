import {
  Directive,
  ElementRef,
  Injector,
  OnDestroy,
  inject,
  input,
} from '@angular/core';
import { Overlay, OverlayPositionBuilder, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { ChangeDetectionStrategy, Component, input as cinput } from '@angular/core';

@Component({
  selector: 'app-tooltip-content',
  template: `<div class="tooltip">{{ text() }}</div>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class TooltipContentComponent {
  readonly text = cinput<string>('');
}

@Directive({
  selector: '[appTooltip]',
  host: {
    '(mouseenter)': 'show()',
    '(mouseleave)': 'hide()',
    '(focus)': 'show()',
    '(blur)': 'hide()',
  },
})
export class TooltipDirective implements OnDestroy {
  private readonly overlay = inject(Overlay);
  private readonly positionBuilder = inject(OverlayPositionBuilder);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly injector = inject(Injector);

  readonly text = input.required<string>({ alias: 'appTooltip' });
  readonly placement = input<'top' | 'bottom' | 'right'>('top');

  private overlayRef?: OverlayRef;

  show(): void {
    if (this.overlayRef || !this.text()) {
      return;
    }
    const positions =
      this.placement() === 'right'
        ? [{ originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: 8 } as const]
        : this.placement() === 'bottom'
          ? [{ originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 8 } as const]
          : [{ originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -8 } as const];

    const positionStrategy = this.positionBuilder
      .flexibleConnectedTo(this.host)
      .withPositions(positions);

    this.overlayRef = this.overlay.create({ positionStrategy });
    const portal = new ComponentPortal(TooltipContentComponent, null, this.injector);
    const ref = this.overlayRef.attach(portal);
    ref.setInput('text', this.text());
  }

  hide(): void {
    this.overlayRef?.dispose();
    this.overlayRef = undefined;
  }

  ngOnDestroy(): void {
    this.hide();
  }
}
