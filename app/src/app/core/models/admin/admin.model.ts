export const ADMIN_PERMISSIONS = [
  'USER_READ',
  'ROLE_READ',
  'PERMISSION_READ',
  'AUDIT_READ',
  'SERVICE_CLIENT_MANAGE',
  'NOTIFICATION_CATEGORY_READ',
  'NOTIFICATION_AUDIT_READ',
  'NOTIFICATION_DEAD_LETTER_REPLAY',
  'WORKSPACE_MANAGE',
] as const;

export interface UserCreateRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  roles?: string[];
  active?: boolean;
  emailVerified?: boolean;
}

export interface UserUpdateRequest {
  firstName: string;
  lastName: string;
  email: string;
  active: boolean;
  theme?: string;
  themeMode?: string;
  locale?: string;
  timezone?: string;
}

export interface UserPatchRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  active?: boolean;
  theme?: string;
  themeMode?: string;
  locale?: string;
  timezone?: string;
}

export interface UpdateUserRolesRequest {
  roles: string[];
}

export interface UpdateUserPermissionsRequest {
  permissions: string[];
}

export interface UpdateUserStatusRequest {
  active: boolean;
}

export interface UserListQuery {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
  active?: boolean;
  emailVerified?: boolean;
  role?: string;
  [key: string]: string | number | boolean | undefined;
}

export interface Role {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly system: boolean;
  readonly permissions: string[];
  readonly createdAt: string;
  readonly updatedAt: string | null;
}

export interface RoleRequest {
  name: string;
  description?: string;
  permissions?: string[];
}

export interface RoleListQuery {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
  [key: string]: string | number | undefined;
}

export interface Permission {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly category: string | null;
  readonly createdAt: string;
  readonly updatedAt: string | null;
}

export interface PermissionRequest {
  name: string;
  description?: string;
  category?: string;
}

export interface PermissionListQuery {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
  [key: string]: string | number | undefined;
}

export interface ServiceClient {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  readonly scopes: string[];
  readonly active: boolean;
  readonly lastUsedAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string | null;
  readonly clientSecret: string | null;
}

export interface ServiceClientRequest {
  name: string;
  description?: string;
  scopes: string[];
  active?: boolean;
}

export interface ServiceClientListQuery {
  page?: number;
  size?: number;
  sort?: string;
  search?: string;
  [key: string]: string | number | undefined;
}
