import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { TranslationService } from '@core/services/i18n';
import { TranslatePipe } from '@core/services/i18n/translate.pipe';
import { ButtonComponent } from '@shared/ui/button/button.component';
import { IconComponent } from '@shared/ui/icon/icon.component';
import { SpeechService } from '../data/speech.service';

const RATES = [0.75, 1, 1.25, 1.5, 1.75];
const RATE_KEY = 'sophia.ai.speechRate';
const VOICE_KEY = 'sophia.ai.speechVoice';

function stored(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function store(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Private mode: the choice just does not outlive the page.
  }
}

/** Play, pause and stop for reading a text aloud, with speed and voice, and the sentence being read shown. */
@Component({
  selector: 'app-speech-player',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonComponent, IconComponent, TranslatePipe],
  template: `
    @if (!speech.supported) {
      <p class="sp__note"><app-icon name="info" [size]="14" /> {{ 'ai.listen.unsupported' | t }}</p>
    } @else {
      <div class="sp">
        <div class="sp__row">
          @if (!mine()) {
            <app-button variant="primary" size="sm" icon="play" [disabled]="!text().trim()" (click)="play()">{{ 'ai.listen.play' | t }}</app-button>
          } @else {
            @if (speech.state() === 'paused') {
              <app-button variant="primary" size="sm" icon="play" (click)="speech.resume()">{{ 'ai.listen.resume' | t }}</app-button>
            } @else {
              <app-button variant="secondary" size="sm" icon="pause" (click)="speech.pause()">{{ 'ai.listen.pause' | t }}</app-button>
            }
            <app-button variant="text" size="sm" icon="square" (click)="stop()">{{ 'ai.listen.stop' | t }}</app-button>
          }
          <label class="sp__ctl">
            <span>{{ 'ai.listen.speed' | t }}</span>
            <select [value]="rate()" (change)="setRate(+$any($event.target).value)">
              @for (r of rates; track r) {
                <option [value]="r" [selected]="r === rate()">{{ r }}x</option>
              }
            </select>
          </label>
          @if (voices().length > 1) {
            <label class="sp__ctl">
              <span>{{ 'ai.listen.voice' | t }}</span>
              <select [attr.aria-label]="'ai.listen.voice' | t" (change)="setVoice($any($event.target).value)">
                <option value="" [selected]="!voice()">{{ 'ai.listen.voiceDefault' | t }}</option>
                @for (v of voices(); track v.voiceURI) {
                  <option [value]="v.voiceURI" [selected]="v.voiceURI === voice()">{{ v.name }} ({{ v.lang }})</option>
                }
              </select>
            </label>
          }
        </div>
        @if (mine()) {
          <div class="sp__meter" role="progressbar" [attr.aria-valuenow]="percent()" aria-valuemin="0" aria-valuemax="100"><span [style.width.%]="percent()"></span></div>
          <p class="sp__now" aria-live="off">{{ speech.current() }}</p>
        }
      </div>
    }
  `,
  styles: `
    :host { display: block; }
    .sp { display: flex; flex-direction: column; gap: 8px; }
    .sp__row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .sp__ctl { display: inline-flex; align-items: center; gap: 6px; font-size: var(--type-fine-size); color: var(--text-subtle); }
    select { max-width: 200px; padding: 5px 8px; font: inherit; font-size: var(--type-caption-size); color: var(--text); background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-md); &:focus-visible { outline: 2px solid var(--accent-focus); } }
    .sp__meter { height: 4px; overflow: hidden; background: var(--surface-sunken); border-radius: var(--radius-full); span { display: block; height: 100%; background: var(--accent); transition: width var(--duration-base) var(--ease-standard); } }
    .sp__now { margin: 0; font-size: var(--type-caption-size); line-height: 1.5; color: var(--text-muted); }
    .sp__note { display: flex; gap: 6px; margin: 0; font-size: var(--type-caption-size); color: var(--text-subtle); }
  `,
})
export class SpeechPlayerComponent {
  protected readonly speech = inject(SpeechService);
  private readonly i18n = inject(TranslationService);

  /** What to read, already free of Markdown symbols. */
  readonly text = input.required<string>();

  readonly rates = RATES;
  readonly rate = signal(Number(stored(RATE_KEY)) || 1);
  readonly voice = signal<string | null>(stored(VOICE_KEY));
  readonly voices = computed(() => this.speech.voicesFor(this.i18n.locale()));
  private readonly started = signal(false);
  /** True only while this player's own reading is on; another player's reading does not light up these controls. */
  readonly mine = computed(() => this.started() && this.speech.state() !== 'idle');
  readonly percent = computed(() => Math.round(this.speech.progress() * 100));

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.started()) {
        this.speech.stop();
      }
    });
  }

  play(from = 0): void {
    this.started.set(true);
    this.speech.speak(this.text(), { language: this.i18n.locale(), rate: this.rate(), voice: this.voice(), from });
  }

  stop(): void {
    this.speech.stop();
    this.started.set(false);
  }

  setRate(rate: number): void {
    this.rate.set(rate);
    store(RATE_KEY, String(rate));
    if (this.mine()) {
      this.play(this.speech.index()); // carries on from the piece being read, with the new setting
    }
  }

  setVoice(uri: string): void {
    this.voice.set(uri || null);
    store(VOICE_KEY, uri);
    if (this.mine()) {
      this.play(this.speech.index());
    }
  }
}
