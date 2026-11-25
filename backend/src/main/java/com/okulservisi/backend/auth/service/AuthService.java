package com.okulservisi.backend.auth.service;

import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.request.LogoutRequest;
import com.okulservisi.backend.auth.dto.request.RefreshRequest;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.response.MeResponse;
import com.okulservisi.backend.auth.dto.response.RefreshResponse;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.user.entity.User;

/**
 * Oturum/token yaşam döngüsü: login/refresh/logout/me. Parola yönetimi (changePassword/
 * forgotPassword/resetPassword) {@code PasswordManagementService}'te — iki bağımsız sorumluluk,
 * ayrı sınıf.
 */
public interface AuthService {

    LoginResponse login(LoginRequest request, String clientIp);

    RefreshResponse refresh(RefreshRequest request);

    void logout(AuthenticatedUser principal, LogoutRequest request);

    MeResponse me(AuthenticatedUser principal);

    /**
     * {@code SocialAuthService} tarafından, zaten bağlı bir {@code oauth_identity} için normal
     * {@code login()} ile AYNI token üretimini tekrar etmeden kullanır.
     */
    LoginResponse issueTokensForUser(User user);
}
