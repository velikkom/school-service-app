package com.okulservisi.backend.contract.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.contract.dto.request.ContractApproveRequest;
import com.okulservisi.backend.contract.dto.request.ContractCreateRequest;
import com.okulservisi.backend.contract.dto.request.ContractRejectRequest;
import com.okulservisi.backend.contract.dto.request.ContractUpdateRequest;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.contract.repository.ContractRepository;
import com.okulservisi.backend.contract.repository.ContractSpecifications;
import com.okulservisi.backend.contract.service.ContractService;
import com.okulservisi.backend.notification.service.OperationalNotificationService;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.trip.policy.TripAudienceResolver;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "11. Sözleşme" (bkz. docs/RBAC_MATRIX.md). ADMIN tam CRUD; PARENT sadece kendi
 * çocuğunun sözleşmelerini salt-okunur görür.
 */
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    /** Henüz var olmayan bir kaydı "hariç tutmak" için kullanılan, hiçbir gerçek ID ile çakışmayan değer. */
    private static final Long NO_EXCLUSION = 0L;

    /**
     * Süresiz (endDate==null) sözleşme için "üst sınır yok" sentinel'i — bkz. {@link #assertNoOverlap}.
     */
    private static final LocalDate FAR_FUTURE = LocalDate.of(9999, 12, 31);

    private final ContractRepository contractRepository;
    private final StudentRepository studentRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StudentScopePolicy studentScopePolicy;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final OperationalNotificationService notificationService;
    private final TripAudienceResolver audienceResolver;

    @Override
    public Page<Contract> list(Long studentId, ContractStatus status, AuthenticatedUser principal, Pageable pageable) {
        if (studentId != null) {
            studentAccessGuard.assertReadable(studentId, principal);
        }
        Specification<Contract> spec = Specification.allOf(
                ContractSpecifications.hasStudentId(studentId),
                ContractSpecifications.hasStatus(status),
                studentId == null ? scopeSpecification(principal) : null);
        return contractRepository.findAll(spec, pageable);
    }

    private Specification<Contract> scopeSpecification(AuthenticatedUser principal) {
        Set<Long> ids = studentScopePolicy.accessibleStudentIds(principal);
        return ids == null ? null : ContractSpecifications.studentIdIn(ids);
    }

    @Override
    public Contract getByIdForPrincipal(Long id, AuthenticatedUser principal) {
        Contract contract = getById(id);
        studentAccessGuard.assertReadable(contract.getStudent().getId(), principal);
        return contract;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Contract create(ContractCreateRequest request) {
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new BadRequestException("Belirtilen studentId'ye sahip öğrenci bulunamadı."));

        assertValidDateRange(request.startDate(), request.endDate());
        assertNoOverlap(student.getId(), request.startDate(), request.endDate(), NO_EXCLUSION);

        Contract contract = new Contract();
        contract.setStudent(student);
        contract.setMonthlyFee(request.monthlyFee());
        contract.setDiscountAmount(request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount());
        contract.setStartDate(request.startDate());
        contract.setEndDate(request.endDate());
        contract.setStatus(ContractStatus.ACTIVE);
        return contractRepository.save(contract);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Contract update(Long id, ContractUpdateRequest request) {
        Contract contract = getById(id);
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new ConflictException("Sadece ACTIVE durumdaki bir sözleşme güncellenebilir.");
        }

        LocalDate newEndDate = request.endDate() != null ? request.endDate() : contract.getEndDate();
        assertValidDateRange(contract.getStartDate(), newEndDate);
        if (!Objects.equals(newEndDate, contract.getEndDate())) {
            assertNoOverlap(contract.getStudent().getId(), contract.getStartDate(), newEndDate, contract.getId());
        }

        if (request.monthlyFee() != null) {
            contract.setMonthlyFee(request.monthlyFee());
        }
        if (request.discountAmount() != null) {
            contract.setDiscountAmount(request.discountAmount());
        }
        contract.setEndDate(newEndDate);
        return contractRepository.save(contract);
    }

    @Override
    @Transactional
    public Contract cancel(Long id) {
        Contract contract = getById(id);
        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw new ConflictException("Sözleşme zaten iptal edilmiş.");
        }
        contract.setStatus(ContractStatus.CANCELLED);
        return contractRepository.save(contract);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Contract approve(Long id, ContractApproveRequest request) {
        Contract contract = getById(id);
        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new ConflictException("Sadece PENDING_APPROVAL durumundaki bir sözleşme onaylanabilir.");
        }
        LocalDate effectiveStartDate = request.startDate() != null ? request.startDate() : contract.getStartDate();
        BigDecimal effectiveDiscountAmount =
                request.discountAmount() != null ? request.discountAmount() : contract.getDiscountAmount();

        assertValidDateRange(effectiveStartDate, contract.getEndDate());
        assertNoOverlap(contract.getStudent().getId(), effectiveStartDate, contract.getEndDate(), contract.getId());

        contract.setMonthlyFee(request.monthlyFee());
        contract.setStartDate(effectiveStartDate);
        contract.setDiscountAmount(effectiveDiscountAmount);
        contract.setStatus(ContractStatus.ACTIVE);
        Contract saved = contractRepository.save(contract);
        // 2026-11-18 (docs/API.md §27): başvuru sonucu veliye bildirilir (aynı transaction).
        notificationService.notify(audienceResolver.parentUsersOfStudent(contract.getStudent().getId()),
                "Öğrenci kayıt başvurunuz onaylandı",
                contract.getStudent().getFullName() + " için servis kayıt başvurunuz onaylandı.",
                "contract:" + saved.getId() + ":approved");
        return saved;
    }

    @Override
    @Transactional
    public Contract reject(Long id, ContractRejectRequest request) {
        Contract contract = getById(id);
        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new ConflictException("Sadece PENDING_APPROVAL durumundaki bir sözleşme reddedilebilir.");
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? null : request.reason().trim();
        contract.setStatus(ContractStatus.CANCELLED);
        contract.setRejectionReason(reason);
        Contract saved = contractRepository.save(contract);

        Student student = contract.getStudent();
        // Bildirim, veli bağları kapatılmadan ÖNCE alıcıları çözer (aynı transaction'da yazılır).
        notificationService.notify(audienceResolver.parentUsersOfStudent(student.getId()),
                "Öğrenci kayıt başvurunuz reddedildi",
                student.getFullName() + " için servis kayıt başvurunuz reddedildi."
                        + (reason == null ? "" : " Gerekçe: " + reason),
                "contract:" + saved.getId() + ":rejected");
        student.setActive(false);
        studentRepository.save(student);

        List<ParentStudentLink> activeLinks = parentStudentLinkRepository.findByStudentIdAndActiveTrue(student.getId());
        for (ParentStudentLink link : activeLinks) {
            link.setActive(false);
            link.setRevokedAt(Instant.now());
        }
        parentStudentLinkRepository.saveAll(activeLinks);

        return saved;
    }

    private void assertNoOverlap(Long studentId, LocalDate startDate, LocalDate endDate, Long excludingId) {
        LocalDate effectiveEndDate = endDate == null ? FAR_FUTURE : endDate;
        List<Contract> overlapping =
                contractRepository.findOverlappingActive(studentId, startDate, effectiveEndDate, excludingId);
        if (!overlapping.isEmpty()) {
            throw new ConflictException(
                    "Bu öğrenci için tarih aralığı çakışan başka bir ACTIVE sözleşme zaten var.");
        }
    }

    private void assertValidDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BadRequestException("endDate, startDate'den önce olamaz.");
        }
    }

    private Contract getById(Long id) {
        return contractRepository.findById(id).orElseThrow(() -> NotFoundException.of("Sözleşme", id));
    }
}
