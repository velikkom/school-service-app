package com.okulservisi.backend.contract;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.contract.repository.ContractRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.contract.dto.request.ContractApproveRequest;
import com.okulservisi.backend.contract.dto.request.ContractRejectRequest;
import com.okulservisi.backend.contract.dto.response.ContractResponse;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md §19 "Veli öz-kayıt" — {@code POST /api/contracts/{id}/approve}/{@code /reject}
 * (ADMIN only, SADECE PENDING_APPROVAL durumunda izinli). {@code RegistrationRequestControllerTest}
 * ile AYNI desen: gerçek izole Testcontainers Postgres'e karşı.
 */
class ContractApprovalControllerTest extends AbstractIntegrationTest {

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
        student.setBirthDate(LocalDate.of(2016, 4, 1));
        student.setActive(true);
        return studentRepository.save(student);
    }

    private Contract createContract(Student student, ContractStatus status, LocalDate startDate, LocalDate endDate) {
        Contract contract = new Contract();
        contract.setStudent(student);
        contract.setMonthlyFee(new BigDecimal("1000.00"));
        contract.setDiscountAmount(BigDecimal.ZERO);
        contract.setStartDate(startDate);
        contract.setEndDate(endDate);
        contract.setStatus(status);
        return contractRepository.save(contract);
    }

    @Test
    void approve_pendingApproval_asAdmin_setsActiveAndAppliesNewFee() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Onay Öğrenci 1");
        Contract pending = createContract(student, ContractStatus.PENDING_APPROVAL, LocalDate.now(), null);

        ResponseEntity<ContractResponse> response = restTemplate().exchange(
                "/api/contracts/" + pending.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new ContractApproveRequest(new BigDecimal("1800.00"), null, null), authHeaderFor(admin)),
                ContractResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(response.getBody().monthlyFee()).isEqualByComparingTo(new BigDecimal("1800.00"));
        assertThat(response.getBody().studentFullName())
                .as("ADMIN onay ekranında öğrenci adının görünmesi için (docs/API.md §11)")
                .isEqualTo("Onay Öğrenci 1");
    }

    @Test
    void approve_withOverlappingActiveContractForSameStudent_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Onay Öğrenci 2");
        createContract(student, ContractStatus.ACTIVE, LocalDate.of(2026, 1, 1), null);
        Contract pending = createContract(student, ContractStatus.PENDING_APPROVAL, LocalDate.of(2026, 3, 1), null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/contracts/" + pending.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new ContractApproveRequest(new BigDecimal("1800.00"), null, null), authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode())
                .as("ACTIVE'e geçerken aynı öğrencinin başka ACTIVE sözleşmesiyle çakışma reddedilmeli")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approve_whenStatusIsNotPendingApproval_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Onay Öğrenci 3");
        Contract active = createContract(student, ContractStatus.ACTIVE, LocalDate.now(), null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/contracts/" + active.getId() + "/approve", HttpMethod.POST,
                new HttpEntity<>(new ContractApproveRequest(new BigDecimal("1800.00"), null, null), authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reject_pendingApproval_asAdmin_cancelsContractAndDeactivatesStudentAndRevokesLink() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Student student = createStudent("Red Öğrenci 1");
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Red Veli 1");
        parent = parentRepository.save(parent);
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationType("MOTHER");
        link.setActive(true);
        link = parentStudentLinkRepository.save(link);
        Contract pending = createContract(student, ContractStatus.PENDING_APPROVAL, LocalDate.now(), null);

        ResponseEntity<ContractResponse> response = restTemplate().exchange(
                "/api/contracts/" + pending.getId() + "/reject", HttpMethod.POST,
                new HttpEntity<>(new ContractRejectRequest("eksik belge"), authHeaderFor(admin)),
                ContractResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(response.getBody().studentFullName()).isEqualTo("Red Öğrenci 1");

        Student reloadedStudent = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(reloadedStudent.isActive()).as("reddedilen öğrencinin isActive'i false olmalı").isFalse();

        ParentStudentLink reloadedLink = parentStudentLinkRepository.findById(link.getId()).orElseThrow();
        assertThat(reloadedLink.isActive()).as("bağlantı revoke edilmeli").isFalse();
        assertThat(reloadedLink.getRevokedAt()).isNotNull();
    }

    @Test
    void reject_whenStatusIsNotPendingApproval_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Red Öğrenci 2");
        Contract active = createContract(student, ContractStatus.ACTIVE, LocalDate.now(), null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/contracts/" + active.getId() + "/reject", HttpMethod.POST,
                new HttpEntity<>(new ContractRejectRequest(null), authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void list_pendingApproval_includesStudentFullName() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Liste Öğrenci 1");
        createContract(student, ContractStatus.PENDING_APPROVAL, LocalDate.now(), null);

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/contracts?status=PENDING_APPROVAL", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("liste ekranı 'Öğrenci #id' yerine gerçek adı göstermeli (ADMIN'de bulunan gerçek bug)")
                .contains("Liste Öğrenci 1");
    }

    @Test
    void approveAndReject_asNonAdmin_returns403() {
        Student student = createStudent("RBAC Öğrenci");
        Contract pending = createContract(student, ContractStatus.PENDING_APPROVAL, LocalDate.now(), null);
        for (Role role : new Role[] {Role.DRIVER, Role.HOSTESS, Role.PARENT, Role.STUDENT}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);

            ResponseEntity<ApiError> approveResponse = restTemplate().exchange(
                    "/api/contracts/" + pending.getId() + "/approve", HttpMethod.POST,
                    new HttpEntity<>(new ContractApproveRequest(new BigDecimal("1800.00"), null, null), authHeaderFor(user)),
                    ApiError.class);
            assertThat(approveResponse.getStatusCode()).as("role=%s approve", role).isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<ApiError> rejectResponse = restTemplate().exchange(
                    "/api/contracts/" + pending.getId() + "/reject", HttpMethod.POST,
                    new HttpEntity<>(new ContractRejectRequest(null), authHeaderFor(user)),
                    ApiError.class);
            assertThat(rejectResponse.getStatusCode()).as("role=%s reject", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
