package com.okulservisi.backend.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.attendance.dto.request.AbsenceNoticeCreateRequest;
import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionApproveRequest;
import com.okulservisi.backend.attendance.dto.request.AttendanceCorrectionCreateRequest;
import com.okulservisi.backend.attendance.dto.request.BoardingRequest;
import com.okulservisi.backend.attendance.dto.request.DecisionReasonRequest;
import com.okulservisi.backend.attendance.dto.request.DropoffRequest;
import com.okulservisi.backend.attendance.dto.response.AbsenceNoticeResponse;
import com.okulservisi.backend.attendance.dto.response.AttendanceCorrectionResponse;
import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import com.okulservisi.backend.attendance.dto.response.TripRosterEntryResponse;
import com.okulservisi.backend.attendance.entity.AbsenceNoticeStatus;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import com.okulservisi.backend.attendance.entity.CorrectionKind;
import com.okulservisi.backend.attendance.entity.CorrectionStatus;
import com.okulservisi.backend.attendance.repository.AttendanceEventRepository;
import com.okulservisi.backend.common.entity.AuditLog;
import com.okulservisi.backend.common.entity.AuditLogRepository;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.support.AbstractOperationsTest;
import com.okulservisi.backend.trip.dto.response.DailyServiceEntryResponse;
import com.okulservisi.backend.student.entity.Student;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * 2026-11-18 — docs/API.md §22 (veli "servise binmeyecek" bildirimi), §24 (yoklama düzeltme) ve §26
 * (günlük servis). Gerçek PostgreSQL üzerinde kapsam, tekillik, resmi yoklamanın değişmemesi, eski veri
 * koruması, çift sonuçlandırma ve denetim kaydı doğrulanır.
 */
class AbsenceNoticeAndCorrectionTest extends AbstractOperationsTest {

    @Autowired
    private AttendanceEventRepository attendanceEventRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private <T> ResponseEntity<T> post(String url, Object body, HttpHeaders headers, Class<T> type) {
        return restTemplate().exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private TripRosterEntryResponse rosterEntry(Scenario s, HttpHeaders headers) {
        TripRosterEntryResponse[] roster = restTemplate().exchange("/api/trips/" + s.trip.id() + "/roster",
                HttpMethod.GET, new HttpEntity<>(headers), TripRosterEntryResponse[].class).getBody();
        return List.of(roster).stream().filter(r -> r.studentId().equals(s.student.getId())).findFirst().orElseThrow();
    }

    private long activeNoticeRows(Scenario s) {
        return jdbcTemplate.queryForObject("select count(*) from trip_absence_notice where trip_id = ? and student_id = ? "
                + "and status = 'ACTIVE'", Long.class, s.trip.id(), s.student.getId());
    }

    @Test
    void absenceNotice_resubmitDoesNotDuplicate_doesNotChangeAttendance_andStaffCanStillBoard() {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 10));
        String url = "/api/trips/" + s.trip.id() + "/absence-notices";

        ResponseEntity<AbsenceNoticeResponse> first = post(url,
                new AbsenceNoticeCreateRequest(s.student.getId(), "Hasta"), auth(s.parentUser), AbsenceNoticeResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().status()).isEqualTo(AbsenceNoticeStatus.ACTIVE);
        assertThat(first.getBody().createdAt()).isNotNull();

        ResponseEntity<AbsenceNoticeResponse> again = post(url,
                new AbsenceNoticeCreateRequest(s.student.getId(), "Hasta"), auth(s.parentUser), AbsenceNoticeResponse.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(activeNoticeRows(s)).isEqualTo(1);

        // Şoför yolcu listesinde görür; resmi yoklama değişmemiştir.
        TripRosterEntryResponse entry = rosterEntry(s, auth(s.driver.getUser()));
        assertThat(entry.absenceNotice()).isNotNull();
        assertThat(entry.absenceNotice().note()).isEqualTo("Hasta");
        assertThat(entry.status()).isEqualTo(RosterStatus.NONE);
        assertThat(attendanceEventRepository.findByTripIdAndStudentIdAndCorrectionFalse(s.trip.id(), s.student.getId()))
                .isEmpty();
        // Hostes de görür.
        ResponseEntity<AbsenceNoticeResponse[]> list = restTemplate().exchange(url, HttpMethod.GET,
                new HttpEntity<>(auth(s.hostess.getUser())), AbsenceNoticeResponse[].class);
        assertThat(list.getBody()).hasSize(1);

        // Çocuk durağa geldi: görevli binişi kaydedebilir.
        ResponseEntity<String> boarding = post("/api/trips/" + s.trip.id() + "/attendance-events/boardings",
                new BoardingRequest(s.student.getId()), auth(s.hostess.getUser()), String.class);
        assertThat(boarding.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Yoklama girildikten sonra veli geri alamaz.
        ResponseEntity<ApiError> withdraw = post(url + "/" + first.getBody().id() + "/withdraw", null,
                auth(s.parentUser), ApiError.class);
        assertThat(withdraw.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void absenceNotice_scopeRules() {
        Scenario s = scenario(RouteDirection.AFTERNOON, LocalDate.of(2026, 12, 11));
        String url = "/api/trips/" + s.trip.id() + "/absence-notices";
        AbsenceNoticeCreateRequest body = new AbsenceNoticeCreateRequest(s.student.getId(), null);

        // Bağlı olmayan veli: 404.
        Student otherChild = student("Başka çocuk", null);
        assertThat(post(url, body, auth(parentOf(otherChild).getUser()), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // Çocuk o seferin yolcusu değil: 404.
        var otherParent = parentOf(otherChild);
        assertThat(post(url, new AbsenceNoticeCreateRequest(otherChild.getId(), null), auth(otherParent.getUser()),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Öğrenci/şoför bildirim bırakamaz: 403.
        assertThat(post(url, body, auth(s.studentUser), ApiError.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(url, body, auth(s.driver.getUser()), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // Veli listeleyemez (personel/ADMIN uç noktası).
        assertThat(restTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(auth(s.parentUser)), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Geri alma (yoklama yokken) ve yeniden gönderme.
        AbsenceNoticeResponse created = post(url, body, auth(s.parentUser), AbsenceNoticeResponse.class).getBody();
        ResponseEntity<AbsenceNoticeResponse> withdrawn = post(url + "/" + created.id() + "/withdraw", null,
                auth(s.parentUser), AbsenceNoticeResponse.class);
        assertThat(withdrawn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withdrawn.getBody().status()).isEqualTo(AbsenceNoticeStatus.WITHDRAWN);
        assertThat(activeNoticeRows(s)).isZero();
        assertThat(post(url, body, auth(s.parentUser), AbsenceNoticeResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(activeNoticeRows(s)).isEqualTo(1);
    }

    @Test
    void absenceNotice_concurrentSubmits_produceSingleActiveNotice() throws Exception {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 12));
        String url = "/api/trips/" + s.trip.id() + "/absence-notices";
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Callable<Integer> call = () -> {
                go.await();
                return post(url, new AbsenceNoticeCreateRequest(s.student.getId(), "x"), auth(s.parentUser), String.class)
                        .getStatusCode().value();
            };
            results.add(pool.submit(call));
        }
        go.countDown();
        int created = 0;
        for (Future<Integer> result : results) {
            int status = result.get();
            assertThat(status).isIn(200, 201);
            if (status == 201) {
                created++;
            }
        }
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertThat(activeNoticeRows(s)).isEqualTo(1);
    }

    @Test
    void correction_byDriver_approvedByAdmin_appliesRules_writesAudit_andCannotBeAppliedTwice() throws Exception {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 13));
        post("/api/trips/" + s.trip.id() + "/attendance-events/boardings", new BoardingRequest(s.student.getId()),
                auth(s.driver.getUser()), String.class);

        // Kayıt yokken (başka öğrenci) talep açılamaz; atanmamış şoför 404.
        AttendanceCorrectionCreateRequest request = new AttendanceCorrectionCreateRequest(
                s.trip.id(), s.student.getId(), RosterStatus.ABSENT, "Yanlışlıkla bindi işaretlendi");
        assertThat(post("/api/attendance-corrections", request, auth(driver().getUser()), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Şoför istenen durumu belirtmek zorunda.
        assertThat(post("/api/attendance-corrections", new AttendanceCorrectionCreateRequest(
                s.trip.id(), s.student.getId(), null, "x"), auth(s.driver.getUser()), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<AttendanceCorrectionResponse> created = post("/api/attendance-corrections", request,
                auth(s.driver.getUser()), AttendanceCorrectionResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().kind()).isEqualTo(CorrectionKind.CORRECTION);
        assertThat(created.getBody().sourceStatus()).isEqualTo(RosterStatus.BOARDED);
        Long id = created.getBody().id();

        // Aynı sefer+öğrenci için ikinci bekleyen talep: 409.
        assertThat(post("/api/attendance-corrections", request, auth(s.hostess.getUser()), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rosterEntry(s, auth(s.driver.getUser())).pendingCorrectionId()).isEqualTo(id);
        // Şoför onaylayamaz.
        assertThat(post("/api/attendance-corrections/" + id + "/approve", null, auth(s.driver.getUser()),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<AttendanceCorrectionResponse> approved = post("/api/attendance-corrections/" + id + "/approve",
                new AttendanceCorrectionApproveRequest(null, "Kontrol edildi"), auth(s.admin),
                AttendanceCorrectionResponse.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().status()).isEqualTo(CorrectionStatus.APPROVED);
        assertThat(approved.getBody().appliedStatus()).isEqualTo(RosterStatus.ABSENT);
        assertThat(rosterEntry(s, auth(s.admin)).status()).isEqualTo(RosterStatus.ABSENT);
        List<AttendanceEvent> valid = attendanceEventRepository
                .findByTripIdAndStudentIdAndCorrectionFalse(s.trip.id(), s.student.getId());
        assertThat(valid).extracting(AttendanceEvent::getEventType).containsExactly(AttendanceEventType.ABSENCE);
        // Biniş kaydı silinmedi, geçersiz işaretlendi.
        Long voided = jdbcTemplate.queryForObject("select count(*) from attendance_event where trip_id = ? and "
                + "student_id = ? and voided_by_correction_id = ?", Long.class, s.trip.id(), s.student.getId(), id);
        assertThat(voided).isEqualTo(1);

        List<AuditLog> audits = auditLogRepository.findAll().stream()
                .filter(a -> "attendance_correction_request".equals(a.getEntityName()) && id.equals(a.getEntityId()))
                .toList();
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getAction()).isEqualTo("ATTENDANCE_CORRECTION_APPROVED");
        com.fasterxml.jackson.databind.JsonNode detail = objectMapper.readTree(audits.get(0).getDetail());
        assertThat(detail.get("oldStatus").asText()).isEqualTo("BOARDED");
        assertThat(detail.get("newStatus").asText()).isEqualTo("ABSENT");
        assertThat(detail.get("requestReason").asText()).isEqualTo("Yanlışlıkla bindi işaretlendi");
        assertThat(detail.get("requestedByUserId").asLong()).isEqualTo(s.driver.getUser().getId());
        assertThat(detail.get("decidedByUserId").asLong()).isEqualTo(s.admin.getId());
        assertThat(detail.get("decidedAt").asText()).isNotBlank();
        assertThat(notificationCount(s.driver.getUser(), "attendance-correction:" + id + ":decided")).isEqualTo(1);

        // İkinci onay: 409, yoklamaya dokunulmaz.
        assertThat(post("/api/attendance-corrections/" + id + "/approve", null, auth(s.admin), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(attendanceEventRepository.findByTripIdAndStudentIdAndCorrectionFalse(s.trip.id(), s.student.getId()))
                .hasSize(1);

        // Düzeltme sonrası kurallar: devamsız öğrenciye biniş eklenemez.
        assertThat(post("/api/trips/" + s.trip.id() + "/attendance-events/boardings",
                new BoardingRequest(s.student.getId()), auth(s.driver.getUser()), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void correction_toDroppedOff_addsBoardingBeforeDropoff() {
        Scenario s = scenario(RouteDirection.AFTERNOON, LocalDate.of(2026, 12, 14));
        post("/api/trips/" + s.trip.id() + "/attendance-events/absences",
                new com.okulservisi.backend.attendance.dto.request.AbsenceRequest(s.student.getId(), null),
                auth(s.driver.getUser()), String.class);
        Long id = post("/api/attendance-corrections", new AttendanceCorrectionCreateRequest(s.trip.id(),
                s.student.getId(), RosterStatus.DROPPED_OFF, "Bindi ve indi"), auth(s.hostess.getUser()),
                AttendanceCorrectionResponse.class).getBody().id();
        post("/api/attendance-corrections/" + id + "/approve", null, auth(s.admin), AttendanceCorrectionResponse.class);
        assertThat(attendanceEventRepository.findByTripIdAndStudentIdAndCorrectionFalse(s.trip.id(), s.student.getId()))
                .extracting(AttendanceEvent::getEventType)
                .containsExactlyInAnyOrder(AttendanceEventType.BOARDING, AttendanceEventType.DROPOFF);
        assertThat(rosterEntry(s, auth(s.admin)).status()).isEqualTo(RosterStatus.DROPPED_OFF);
        // Tekrar iniş kaydı: 409 (tekrar kayıt kuralı korunur).
        assertThat(post("/api/trips/" + s.trip.id() + "/attendance-events/dropoffs",
                new DropoffRequest(s.student.getId()), auth(s.driver.getUser()), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void correction_staleSource_isNotOverwritten_andCanBeRejected() {
        Scenario s = scenario(RouteDirection.AFTERNOON, LocalDate.of(2026, 12, 15));
        post("/api/trips/" + s.trip.id() + "/attendance-events/boardings", new BoardingRequest(s.student.getId()),
                auth(s.driver.getUser()), String.class);
        Long id = post("/api/attendance-corrections", new AttendanceCorrectionCreateRequest(s.trip.id(),
                s.student.getId(), RosterStatus.ABSENT, "Gelmedi"), auth(s.driver.getUser()),
                AttendanceCorrectionResponse.class).getBody().id();

        // Talepten sonra kaynak kayıt değişti (iniş kaydedildi).
        post("/api/trips/" + s.trip.id() + "/attendance-events/dropoffs", new DropoffRequest(s.student.getId()),
                auth(s.driver.getUser()), String.class);

        AttendanceCorrectionResponse view = restTemplate().exchange("/api/attendance-corrections/" + id, HttpMethod.GET,
                new HttpEntity<>(auth(s.admin)), AttendanceCorrectionResponse.class).getBody();
        assertThat(view.stale()).isTrue();
        assertThat(view.currentStatus()).isEqualTo(RosterStatus.DROPPED_OFF);

        assertThat(post("/api/attendance-corrections/" + id + "/approve", null, auth(s.admin), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rosterEntry(s, auth(s.admin)).status()).isEqualTo(RosterStatus.DROPPED_OFF);

        // Gerekçesiz ret: 400; gerekçeli ret: 200.
        assertThat(post("/api/attendance-corrections/" + id + "/reject", new DecisionReasonRequest(" "), auth(s.admin),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<AttendanceCorrectionResponse> rejected = post("/api/attendance-corrections/" + id + "/reject",
                new DecisionReasonRequest("Kayıt güncel"), auth(s.admin), AttendanceCorrectionResponse.class);
        assertThat(rejected.getBody().status()).isEqualTo(CorrectionStatus.REJECTED);
        assertThat(rejected.getBody().decisionNote()).isEqualTo("Kayıt güncel");

        // Talep sahibi kendi talebinin sonucunu görür; başka personel göremez.
        assertThat(restTemplate().exchange("/api/attendance-corrections/" + id, HttpMethod.GET,
                new HttpEntity<>(auth(s.driver.getUser())), AttendanceCorrectionResponse.class).getBody().status())
                .isEqualTo(CorrectionStatus.REJECTED);
        assertThat(restTemplate().exchange("/api/attendance-corrections/" + id, HttpMethod.GET,
                new HttpEntity<>(auth(s.hostess.getUser())), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void correction_concurrentApprovals_applyOnlyOnce() throws Exception {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 16));
        post("/api/trips/" + s.trip.id() + "/attendance-events/boardings", new BoardingRequest(s.student.getId()),
                auth(s.driver.getUser()), String.class);
        Long id = post("/api/attendance-corrections", new AttendanceCorrectionCreateRequest(s.trip.id(),
                s.student.getId(), RosterStatus.ABSENT, "Gelmedi"), auth(s.driver.getUser()),
                AttendanceCorrectionResponse.class).getBody().id();

        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return post("/api/attendance-corrections/" + id + "/approve", null, auth(s.admin), String.class)
                        .getStatusCode().value();
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<Integer> result : results) {
            int status = result.get();
            assertThat(status).isIn(200, 409);
            if (status == 200) {
                ok++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(attendanceEventRepository.findByTripIdAndStudentIdAndCorrectionFalse(s.trip.id(), s.student.getId()))
                .extracting(AttendanceEvent::getEventType).containsExactly(AttendanceEventType.ABSENCE);
        Long audits = jdbcTemplate.queryForObject("select count(*) from audit_log where entity_name = "
                + "'attendance_correction_request' and entity_id = ?", Long.class, id);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void parentReview_onlyForOwnChild_andStudentCannotRequest() {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 17));
        post("/api/trips/" + s.trip.id() + "/attendance-events/absences",
                new com.okulservisi.backend.attendance.dto.request.AbsenceRequest(s.student.getId(), null),
                auth(s.driver.getUser()), String.class);
        AttendanceCorrectionCreateRequest review = new AttendanceCorrectionCreateRequest(
                s.trip.id(), s.student.getId(), null, "Çocuğum servise bindi");

        Student otherChild = student("Diğer", null);
        assertThat(post("/api/attendance-corrections", review, auth(parentOf(otherChild).getUser()), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/api/attendance-corrections", review, auth(s.studentUser), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<AttendanceCorrectionResponse> created = post("/api/attendance-corrections", review,
                auth(s.parentUser), AttendanceCorrectionResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().kind()).isEqualTo(CorrectionKind.REVIEW);

        // ADMIN istenen durum olmadan onaylarsa 400; yeni durumla onay.
        assertThat(post("/api/attendance-corrections/" + created.getBody().id() + "/approve", null, auth(s.admin),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<AttendanceCorrectionResponse> approved = post(
                "/api/attendance-corrections/" + created.getBody().id() + "/approve",
                new AttendanceCorrectionApproveRequest(RosterStatus.BOARDED, null), auth(s.admin),
                AttendanceCorrectionResponse.class);
        assertThat(approved.getBody().appliedStatus()).isEqualTo(RosterStatus.BOARDED);
        assertThat(notificationCount(s.parentUser, "attendance-correction:" + created.getBody().id() + ":decided"))
                .isEqualTo(1);
    }

    @Test
    void dailyService_studentAndParentScopes() {
        LocalDate today = today();
        Scenario s = scenario(RouteDirection.MORNING, today);
        String url = "/api/students/" + s.student.getId() + "/daily-service?date=" + today;

        DailyServiceEntryResponse[] asStudent = restTemplate().exchange(url, HttpMethod.GET,
                new HttpEntity<>(auth(s.studentUser)), DailyServiceEntryResponse[].class).getBody();
        assertThat(asStudent).hasSize(1);
        assertThat(asStudent[0].stopName()).isEqualTo(s.stop1.getName());
        assertThat(asStudent[0].vehiclePlate()).isEqualTo(s.vehicle.getPlate());
        assertThat(asStudent[0].canSubmitAbsenceNotice()).isFalse();

        DailyServiceEntryResponse[] asParent = restTemplate().exchange(url, HttpMethod.GET,
                new HttpEntity<>(auth(s.parentUser)), DailyServiceEntryResponse[].class).getBody();
        assertThat(asParent[0].canSubmitAbsenceNotice()).isTrue();

        Student otherChild = student("Yabancı", null);
        assertThat(restTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(auth(parentOf(otherChild).getUser())),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(auth(user(
                com.okulservisi.backend.user.entity.Role.STUDENT))), ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(auth(s.driver.getUser())),
                ApiError.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
