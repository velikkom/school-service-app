package com.okulservisi.backend.auth.service;

import com.okulservisi.backend.auth.dto.request.SocialLinkRequest;
import com.okulservisi.backend.auth.dto.request.SocialLoginRequest;
import com.okulservisi.backend.auth.dto.response.SocialLoginResponse;
import com.okulservisi.backend.auth.security.AuthenticatedUser;

/**
 * Sosyal giriş (Google/Facebook/X) backend tarafı — docs/SECURITY.md "Sosyal giriş" bölümündeki
 * 3 senaryonun TAM uygulaması.
 */
public interface SocialAuthService {

    SocialLoginResponse socialLogin(SocialLoginRequest request, String internalCallerKey);

    void socialLink(AuthenticatedUser principal, SocialLinkRequest request);
}
