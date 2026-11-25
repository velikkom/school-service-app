package com.okulservisi.backend.attendance.service;

import com.okulservisi.backend.attendance.dto.request.AbsenceNoticeCreateRequest;
import com.okulservisi.backend.attendance.dto.response.AbsenceNoticeResponse;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import java.util.List;

/** docs/API.md §22 — veli "servise binmeyecek" bildirimi. */
public interface TripAbsenceNoticeService {

    /** @return {@code created=true} ise yeni kayıt (201), değilse zaten aktif olan bildirim (200). */
    Result submit(Long tripId, AbsenceNoticeCreateRequest request, AuthenticatedUser principal);

    AbsenceNoticeResponse withdraw(Long tripId, Long noticeId, AuthenticatedUser principal);

    List<AbsenceNoticeResponse> listActive(Long tripId, AuthenticatedUser principal);

    record Result(AbsenceNoticeResponse notice, boolean created) {
    }
}
