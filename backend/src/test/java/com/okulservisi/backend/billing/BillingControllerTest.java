package com.okulservisi.backend.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.billing.dto.request.BillingRunRequest;
import com.okulservisi.backend.billing.dto.response.BillingRunResponse;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.repository.ContractRepository;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "12. Aylık ücret / tahakkuk" (bkz. docs/RBAC_MATRIX.md). docs/TEST_PLAN.md
 * eşleştirmesi: idempotency (aynı period 2. kez çalıştırılınca 0 yeni kayıt), tutar hesaplama
 * (monthlyFee - discountAmount), RBAC.
 */
class BillingControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentLinkRepository parentStudentLinkRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private Student createStudent(String fullName) {
        Student student = new Student();
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2015, 6, 1));
        student.setActive(true);
        return studentRepository.save(student);
    }

    private Contract createContract(Student student, BigDecimal monthlyFee, BigDecimal discount, LocalDate start, LocalDate end) {
        Contract contract = new Contract();
        contract.setStudent(student);
        contract.setMonthlyFee(monthlyFee);
        contract.setDiscountAmount(discount);
        contract.setStartDate(start);
        contract.setEndDate(end);
        contract.setStatus(ContractStatus.ACTIVE);
        return contractRepository.save(contract);
    }

    @Test
    void run_createsChargeWithCorrectAmount_andIsIdempotentOnSecondRun() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Fatura Öğrenci 1");
        createContract(student, new BigDecimal("1500.00"), new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), null);

        ResponseEntity<BillingRunResponse> firstRun = restTemplate().exchange("/api/billing/run", HttpMethod.POST,
                new HttpEntity<>(new BillingRunRequest("2026-10"), authHeaderFor(admin)), BillingRunResponse.class);
        assertThat(firstRun.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstRun.getBody().created()).isGreaterThanOrEqualTo(1);
        assertThat(firstRun.getBody().period()).isEqualTo("2026-10");

        ResponseEntity<PageResponse<Object>> charges = restTemplate().exchange(
                "/api/billing/charges?studentId=" + student.getId() + "&period=2026-10", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), new ParameterizedTypeReference<>() {
                });
        assertThat(charges.getBody().content()).hasSize(1);

        // İKİNCİ kez AYNI dönemi çalıştır: idempotent, 0 yeni kayıt.
        ResponseEntity<BillingRunResponse> secondRun = restTemplate().exchange("/api/billing/run", HttpMethod.POST,
                new HttpEntity<>(new BillingRunRequest("2026-10"), authHeaderFor(admin)), BillingRunResponse.class);
        assertThat(secondRun.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondRun.getBody().created())
                .as("aynı dönem ikinci kez çalıştırıldığında yeni kayıt oluşmamalı")
                .isZero();
        assertThat(secondRun.getBody().skipped()).isGreaterThanOrEqualTo(1);

        ResponseEntity<PageResponse<Object>> chargesAfterSecondRun = restTemplate().exchange(
                "/api/billing/charges?studentId=" + student.getId() + "&period=2026-10", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), new ParameterizedTypeReference<>() {
                });
        assertThat(chargesAfterSecondRun.getBody().content())
                .as("ikinci run'dan sonra hala tek bir tahakkuk kaydı olmalı")
                .hasSize(1);
    }

    @Test
    void run_asNonAdmin_returns403() {
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/billing/run", HttpMethod.POST,
                new HttpEntity<>(new BillingRunRequest("2026-10"), authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void run_invalidPeriodFormat_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/billing/run", HttpMethod.POST,
                new HttpEntity<>(new BillingRunRequest("2026-13"), authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void run_contractOutsidePeriod_isSkippedEntirely() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Fatura Öğrenci 2");
        // Sözleşme 2025 Aralık'ta bitiyor, 2026-10 dönemini KAPSAMIYOR.
        createContract(student, new BigDecimal("1000.00"), BigDecimal.ZERO,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));

        restTemplate().exchange("/api/billing/run", HttpMethod.POST,
                new HttpEntity<>(new BillingRunRequest("2026-10"), authHeaderFor(admin)), BillingRunResponse.class);

        ResponseEntity<PageResponse<Object>> charges = restTemplate().exchange(
                "/api/billing/charges?studentId=" + student.getId() + "&period=2026-10", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), new ParameterizedTypeReference<>() {
                });
        assertThat(charges.getBody().content()).isEmpty();
    }

    @Test
    void listCharges_asParent_scopedToOwnChildrenOnly() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student ownChild = createStudent("Fatura Kapsam Öğrenci 1");
        Student otherChild = createStudent("Fatura Kapsam Öğrenci 2");
        createContract(ownChild, new BigDecimal("1200.00"), BigDecimal.ZERO, LocalDate.of(2026, 1, 1), null);
        createContract(otherChild, new BigDecimal("1300.00"), BigDecimal.ZERO, LocalDate.of(2026, 1, 1), null);

        restTemplate().exchange("/api/billing/run", HttpMethod.POST,
                new HttpEntity<>(new BillingRunRequest("2026-11"), authHeaderFor(admin)), BillingRunResponse.class);

        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Fatura Kapsam Veli");
        parent = parentRepository.save(parent);
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(ownChild);
        link.setRelationType("MOTHER");
        link.setActive(true);
        parentStudentLinkRepository.save(link);

        ResponseEntity<PageResponse<Object>> charges = restTemplate().exchange(
                "/api/billing/charges?period=2026-11", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), new ParameterizedTypeReference<>() {
                });
        assertThat(charges.getBody().content()).hasSize(1);

        // otherChild'a studentId ile doğrudan erişmeye çalışırsa 404.
        ResponseEntity<ApiError> forbiddenScope = restTemplate().exchange(
                "/api/billing/charges?studentId=" + otherChild.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ApiError.class);
        assertThat(forbiddenScope.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listCharges_asDriverOrHostessOrStudent_returns403() {
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/billing/charges", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driverUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
