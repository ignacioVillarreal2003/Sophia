import { Injectable, NgZone, computed, inject, signal } from '@angular/core';
import { splitForSpeech } from '../lib/speech';

export type SpeechState = 'idle' | 'speaking' | 'paused';

/**
 * Reads text aloud with the browser's own voices (the Web Speech API): free, instant and private, since nothing
 * leaves the device. The text is spoken as a queue of short pieces so engines that give up on long utterances keep
 * going, and the piece being read is exposed so the screen can follow along.
 */
@Injectable({ providedIn: 'root' })
export class SpeechService {
  private readonly zone = inject(NgZone);
  private readonly synth: SpeechSynthesis | null = typeof window !== 'undefined' && 'speechSynthesis' in window ? window.speechSynthesis : null;

  readonly supported = this.synth !== null;
  readonly state = signal<SpeechState>('idle');
  readonly voices = signal<SpeechSynthesisVoice[]>([]);
  readonly pieces = signal<string[]>([]);
  readonly index = signal(0);
  readonly current = computed(() => this.pieces()[this.index()] ?? '');
  readonly progress = computed(() => (this.pieces().length ? Math.min(1, this.index() / this.pieces().length) : 0));

  /** Bumped on every start and stop, so a callback from an utterance that was cancelled cannot move the new reading. */
  private generation = 0;

  constructor() {
    if (this.synth) {
      this.loadVoices();
      this.synth.addEventListener?.('voiceschanged', () => this.zone.run(() => this.loadVoices()));
    }
  }

  private loadVoices(): void {
    this.voices.set(this.synth?.getVoices() ?? []);
  }

  /** Voices for a language ("es" matches es-AR, es-ES...), the best match first. */
  voicesFor(language: string): SpeechSynthesisVoice[] {
    const base = language.toLowerCase().split('-')[0];
    const all = this.voices();
    const match = all.filter((v) => v.lang.toLowerCase().startsWith(base));
    return match.length > 0 ? match : all;
  }

  speak(text: string, options: { language: string; rate: number; voice?: string | null; from?: number }): void {
    if (!this.synth) {
      return;
    }
    this.stop();
    const pieces = splitForSpeech(text);
    if (pieces.length === 0) {
      return;
    }
    const generation = ++this.generation;
    const from = Math.min(Math.max(0, options.from ?? 0), pieces.length - 1);
    this.pieces.set(pieces);
    this.index.set(from);
    this.state.set('speaking');
    const voice = options.voice ? this.voices().find((v) => v.voiceURI === options.voice) : undefined;

    const next = (i: number): void => {
      if (generation !== this.generation) {
        return;
      }
      if (i >= pieces.length) {
        this.finish();
        return;
      }
      const utterance = new SpeechSynthesisUtterance(pieces[i]);
      utterance.lang = voice?.lang ?? options.language;
      utterance.rate = options.rate;
      if (voice) {
        utterance.voice = voice;
      }
      utterance.onstart = () => this.zone.run(() => generation === this.generation && this.index.set(i));
      utterance.onend = () => this.zone.run(() => next(i + 1));
      utterance.onerror = (event) => {
        // "canceled" and "interrupted" are what stop() causes; anything else ends the reading.
        if (event.error !== 'canceled' && event.error !== 'interrupted') {
          this.zone.run(() => generation === this.generation && this.finish());
        }
      };
      this.synth!.speak(utterance);
    };
    next(from);
  }

  pause(): void {
    if (this.state() === 'speaking') {
      this.synth?.pause();
      this.state.set('paused');
    }
  }

  resume(): void {
    if (this.state() === 'paused') {
      this.synth?.resume();
      this.state.set('speaking');
    }
  }

  stop(): void {
    this.generation++;
    this.synth?.cancel();
    this.finish();
  }

  private finish(): void {
    this.state.set('idle');
    this.pieces.set([]);
    this.index.set(0);
  }
}
