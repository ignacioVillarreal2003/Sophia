import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';

/**
 * Turns a workspace's authenticated logo endpoint into an object URL an `<img>` can show
 * (a plain `src` cannot carry the bearer token). Results are cached per logo URL; the
 * URL carries a version, so uploading a new logo naturally yields a new cache entry.
 */
@Injectable({ providedIn: 'root' })
export class WorkspaceLogoService {
  private readonly http = inject(HttpClient);
  private readonly cache = new Map<string, Observable<string | null>>();

  resolve(logoUrl: string): Observable<string | null> {
    let entry = this.cache.get(logoUrl);
    if (!entry) {
      entry = this.http.get(`${environment.documentWorkspaceApiUrl}${logoUrl}`, { responseType: 'blob' }).pipe(
        map((blob) => URL.createObjectURL(blob)),
        catchError(() => of(null)),
        shareReplay(1),
      );
      this.cache.set(logoUrl, entry);
    }
    return entry;
  }
}
