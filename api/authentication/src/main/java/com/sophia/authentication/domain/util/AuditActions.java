package com.sophia.authentication.domain.util;

public final class AuditActions {

    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGIN_GOOGLE = "LOGIN_GOOGLE";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    public static final String LOGOUT = "LOGOUT";
    public static final String LOGOUT_ALL = "LOGOUT_ALL";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String VERIFICATION_RESENT = "VERIFICATION_RESENT";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String EMAIL_CHANGE_REQUESTED = "EMAIL_CHANGE_REQUESTED";
    public static final String EMAIL_CHANGED = "EMAIL_CHANGED";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String GOOGLE_LINKED = "GOOGLE_LINKED";
    public static final String GOOGLE_UNLINKED = "GOOGLE_UNLINKED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_ROLES_UPDATED = "USER_ROLES_UPDATED";
    public static final String USER_PERMISSIONS_UPDATED = "USER_PERMISSIONS_UPDATED";
    public static final String USER_STATUS_UPDATED = "USER_STATUS_UPDATED";
    public static final String USER_PHOTO_UPDATED = "USER_PHOTO_UPDATED";
    public static final String USER_PASSWORD_RESET_BY_ADMIN = "USER_PASSWORD_RESET_BY_ADMIN";
    public static final String EMAIL_UNVERIFIED_BY_ADMIN = "EMAIL_UNVERIFIED_BY_ADMIN";
    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    public static final String ROLE_DELETED = "ROLE_DELETED";
    public static final String PERMISSION_CREATED = "PERMISSION_CREATED";
    public static final String PERMISSION_UPDATED = "PERMISSION_UPDATED";
    public static final String PERMISSION_DELETED = "PERMISSION_DELETED";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
    public static final String SESSIONS_REVOKED_ALL = "SESSIONS_REVOKED_ALL";
    public static final String SERVICE_CLIENT_CREATED = "SERVICE_CLIENT_CREATED";
    public static final String SERVICE_CLIENT_UPDATED = "SERVICE_CLIENT_UPDATED";
    public static final String SERVICE_CLIENT_DELETED = "SERVICE_CLIENT_DELETED";
    public static final String SERVICE_CLIENT_SECRET_ROTATED = "SERVICE_CLIENT_SECRET_ROTATED";
    public static final String SERVICE_TOKEN_ISSUED = "SERVICE_TOKEN_ISSUED";

    private AuditActions() {
    }
}
