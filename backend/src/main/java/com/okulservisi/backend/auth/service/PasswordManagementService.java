package com.okulservisi.backend.auth.service;

import com.okulservisi.backend.auth.dto.request.ChangePasswordRequest;
import com.okulservisi.backend.auth.dto.request.ForgotPasswordRequest;
import com.okulservisi.backend.auth.dto.request.ResetPasswordRequest;
import com.okulservisi.backend.auth.security.AuthenticatedUser;

/**
 * Parola yönetimi: changePassword/forgotPassword/resetPassword. Oturum/token yaşam döngüsü
 * (login/refresh/logout/me) {@link AuthService}'te — iki bağımsız sorumluluk, ayrı sınıf.
 */
public interface PasswordManagementService {

    void changePassword(AuthenticatedUser principal, ChangePasswordRequest request);

    void forgotPassword(ForgotPasswordRequest request, String clientIp);

    void resetPassword(ResetPasswordRequest request, String clientIp);
}
