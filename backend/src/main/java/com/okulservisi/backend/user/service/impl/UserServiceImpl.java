package com.okulservisi.backend.user.service.impl;

import com.okulservisi.backend.auth.service.TokenRevocationService;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.user.dto.request.UserCreateRequest;
import com.okulservisi.backend.user.dto.request.UserRoleChangeRequest;
import com.okulservisi.backend.user.dto.request.UserUpdateRequest;
import com.okulservisi.backend.user.dto.response.UserCreateResponse;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.mapper.UserMapper;
import com.okulservisi.backend.user.repository.UserRepository;
import com.okulservisi.backend.user.repository.UserSpecifications;
import com.okulservisi.backend.user.service.UserService;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "2. Kullanıcı yönetimi" — ADMIN-only kullanıcı CRUD'u. Son aktif ADMIN korumalıdır
 * (docs/RBAC_MATRIX.md kural 2): sistemde en az bir aktif ADMIN kalmayacak işlemler 409 ile reddedilir.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String TEMP_PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;
    private final UserMapper userMapper;
    private final DriverRepository driverRepository;
    private final HostessRepository hostessRepository;
    private final ParentRepository parentRepository;
    private final StudentRepository studentRepository;

    @Override
    public Page<User> list(Role role, Boolean isActive, String q, Pageable pageable) {
        return list(role, isActive, q, null, pageable);
    }

    @Override
    public Page<User> list(Role role, Boolean isActive, String q, Boolean studentLinked, Pageable pageable) {
        Specification<User> spec = Specification.allOf(
                UserSpecifications.hasRole(role),
                UserSpecifications.isActive(isActive),
                UserSpecifications.matchesQuery(q),
                UserSpecifications.studentLinked(studentLinked));
        return userRepository.findAll(spec, pageable);
    }

    @Override
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> NotFoundException.of("Kullanıcı", id));
    }

    @Override
    @Transactional
    public UserCreateResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("Bu e-posta ile zaten bir kullanıcı var.");
        }
        boolean serverGenerated = request.password() == null || request.password().isBlank();
        String rawPassword = serverGenerated ? generateTemporaryPassword() : request.password();

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email().trim());
        user.setPhone(request.phone());
        user.setRole(request.role());
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        User saved = userRepository.save(user);

        return new UserCreateResponse(userMapper.toResponse(saved), serverGenerated ? rawPassword : null);
    }

    @Override
    @Transactional
    public User update(Long id, UserUpdateRequest request) {
        User user = getById(id);
        boolean roleChanging = request.role() != null && request.role() != user.getRole();
        boolean activeChanging = request.isActive() != null && request.isActive() != user.isActive();

        if (roleChanging && user.getRole() == Role.ADMIN && user.isActive()) {
            assertNotLastActiveAdmin(id);
        }
        if (activeChanging && Boolean.FALSE.equals(request.isActive()) && user.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin(id);
        }
        if (request.role() != null) {
            throw new BadRequestException(
                    "Rol değişikliği bu uçtan yapılamaz. POST /api/users/{id}/role kullanın "
                            + "(gerekli domain profiliyle birlikte atomik olarak işlenir).");
        }

        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.isActive() != null) {
            user.setActive(request.isActive());
        }
        User saved = userRepository.save(user);

        if (activeChanging) {
            tokenRevocationService.revokeAllRefreshTokensForUser(saved.getId());
        }
        return saved;
    }

    @Override
    @Transactional
    public User changeRole(Long id, UserRoleChangeRequest request) {
        User user = userRepository.findByIdForUpdate(id).orElseThrow(() -> NotFoundException.of("Kullanıcı", id));
        Role previousRole = user.getRole();
        Role newRole = request.role();
        boolean roleActuallyChanging = newRole != previousRole;

        if (roleActuallyChanging && previousRole == Role.ADMIN && user.isActive()) {
            assertNotLastActiveAdmin(id);
        }
        // Sprint 3: öğrenci kaydına bağlı bir STUDENT hesabının rolü sessizce değiştirilmez — bağ önce
        // DELETE /api/students/{id}/account ile açıkça kaldırılmalıdır (bkz. docs/API.md §3).
        if (roleActuallyChanging && previousRole == Role.STUDENT && studentRepository.findByUserId(id).isPresent()) {
            throw new ConflictException(
                    "Bu hesap bir öğrenci kaydına bağlı. Rolü değiştirmeden önce öğrenci detayından bağı kaldırın.");
        }

        if (roleActuallyChanging) {
            user.setRole(newRole);
            userRepository.saveAndFlush(user);
        }

        switch (newRole) {
            case DRIVER -> ensureDriverProfile(user, request.licenseNo());
            case HOSTESS -> ensureHostessProfile(user);
            case PARENT -> ensureParentProfile(user);
            default -> { /* ADMIN/STUDENT: ayrı bir domain profil tablosu yok. */ }
        }

        if (roleActuallyChanging) {
            tokenRevocationService.revokeAllRefreshTokensForUser(user.getId());
        }
        return user;
    }

    private void ensureDriverProfile(User user, String licenseNo) {
        Driver driver = driverRepository.findByUserId(user.getId()).orElse(null);
        if (driver == null) {
            if (licenseNo == null || licenseNo.isBlank()) {
                throw new BadRequestException("DRIVER rolü için ehliyet numarası (licenseNo) zorunludur.");
            }
            driver = new Driver();
            driver.setUser(user);
            driver.setLicenseNo(licenseNo);
        } else if (licenseNo != null && !licenseNo.isBlank()) {
            driver.setLicenseNo(licenseNo);
        }
        driver.setActive(true);
        driverRepository.save(driver);
    }

    private void ensureHostessProfile(User user) {
        Hostess hostess = hostessRepository.findByUserId(user.getId()).orElse(null);
        if (hostess == null) {
            hostess = new Hostess();
            hostess.setUser(user);
        }
        hostess.setActive(true);
        hostessRepository.save(hostess);
    }

    private void ensureParentProfile(User user) {
        if (parentRepository.existsByUserId(user.getId())) {
            return;
        }
        Parent parent = new Parent();
        parent.setUser(user);
        parent.setFullName(user.getFullName());
        parent.setPhone(user.getPhone());
        parentRepository.save(parent);
    }

    @Override
    @Transactional
    public User deactivate(Long id) {
        User user = getById(id);
        if (user.getRole() == Role.ADMIN) {
            assertNotLastActiveAdmin(id);
        }
        user.setActive(false);
        User saved = userRepository.save(user);
        tokenRevocationService.revokeAllRefreshTokensForUser(saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public User activate(Long id) {
        User user = getById(id);
        user.setActive(true);
        return userRepository.save(user);
    }

    private void assertNotLastActiveAdmin(Long excludingUserId) {
        long activeAdminCount = userRepository.countByRoleAndActiveTrue(Role.ADMIN);
        if (activeAdminCount <= 1) {
            throw new ConflictException("Sistemde en az bir aktif ADMIN kalmalıdır.");
        }
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
