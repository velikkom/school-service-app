package com.okulservisi.backend.enrollment.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.contract.mapper.ContractMapper;
import com.okulservisi.backend.contract.repository.ContractRepository;
import com.okulservisi.backend.enrollment.dto.request.EnrollmentCreateRequest;
import com.okulservisi.backend.enrollment.dto.response.EnrollmentResponse;
import com.okulservisi.backend.enrollment.service.EnrollmentService;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.parent.service.ParentService;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.repository.SchoolRepository;
import com.okulservisi.backend.settings.service.InstitutionSettingsService;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.mapper.StudentMapper;
import com.okulservisi.backend.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md §19 "Veli öz-kayıt (öğrenci kaydı sihirbazı)" — PARENT hesabı hiç bağlı öğrencisi
 * yoksa kendi başına İLK öğrencisini kaydeder. {@code RegistrationRequestService}'teki "talep"
 * deseninden FARKLI olarak ayrı bir talep tablosu YOKTUR: {@code Student}/{@code ParentStudentLink}/
 * {@code Contract} ANINDA (tek transaction'da) oluşturulur, ama {@code Contract.status=
 * PENDING_APPROVAL} ile başlar — ADMIN {@code POST /api/contracts/{id}/approve}/{@code /reject}
 * edene kadar "aktif" sayılmaz (bkz. {@code ContractService}).
 */
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final ParentService parentService;
    private final ParentRepository parentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final StudentRepository studentRepository;
    private final ContractRepository contractRepository;
    private final SchoolRepository schoolRepository;
    private final InstitutionSettingsService institutionSettingsService;
    private final StudentMapper studentMapper;
    private final ContractMapper contractMapper;

    /**
     * 2026-11-18 (docs/API.md §19/§27 "ikinci çocuk"): artık SADECE onay bekleyen (PENDING_APPROVAL)
     * bir başvuru yeni başvuruyu engeller. İlk öz-kayıt onaylandıktan (ACTIVE) sonra veli aynı
     * hesabından ikinci/sonraki çocuğu için başvurabilir; onay yine mevcut
     * {@code POST /api/contracts/{id}/approve} akışıyla olur.
     */
    private static final List<ContractStatus> BLOCKING_STATUSES = List.of(ContractStatus.PENDING_APPROVAL);

    @Override
    @Transactional
    public EnrollmentResponse create(EnrollmentCreateRequest request, AuthenticatedUser principal) {
        if (!Boolean.TRUE.equals(request.contractAccepted())) {
            throw new BadRequestException("Sözleşme şartlarının kabul edilmesi (contractAccepted=true) zorunludur.");
        }

        School school = schoolRepository.findById(request.schoolId())
                .filter(School::isActive)
                .orElseThrow(() -> new BadRequestException("Geçersiz okul seçimi."));

        Parent parent = parentService.getByUserId(principal.getUserId());
        // Aynı velinin eşzamanlı iki başvurusu veli satırı kilidiyle sıraya girer (tek bekleyen başvuru).
        parent = parentRepository.findByIdForUpdate(parent.getId()).orElseThrow();
        assertNoPendingEnrollment(parent.getId());

        if (studentRepository.existsByTcNo(request.student().tcNo())) {
            throw new ConflictException("Bu T.C. kimlik numarası ile zaten bir öğrenci var.");
        }

        BigDecimal defaultMonthlyFee = institutionSettingsService.get().getDefaultMonthlyFee();
        if (defaultMonthlyFee == null) {
            throw new ConflictException(
                    "Kurum henüz varsayılan aylık ücret belirlememiş, öz-kayıt şu an için mümkün değil.");
        }

        parent.setPhone(request.parentPhone());
        parentRepository.save(parent);

        Student student = new Student();
        student.setFullName(request.student().fullName());
        student.setBirthDate(request.student().birthDate());
        student.setTcNo(request.student().tcNo());
        student.setGrade(request.student().grade());
        student.setGender(request.student().gender());
        student.setHealthNote(request.student().healthNote());
        student.setSchool(school);
        student.setSchoolName(school.getName());
        student.setCity(request.address().city());
        student.setDistrict(request.address().district());
        student.setNeighborhood(request.address().neighborhood());
        student.setAddressLine(request.address().addressLine());
        student.setActive(true);
        Student savedStudent = studentRepository.save(student);

        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(savedStudent);
        link.setRelationType(request.relationType());
        link.setActive(true);
        parentStudentLinkRepository.save(link);

        Contract contract = new Contract();
        contract.setStudent(savedStudent);
        contract.setMonthlyFee(defaultMonthlyFee);
        contract.setDiscountAmount(BigDecimal.ZERO);
        contract.setStartDate(LocalDate.now());
        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        Contract savedContract = contractRepository.save(contract);

        return new EnrollmentResponse(
                studentMapper.toResponse(savedStudent),
                contractMapper.toResponse(savedContract, savedStudent.getFullName()));
    }

    private void assertNoPendingEnrollment(Long parentId) {
        List<ParentStudentLink> activeLinks = parentStudentLinkRepository.findByParentIdAndActiveTrue(parentId);
        if (activeLinks.isEmpty()) {
            return;
        }
        List<Long> studentIds = activeLinks.stream().map(link -> link.getStudent().getId()).toList();
        if (contractRepository.existsByStudentIdInAndStatusIn(studentIds, BLOCKING_STATUSES)) {
            throw new ConflictException(
                    "Onay bekleyen bir öğrenci kayıt başvurunuz var; sonuçlanmadan yeni başvuru yapılamaz.");
        }
    }
}
