package com.okulservisi.backend.attendance.controller;

import com.okulservisi.backend.attendance.dto.request.AbsenceNoticeCreateRequest;
import com.okulservisi.backend.attendance.dto.response.AbsenceNoticeResponse;
import com.okulservisi.backend.attendance.service.TripAbsenceNoticeService;
import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.security.annotation.AdminDriverOrHostess;
import com.okulservisi.backend.security.annotation.ParentOnly;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md §22 — {@code /api/trips/{tripId}/absence-notices}. PARENT gönderir/geri alır (sadece
 * bağlı çocuğu); ADMIN ve atanmış DRIVER/HOSTESS aktif bildirimleri listeler (ayrıca roster satırında
 * {@code absenceNotice} olarak görünür).
 */
@RestController
@RequestMapping("/api/trips/{tripId}/absence-notices")
@RequiredArgsConstructor
public class TripAbsenceNoticeController {

    private final TripAbsenceNoticeService absenceNoticeService;

    @PostMapping
    @ParentOnly
    public ResponseEntity<AbsenceNoticeResponse> submit(@PathVariable Long tripId,
            @Valid @RequestBody AbsenceNoticeCreateRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        TripAbsenceNoticeService.Result result = absenceNoticeService.submit(tripId, request, principal);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.notice());
    }

    @PostMapping("/{noticeId}/withdraw")
    @ParentOnly
    public AbsenceNoticeResponse withdraw(@PathVariable Long tripId, @PathVariable Long noticeId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return absenceNoticeService.withdraw(tripId, noticeId, principal);
    }

    @GetMapping
    @AdminDriverOrHostess
    public List<AbsenceNoticeResponse> list(@PathVariable Long tripId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return absenceNoticeService.listActive(tripId, principal);
    }
}
