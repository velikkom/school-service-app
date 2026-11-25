package com.okulservisi.backend.contract.dto.request;

/**
 * docs/API.md §19: {@code POST /api/contracts/{id}/reject} (ADMIN) {@code { reason? }}.
 *
 * <p><b>Tasarım kararı:</b> şekli {@code auth.dto.RegistrationRequestRejectRequest} ile aynı
 * ({@code {reason}}), ama AYNI sınıf reuse edilmedi — contract paketinin auth paketine bağımlı
 * olması istenmiyor (her domain'in görev tanımında belirtildiği gibi kendi DTO'ları var, bkz.
 * {@code ContractUpdateRequest}/{@code StudentUpdateRequest} emsali: şekil örtüşse de domain'ler
 * arası DTO paylaşımı bu projede YOK) ve ileride contract-reddi için şekiller ayrışabilir.
 */
public record ContractRejectRequest(String reason) {
}
