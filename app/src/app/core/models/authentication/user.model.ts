import { AuthenticationProvider } from './enums';

export interface User {
  readonly id: string;
  readonly firstName: string;
  readonly lastName: string;
  readonly fullName: string;
  readonly email: string;
  readonly photoUrl: string | null;
  readonly active: boolean;
  readonly emailVerified: boolean;
  readonly authenticationProvider: AuthenticationProvider;
  readonly hasPassword: boolean;
  readonly googleLinked: boolean;
  readonly googleEmail: string | null;
  readonly lastLoginAt: string | null;
  readonly roles: string[];
  readonly permissions: string[];
  readonly directPermissions: string[];
  readonly theme: string;
  readonly themeMode: string;
  readonly locale: string;
  readonly timezone: string;
  /** Module slugs kept off the home screen. Everything not listed here shows. */
  readonly hiddenModules: string[];
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface UserRef {
  readonly id: string;
  readonly fullName: string;
  readonly email?: string;
  readonly photoUrl?: string | null;
}
