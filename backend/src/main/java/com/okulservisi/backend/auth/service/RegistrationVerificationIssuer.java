package com.okulservisi.backend.auth.service;

import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.entity.RegistrationVerificationToken;
import com.okulservisi.backend.auth.repository.RegistrationVerificationTokenRepository;
import com.okulservisi.backend.auth.security.OpaqueTokenGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Doğrulama token'ı üretme + e-posta gönderme: {@link RegistrationRequestService} (public kayıt
 * talebi) ve {@link SocialAuthService} (sosyal giriş, provider e-postayı doğrulamadığında) AYNI
 * mantığı paylaşır — tek yerde toplanmıştır.
 */
@Component
@RequiredArgsConstructor
public class RegistrationVerificationIssuer {

    private final RegistrationVerificationTokenRepository registrationVerificationTokenRepository;
    private final OpaqueTokenGenerator opaqueTokenGenerator;
    private final RegistrationMailService registrationMailService;

    public void issueAndSend(RegistrationRequest registrationRequest) {
        String rawToken = opaqueTokenGenerator.generate();
        RegistrationVerificationToken verificationToken = new RegistrationVerificationToken();
        verificationToken.setRegistrationRequest(registrationRequest);
        verificationToken.setTokenHash(opaqueTokenGenerator.hash(rawToken));
        verificationToken.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        registrationVerificationTokenRepository.save(verificationToken);
        registrationMailService.sendVerificationEmail(registrationRequest.getEmail(), rawToken);
    }
}
