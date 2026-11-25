package com.okulservisi.backend.contract.dto.response;

import com.okulservisi.backend.contract.entity.ContractStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * docs/RBAC_MATRIX.md: PARENT için "salt okunur özet" — ayrı bir küçültülmüş DTO yerine AYNI DTO,
 * sadece erişim kapsamı (StudentAccessGuard/StudentScopePolicy) ile kısıtlanmıştır (bkz.
 * ContractService javadoc'u, gerekçe orada); sözleşme alanlarının hiçbiri PARENT için hassas/gizlenmesi
 * gereken bilgi değildir (zaten kendi çocuğunun ücreti/tarihleri).
 */
public record ContractResponse(
        Long id,
        Long studentId,
        // docs/API.md "11. Sözleşme": salt-okunur görüntüleme alanı (2026-10-25, §19 ADMIN onay
        // ekranı için eklendi) — birincil referans hâlâ studentId.
        String studentFullName,
        BigDecimal monthlyFee,
        BigDecimal discountAmount,
        LocalDate startDate,
        LocalDate endDate,
        ContractStatus status,
        // 2026-11-18: öz-kayıt başvurusu reddedildiyse gerekçe (aksi null).
        String rejectionReason) {
}
