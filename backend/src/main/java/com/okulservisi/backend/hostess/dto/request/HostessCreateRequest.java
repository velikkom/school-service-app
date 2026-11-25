package com.okulservisi.backend.hostess.dto.request;

import jakarta.validation.constraints.NotNull;

/** docs/API.md "6. Şoför/Hostes": her Hostess bir User (role=HOSTESS) ile 1-1 (docs/DATABASE_DESIGN.md). */
public record HostessCreateRequest(@NotNull(message = "userId zorunludur.") Long userId) {
}
