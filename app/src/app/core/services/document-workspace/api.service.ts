import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '@env/environment';
import { Observable, map } from 'rxjs';
import { ApiResponse, PageQuery, PageResponse } from '@core/models/api.model';

type ParamValue = string | number | boolean | undefined | null;

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.documentWorkspaceApiUrl;

  get<T>(path: string, params?: Record<string, ParamValue>, context?: HttpContext): Observable<T> {
    return this.http
      .get<ApiResponse<T>>(this.url(path), { params: this.params(params), context })
      .pipe(map((r) => r.data));
  }

  getPage<T>(path: string, query?: PageQuery, context?: HttpContext): Observable<PageResponse<T>> {
    return this.http
      .get<ApiResponse<PageResponse<T>>>(this.url(path), { params: this.params(query), context })
      .pipe(map((r) => r.data));
  }

  post<T>(path: string, body?: unknown, context?: HttpContext): Observable<T> {
    return this.http
      .post<ApiResponse<T>>(this.url(path), body ?? {}, { context })
      .pipe(map((r) => r.data));
  }

  put<T>(path: string, body?: unknown, context?: HttpContext): Observable<T> {
    return this.http
      .put<ApiResponse<T>>(this.url(path), body ?? {}, { context })
      .pipe(map((r) => r.data));
  }

  patch<T>(path: string, body?: unknown, context?: HttpContext): Observable<T> {
    return this.http
      .patch<ApiResponse<T>>(this.url(path), body ?? {}, { context })
      .pipe(map((r) => r.data));
  }

  delete<T>(path: string, context?: HttpContext): Observable<T> {
    return this.http
      .delete<ApiResponse<T>>(this.url(path), { context })
      .pipe(map((r) => r.data));
  }

  private url(path: string): string {
    return path.startsWith('http') ? path : `${this.base}${path}`;
  }

  private params(source?: Record<string, ParamValue>): HttpParams {
    let params = new HttpParams();
    if (!source) {
      return params;
    }
    for (const [key, value] of Object.entries(source)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return params;
  }
}
