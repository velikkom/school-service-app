package com.okulservisi.backend.trip.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.security.annotation.AdminParentOrStudent;
import com.okulservisi.backend.trip.dto.response.DailyServiceEntryResponse;
import com.okulservisi.backend.trip.service.DailyServiceQueryService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md §26 — {@code GET /api/students/{studentId}/daily-service?date=}. */
@RestController
@RequestMapping("/api/students/{studentId}/daily-service")
@RequiredArgsConstructor
public class StudentDailyServiceController {

    private final DailyServiceQueryService dailyServiceQueryService;

    @GetMapping
    @AdminParentOrStudent
    public List<DailyServiceEntryResponse> get(@PathVariable Long studentId,
            @RequestParam(required = false) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return dailyServiceQueryService.getDailyService(studentId, date, principal);
    }
}
