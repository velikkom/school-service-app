package com.okulservisi.backend.payment.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.payment.dto.response.StudentStatementResponse;

/** docs/API.md "13. Ödeme": {@code GET /api/students/{id}/statement}. */
public interface StudentStatementService {

    StudentStatementResponse getStatement(Long studentId, AuthenticatedUser principal);
}
