/** Error codes the AI service answers with that have their own wording (`ai.error.<CODE>` in the translations). */
const KNOWN_CODES = new Set([
  'AI_DISABLED',
  'AI_NOT_CONFIGURED',
  'QUOTA_EXCEEDED',
  'TOO_MANY_CONCURRENT',
  'RATE_LIMITED',
  'UNSUPPORTED_FILE',
  'FILE_TOO_LARGE',
  'SOURCE_NOT_SUPPORTED',
  'SOURCE_TOO_LARGE',
  'SOURCE_UNAVAILABLE',
  'GENERATION_REFUSED',
  'GENERATION_FAILED',
  'UPSTREAM_ERROR',
  'ACCESS_DENIED',
  'RESOURCE_NOT_FOUND',
  'STREAM_INTERRUPTED',
  'NETWORK',
]);

/**
 * What to tell the person. Codes with their own translation use it; a validation or conflict message from the server
 * is already written for people, so it is shown as it is; anything else gets the generic text.
 */
export function aiErrorMessage(
  error: { code?: string; message?: string; status?: number } | null | undefined,
  translate: (key: string) => string,
): string {
  const code = error?.code ?? '';
  if (KNOWN_CODES.has(code)) {
    return translate(`ai.error.${code}`);
  }
  if (error?.message && (error.status === 400 || error.status === 409 || error.status === 422)) {
    return error.message;
  }
  return translate('ai.error.GENERATION_FAILED');
}
