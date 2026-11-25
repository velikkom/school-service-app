package com.okulservisi.backend.auth.api;

/**
 * docs/API.md "1. Auth" ve "§18 Kayıt talebi" — auth domain'indeki iki controller'ın
 * ({@code AuthController}, {@code RegistrationRequestController}) mapping sabitleri. İki ayrı taban
 * yol olduğu için sabitler {@code AUTH_}/{@code REGISTRATION_REQUESTS_} önekiyle ayrılır.
 */
public final class AuthApiPaths {

    private AuthApiPaths() {
    }

    // --- AuthController: /api/auth ---
    public static final String AUTH_BASE = "/api/auth";
    public static final String LOGIN = "/login";
    public static final String REFRESH = "/refresh";
    public static final String LOGOUT = "/logout";
    public static final String ME = "/me";
    public static final String CHANGE_PASSWORD = "/change-password";
    public static final String FORGOT_PASSWORD = "/forgot-password";
    public static final String RESET_PASSWORD = "/reset-password";
    public static final String REGISTER_REQUESTS = "/register-requests";
    public static final String REGISTER_REQUESTS_VERIFY_EMAIL = "/register-requests/verify-email";
    public static final String SOCIAL_LOGIN = "/social-login";
    public static final String SOCIAL_LINK = "/social-link";

    // --- RegistrationRequestController: /api/registration-requests ---
    public static final String REGISTRATION_REQUESTS_BASE = "/api/registration-requests";
    public static final String REGISTRATION_REQUEST_BY_ID = "/{id}";
    public static final String REGISTRATION_REQUEST_APPROVE = "/{id}/approve";
    public static final String REGISTRATION_REQUEST_REJECT = "/{id}/reject";
}
