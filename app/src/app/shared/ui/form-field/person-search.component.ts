import { ChangeDetectionStrategy, Component, forwardRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';
import { PageResponse } from '@core/models/api.model';
import { ApiService } from '@core/services/authentication/api.service';
import { AvatarComponent } from '../avatar/avatar.component';
import { IconComponent } from '../icon/icon.component';

interface Person {
  readonly id: string;
  readonly email: string;
  readonly fullName: string;
  readonly photoUrl: string | null;
}

const PAGE = 8;

/**
 * Picks a person out of the directory: the text matches name or email, the list shows a page of matches and brings the
 * next one in as it is scrolled to. The value is the email of who was picked.
 */
@Component({
  selector: 'app-person-search',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [AvatarComponent, IconComponent],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => PersonSearchComponent), multi: true }],
  template: `
    @if (chosen(); as person) {
      <div class="ps__chosen">
        <app-avatar [name]="person.fullName || person.email" [photoUrl]="person.photoUrl" [size]="30" />
        <span class="ps__line">
          <span class="ps__name">{{ person.fullName || person.email }}</span>
          @if (person.fullName) {
            <span class="ps__mail">{{ person.email }}</span>
          }
        </span>
        <button type="button" class="ps__clear" [attr.aria-label]="clearLabel()" [title]="clearLabel()" (click)="clear()">
          <app-icon name="x" [size]="14" />
        </button>
      </div>
    } @else {
      <div class="ps__box" [class.ps__box--invalid]="invalid()">
        <app-icon name="search" [size]="15" class="ps__icon" />
        <input
          class="ps__input"
          type="text"
          autocomplete="off"
          role="combobox"
          [attr.aria-expanded]="open()"
          [placeholder]="placeholder()"
          [value]="text()"
          (input)="onInput($any($event.target).value)"
          (focus)="open.set(true)"
          (blur)="onBlur()"
        />
      </div>
      @if (open() && text().trim().length >= 2) {
        <ul class="ps__list" role="listbox" (scroll)="onScroll($event)">
          @for (p of results(); track p.id) {
            <li>
              <button type="button" class="ps__option" role="option" (mousedown)="$event.preventDefault()" (click)="pick(p)">
                <app-avatar [name]="p.fullName || p.email" [photoUrl]="p.photoUrl" [size]="26" />
                <span class="ps__who">
                  <span class="ps__name">{{ p.fullName || p.email }}</span>
                  @if (p.fullName) {
                    <span class="ps__mail">{{ p.email }}</span>
                  }
                </span>
              </button>
            </li>
          } @empty {
            <li class="ps__empty">{{ loading() ? '…' : emptyText() }}</li>
          }
          @if (loading() && results().length > 0) {
            <li class="ps__empty">…</li>
          }
        </ul>
      }
    }
  `,
  styles: `
    :host { display: block; }
    .ps__box, .ps__chosen {
      position: relative; display: flex; align-items: center; height: 44px; background: var(--surface);
      border-radius: var(--radius-md); box-shadow: inset 0 0 0 1px var(--border);
    }
    .ps__box--invalid { box-shadow: inset 0 0 0 1px #d92d20; }
    .ps__box:focus-within { box-shadow: inset 0 0 0 1.5px var(--accent-focus), 0 0 0 3px color-mix(in srgb, var(--accent-focus) 15%, transparent); }
    .ps__icon { position: absolute; left: 14px; color: var(--text-subtle); pointer-events: none; }
    .ps__input { width: 100%; height: 100%; padding: 0 16px 0 38px; font: inherit; font-size: var(--type-body-size); color: var(--text); background: transparent; border: 0; outline: none; }
    .ps__chosen { gap: 12px; padding: 0 8px 0 7px; background: var(--surface-sunken); }
    .ps__line { display: flex; align-items: baseline; gap: 10px; min-width: 0; flex: 1; }
    .ps__line .ps__name { flex: none; max-width: 60%; font-size: var(--type-body-size); }
    .ps__line .ps__mail { min-width: 0; font-size: var(--type-caption-size); }
    .ps__who { display: flex; flex-direction: column; min-width: 0; flex: 1; text-align: left; }
    .ps__name { font-size: var(--type-caption-size); font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ps__mail { font-size: 12px; color: var(--text-subtle); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ps__clear { display: grid; place-items: center; flex: none; width: 28px; height: 28px; color: var(--text-subtle); background: transparent; border: 0; border-radius: var(--radius-sm); cursor: pointer; &:hover { color: var(--text); background: var(--surface-muted); } }
    .ps__list { margin: 6px 0 0; padding: 4px; max-height: 240px; overflow-y: auto; list-style: none; background: var(--surface); border-radius: var(--radius-md); box-shadow: inset 0 0 0 1px var(--border); }
    .ps__option { display: flex; align-items: center; gap: 10px; width: 100%; padding: 8px 12px; font: inherit; background: transparent; border: 0; border-radius: var(--radius-sm); cursor: pointer; &:hover, &:focus-visible { background: var(--surface-muted); outline: none; } }
    .ps__empty { padding: 10px 12px; font-size: var(--type-caption-size); color: var(--text-subtle); }
  `,
})
export class PersonSearchComponent implements ControlValueAccessor {
  readonly placeholder = input('');
  readonly emptyText = input('');
  readonly clearLabel = input('Clear');
  readonly invalid = input(false);

  private readonly api = inject(ApiService);

  protected readonly chosen = signal<Person | null>(null);
  protected readonly text = signal('');
  protected readonly open = signal(false);
  protected readonly loading = signal(false);
  protected readonly results = signal<Person[]>([]);

  private page = 0;
  private last = true;
  private readonly queries = new Subject<string>();
  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  constructor() {
    this.queries
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        tap(() => {
          this.page = 0;
          this.last = true;
          this.results.set([]);
          this.loading.set(true);
        }),
        switchMap((text) => this.fetch(text, 0)),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.apply(page, true));
  }

  protected onInput(text: string): void {
    this.text.set(text);
    this.open.set(true);
    if (text.trim().length >= 2) this.queries.next(text.trim());
    else {
      this.results.set([]);
      this.queries.next('');
    }
  }

  /** The next page, once the list has been scrolled almost to its end. */
  protected onScroll(event: Event): void {
    const list = event.target as HTMLElement;
    if (this.loading() || this.last || list.scrollTop + list.clientHeight < list.scrollHeight - 24) return;
    this.loading.set(true);
    this.fetch(this.text().trim(), this.page + 1).subscribe((page) => this.apply(page, false));
  }

  protected pick(person: Person): void {
    this.chosen.set(person);
    this.open.set(false);
    this.text.set('');
    this.onChange(person.email);
  }

  protected clear(): void {
    this.chosen.set(null);
    this.onChange('');
  }

  protected onBlur(): void {
    this.open.set(false);
    this.onTouched();
  }

  private fetch(text: string, page: number) {
    if (text.length < 2) return of(null);
    return this.api
      .getPage<Person>('/api/v1/users/lookup/search', { q: text, page, size: PAGE })
      .pipe(catchError(() => of(null)));
  }

  private apply(page: PageResponse<Person> | null, fresh: boolean): void {
    this.loading.set(false);
    if (!page) return;
    this.page = page.page;
    this.last = page.last;
    this.results.update((list) => (fresh ? page.content : [...list, ...page.content]));
  }

  writeValue(value: string | null): void {
    if (!value) this.chosen.set(null);
    else if (this.chosen()?.email !== value) this.chosen.set({ id: '', email: value, fullName: '', photoUrl: null });
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
}
