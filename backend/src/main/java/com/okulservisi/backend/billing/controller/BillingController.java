package com.okulservisi.backend.billing.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.billing.api.BillingApiPaths;
import com.okulservisi.backend.billing.dto.request.BillingRunRequest;
import com.okulservisi.backend.billing.dto.response.BillingRunResponse;
import com.okulservisi.backend.billing.dto.response.MonthlyChargeResponse;
import com.okulservisi.backend.billing.entity.MonthlyCharge;
import com.okulservisi.backend.billing.mapper.MonthlyChargeMapper;
import com.okulservisi.backend.billing.service.BillingRunResult;
import com.okulservisi.backend.billing.service.BillingService;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AdminOrParent;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "12. Aylık ücret / tahakkuk" (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(BillingApiPaths.BASE)
@RequiredArgsConstructor
public class BillingController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "period", "amount", "createdAt");

    private final BillingService billingService;
    private final MonthlyChargeMapper monthlyChargeMapper;

    @PostMapping(BillingApiPaths.RUN)
    @AdminOnly
    public ResponseEntity<BillingRunResponse> run(@Valid @RequestBody BillingRunRequest request) {
        BillingRunResult result = billingService.run(request.period());
        return ResponseEntity.ok(new BillingRunResponse(result.period(), result.created(), result.skipped()));
    }

    @GetMapping(BillingApiPaths.CHARGES)
    @AdminOrParent
    public PageResponse<MonthlyChargeResponse> listCharges(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "period,desc");
        Page<MonthlyCharge> result = billingService.listCharges(studentId, period, principal, pageable);
        return PageResponse.of(result, result.getContent().stream().map(monthlyChargeMapper::toResponse).toList());
    }
}
