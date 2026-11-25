package com.okulservisi.backend.auth.controller;

import com.okulservisi.backend.auth.api.AuthApiPaths;
import com.okulservisi.backend.auth.dto.request.ChangePasswordRequest;
import com.okulservisi.backend.auth.dto.request.ForgotPasswordRequest;
import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.dto.request.LogoutRequest;
import com.okulservisi.backend.auth.dto.response.MeResponse;
import com.okulservisi.backend.auth.dto.request.RefreshRequest;
import com.okulservisi.backend.auth.dto.response.RefreshResponse;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestCreateRequest;
import com.okulservisi.backend.auth.dto.request.ResetPasswordRequest;
import com.okulservisi.backend.auth.dto.request.SocialLinkRequest;
import com.okulservisi.backend.auth.dto.request.SocialLoginRequest;
import com.okulservisi.backend.auth.dto.response.SocialLoginResponse;
import com.okulservisi.backend.auth.dto.request.VerifyEmailRequest;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.auth.service.AuthService;
import com.okulservisi.backend.auth.service.PasswordManagementService;
import com.okulservisi.backend.auth.service.RegistrationRequestService;
import com.okulservisi.backend.auth.service.SocialAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "1. Auth" bölümüyle tam uyumlu. */
@RestController
@RequestMapping(AuthApiPaths.AUTH_BASE)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordManagementService passwordManagementService;
    private final RegistrationRequestService registrationRequestService;
    private final SocialAuthService socialAuthService;

    @PostMapping(AuthApiPaths.LOGIN)
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, clientIp(httpRequest)));
    }

    @PostMapping(AuthApiPaths.REFRESH)
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping(AuthApiPaths.LOGOUT)
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(principal, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(AuthApiPaths.ME)
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(authService.me(principal));
    }

    @PostMapping(AuthApiPaths.CHANGE_PASSWORD)
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        passwordManagementService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(AuthApiPaths.FORGOT_PASSWORD)
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        passwordManagementService.forgotPassword(request, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping(AuthApiPaths.RESET_PASSWORD)
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        passwordManagementService.resetPassword(request, clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(AuthApiPaths.REGISTER_REQUESTS)
    public ResponseEntity<Void> createRegistrationRequest(
            @Valid @RequestBody RegistrationRequestCreateRequest request, HttpServletRequest httpRequest) {
        registrationRequestService.createRequest(request, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping(AuthApiPaths.REGISTER_REQUESTS_VERIFY_EMAIL)
    public ResponseEntity<Void> verifyRegistrationEmail(@Valid @RequestBody VerifyEmailRequest request,
            HttpServletRequest httpRequest) {
        registrationRequestService.verifyEmail(request.token(), clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(AuthApiPaths.SOCIAL_LOGIN)
    public ResponseEntity<SocialLoginResponse> socialLogin(@Valid @RequestBody SocialLoginRequest request,
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String internalServiceKey) {
        return ResponseEntity.ok(socialAuthService.socialLogin(request, internalServiceKey));
    }

    @PostMapping(AuthApiPaths.SOCIAL_LINK)
    public ResponseEntity<Void> socialLink(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody SocialLinkRequest request) {
        socialAuthService.socialLink(principal, request);
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
