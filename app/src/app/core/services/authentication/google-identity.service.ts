import { Injectable } from '@angular/core';
import { environment } from '@env/environment';

export type GoogleButtonText = 'signin_with' | 'signup_with' | 'continue_with';

interface GoogleCredentialResponse {
  credential: string;
}

interface GoogleIdConfig {
  client_id: string;
  callback: (response: GoogleCredentialResponse) => void;
  ux_mode?: 'popup' | 'redirect';
}

interface GoogleButtonConfig {
  type?: 'standard' | 'icon';
  theme?: 'outline' | 'filled_blue' | 'filled_black';
  size?: 'large' | 'medium' | 'small';
  shape?: 'rectangular' | 'pill' | 'circle' | 'square';
  text?: GoogleButtonText;
  logo_alignment?: 'left' | 'center';
  width?: number;
}

interface GoogleAccountsId {
  initialize(config: GoogleIdConfig): void;
  renderButton(parent: HTMLElement, options: GoogleButtonConfig): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } };
  }
}

const SCRIPT_SRC = 'https://accounts.google.com/gsi/client';
const NATIVE_BUTTON_HEIGHT = 40;
const MAX_NATIVE_WIDTH = 400;

@Injectable({ providedIn: 'root' })
export class GoogleIdentityService {
  private scriptPromise: Promise<void> | null = null;

  private loadScript(): Promise<void> {
    if (this.scriptPromise) {
      return this.scriptPromise;
    }
    this.scriptPromise = new Promise<void>((resolve, reject) => {
      if (window.google?.accounts?.id) {
        resolve();
        return;
      }
      const script = document.createElement('script');
      script.src = SCRIPT_SRC;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Failed to load Google Identity Services'));
      document.head.appendChild(script);
    });
    return this.scriptPromise;
  }

  async renderButton(
    container: HTMLElement,
    onCredential: (idToken: string) => void,
    options: { text?: GoogleButtonText; width?: number; height?: number } = {},
  ): Promise<void> {
    await this.loadScript();
    const google = window.google;
    if (!google) {
      throw new Error('Google Identity Services did not initialise');
    }
    google.accounts.id.initialize({
      client_id: environment.googleClientId,
      ux_mode: 'popup',
      callback: (response) => onCredential(response.credential),
    });

    const targetHeight = options.height ?? NATIVE_BUTTON_HEIGHT;
    const scale = targetHeight / NATIVE_BUTTON_HEIGHT;
    const targetWidth = options.width ?? 360;
    const nativeWidth = Math.min(MAX_NATIVE_WIDTH, Math.round(targetWidth / scale));

    container.innerHTML = '';
    container.style.display = 'flex';
    container.style.alignItems = 'center';
    container.style.justifyContent = 'center';
    container.style.height = `${targetHeight}px`;

    const scaledWrapper = document.createElement('div');
    scaledWrapper.style.transform = `scale(${scale})`;
    container.appendChild(scaledWrapper);

    google.accounts.id.renderButton(scaledWrapper, {
      type: 'standard',
      theme: 'filled_black',
      size: 'large',
      shape: 'rectangular',
      logo_alignment: 'left',
      width: nativeWidth,
      text: options.text ?? 'continue_with',
    });
  }
}
