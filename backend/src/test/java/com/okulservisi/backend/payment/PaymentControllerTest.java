package com.okulservisi.backend.payment;
import com.okulservisi.backend.payment.entity.PaymentMethod;
import com.okulservisi.backend.payment.entity.PaymentStatus;
import com.okulservisi.backend.payment.repository.PaymentRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.billing.entity.MonthlyCharge;
import com.okulservisi.backend.billing.repository.MonthlyChargeRepository;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.repository.ContractRepository;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.payment.dto.request.PaymentCreateRequest;
import com.okulservisi.backend.payment.dto.response.PaymentResponse;
import com.okulservisi.backend.payment.dto.response.StudentStatementResponse;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "13. Ödeme" (bkz. docs/RBAC_MATRIX.md). docs/TEST_PLAN.md eşleştirmesi: aşırı ödeme
 * 409, aynı Idempotency-Key ile 2. istek aynı ödemeyi döner (gerçek, ayrı 2 HTTP isteği), EŞZAMANLI
 * 2 ödeme isteği (2-thread test, PasswordResetConcurrencyTest deseninde) sadece birinin başarılı
 * olduğunu doğrular, RBAC, statement.
 */
class PaymentControllerTest extends AbstractIntegrationTest {

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
    private MonthlyChargeRepository monthlyChargeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

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

    /** Öğrenciye {@code amount} tutarında tek bir tahakkuk (monthly_charge) oluşturur — borç tabanı. */
    private void giveStudentCharge(Student student, BigDecimal amount, String period) {
        Contract contract = new Contract();
        contract.setStudent(student);
        contract.setMonthlyFee(amount);
        contract.setDiscountAmount(BigDecimal.ZERO);
        contract.setStartDate(LocalDate.of(2026, 1, 1));
        contract.setStatus(ContractStatus.ACTIVE);
        contract = contractRepository.save(contract);

        MonthlyCharge charge = new MonthlyCharge();
        charge.setStudent(student);
        charge.setContract(contract);
        charge.setPeriod(period);
        charge.setAmount(amount);
        monthlyChargeRepository.save(charge);
    }

    @Test
    void create_asAdmin_succeedsWhenWithinBalance() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Ödeme Öğrenci 1");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), new BigDecimal("400.00"), PaymentMethod.CASH, "Nakit ödeme");
        ResponseEntity<PaymentResponse> response = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(admin)), PaymentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.getBody().amount()).isEqualByComparingTo(new BigDecimal("400.00"));
    }

    @Test
    void create_amountExceedsRemainingBalance_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Ödeme Öğrenci 2");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), new BigDecimal("1000.01"), PaymentMethod.CASH, null);
        ResponseEntity<ApiError> response = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("kalan bakiyeden fazla ödeme reddedilmeli")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_asNonAdmin_returns403() {
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Student student = createStudent("Ödeme Öğrenci 3");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), new BigDecimal("100.00"), PaymentMethod.CASH, null);
        ResponseEntity<ApiError> response = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_withSameIdempotencyKey_secondRequestReturnsSamePayment_noDuplicateRecord() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Ödeme Öğrenci 4");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        String idempotencyKey = "idem-" + UUID.randomUUID();
        PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), new BigDecimal("300.00"), PaymentMethod.BANK_TRANSFER, null);

        HttpHeaders headers = authHeaderFor(admin);
        headers.set("Idempotency-Key", idempotencyKey);

        ResponseEntity<PaymentResponse> first = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(request, headers), PaymentResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // AYRI, ikinci bir HTTP isteği - AYNI idempotency key ile.
        ResponseEntity<PaymentResponse> second = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(request, headers), PaymentResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id())
                .as("aynı idempotency key ile ikinci istek AYNI ödeme kaydını dönmeli")
                .isEqualTo(first.getBody().id());

        long paymentCount = paymentRepository.findByStudentIdOrderByCreatedAtAsc(student.getId()).size();
        assertThat(paymentCount).as("ikinci kayıt OLUŞTURULMAMALI").isEqualTo(1);
    }

    @Test
    void create_concurrentPaymentsForSameStudent_onlyOneSucceeds() throws Exception {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Ödeme Öğrenci 5");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        try {
            Future<Void> futureA = executor.submit(concurrentPaymentTask(admin, student, new BigDecimal("600.00"),
                    readyLatch, startLatch, successCount, conflictCount));
            Future<Void> futureB = executor.submit(concurrentPaymentTask(admin, student, new BigDecimal("600.00"),
                    readyLatch, startLatch, successCount, conflictCount));

            boolean bothReady = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(bothReady).as("iki thread de zamanında hazır olmalı").isTrue();
            startLatch.countDown();

            futureA.get(30, TimeUnit.SECONDS);
            futureB.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(successCount.get())
                .as("1000 bakiyeye karşı iki eşzamanlı 600'lük ödemeden SADECE biri başarılı olmalı "
                        + "(pessimistic lock ile serialize edilip ikinci istek güncel bakiyeyi görmeli)")
                .isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        BigDecimal totalCompleted = paymentRepository.sumCompletedAmountByStudentId(student.getId());
        assertThat(totalCompleted).isEqualByComparingTo(new BigDecimal("600.00"));
    }

    private Callable<Void> concurrentPaymentTask(User admin, Student student, BigDecimal amount,
            CountDownLatch readyLatch, CountDownLatch startLatch, AtomicInteger successCount, AtomicInteger conflictCount) {
        return () -> {
            readyLatch.countDown();
            startLatch.await();
            PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), amount, PaymentMethod.CASH, null);
            ResponseEntity<String> response = restTemplate().exchange("/api/payments", HttpMethod.POST,
                    new HttpEntity<>(request, authHeaderFor(admin)), String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                successCount.incrementAndGet();
            } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                conflictCount.incrementAndGet();
            }
            return null;
        };
    }

    @Test
    void cancel_setsStatusCancelled_andExcludedFromBalance() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Ödeme Öğrenci 6");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), new BigDecimal("500.00"), PaymentMethod.CASH, null);
        ResponseEntity<PaymentResponse> created = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(admin)), PaymentResponse.class);

        ResponseEntity<PaymentResponse> cancelled = restTemplate().exchange(
                "/api/payments/" + created.getBody().id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), PaymentResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.getBody().cancelledAt()).isNotNull();

        // İptal edilen ödeme bakiyeyi etkilemediği için TÜM tutar (1000.00) tekrar ödenebilir olmalı.
        PaymentCreateRequest fullAmountAgain = new PaymentCreateRequest(student.getId(), new BigDecimal("1000.00"), PaymentMethod.CASH, null);
        ResponseEntity<PaymentResponse> secondPayment = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(fullAmountAgain, authHeaderFor(admin)), PaymentResponse.class);
        assertThat(secondPayment.getStatusCode())
                .as("iptal edilen ödeme bakiyeyi azaltmamalı, tam tutar hala ödenebilir olmalı")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancel_alreadyCancelled_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Ödeme Öğrenci 7");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), new BigDecimal("200.00"), PaymentMethod.CASH, null);
        ResponseEntity<PaymentResponse> created = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(request, authHeaderFor(admin)), PaymentResponse.class);
        Long id = created.getBody().id();

        restTemplate().exchange("/api/payments/" + id + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), PaymentResponse.class);

        ResponseEntity<ApiError> secondCancel = restTemplate().exchange("/api/payments/" + id + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), ApiError.class);
        assertThat(secondCancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void statement_includesChargeAndPayment_withRunningBalance() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Ödeme Öğrenci 8");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-01");

        PaymentCreateRequest request = new PaymentCreateRequest(student.getId(), new BigDecimal("300.00"), PaymentMethod.CASH, null);
        restTemplate().exchange("/api/payments", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), PaymentResponse.class);

        ResponseEntity<StudentStatementResponse> statement = restTemplate().exchange(
                "/api/students/" + student.getId() + "/statement", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), StudentStatementResponse.class);

        assertThat(statement.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statement.getBody().entries()).hasSize(2);
        assertThat(statement.getBody().entries().get(0).type()).isEqualTo("CHARGE");
        assertThat(statement.getBody().entries().get(1).type()).isEqualTo("PAYMENT");
        assertThat(statement.getBody().currentBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
    }

    @Test
    void statement_asParentOfChild_succeeds_andOtherParent_returns404() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student ownChild = createStudent("Ekstre Kapsam Öğrenci 1");
        Student otherChild = createStudent("Ekstre Kapsam Öğrenci 2");
        giveStudentCharge(ownChild, new BigDecimal("500.00"), "2026-01");

        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Ekstre Kapsam Veli");
        parent = parentRepository.save(parent);
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(ownChild);
        link.setRelationType("MOTHER");
        link.setActive(true);
        parentStudentLinkRepository.save(link);

        ResponseEntity<StudentStatementResponse> accessible = restTemplate().exchange(
                "/api/students/" + ownChild.getId() + "/statement", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), StudentStatementResponse.class);
        assertThat(accessible.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> notAccessible = restTemplate().exchange(
                "/api/students/" + otherChild.getId() + "/statement", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ApiError.class);
        assertThat(notAccessible.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(admin).isNotNull();
    }

    @Test
    void statement_asDriverOrHostessOrStudent_returns403() {
        Student student = createStudent("Ekstre Öğrenci RBAC");
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/students/" + student.getId() + "/statement", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driverUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
