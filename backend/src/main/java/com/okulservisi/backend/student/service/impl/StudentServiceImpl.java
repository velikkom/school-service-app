package com.okulservisi.backend.student.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.repository.SchoolRepository;
import com.okulservisi.backend.student.dto.request.StudentCreateRequest;
import com.okulservisi.backend.student.dto.request.StudentUpdateRequest;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.mapper.StudentMapper;
import com.okulservisi.backend.student.policy.StudentAccessGuard;
import com.okulservisi.backend.student.policy.StudentScopePolicy;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.student.repository.StudentSpecifications;
import com.okulservisi.backend.student.service.StudentService;
import com.okulservisi.backend.auth.service.TokenRevocationService;
import com.okulservisi.backend.common.service.AuditLogService;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "3. Öğrenci" — ADMIN tam erişim; PARENT sadece aktif bağlı çocukları; STUDENT sadece
 * kendisi (bkz. docs/RBAC_MATRIX.md "Nesne bazlı erişim ilkesi"). Liste kapsamı burada, tekil kayıt
 * kapsamı {@link StudentAccessGuard} ile kontrol edilir.
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final StudentAccessGuard studentAccessGuard;
    private final StudentScopePolicy studentScopePolicy;
    private final StudentMapper studentMapper;
    private final UserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;
    private final AuditLogService auditLogService;

    @Override
    public Page<Student> list(String q, Long routeId, Boolean isActive, AuthenticatedUser principal, Pageable pageable) {
        Specification<Student> spec = Specification.allOf(
                StudentSpecifications.matchesQuery(q),
                StudentSpecifications.assignedToRoute(routeId),
                StudentSpecifications.isActive(isActive),
                scopeSpecification(principal));
        return studentRepository.findAll(spec, pageable);
    }

    private Specification<Student> scopeSpecification(AuthenticatedUser principal) {
        Set<Long> ids = studentScopePolicy.accessibleStudentIds(principal);
        return ids == null ? null : StudentSpecifications.idIn(ids);
    }

    @Override
    public Student getByIdForPrincipal(Long id, AuthenticatedUser principal) {
        studentAccessGuard.assertReadable(id, principal);
        return studentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Öğrenci", id));
    }

    @Override
    @Transactional
    public Student create(StudentCreateRequest request) {
        if (request.tcNo() != null && studentRepository.existsByTcNo(request.tcNo())) {
            throw new ConflictException("Bu T.C. kimlik numarası ile zaten bir öğrenci var.");
        }
        Student student = new Student();
        student.setFullName(request.fullName());
        student.setBirthDate(request.birthDate());
        student.setTcNo(request.tcNo());
        applySchool(student, request.schoolId());
        student.setGrade(request.grade());
        student.setActive(true);
        return studentRepository.save(student);
    }

    @Override
    @Transactional
    public Student update(Long id, StudentUpdateRequest request) {
        Student student = getById(id);
        if (request.tcNo() != null && !request.tcNo().equals(student.getTcNo())
                && studentRepository.existsByTcNo(request.tcNo())) {
            throw new ConflictException("Bu T.C. kimlik numarası ile zaten bir öğrenci var.");
        }
        studentMapper.updateFromRequest(student, request);
        if (request.schoolId() != null) {
            applySchool(student, request.schoolId());
        }
        return studentRepository.save(student);
    }

    private void applySchool(Student student, Long schoolId) {
        if (schoolId == null) {
            student.setSchool(null);
            student.setSchoolName(null);
            return;
        }
        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new BadRequestException("Geçersiz okul seçimi."));
        student.setSchool(school);
        student.setSchoolName(school.getName());
    }

    @Override
    @Transactional
    public Student deactivate(Long id) {
        Student student = getById(id);
        student.setActive(false);
        return studentRepository.save(student);
    }

    @Override
    @Transactional
    public Student linkAccount(Long studentId, Long userId, Long actorUserId) {
        // Kilit sırası her zaman kullanıcı → öğrenci: aynı hesabı iki öğrenciye ya da aynı öğrenciye iki
        // hesabı eşzamanlı bağlamaya çalışan istekler serileşir ve kaybeden taraf 409 alır. Son savunma
        // hattı V4'teki uq_student_user_id kısmi tekil indeksidir (ihlal → 409 DATA_INTEGRITY_VIOLATION).
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BadRequestException("Seçilen kullanıcı hesabı bulunamadı."));
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> NotFoundException.of("Öğrenci", studentId));

        if (student.getUser() != null) {
            if (student.getUser().getId().equals(user.getId())) {
                return student; // aynı çift: güvenli tekrar
            }
            throw new ConflictException(
                    "Bu öğrenci zaten başka bir hesaba bağlı. Önce mevcut bağı kaldırın.");
        }
        if (user.getRole() != Role.STUDENT) {
            throw new BadRequestException("Yalnızca STUDENT rolündeki hesaplar öğrenci kaydına bağlanabilir.");
        }
        if (!user.isActive()) {
            throw new BadRequestException("Pasif bir hesap öğrenci kaydına bağlanamaz.");
        }
        if (!student.isActive()) {
            throw new BadRequestException("Pasif bir öğrenci kaydına hesap bağlanamaz.");
        }
        studentRepository.findByUserId(user.getId()).ifPresent(other -> {
            throw new ConflictException("Bu hesap zaten başka bir öğrenci kaydına bağlı.");
        });

        student.setUser(user);
        Student saved = studentRepository.saveAndFlush(student);
        auditLogService.record(actorUserId, "STUDENT_ACCOUNT_LINKED", "student", saved.getId(),
                Map.of("userId", user.getId()));
        return saved;
    }

    @Override
    @Transactional
    public Student unlinkAccount(Long studentId, Long actorUserId) {
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> NotFoundException.of("Öğrenci", studentId));
        User previous = student.getUser();
        if (previous == null) {
            return student;
        }
        student.setUser(null);
        Student saved = studentRepository.saveAndFlush(student);
        // Eski oturumun yenilenerek sürmemesi için refresh token'lar iptal edilir; erişim kapsamı zaten
        // her istekte veritabanından türetildiği için mevcut access token da artık bu öğrenciyi göremez.
        tokenRevocationService.revokeAllRefreshTokensForUser(previous.getId());
        auditLogService.record(actorUserId, "STUDENT_ACCOUNT_UNLINKED", "student", saved.getId(),
                Map.of("userId", previous.getId()));
        return saved;
    }

    private Student getById(Long id) {
        return studentRepository.findById(id).orElseThrow(() -> NotFoundException.of("Öğrenci", id));
    }
}
