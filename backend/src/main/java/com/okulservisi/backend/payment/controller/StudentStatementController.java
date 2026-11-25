package com.okulservisi.backend.payment.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.payment.api.PaymentApiPaths;
import com.okulservisi.backend.payment.dto.response.StudentStatementResponse;
import com.okulservisi.backend.payment.service.StudentStatementService;
import com.okulservisi.backend.security.annotation.AdminOrParent;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "13. Ödeme": {@code GET /api/students/{id}/statement} — nesne bazlı kontrol (ADMIN
 * tümü; PARENT kendi çocuğu, bkz. {@link StudentStatementService}). Dar kapsamlı bir alt-kaynak
 * olduğu için (bkz. {@code StudentAttendanceController} emsali) pagination zarfı KULLANILMAZ.
 */
@RestController
@RequestMapping(PaymentApiPaths.STUDENT_STATEMENT_BASE)
@RequiredArgsConstructor
public class StudentStatementController {

    private final StudentStatementService studentStatementService;

    @GetMapping
    @AdminOrParent
    public StudentStatementResponse getStatement(@PathVariable Long studentId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return studentStatementService.getStatement(studentId, principal);
    }
}
