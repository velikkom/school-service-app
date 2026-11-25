package com.okulservisi.backend.route.service;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.route.dto.request.StopChangeRequestCreateRequest;
import com.okulservisi.backend.route.dto.response.StopChangeRequestResponse;
import com.okulservisi.backend.route.dto.response.StopOptionResponse;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.StopChangeRequestStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** docs/API.md §25 — veli durak değişikliği talepleri. */
public interface StopChangeRequestService {

    List<StopOptionResponse> options(Long studentId, RouteDirection direction, AuthenticatedUser principal);

    StopChangeRequestResponse create(StopChangeRequestCreateRequest request, AuthenticatedUser principal);

    Page<StopChangeRequestResponse> list(StopChangeRequestStatus status, AuthenticatedUser principal,
            Pageable pageable);

    StopChangeRequestResponse approve(Long id, String note, AuthenticatedUser principal);

    StopChangeRequestResponse reject(Long id, String reason, AuthenticatedUser principal);
}
