type Translate = (key: string, params?: Record<string, string | number>) => string;

export function deviceLabel(userAgent: string | null | undefined, translate: Translate): string {
  if (!userAgent) return translate('device.unknown');

  let browser = translate('device.unknownBrowser');
  if (/Edg\//.test(userAgent)) browser = 'Edge';
  else if (/OPR\//.test(userAgent)) browser = 'Opera';
  else if (/Chrome\//.test(userAgent)) browser = 'Chrome';
  else if (/Firefox\//.test(userAgent)) browser = 'Firefox';
  else if (/Safari\//.test(userAgent)) browser = 'Safari';

  let os = '';
  if (/Windows/.test(userAgent)) os = 'Windows';
  else if (/Mac OS X/.test(userAgent)) os = 'Mac';
  else if (/Android/.test(userAgent)) os = 'Android';
  else if (/iPhone|iPad/.test(userAgent)) os = 'iOS';
  else if (/Linux/.test(userAgent)) os = 'Linux';

  return os ? translate('device.browserOnOs', { browser, os }) : browser;
}
