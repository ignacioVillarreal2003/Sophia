import { HttpContext, HttpContextToken } from '@angular/common/http';

export const SKIP_AUTH = new HttpContextToken<boolean>(() => false);

export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);

export const RETRY_COUNT = new HttpContextToken<number>(() => 2);

export function skipAuth(ctx = new HttpContext()): HttpContext {
  return ctx.set(SKIP_AUTH, true);
}

export function silentErrors(ctx = new HttpContext()): HttpContext {
  return ctx.set(SKIP_ERROR_TOAST, true);
}
