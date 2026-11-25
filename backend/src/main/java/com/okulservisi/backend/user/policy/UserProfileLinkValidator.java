package com.okulservisi.backend.user.policy;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;

import com.okulservisi.backend.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Parent/Driver/Hostess gibi User'a 1-1 bağlı profil domain'lerinin create() akışında
 * tekrarlanan "userId var mı + rolü doğru mu" doğrulamasını merkezileştirir.
 * Domain'e özgü "zaten var mı" (existsByUserId) kontrolü burada DEĞİL, çağıran serviste kalır.
 */
@Component
@RequiredArgsConstructor
public class UserProfileLinkValidator {

    private final UserRepository userRepository;

    public User validateUserForNewProfile(Long userId, Role requiredRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Belirtilen userId'ye sahip kullanıcı bulunamadı."));
        if (user.getRole() != requiredRole) {
            throw new BadRequestException("Kullanıcının rolü " + requiredRole + " olmalıdır.");
        }
        return user;
    }
}
