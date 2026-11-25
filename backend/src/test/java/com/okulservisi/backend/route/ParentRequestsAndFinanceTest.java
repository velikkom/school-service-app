package com.okulservisi.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.contract.dto.request.ContractApproveRequest;
import com.okulservisi.backend.contract.dto.request.ContractRejectRequest;
import com.okulservisi.backend.contract.dto.response.ContractResponse;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.enrollment.dto.request.EnrollmentCreateRequest;
import com.okulservisi.backend.enrollment.dto.response.EnrollmentResponse;
import com.okulservisi.backend.payment.entity.Payment;
import com.okulservisi.backend.payment.entity.PaymentMethod;
import com.okulservisi.backend.payment.entity.PaymentStatus;
import com.okulservisi.backend.payment.repository.PaymentRepository;
import com.okulservisi.backend.route.dto.request.StopChangeDecisionRequest;
import com.okulservisi.backend.route.dto.request.StopChangeRequestCreateRequest;
import com.okulservisi.backend.route.dto.response.StopChangeRequestResponse;
import com.okulservisi.backend.route.dto.response.StopOptionResponse;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.Stop;
import com.okulservisi.backend.route.entity.StopChangeRequestStatus;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.repository.SchoolRepository;
import com.okulservisi.backend.settings.entity.InstitutionSettings;
import com.okulservisi.backend.settings.repository.InstitutionSettingsRepository;
import com.okulservisi.backend.settings.service.InstitutionSettingsService;
import com.okulservisi.backend.student.entity.Gender;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.support.AbstractOperationsTest;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 2026-11-18 — docs/API.md §25 (durak değişikliği), §19/§27 (ikinci çocuk başvurusu) ve §27 (makbuz/
 * ekstre belgeleri): onayın doğru ilişki/atamayı ürettiği, kapsam ve STUDENT'ın finans verisine
 * erişemediği doğrulanır.
 */
class ParentRequestsAndFinanceTest extends AbstractOperationsTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SchoolRepository schoolRepository;
    @Autowired private InstitutionSettingsService institutionSettingsService;
    @Autowired private InstitutionSettingsRepository institutionSettingsRepository;

    private <T> ResponseEntity<T> post(String url, Object body, HttpHeaders headers, Class<T> type) {
        return restTemplate().exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    @Test
    void stopChange_approvalClosesOldAssignmentAndOpensNewOne_andNotifiesParent() {
        User admin = user(Role.ADMIN);
        Route route = route(RouteDirection.MORNING);
        Stop oldStop = stop(route, 1);
        Route otherRoute = route(RouteDirection.MORNING);
        Stop newStop = stop(otherRoute, 3);
        Stop wrongDirectionStop = stop(route(RouteDirection.AFTERNOON), 1);
        Student student = student("Durak Öğrencisi", null);
        StudentRouteAssignment current = assign(student, oldStop, today().minusDays(20), null);
        var parent = parentOf(student);
        LocalDate effective = today().plusDays(3);

        // Seçenekler: veli kendi çocuğu için görür, yabancı veli 404.
        StopOptionResponse[] options = restTemplate().exchange("/api/stop-change-requests/options?studentId="
                + student.getId() + "&direction=MORNING", HttpMethod.GET, new HttpEntity<>(auth(parent.getUser())),
                StopOptionResponse[].class).getBody();
        assertThat(options).extracting(StopOptionResponse::stopId).contains(newStop.getId())
                .doesNotContain(wrongDirectionStop.getId());
        Student stranger = student("Yabancı", null);
        assertThat(restTemplate().exchange("/api/stop-change-requests/options?studentId=" + student.getId()
                + "&direction=MORNING", HttpMethod.GET, new HttpEntity<>(auth(parentOf(stranger).getUser())),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        StopChangeRequestCreateRequest body = new StopChangeRequestCreateRequest(student.getId(),
                RouteDirection.MORNING, newStop.getId(), effective, "Taşındık");
        assertThat(post("/api/stop-change-requests", new StopChangeRequestCreateRequest(student.getId(),
                RouteDirection.MORNING, wrongDirectionStop.getId(), effective, null), auth(parent.getUser()),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/stop-change-requests", new StopChangeRequestCreateRequest(student.getId(),
                RouteDirection.MORNING, newStop.getId(), today().minusDays(1), null), auth(parent.getUser()),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/stop-change-requests", body, auth(parentOf(stranger).getUser()), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<StopChangeRequestResponse> created = post("/api/stop-change-requests", body,
                auth(parent.getUser()), StopChangeRequestResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().currentStopName()).isEqualTo(oldStop.getName());
        assertThat(post("/api/stop-change-requests", body, auth(parent.getUser()), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        Long id = created.getBody().id();

        // Veli onaylayamaz.
        assertThat(post("/api/stop-change-requests/" + id + "/approve", null, auth(parent.getUser()), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<StopChangeRequestResponse> approved = post("/api/stop-change-requests/" + id + "/approve",
                new StopChangeDecisionRequest("Uygun", null), auth(admin), StopChangeRequestResponse.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status()).isEqualTo(StopChangeRequestStatus.APPROVED);

        List<StudentRouteAssignment> assignments = assignmentRepository
                .findByStudentIdAndDirection(student.getId(), RouteDirection.MORNING).stream()
                .sorted(Comparator.comparing(StudentRouteAssignment::getValidFrom)).toList();
        assertThat(assignments).hasSize(2);
        assertThat(assignments.get(0).getId()).isEqualTo(current.getId());
        assertThat(assignments.get(0).getValidTo()).isEqualTo(effective.minusDays(1));
        assertThat(assignments.get(1).getStop().getId()).isEqualTo(newStop.getId());
        assertThat(assignments.get(1).getRoute().getId()).isEqualTo(otherRoute.getId());
        assertThat(assignments.get(1).getValidFrom()).isEqualTo(effective);
        assertThat(assignments.get(1).getValidTo()).isNull();
        assertThat(approved.getBody().resultingAssignmentId()).isEqualTo(assignments.get(1).getId());
        assertThat(notificationCount(parent.getUser(), "stop-change-request:" + id + ":decided")).isEqualTo(1);

        // İkinci sonuçlandırma: 409.
        assertThat(post("/api/stop-change-requests/" + id + "/reject", new StopChangeDecisionRequest(null, "x"),
                auth(admin), ApiError.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void stopChange_rejectNeedsReason_andConflictingFutureAssignmentBlocksApproval() {
        User admin = user(Role.ADMIN);
        Route route = route(RouteDirection.AFTERNOON);
        Stop stopA = stop(route, 1);
        Stop stopB = stop(route, 2);
        Student student = student("Çakışma Öğrencisi", null);
        // Bugün aktif atama yok, ama ileride başlayan bir atama var.
        assign(student, stopA, today().plusDays(10), null);
        var parent = parentOf(student);
        Long id = post("/api/stop-change-requests", new StopChangeRequestCreateRequest(student.getId(),
                RouteDirection.AFTERNOON, stopB.getId(), today().plusDays(1), null), auth(parent.getUser()),
                StopChangeRequestResponse.class).getBody().id();

        assertThat(post("/api/stop-change-requests/" + id + "/approve", null, auth(admin), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/api/stop-change-requests/" + id + "/reject", new StopChangeDecisionRequest(null, ""),
                auth(admin), ApiError.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<StopChangeRequestResponse> rejected = post("/api/stop-change-requests/" + id + "/reject",
                new StopChangeDecisionRequest(null, "Hat dolu"), auth(admin), StopChangeRequestResponse.class);
        assertThat(rejected.getBody().status()).isEqualTo(StopChangeRequestStatus.REJECTED);
        assertThat(rejected.getBody().decisionNote()).isEqualTo("Hat dolu");
        assertThat(notificationCount(parent.getUser(), "stop-change-request:" + id + ":decided")).isEqualTo(1);
    }

    private School school() {
        School school = new School();
        school.setName("Ops Okul " + UUID.randomUUID());
        school.setActive(true);
        return schoolRepository.save(school);
    }

    private String tcNo() {
        return String.valueOf(20_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
    }

    private EnrollmentCreateRequest enrollment(String name) {
        String tc = tcNo();
        return new EnrollmentCreateRequest(school().getId(), "FATHER", "05551112233",
                new EnrollmentCreateRequest.StudentPart(name, LocalDate.of(2017, 2, 1), tc, "2-B", Gender.MALE, null),
                new EnrollmentCreateRequest.AddressPart("İstanbul", "Üsküdar", "Mahalle", "Sokak 1"), true);
    }

    @Test
    void secondChild_allowedAfterFirstApproved_singlePending_andApprovalCreatesNoDuplicates() {
        InstitutionSettings settings = institutionSettingsService.get();
        settings.setDefaultMonthlyFee(new BigDecimal("1250.00"));
        institutionSettingsRepository.save(settings);
        User admin = user(Role.ADMIN);
        Student first = student("İlk Çocuk", null);
        var parent = parentOf(first);
        activeContract(first);
        String url = "/api/parents/me/student-enrollments";

        ResponseEntity<EnrollmentResponse> second = post(url, enrollment("İkinci Çocuk"), auth(parent.getUser()),
                EnrollmentResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody().contract().status()).isEqualTo(ContractStatus.PENDING_APPROVAL);

        // Bekleyen başvuru varken üçüncü başvuru: 409.
        assertThat(post(url, enrollment("Üçüncü Çocuk"), auth(parent.getUser()), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Long contractId = second.getBody().contract().id();
        Long secondStudentId = second.getBody().student().id();
        ResponseEntity<ContractResponse> approved = post("/api/contracts/" + contractId + "/approve",
                new ContractApproveRequest(new BigDecimal("1100.00"), null, null), auth(admin), ContractResponse.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status()).isEqualTo(ContractStatus.ACTIVE);
        // İkinci onay: 409, yinelenen aktif sözleşme yok.
        assertThat(post("/api/contracts/" + contractId + "/approve",
                new ContractApproveRequest(new BigDecimal("1100.00"), null, null), auth(admin), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contractRepository.findByStudentId(secondStudentId))
                .filteredOn(c -> c.getStatus() == ContractStatus.ACTIVE).hasSize(1);
        assertThat(parentStudentLinkRepository.findByStudentIdAndActiveTrue(secondStudentId)).hasSize(1);
        assertThat(parentStudentLinkRepository.findByParentIdAndActiveTrue(parent.getId())).hasSize(2);
        assertThat(notificationCount(parent.getUser(), "contract:" + contractId + ":approved")).isEqualTo(1);

        // Onaydan sonra yeni bir başvuru tekrar mümkün; ret gerekçesi saklanır ve veliye bildirilir.
        ResponseEntity<EnrollmentResponse> third = post(url, enrollment("Üçüncü Çocuk"), auth(parent.getUser()),
                EnrollmentResponse.class);
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long thirdContractId = third.getBody().contract().id();
        ResponseEntity<ContractResponse> rejected = post("/api/contracts/" + thirdContractId + "/reject",
                new ContractRejectRequest("Kontenjan dolu"), auth(admin), ContractResponse.class);
        assertThat(rejected.getBody().status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(rejected.getBody().rejectionReason()).isEqualTo("Kontenjan dolu");
        assertThat(notificationCount(parent.getUser(), "contract:" + thirdContractId + ":rejected")).isEqualTo(1);
    }

    private Payment payment(Student student, User admin) {
        Payment payment = new Payment();
        payment.setStudent(student);
        payment.setAmount(new BigDecimal("1250.00"));
        payment.setMethod(PaymentMethod.CASH);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCreatedBy(admin);
        return paymentRepository.save(payment);
    }

    @Test
    void financeDocuments_parentOwnChildOnly_studentForbidden() {
        User admin = user(Role.ADMIN);
        User studentUser = user(Role.STUDENT);
        Student child = student("Belge Öğrencisi", studentUser);
        var parent = parentOf(child);
        Payment payment = payment(child, admin);
        Student strangerChild = student("Yabancı Belge", null);
        var stranger = parentOf(strangerChild);

        ResponseEntity<String> receipt = restTemplate().exchange("/api/payments/" + payment.getId() + "/receipt",
                HttpMethod.GET, new HttpEntity<>(auth(parent.getUser())), String.class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receipt.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(receipt.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("makbuz-" + payment.getId() + ".html");
        assertThat(receipt.getBody()).contains("Ödeme Makbuzu").contains("1.250,00 TL").contains("Belge Öğrencisi")
                .contains("Nakit");

        ResponseEntity<String> statement = restTemplate().exchange("/api/students/" + child.getId()
                + "/statement/document", HttpMethod.GET, new HttpEntity<>(auth(parent.getUser())), String.class);
        assertThat(statement.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statement.getBody()).contains("Hesap Ekstresi").contains("-1.250,00 TL");

        // Başka velinin çocuğu: 404.
        assertThat(restTemplate().exchange("/api/payments/" + payment.getId() + "/receipt", HttpMethod.GET,
                new HttpEntity<>(auth(stranger.getUser())), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate().exchange("/api/students/" + child.getId() + "/statement/document", HttpMethod.GET,
                new HttpEntity<>(auth(stranger.getUser())), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // STUDENT finans uç noktalarına erişemez (kendi kaydı olsa bile).
        for (String url : List.of("/api/payments/" + payment.getId() + "/receipt",
                "/api/students/" + child.getId() + "/statement/document",
                "/api/students/" + child.getId() + "/statement",
                "/api/payments?studentId=" + child.getId())) {
            assertThat(restTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(auth(studentUser)), String.class)
                    .getStatusCode()).as(url).isEqualTo(HttpStatus.FORBIDDEN);
        }
        // ADMIN her belgeye erişir.
        assertThat(restTemplate().exchange("/api/payments/" + payment.getId() + "/receipt", HttpMethod.GET,
                new HttpEntity<>(auth(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
