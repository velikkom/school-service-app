package com.okulservisi.backend.contract.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.contract.dto.request.ContractApproveRequest;
import com.okulservisi.backend.contract.dto.request.ContractCreateRequest;
import com.okulservisi.backend.contract.dto.request.ContractRejectRequest;
import com.okulservisi.backend.contract.dto.request.ContractUpdateRequest;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * docs/API.md "11. Sözleşme" (bkz. docs/RBAC_MATRIX.md). ADMIN tam CRUD; PARENT sadece kendi
 * çocuğunun sözleşmelerini salt-okunur görür.
 */
public interface ContractService {

    Page<Contract> list(Long studentId, ContractStatus status, AuthenticatedUser principal, Pageable pageable);

    Contract getByIdForPrincipal(Long id, AuthenticatedUser principal);

    Contract create(ContractCreateRequest request);

    Contract update(Long id, ContractUpdateRequest request);

    Contract cancel(Long id);

    Contract approve(Long id, ContractApproveRequest request);

    Contract reject(Long id, ContractRejectRequest request);
}
