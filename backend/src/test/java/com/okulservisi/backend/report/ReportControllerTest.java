package com.okulservisi.backend.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.attendance.repository.AttendanceEventRepository;
import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.billing.entity.MonthlyCharge;
import com.okulservisi.backend.billing.repository.MonthlyChargeRepository;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.repository.ContractRepository;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.payment.entity.Payment;
import com.okulservisi.backend.payment.entity.PaymentMethod;
import com.okulservisi.backend.payment.repository.PaymentRepository;
import com.okulservisi.backend.payment.dto.request.PaymentCreateRequest;
import com.okulservisi.backend.payment.dto.response.PaymentResponse;
import com.okulservisi.backend.report.dto.response.CollectionsReportResponse;
import com.okulservisi.backend.report.dto.response.DailySummaryResponse;
import com.okulservisi.backend.report.dto.response.ReceivablesReportResponse;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "14. Dashboard / Rapor" (bkz. docs/RBAC_MATRIX.md: SADECE ADMIN).
 *
 * <p><b>ÖNEMLİ — test izolasyonu notu:</b> {@code daily-summary} belirli bir {@code businessDate}'e
 * göre sorgular — bu testlerde HİÇBİR başka test sınıfının kullanmadığı bir "nöbetçi" (sentinel)
 * tarih (2099 yılı) kullanılarak tam izolasyon sağlanır. ANCAK {@code collections}/{@code
 * receivables} TÜM veritabanı genelinde (tarih dışında herhangi bir kapsam filtresi olmadan,
 * docs/API.md kuralı böyle) toplam alır — bu yüzden bu iki rapor testinde MUTLAK değerler YERİNE
 * fikstürü oluşturmadan ÖNCE/SONRA alınan ölçümler arasındaki FARK (delta) doğrulanır; böylece aynı
 * Testcontainers Postgres'i paylaşan başka test sınıflarının (ör. PaymentControllerTest) verisi
 * sonucu YANLIŞ POZİTİF/NEGATİF üretmez.
 */
class ReportControllerTest extends AbstractIntegrationTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private AttendanceEventRepository attendanceEventRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private MonthlyChargeRepository monthlyChargeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private Route createRoute() {
        Route route = new Route();
        route.setName("Rapor Route " + UUID.randomUUID());
        route.setDirection(RouteDirection.MORNING);
        route.setActive(true);
        return routeRepository.save(route);
    }

    private Vehicle createVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("RPT-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    private Driver createDriver() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Driver driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNo("LIC-" + UUID.randomUUID());
        driver.setActive(true);
        return driverRepository.save(driver);
    }

    private Trip createTrip(Route route, LocalDate businessDate, TripStatus status) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(createVehicle());
        trip.setDriver(createDriver());
        trip.setBusinessDate(businessDate);
        trip.setDirection(RouteDirection.MORNING);
        trip.setStatus(status);
        return tripRepository.save(trip);
    }

    private Student createStudent(String fullName) {
        Student student = new Student();
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2015, 6, 1));
        student.setActive(true);
        return studentRepository.save(student);
    }

    private void recordAttendance(Trip trip, Student student, AttendanceEventType type, User recordedBy) {
        AttendanceEvent event = new AttendanceEvent();
        event.setTrip(trip);
        event.setStudent(student);
        event.setEventType(type);
        event.setRecordedAt(Instant.now());
        event.setRecordedBy(recordedBy);
        event.setCorrection(false);
        attendanceEventRepository.save(event);
    }

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
    void dailySummary_countsTripsByStatus_andAttendanceByType() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        LocalDate sentinelDate = LocalDate.of(2099, 1, 1); // Bu test sınıfına özel, başka hiçbir testte kullanılmaz.
        Route route = createRoute();

        Trip planned = createTrip(route, sentinelDate, TripStatus.PLANNED);
        Trip inProgress = createTrip(route, sentinelDate, TripStatus.IN_PROGRESS);
        Trip completed = createTrip(route, sentinelDate, TripStatus.COMPLETED);
        Trip cancelled = createTrip(route, sentinelDate, TripStatus.CANCELLED);
        assertThat(planned).isNotNull();
        assertThat(inProgress).isNotNull();
        assertThat(cancelled).isNotNull();

        Student studentA = createStudent("Rapor Öğrenci A");
        Student studentB = createStudent("Rapor Öğrenci B");
        recordAttendance(completed, studentA, AttendanceEventType.BOARDING, admin);
        recordAttendance(completed, studentA, AttendanceEventType.DROPOFF, admin);
        recordAttendance(completed, studentB, AttendanceEventType.ABSENCE, admin);

        ResponseEntity<DailySummaryResponse> response = restTemplate().exchange(
                "/api/reports/operations/daily-summary?date=" + sentinelDate, HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), DailySummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DailySummaryResponse body = response.getBody();
        assertThat(body.totalTrips()).isEqualTo(4);
        assertThat(body.plannedCount()).isEqualTo(1);
        assertThat(body.inProgressCount()).isEqualTo(1);
        assertThat(body.completedCount()).isEqualTo(1);
        assertThat(body.cancelledCount()).isEqualTo(1);
        assertThat(body.boardingCount()).isEqualTo(1);
        assertThat(body.dropoffCount()).isEqualTo(1);
        assertThat(body.absenceCount()).isEqualTo(1);
    }

    @Test
    void dailySummary_missingDateParam_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/reports/operations/daily-summary", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void dailySummary_asNonAdmin_returns403() {
        User driver = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/reports/operations/daily-summary?date=2099-01-01", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void collections_sumsOnlyCompletedPayments_todayRange_withMethodBreakdown() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        LocalDate today = LocalDate.now(ISTANBUL);
        String range = "from=" + today + "&to=" + today;

        CollectionsReportResponse baseline = restTemplate().exchange(
                "/api/reports/finance/collections?" + range, HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), CollectionsReportResponse.class).getBody();
        BigDecimal baselineCash = methodAmount(baseline, PaymentMethod.CASH);
        BigDecimal baselineBankTransfer = methodAmount(baseline, PaymentMethod.BANK_TRANSFER);

        Student student = createStudent("Rapor Tahsilat Öğrenci");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-10");

        PaymentCreateRequest cashPayment = new PaymentCreateRequest(student.getId(), new BigDecimal("400.00"), PaymentMethod.CASH, null);
        restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(cashPayment, authHeaderFor(admin)), PaymentResponse.class);
        PaymentCreateRequest bankPayment = new PaymentCreateRequest(student.getId(), new BigDecimal("100.00"), PaymentMethod.BANK_TRANSFER, null);
        restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(bankPayment, authHeaderFor(admin)), PaymentResponse.class);

        // İptal edilen ödeme SAYILMAMALI.
        PaymentCreateRequest cancelledPayment = new PaymentCreateRequest(student.getId(), new BigDecimal("50.00"), PaymentMethod.CARD, null);
        ResponseEntity<PaymentResponse> cancelledResponse = restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(cancelledPayment, authHeaderFor(admin)), PaymentResponse.class);
        restTemplate().exchange("/api/payments/" + cancelledResponse.getBody().id() + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), PaymentResponse.class);

        ResponseEntity<CollectionsReportResponse> after = restTemplate().exchange(
                "/api/reports/finance/collections?" + range, HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), CollectionsReportResponse.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        BigDecimal totalDelta = after.getBody().totalCollected().subtract(baseline.totalCollected());
        assertThat(totalDelta)
                .as("CASH 400 + BANK_TRANSFER 100 = 500 artmalı, iptal edilen 50 SAYILMAMALI")
                .isEqualByComparingTo(new BigDecimal("500.00"));

        BigDecimal cashDelta = methodAmount(after.getBody(), PaymentMethod.CASH).subtract(baselineCash);
        BigDecimal bankDelta = methodAmount(after.getBody(), PaymentMethod.BANK_TRANSFER).subtract(baselineBankTransfer);
        assertThat(cashDelta).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(bankDelta).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    private BigDecimal methodAmount(CollectionsReportResponse response, PaymentMethod method) {
        Map<PaymentMethod, BigDecimal> byMethod = response.byMethod().stream()
                .collect(Collectors.toMap(CollectionsReportResponse.MethodAmount::method, CollectionsReportResponse.MethodAmount::amount));
        return byMethod.getOrDefault(method, BigDecimal.ZERO);
    }

    @Test
    void collections_missingParams_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/reports/finance/collections", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void collections_asNonAdmin_returns403() {
        User hostess = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        LocalDate today = LocalDate.now(ISTANBUL);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/reports/finance/collections?from=" + today + "&to=" + today, HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(hostess)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void receivables_sumsChargesAndPayments_asDelta() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ReceivablesReportResponse baseline = restTemplate().exchange(
                "/api/reports/finance/receivables", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), ReceivablesReportResponse.class).getBody();

        Student student = createStudent("Rapor Alacak Öğrenci");
        giveStudentCharge(student, new BigDecimal("1000.00"), "2026-11");
        PaymentCreateRequest payment = new PaymentCreateRequest(student.getId(), new BigDecimal("300.00"), PaymentMethod.CASH, null);
        restTemplate().exchange("/api/payments", HttpMethod.POST,
                new HttpEntity<>(payment, authHeaderFor(admin)), PaymentResponse.class);

        ResponseEntity<ReceivablesReportResponse> after = restTemplate().exchange(
                "/api/reports/finance/receivables", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), ReceivablesReportResponse.class);

        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.OK);
        BigDecimal chargedDelta = after.getBody().totalCharged().subtract(baseline.totalCharged());
        BigDecimal collectedDelta = after.getBody().totalCollected().subtract(baseline.totalCollected());
        assertThat(chargedDelta).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(collectedDelta).isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(after.getBody().totalReceivable())
                .isEqualByComparingTo(after.getBody().totalCharged().subtract(after.getBody().totalCollected()));
    }

    @Test
    void receivables_asNonAdmin_returns403() {
        User parent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/reports/finance/receivables", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parent)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
