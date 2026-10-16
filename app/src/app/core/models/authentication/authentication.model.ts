import { User } from './user.model';

export const EMAIL_NOT_VERIFIED_MESSAGE = 'Email address has not been verified';

export interface LoginRequest {
  email: string;
  password: string;
  rememberMe?: boolean;
  deviceName?: string;
}

export interface RegisterRequest {
  firstName?: string;
  lastName?: string;
  email: string;
  password: string;
}

export interface GoogleLoginRequest {
  idToken: string;
  rememberMe?: boolean;
  deviceName?: string;
}

export interface AuthResponse {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly tokenType: string;
  readonly expiresIn: number;
  readonly user: User;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResendVerificationRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface VerifyEmailRequest {
  token: string;
}

export interface ChangeEmailRequest {
  newEmail: string;
  password: string;
}

export interface ConfirmEmailChangeRequest {
  token: string;
}

export interface DeleteAccountRequest {
  password?: string;
}

export interface GoogleLinkRequest {
  idToken: string;
}

export interface Session {
  readonly id: string;
  readonly ipAddress: string | null;
  readonly userAgent: string | null;
  readonly deviceName: string | null;
  readonly rememberMe: boolean;
  readonly lastActiveAt: string | null;
  readonly createdAt: string;
  readonly expiresAt: string;
  readonly current: boolean;
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
}
