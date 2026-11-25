package com.okulservisi.backend.auth.service;

import com.okulservisi.backend.auth.dto.request.RegistrationRequestApproveRequest;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestCreateRequest;
import com.okulservisi.backend.auth.dto.request.RegistrationRequestRejectRequest;
import com.okulservisi.backend.auth.entity.RegistrationRequest;
import com.okulservisi.backend.auth.entity.RegistrationRequestStatus;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Public kayıt talebi + ADMIN onay/red akışı (bkz. docs/SECURITY.md "Kayıt talebi ve ADMIN
 * onayı", docs/API.md §1 ve §18).
 */
public interface RegistrationRequestService {

    void createRequest(RegistrationRequestCreateRequest request, String clientIp);

    void verifyEmail(String token, String clientIp);

    Page<RegistrationRequest> list(RegistrationRequestStatus status, Pageable pageable);

    RegistrationRequest getById(Long id);

    User approve(Long id, RegistrationRequestApproveRequest request, AuthenticatedUser admin);

    RegistrationRequest reject(Long id, RegistrationRequestRejectRequest request, AuthenticatedUser admin);
}
