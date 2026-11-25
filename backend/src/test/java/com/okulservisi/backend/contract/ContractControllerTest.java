package com.okulservisi.backend.contract;
import com.okulservisi.backend.contract.entity.ContractStatus;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.contract.dto.request.ContractCreateRequest;
import com.okulservisi.backend.contract.dto.response.ContractResponse;
import com.okulservisi.backend.contract.dto.request.ContractUpdateRequest;
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
 * docs/API.md "11. Sözleşme" (bkz. docs/RBAC_MATRIX.md). docs/TEST_PLAN.md eşleştirmesi: Faz G
 * yeni modül — overlap kontrolü (ACTIVE-ACTIVE çakışma reddi, CANCELLED ile çakışma serbest),
 * RBAC (DRIVER/HOSTESS/STUDENT 403, PARENT scope+404), validasyon (monthlyFee<=0 -> 400).
 */
class ContractControllerTest extends AbstractIntegrationTest {

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

    private ContractCreateRequest createRequest(Long studentId, LocalDate startDate, LocalDate endDate) {
        return new ContractCreateRequest(studentId, new BigDecimal("1500.00"), startDate, endDate, null);
    }

    @Test
    void create_asAdmin_startsActive() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Sözleşme Öğrenci 1");

        ResponseEntity<ContractResponse> response = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 1, 1), null), authHeaderFor(admin)),
                ContractResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(response.getBody().discountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void create_asNonAdmin_returns403() {
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Student student = createStudent("Sözleşme Öğrenci 2");

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 1, 1), null), authHeaderFor(driverUser)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_nonPositiveMonthlyFee_returns400WithFieldError() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Sözleşme Öğrenci 3");
        ContractCreateRequest invalid = new ContractCreateRequest(student.getId(), BigDecimal.ZERO, LocalDate.of(2026, 1, 1), null, null);

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(invalid, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).anyMatch(fe -> fe.field().equals("monthlyFee"));
    }

    @Test
    void create_overlappingActiveContractForSameStudent_isRejected() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Sözleşme Öğrenci 4");

        ResponseEntity<ContractResponse> first = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)), authHeaderFor(admin)),
                ContractResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> overlapping = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 3, 1), null), authHeaderFor(admin)),
                ApiError.class);

        assertThat(overlapping.getStatusCode())
                .as("aynı öğrenci, tarih aralığı çakışan ikinci ACTIVE sözleşme reddedilmeli")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_afterCancellingFirst_overlappingDatesAreAllowed() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Sözleşme Öğrenci 5");

        ResponseEntity<ContractResponse> first = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30)), authHeaderFor(admin)),
                ContractResponse.class);
        Long firstId = first.getBody().id();

        ResponseEntity<ContractResponse> cancelled = restTemplate().exchange(
                "/api/contracts/" + firstId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), ContractResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(ContractStatus.CANCELLED);

        // İptal edilmiş sözleşmeyle tarih çakışması artık engel değil.
        ResponseEntity<ContractResponse> second = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 3, 1), null), authHeaderFor(admin)),
                ContractResponse.class);
        assertThat(second.getStatusCode())
                .as("CANCELLED durumundaki sözleşme çakışma sayılmamalı")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void update_monthlyFee_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Sözleşme Öğrenci 6");
        ResponseEntity<ContractResponse> created = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 1, 1), null), authHeaderFor(admin)),
                ContractResponse.class);

        ContractUpdateRequest update = new ContractUpdateRequest(new BigDecimal("1750.00"), new BigDecimal("50.00"), null);
        ResponseEntity<ContractResponse> updated = restTemplate().exchange(
                "/api/contracts/" + created.getBody().id(), HttpMethod.PATCH,
                new HttpEntity<>(update, authHeaderFor(admin)), ContractResponse.class);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().monthlyFee()).isEqualByComparingTo(new BigDecimal("1750.00"));
        assertThat(updated.getBody().discountAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void cancel_alreadyCancelled_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Sözleşme Öğrenci 7");
        ResponseEntity<ContractResponse> created = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 1, 1), null), authHeaderFor(admin)),
                ContractResponse.class);
        Long id = created.getBody().id();

        restTemplate().exchange("/api/contracts/" + id + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), ContractResponse.class);

        ResponseEntity<ApiError> secondCancel = restTemplate().exchange("/api/contracts/" + id + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), ApiError.class);
        assertThat(secondCancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getById_asParentOfChild_succeeds_andOtherParent_returns404() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student ownChild = createStudent("Sözleşme Kapsam Öğrenci 1");
        Student otherChild = createStudent("Sözleşme Kapsam Öğrenci 2");

        ResponseEntity<ContractResponse> contract = restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(ownChild.getId(), LocalDate.of(2026, 1, 1), null), authHeaderFor(admin)),
                ContractResponse.class);

        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Sözleşme Kapsam Veli");
        parent = parentRepository.save(parent);
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(ownChild);
        link.setRelationType("MOTHER");
        link.setActive(true);
        parentStudentLinkRepository.save(link);

        ResponseEntity<ContractResponse> accessible = restTemplate().exchange(
                "/api/contracts/" + contract.getBody().id(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ContractResponse.class);
        assertThat(accessible.getStatusCode()).isEqualTo(HttpStatus.OK);

        User otherParentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent otherParent = new Parent();
        otherParent.setUser(otherParentUser);
        otherParent.setFullName("Diğer Veli");
        otherParent = parentRepository.save(otherParent);
        ParentStudentLink otherLink = new ParentStudentLink();
        otherLink.setParent(otherParent);
        otherLink.setStudent(otherChild);
        otherLink.setRelationType("FATHER");
        otherLink.setActive(true);
        parentStudentLinkRepository.save(otherLink);

        ResponseEntity<ApiError> notAccessible = restTemplate().exchange(
                "/api/contracts/" + contract.getBody().id(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(otherParentUser)), ApiError.class);
        assertThat(notAccessible.getStatusCode())
                .as("bağlı olmayan veli için sözleşme 404 dönmeli (403 DEĞİL)")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_asAdmin_returns200() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Sözleşme Liste Öğrenci");
        restTemplate().exchange("/api/contracts", HttpMethod.POST,
                new HttpEntity<>(createRequest(student.getId(), LocalDate.of(2026, 1, 1), null), authHeaderFor(admin)),
                ContractResponse.class);

        ResponseEntity<String> response = restTemplate().exchange("/api/contracts", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), String.class);

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void list_asDriverOrHostessOrStudent_returns403() {
        Student student = createStudent("Sözleşme Öğrenci 8");
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        User hostessUser = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        User studentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);

        for (User user : new User[] {driverUser, hostessUser, studentUser}) {
            ResponseEntity<ApiError> response = restTemplate().exchange("/api/contracts", HttpMethod.GET,
                    new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode())
                    .as(user.getRole() + " sözleşme listesine erişememeli")
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(student).isNotNull();
    }
}
