package com.okulservisi.backend.route;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.repository.RouteRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.route.dto.request.AssignmentCreateRequest;
import com.okulservisi.backend.route.dto.response.AssignmentResponse;
import com.okulservisi.backend.route.dto.request.AssignmentUpdateRequest;
import com.okulservisi.backend.route.dto.request.RouteCreateRequest;
import com.okulservisi.backend.route.dto.response.RouteResponse;
import com.okulservisi.backend.route.dto.request.StopCreateRequest;
import com.okulservisi.backend.route.dto.response.StopResponse;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "8. Öğrenci atamaları" — aynı öğrenci + aynı yön + çakışan tarih aralığında birden
 * fazla aktif atama yasak (overlap kontrolü, gerçek SERIALIZABLE transaction + {@code
 * findOverlapping} sorgusu - yorum değil, çalışan kontrol). docs/TEST_PLAN.md eşleştirmesi:
 * T-42..T-47.
 */
class AssignmentControllerTest extends AbstractIntegrationTest {

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

    /** route + ilk stop'u (sequenceNo=1) tek seferde oluşturan yardımcı; döner: [routeId, stopId]. */
    private long[] createRouteWithStop(User admin, RouteDirection direction, String routeName, int sequenceNo) {
        RouteCreateRequest routeRequest = new RouteCreateRequest(routeName, direction);
        ResponseEntity<RouteResponse> routeResponse = restTemplate().exchange(
                "/api/routes", HttpMethod.POST, new HttpEntity<>(routeRequest, authHeaderFor(admin)), RouteResponse.class);
        long routeId = routeResponse.getBody().id();

        StopCreateRequest stopRequest = new StopCreateRequest("Durak " + sequenceNo, sequenceNo, LocalTime.of(7, 0));
        ResponseEntity<StopResponse> stopResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.POST, new HttpEntity<>(stopRequest, authHeaderFor(admin)), StopResponse.class);
        long stopId = stopResponse.getBody().id();

        return new long[] {routeId, stopId};
    }

    private ResponseEntity<AssignmentResponse> createAssignment(User admin, Long studentId, long[] routeAndStop,
            RouteDirection direction, LocalDate validFrom, LocalDate validTo) {
        AssignmentCreateRequest request = new AssignmentCreateRequest(
                studentId, routeAndStop[0], routeAndStop[1], direction, validFrom, validTo);
        return restTemplate().exchange(
                "/api/assignments", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), AssignmentResponse.class);
    }

    // ---- T-42 ----
    @Test
    void overlappingActiveAssignment_sameStudentSameDirection_isRejected() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Çakışma Testi Öğrenci 1");
        long[] routeStopA = createRouteWithStop(admin, RouteDirection.MORNING, "Çakışma Route A " + UUID.randomUUID(), 1);
        long[] routeStopB = createRouteWithStop(admin, RouteDirection.MORNING, "Çakışma Route B " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> first = createAssignment(admin, student.getId(), routeStopA,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Aynı öğrenci + aynı yön + çakışan tarih aralığı (farklı route/stop olsa da).
        ResponseEntity<String> overlapping = restTemplate().exchange(
                "/api/assignments", HttpMethod.POST,
                new HttpEntity<>(new AssignmentCreateRequest(student.getId(), routeStopB[0], routeStopB[1],
                        RouteDirection.MORNING, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 12, 31)), authHeaderFor(admin)),
                String.class);

        assertThat(overlapping.getStatusCode())
                .as("aynı öğrenci+yön için çakışan aktif atama reddedilmeli - GERÇEK overlap kontrolü")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-43 ----
    @Test
    void differentDirection_overlappingDates_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Çakışma Testi Öğrenci 2");
        long[] morningRouteStop = createRouteWithStop(admin, RouteDirection.MORNING, "Sabah Route " + UUID.randomUUID(), 1);
        long[] afternoonRouteStop = createRouteWithStop(admin, RouteDirection.AFTERNOON, "Öğleden Sonra Route " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> morning = createAssignment(admin, student.getId(), morningRouteStop,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(morning.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AssignmentResponse> afternoon = createAssignment(admin, student.getId(), afternoonRouteStop,
                RouteDirection.AFTERNOON, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(afternoon.getStatusCode())
                .as("farklı yön (MORNING/AFTERNOON) overlap sayılmamalı")
                .isEqualTo(HttpStatus.OK);
    }

    // ---- T-44 ----
    @Test
    void nonOverlappingDateRanges_sameDirection_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Çakışma Testi Öğrenci 3");
        long[] routeStopA = createRouteWithStop(admin, RouteDirection.MORNING, "Ardışık Route A " + UUID.randomUUID(), 1);
        long[] routeStopB = createRouteWithStop(admin, RouteDirection.MORNING, "Ardışık Route B " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> first = createAssignment(admin, student.getId(), routeStopA,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<AssignmentResponse> second = createAssignment(admin, student.getId(), routeStopB,
                RouteDirection.MORNING, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));

        assertThat(second.getStatusCode())
                .as("ardışık (çakışmayan) tarih aralıkları için ikinci atama başarılı olmalı")
                .isEqualTo(HttpStatus.OK);
    }

    // ---- T-45 ----
    @Test
    void openEndedExistingAssignment_blocksNewOverlappingBoundedAssignment() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Çakışma Testi Öğrenci 4");
        long[] routeStopA = createRouteWithStop(admin, RouteDirection.MORNING, "Açık Uçlu Route A " + UUID.randomUUID(), 1);
        long[] routeStopB = createRouteWithStop(admin, RouteDirection.MORNING, "Açık Uçlu Route B " + UUID.randomUUID(), 1);

        // Mevcut atama süresiz (validTo = null).
        ResponseEntity<AssignmentResponse> openEnded = createAssignment(admin, student.getId(), routeStopA,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), null);
        assertThat(openEnded.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Yeni atama SINIRLI bir aralıkla, mevcut süresiz atamayla çakışıyor.
        ResponseEntity<String> overlapping = restTemplate().exchange(
                "/api/assignments", HttpMethod.POST,
                new HttpEntity<>(new AssignmentCreateRequest(student.getId(), routeStopB[0], routeStopB[1],
                        RouteDirection.MORNING, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)), authHeaderFor(admin)),
                String.class);

        assertThat(overlapping.getStatusCode())
                .as("mevcut süresiz (validTo=null) atama varken çakışan yeni sınırlı atama reddedilmeli")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /**
     * EK senaryo: YENİ atamanın kendisi süresiz (validTo=null) iken mevcut, çakışan bir atama varsa
     * ne olur? {@code StudentRouteAssignmentRepository#findOverlapping} sorgusu
     * {@code a.validFrom <= :validTo} koşulunu içeriyor; {@code :validTo} (yeni atamanın validTo'su)
     * NULL ise SQL'de bu karşılaştırma HER ZAMAN UNKNOWN/false döner ve sorgu satır DÖNDÜRMEZ - yani
     * overlap kontrolü YANLIŞLIKLA atlanabilir. Bu test bunu GERÇEKTEN doğrular (yorum değil).
     */
    @Test
    void newOpenEndedAssignment_overlappingExistingBoundedAssignment_isRejected() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Çakışma Testi Öğrenci 5");
        long[] routeStopA = createRouteWithStop(admin, RouteDirection.MORNING, "Süresiz-Yeni Route A " + UUID.randomUUID(), 1);
        long[] routeStopB = createRouteWithStop(admin, RouteDirection.MORNING, "Süresiz-Yeni Route B " + UUID.randomUUID(), 1);

        // Mevcut atama sınırlı bir aralıkta.
        ResponseEntity<AssignmentResponse> bounded = createAssignment(admin, student.getId(), routeStopA,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(bounded.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Yeni atama SÜRESİZ (validTo = null) ve mevcut sınırlı atamayla aynı öğrenci/yönde çakışıyor.
        ResponseEntity<String> overlapping = restTemplate().exchange(
                "/api/assignments", HttpMethod.POST,
                new HttpEntity<>(new AssignmentCreateRequest(student.getId(), routeStopB[0], routeStopB[1],
                        RouteDirection.MORNING, LocalDate.of(2026, 6, 1), null), authHeaderFor(admin)),
                String.class);

        assertThat(overlapping.getStatusCode())
                .as("YENİ atama süresiz (validTo=null) olsa da mevcut çakışan atama reddetmeli - "
                        + "findOverlapping sorgusunda :validTo NULL bind edilirse SQL karşılaştırması UNKNOWN döner "
                        + "ve overlap satırı KAÇIRILABİLİR (bkz. test Javadoc'u) - bu test bunu yakalamak için yazıldı")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-46 ----
    @Test
    void parentOrStudent_scopeAndObjectLevel404() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student ownChild = createStudent("Kapsam Testi Kendi Çocuk");
        Student otherChild = createStudent("Kapsam Testi Başka Çocuk");
        long[] routeStopOwn = createRouteWithStop(admin, RouteDirection.MORNING, "Kapsam Route Own " + UUID.randomUUID(), 1);
        long[] routeStopOther = createRouteWithStop(admin, RouteDirection.MORNING, "Kapsam Route Other " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> ownAssignment = createAssignment(admin, ownChild.getId(), routeStopOwn,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), null);
        ResponseEntity<AssignmentResponse> otherAssignment = createAssignment(admin, otherChild.getId(), routeStopOther,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), null);
        assertThat(ownAssignment.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(otherAssignment.getStatusCode()).isEqualTo(HttpStatus.OK);

        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Kapsam Testi Veli");
        parent = parentRepository.save(parent);
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(ownChild);
        link.setRelationType("MOTHER");
        link.setActive(true);
        parentStudentLinkRepository.save(link);

        // Liste: sadece kendi çocuğunun ataması görünmeli.
        ResponseEntity<String> listResponse = restTemplate().exchange(
                "/api/assignments", HttpMethod.GET, new HttpEntity<>(authHeaderFor(parentUser)), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).contains("\"studentId\":" + ownChild.getId());
        assertThat(listResponse.getBody()).doesNotContain("\"studentId\":" + otherChild.getId());

        // Detay: başka çocuğun ataması -> 404.
        ResponseEntity<ApiError> detailOther = restTemplate().exchange(
                "/api/assignments/" + otherAssignment.getBody().id(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ApiError.class);
        assertThat(detailOther.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Detay: kendi çocuğunun ataması -> 200.
        ResponseEntity<AssignmentResponse> detailOwn = restTemplate().exchange(
                "/api/assignments/" + ownAssignment.getBody().id(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), AssignmentResponse.class);
        assertThat(detailOwn.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- T-47 ----
    @Test
    void assignmentEndpoints_asDriverOrHostess_return403() {
        for (Role role : new Role[] {Role.DRIVER, Role.HOSTESS}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);
            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/assignments", HttpMethod.GET, new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void containingDateRange_sameStudentSameDirection_isRejected() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Çakışma Testi İçeren Aralık");
        long[] routeStopA = createRouteWithStop(admin, RouteDirection.MORNING, "İçeren Route A " + UUID.randomUUID(), 1);
        long[] routeStopB = createRouteWithStop(admin, RouteDirection.MORNING, "İçeren Route B " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> outer = createAssignment(admin, student.getId(), routeStopA,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(outer.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Yeni atamanın tarih aralığı TAMAMEN mevcut atamanın İÇİNDE (containment) - saf bir overlap durumu.
        ResponseEntity<String> inner = restTemplate().exchange(
                "/api/assignments", HttpMethod.POST,
                new HttpEntity<>(new AssignmentCreateRequest(student.getId(), routeStopB[0], routeStopB[1],
                        RouteDirection.MORNING, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 30)), authHeaderFor(admin)),
                String.class);

        assertThat(inner.getStatusCode())
                .as("yeni atamanın tarih aralığı mevcut atamanın tamamen içinde (containment) - reddedilmeli")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void touchingBoundaryDates_sameStudentSameDirection_isRejected() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Çakışma Testi Sınır Teması");
        long[] routeStopA = createRouteWithStop(admin, RouteDirection.MORNING, "Sınır Route A " + UUID.randomUUID(), 1);
        long[] routeStopB = createRouteWithStop(admin, RouteDirection.MORNING, "Sınır Route B " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> first = createAssignment(admin, student.getId(), routeStopA,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Yeni atama, mevcut atamanın bittiği GÜNDE (2026-01-10) başlıyor - sınır teması. Politika
        // KASITLI olarak sınırları DAHİL (inclusive) sayar (bkz. StudentRouteAssignmentRepository
        // javadoc'u): aynı gün için iki güzergâha atanamaz - bu nedenle bu da ÇAKIŞMA sayılmalı.
        ResponseEntity<String> touching = restTemplate().exchange(
                "/api/assignments", HttpMethod.POST,
                new HttpEntity<>(new AssignmentCreateRequest(student.getId(), routeStopB[0], routeStopB[1],
                        RouteDirection.MORNING, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 1)), authHeaderFor(admin)),
                String.class);

        assertThat(touching.getStatusCode())
                .as("sınır teması (mevcut validTo = yeni validFrom, aynı gün) - politika inclusive, çakışma sayılmalı")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updatingAssignment_withUnchangedOverlappingDates_excludesItself_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Kendisiyle Çakışma Testi");
        long[] routeStop = createRouteWithStop(admin, RouteDirection.MORNING, "Kendi Çakışma Route " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> created = createAssignment(admin, student.getId(), routeStop,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long assignmentId = created.getBody().id();

        // AYNI (kendisiyle çakışan) tarih aralığıyla PATCH - excludingId doğru çalışıyorsa kayıt
        // kendi önceki haliyle "çakışma" SAYILMAMALI (findOverlapping'in excludingId parametresi).
        AssignmentUpdateRequest updateRequest = new AssignmentUpdateRequest(
                null, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        ResponseEntity<AssignmentResponse> updated = restTemplate().exchange(
                "/api/assignments/" + assignmentId, HttpMethod.PATCH,
                new HttpEntity<>(updateRequest, authHeaderFor(admin)), AssignmentResponse.class);

        assertThat(updated.getStatusCode())
                .as("atamanın kendi (değişmemiş) tarih aralığıyla güncellenmesi - excludingId kendini hariç tutmalı")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void parentWithoutActiveLink_cannotSeeOrAccessAssignment() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Bağlantısız Veli Testi Öğrenci");
        long[] routeStop = createRouteWithStop(admin, RouteDirection.MORNING, "Bağlantısız Veli Route " + UUID.randomUUID(), 1);

        ResponseEntity<AssignmentResponse> assignment = createAssignment(admin, student.getId(), routeStop,
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), null);
        assertThat(assignment.getStatusCode()).isEqualTo(HttpStatus.OK);

        // PARENT oluşturulur ama HİÇBİR öğrenciye bağlanmaz (ne aktif ne pasif bir ParentStudentLink
        // yok) - StudentScopePolicy.accessibleStudentIds/canAccess BOŞ KÜME/false döndürmeli, asla
        // sınırsız erişime (null) DÖNÜŞMEMELİ.
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Bağlantısız Veli");
        parentRepository.save(parent);

        // Liste: hiçbir atama görünmemeli (boş sayfa).
        ResponseEntity<String> listResponse = restTemplate().exchange(
                "/api/assignments", HttpMethod.GET, new HttpEntity<>(authHeaderFor(parentUser)), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .as("hiçbir aktif bağlantısı olmayan PARENT için liste tamamen boş olmalı")
                .contains("\"totalElements\":0")
                .doesNotContain("\"studentId\":" + student.getId());

        // Detay: var olan ama yetkisiz atamaya direkt erişim de 404 dönmeli (var/yok bilgisi sızdırılmaz).
        ResponseEntity<ApiError> detail = restTemplate().exchange(
                "/api/assignments/" + assignment.getBody().id(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ApiError.class);
        assertThat(detail.getStatusCode())
                .as("bağlantısız PARENT için var olan bir atamaya doğrudan erişim de 404 dönmeli")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void concurrentOverlappingAssignmentCreation_onlyOneSucceeds() throws Exception {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = createStudent("Eşzamanlılık Testi Öğrencisi");
        long[] routeStopA = createRouteWithStop(admin, RouteDirection.MORNING, "Eşzamanlı Route A " + UUID.randomUUID(), 1);
        long[] routeStopB = createRouteWithStop(admin, RouteDirection.MORNING, "Eşzamanlı Route B " + UUID.randomUUID(), 1);

        HttpHeaders authHeaders = authHeaderFor(admin);
        // AbstractIntegrationTest.restTemplate() burada (worker thread'ler başlamadan ÖNCE, ana test
        // thread'inde) bir kez çağrılarak lazy-init tetiklenir; Spring referans dokümantasyonuna göre
        // bir RestTemplate bir kez yapılandırıldıktan sonra thread-safe'tir ve
        // ExecutorService.submit()'in JMM "happens-before" garantisi sayesinde her iki worker thread
        // de burada zaten oluşturulmuş AYNI instance'ı güvenle paylaşır (ayrıca bir görünürlük sorunu
        // olmaz) - bu yüzden her thread için ayrı bir HTTP client'a gerek yoktur.
        TestRestTemplate sharedClient = restTemplate();

        AssignmentCreateRequest requestA = new AssignmentCreateRequest(student.getId(), routeStopA[0], routeStopA[1],
                RouteDirection.MORNING, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        AssignmentCreateRequest requestB = new AssignmentCreateRequest(student.getId(), routeStopB[0], routeStopB[1],
                RouteDirection.MORNING, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 9, 30));

        CountDownLatch startLatch = new CountDownLatch(1);
        List<Callable<ResponseEntity<String>>> tasks = List.of(
                () -> {
                    startLatch.await();
                    return sharedClient.exchange("/api/assignments", HttpMethod.POST,
                            new HttpEntity<>(requestA, authHeaders), String.class);
                },
                () -> {
                    startLatch.await();
                    return sharedClient.exchange("/api/assignments", HttpMethod.POST,
                            new HttpEntity<>(requestB, authHeaders), String.class);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<String>>> futures = tasks.stream().map(executor::submit).toList();
            startLatch.countDown();

            List<Integer> statusCodes = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                statusCodes.add(future.get(10, TimeUnit.SECONDS).getStatusCode().value());
            }

            assertThat(statusCodes)
                    .as("iki eşzamanlı çakışan POST /api/assignments - TAM OLARAK biri 200 diğeri 409 olmalı, "
                            + "HİÇBİRİ 500 olmamalı (GlobalExceptionHandler.handleConcurrencyFailure doğrulaması): %s",
                            statusCodes)
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }
    }
}
