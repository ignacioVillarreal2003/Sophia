import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {RouterOutlet} from '@angular/router';
import {ThemeService} from '@core/services/theme.service';
import {CommandPaletteService} from '@core/services/command-palette.service';
import {ToastHostComponent} from '@shared/ui/toast/toast-host.component';
import {CommandPaletteComponent} from '@shared/ui/command-palette/command-palette.component';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, ToastHostComponent, CommandPaletteComponent],
  template: `
    <router-outlet/>
    <app-toast-host/>
    <app-command-palette/>
  `,
  host: {
    '(document:keydown)': 'onKeydown($event)',
  },
})
export class App {
  private readonly theme = inject(ThemeService);
  private readonly commandPalette = inject(CommandPaletteService);

  onKeydown(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.commandPalette.toggle();
    }
  }
}
