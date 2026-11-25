package com.okulservisi.backend.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.enrollment.dto.request.EnrollmentCreateRequest;
import com.okulservisi.backend.enrollment.dto.response.EnrollmentResponse;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.repository.SchoolRepository;
import com.okulservisi.backend.settings.entity.InstitutionSettings;
import com.okulservisi.backend.settings.service.InstitutionSettingsService;
import com.okulservisi.backend.settings.repository.InstitutionSettingsRepository;
import com.okulservisi.backend.student.entity.Gender;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md §19 "Veli öz-kayıt" — {@code POST /api/parents/me/student-enrollments} (PARENT only).
 *
 * <p><b>ÖNEMLİ — {@code institution_settings} tek satırlıktır ve TÜM test sınıfları paylaşır</b>
 * (bkz. {@code InstitutionSettingsControllerTest} javadoc'u) — bu yüzden her test KENDİ
 * {@code defaultMonthlyFee} değerini (repository üzerinden, PATCH'in {@code null}=değişmez
 * semantiğini atlayarak) açıkça set eder, sıra/çakışma bağımsız hale gelir.
 */
class EnrollmentControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentLinkRepository parentStudentLinkRepository;

    @Autowired
    private InstitutionSettingsService institutionSettingsService;

    @Autowired
    private InstitutionSettingsRepository institutionSettingsRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private void setDefaultMonthlyFee(BigDecimal fee) {
        InstitutionSettings settings = institutionSettingsService.get();
        settings.setDefaultMonthlyFee(fee);
        institutionSettingsRepository.save(settings);
    }

    private User createParentUser() {
        return TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
    }

    private Parent createParentProfile(User parentUser, String fullName) {
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName(fullName);
        return parentRepository.save(parent);
    }

    private School createActiveSchool() {
        School school = new School();
        school.setName("Öz Kayıt Test Okulu " + UUID.randomUUID());
        school.setActive(true);
        return schoolRepository.save(school);
    }

    private EnrollmentCreateRequest validRequest(String tcNo) {
        return validRequest(tcNo, createActiveSchool().getId());
    }

    private EnrollmentCreateRequest validRequest(String tcNo, Long schoolId) {
        return new EnrollmentCreateRequest(
                schoolId,
                "MOTHER",
                "0555" + tcNo.substring(0, Math.min(7, tcNo.length())),
                new EnrollmentCreateRequest.StudentPart(
                        "Öz Kayıt Öğrenci " + tcNo, LocalDate.of(2016, 9, 1), tcNo, "3-A", Gender.FEMALE, null),
                new EnrollmentCreateRequest.AddressPart("İstanbul", "Kadıköy", "Mahalle " + tcNo, "Sokak No:1"),
                true);
    }

    private String uniqueTcNo() {
        return String.valueOf(10_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
    }

    @Test
    void create_withValidDataAndDefaultFeeSet_createsStudentLinkAndPendingContract() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUser = createParentUser();
        Parent parent = createParentProfile(parentUser, "Öz Kayıt Velisi 1");
        String tcNo = uniqueTcNo();
        School school = createActiveSchool();
        EnrollmentCreateRequest request = validRequest(tcNo, school.getId());

        ResponseEntity<EnrollmentResponse> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(parentUser)), EnrollmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        EnrollmentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.student().tcNo()).isEqualTo(tcNo);
        assertThat(body.student().isActive()).isTrue();
        assertThat(body.student().gender()).isEqualTo(Gender.FEMALE);
        assertThat(body.student().schoolId())
                .as("schoolId student.schoolId + schoolName senkron set edilmeli")
                .isEqualTo(school.getId());
        assertThat(body.student().schoolName()).isEqualTo(school.getName());
        assertThat(body.contract().status()).isEqualTo(ContractStatus.PENDING_APPROVAL);
        assertThat(body.contract().monthlyFee()).isEqualByComparingTo(new BigDecimal("1250.00"));
        assertThat(body.contract().startDate()).isEqualTo(LocalDate.now());

        List<ParentStudentLink> links = parentStudentLinkRepository.findByStudentIdAndActiveTrue(body.student().id());
        assertThat(links).hasSize(1);
        assertThat(links.get(0).getRelationType()).isEqualTo("MOTHER");

        Parent reloadedParent = parentRepository.findById(parent.getId()).orElseThrow();
        assertThat(reloadedParent.getPhone())
                .as("parentPhone yan etki olarak parent.phone'u güncellemeli")
                .isEqualTo(request.parentPhone());
    }

    @Test
    void create_contractNotAccepted_returns400() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 2");
        EnrollmentCreateRequest base = validRequest(uniqueTcNo());
        EnrollmentCreateRequest request = new EnrollmentCreateRequest(
                base.schoolId(), base.relationType(), base.parentPhone(), base.student(), base.address(), false);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_withoutParentPhone_returns400() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 6");
        EnrollmentCreateRequest base = validRequest(uniqueTcNo());
        EnrollmentCreateRequest request = new EnrollmentCreateRequest(
                base.schoolId(), base.relationType(), null, base.student(), base.address(), base.contractAccepted());

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("parentPhone zorunlu - eksikse 400 dönmeli")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_withoutSchoolId_returns400() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 7");
        EnrollmentCreateRequest base = validRequest(uniqueTcNo());
        EnrollmentCreateRequest request = new EnrollmentCreateRequest(
                null, base.relationType(), base.parentPhone(), base.student(), base.address(), base.contractAccepted());

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("schoolId zorunlu - eksikse 400 dönmeli")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_withNonExistentSchoolId_returns400() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 8");
        String tcNo = uniqueTcNo();
        EnrollmentCreateRequest request = validRequest(tcNo, 999_999_999L);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("var olmayan schoolId - 400 dönmeli (geçersiz okul seçimi)")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_withInactiveSchoolId_returns400() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 9");
        School inactiveSchool = createActiveSchool();
        inactiveSchool.setActive(false);
        schoolRepository.save(inactiveSchool);
        String tcNo = uniqueTcNo();
        EnrollmentCreateRequest request = validRequest(tcNo, inactiveSchool.getId());

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("pasif okul - 400 dönmeli (geçersiz okul seçimi)")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_whenParentAlreadyHasPendingOrActiveStudent_returns409() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 3");

        ResponseEntity<EnrollmentResponse> first = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(validRequest(uniqueTcNo()), authHeaderFor(parentUser)), EnrollmentResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiError> second = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(validRequest(uniqueTcNo()), authHeaderFor(parentUser)), ApiError.class);

        assertThat(second.getStatusCode())
                .as("v1: tek öğrenci/veli - zaten PENDING_APPROVAL öğrencisi olan veli ikinci kez kayıt yapamamalı")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_tcNoAlreadyUsedByAnotherStudent_returns409() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        String tcNo = uniqueTcNo();
        Student existing = new Student();
        existing.setFullName("Var Olan Öğrenci");
        existing.setBirthDate(LocalDate.of(2014, 1, 1));
        existing.setTcNo(tcNo);
        existing.setActive(true);
        studentRepository.save(existing);

        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 4");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(validRequest(tcNo), authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_whenDefaultMonthlyFeeIsNull_returns409() {
        setDefaultMonthlyFee(null);
        User parentUser = createParentUser();
        createParentProfile(parentUser, "Öz Kayıt Velisi 5");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(validRequest(uniqueTcNo()), authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_asNonParent_returns403() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        for (Role role : new Role[] {Role.ADMIN, Role.DRIVER, Role.HOSTESS, Role.STUDENT}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);

            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/parents/me/student-enrollments", HttpMethod.POST,
                    new HttpEntity<>(validRequest(uniqueTcNo()), authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void create_parentIdAlwaysResolvedFromPrincipal_eachParentOnlyLinkedToOwnStudent() {
        setDefaultMonthlyFee(new BigDecimal("1250.00"));
        User parentUserA = createParentUser();
        Parent parentA = createParentProfile(parentUserA, "Veli A");
        User parentUserB = createParentUser();
        Parent parentB = createParentProfile(parentUserB, "Veli B");

        ResponseEntity<EnrollmentResponse> responseA = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(validRequest(uniqueTcNo()), authHeaderFor(parentUserA)), EnrollmentResponse.class);
        ResponseEntity<EnrollmentResponse> responseB = restTemplate().exchange(
                "/api/parents/me/student-enrollments", HttpMethod.POST,
                new HttpEntity<>(validRequest(uniqueTcNo()), authHeaderFor(parentUserB)), EnrollmentResponse.class);

        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Long studentIdA = responseA.getBody().student().id();
        Long studentIdB = responseB.getBody().student().id();

        assertThat(parentStudentLinkRepository.existsByParentIdAndStudentIdAndActiveTrue(parentA.getId(), studentIdA)).isTrue();
        assertThat(parentStudentLinkRepository.existsByParentIdAndStudentIdAndActiveTrue(parentB.getId(), studentIdB)).isTrue();
        assertThat(parentStudentLinkRepository.existsByParentIdAndStudentIdAndActiveTrue(parentA.getId(), studentIdB))
                .as("A velisi B'nin öğrencisine bağlanmamalı - parentId her zaman principal'dan çözülür")
                .isFalse();
        assertThat(parentStudentLinkRepository.existsByParentIdAndStudentIdAndActiveTrue(parentB.getId(), studentIdA))
                .isFalse();
    }
}
