package com.okulservisi.backend.user.api;

/** docs/API.md "2. Kullanıcı yönetimi" — {@code UserController} mapping sabitleri. */
public final class UserApiPaths {

    private UserApiPaths() {
    }

    public static final String BASE = "/api/users";
    public static final String BY_ID = "/{id}";
    public static final String CHANGE_ROLE = "/{id}/role";
    public static final String DEACTIVATE = "/{id}/deactivate";
    public static final String ACTIVATE = "/{id}/activate";
}
