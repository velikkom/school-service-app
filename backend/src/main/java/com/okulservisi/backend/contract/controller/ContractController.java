package com.okulservisi.backend.contract.controller;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.contract.api.ContractApiPaths;
import com.okulservisi.backend.contract.dto.request.ContractApproveRequest;
import com.okulservisi.backend.contract.dto.request.ContractCreateRequest;
import com.okulservisi.backend.contract.dto.request.ContractRejectRequest;
import com.okulservisi.backend.contract.dto.response.ContractResponse;
import com.okulservisi.backend.contract.dto.request.ContractUpdateRequest;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.contract.mapper.ContractMapper;
import com.okulservisi.backend.contract.service.ContractService;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.security.annotation.AdminOrParent;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * docs/API.md "11. Sözleşme" (bkz. docs/RBAC_MATRIX.md: DRIVER/HOSTESS/STUDENT tamamen ❌, PARENT
 * sadece kendi çocuğu salt-okunur, ADMIN tam CRUD).
 */
@RestController
@RequestMapping(ContractApiPaths.BASE)
@RequiredArgsConstructor
public class ContractController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "startDate", "endDate", "status", "createdAt");

    private final ContractService contractService;
    private final ContractMapper contractMapper;
    private final StudentRepository studentRepository;

    /**
     * {@code contract.getStudent()} {@code open-in-view: false} altında lazy proxy olabilir (bkz.
     * {@code ContractMapper} javadoc'u) — isim göstermek için {@code StudentRepository} ile AYRICA
     * (proxy olmayan) yüklenir. Tekil endpoint'ler için.
     */
    private ContractResponse toResponse(Contract contract) {
        Student student = studentRepository.findById(contract.getStudent().getId()).orElse(null);
        return contractMapper.toResponse(contract, student == null ? null : student.getFullName());
    }

    /** Liste endpoint'i için N+1 yerine tek sorguda toplu isim çözümü. */
    private List<ContractResponse> toResponseList(List<Contract> contracts) {
        Set<Long> studentIds = contracts.stream().map(c -> c.getStudent().getId()).collect(Collectors.toSet());
        Map<Long, String> namesById = new LinkedHashMap<>();
        studentRepository.findAllById(studentIds).forEach(s -> namesById.put(s.getId(), s.getFullName()));
        return contracts.stream()
                .map(c -> contractMapper.toResponse(c, namesById.get(c.getStudent().getId())))
                .toList();
    }

    @GetMapping
    @AdminOrParent
    public PageResponse<ContractResponse> list(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "startDate,desc");
        Page<Contract> result = contractService.list(studentId, status, principal, pageable);
        return PageResponse.of(result, toResponseList(result.getContent()));
    }

    @GetMapping(ContractApiPaths.BY_ID)
    @AdminOrParent
    public ResponseEntity<ContractResponse> getById(@PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(toResponse(contractService.getByIdForPrincipal(id, principal)));
    }

    @PostMapping
    @AdminOnly
    public ResponseEntity<ContractResponse> create(@Valid @RequestBody ContractCreateRequest request) {
        return ResponseEntity.ok(toResponse(contractService.create(request)));
    }

    @PatchMapping(ContractApiPaths.BY_ID)
    @AdminOnly
    public ResponseEntity<ContractResponse> update(@PathVariable Long id,
            @Valid @RequestBody ContractUpdateRequest request) {
        return ResponseEntity.ok(toResponse(contractService.update(id, request)));
    }

    @PostMapping(ContractApiPaths.CANCEL)
    @AdminOnly
    public ResponseEntity<ContractResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(contractService.cancel(id)));
    }

    /** docs/API.md §19 — veli öz-kayıt onayı, SADECE {@code status=PENDING_APPROVAL} iken izinli. */
    @PostMapping(ContractApiPaths.APPROVE)
    @AdminOnly
    public ResponseEntity<ContractResponse> approve(@PathVariable Long id,
            @Valid @RequestBody ContractApproveRequest request) {
        return ResponseEntity.ok(toResponse(contractService.approve(id, request)));
    }

    /** docs/API.md §19 — veli öz-kayıt reddi, SADECE {@code status=PENDING_APPROVAL} iken izinli. */
    @PostMapping(ContractApiPaths.REJECT)
    @AdminOnly
    public ResponseEntity<ContractResponse> reject(@PathVariable Long id,
            @RequestBody(required = false) ContractRejectRequest request) {
        ContractRejectRequest effectiveRequest = request == null ? new ContractRejectRequest(null) : request;
        return ResponseEntity.ok(toResponse(contractService.reject(id, effectiveRequest)));
    }
}
