package com.okulservisi.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.support.AbstractOperationsTest;
import com.okulservisi.backend.trip.dto.request.TripCancelRequest;
import com.okulservisi.backend.trip.dto.request.TripCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripIncidentCreateRequest;
import com.okulservisi.backend.trip.dto.request.TripIncidentResolveRequest;
import com.okulservisi.backend.trip.dto.request.TripUpdateRequest;
import com.okulservisi.backend.trip.dto.response.TripIncidentResponse;
import com.okulservisi.backend.trip.dto.response.TripResponse;
import com.okulservisi.backend.trip.entity.TripIncidentStatus;
import com.okulservisi.backend.trip.entity.TripIncidentType;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * 2026-11-18 — docs/API.md §21/§23: sefer düzenleme, sadece ADMIN doğrudan iptal, iptal talebi,
 * gecikme bildirimi, çakışma ve eşzamanlılık senaryoları (gerçek PostgreSQL, Testcontainers).
 */
class TripOperationsTest extends AbstractOperationsTest {

    @Autowired
    private TripRepository tripRepository;

    private ResponseEntity<ApiError> postError(String url, Object body, org.springframework.http.HttpHeaders headers) {
        return restTemplate().exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), ApiError.class);
    }

    @Test
    void directCancel_onlyAdmin_driverAndHostessGet403_familyNotifiedOnce() {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 1));
        String url = "/api/trips/" + s.trip.id() + "/cancel";

        assertThat(postError(url, null, auth(s.driver.getUser())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postError(url, null, auth(s.hostess.getUser())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postError(url, null, auth(s.parentUser)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(tripRepository.findById(s.trip.id()).orElseThrow().getStatus()).isEqualTo(TripStatus.PLANNED);

        ResponseEntity<TripResponse> cancelled = restTemplate().exchange(url, HttpMethod.POST,
                new HttpEntity<>(new TripCancelRequest("Kar tatili"), auth(s.admin)), TripResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(TripStatus.CANCELLED);
        assertThat(cancelled.getBody().cancellationReason()).isEqualTo("Kar tatili");

        String key = "trip:" + s.trip.id() + ":cancelled";
        assertThat(notificationCount(s.parentUser, key)).isEqualTo(1);
        assertThat(notificationCount(s.studentUser, key)).isEqualTo(1);
        assertThat(notificationCount(s.driver.getUser(), key)).isEqualTo(1);
        assertThat(notificationCount(s.hostess.getUser(), key)).isEqualTo(1);

        // Tekrar iptal: 409, ikinci bildirim yok.
        assertThat(postError(url, null, auth(s.admin)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(notificationCount(s.parentUser, key)).isEqualTo(1);
    }

    @Test
    void startAndComplete_stillAllowedForAssignedStaff() {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 2));
        ResponseEntity<TripResponse> started = restTemplate().exchange("/api/trips/" + s.trip.id() + "/start",
                HttpMethod.POST, new HttpEntity<>(auth(s.hostess.getUser())), TripResponse.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<TripResponse> completed = restTemplate().exchange("/api/trips/" + s.trip.id() + "/complete",
                HttpMethod.POST, new HttpEntity<>(auth(s.driver.getUser())), TripResponse.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().status()).isEqualTo(TripStatus.COMPLETED);
    }

    @Test
    void tripResponse_includesPlateForHostess() {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 3));
        ResponseEntity<TripResponse[]> mine = restTemplate().exchange("/api/trips/mine?businessDate=2026-12-03",
                HttpMethod.GET, new HttpEntity<>(auth(s.hostess.getUser())), TripResponse[].class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).hasSize(1);
        assertThat(mine.getBody()[0].vehiclePlate()).isEqualTo(s.vehicle.getPlate());
        assertThat(mine.getBody()[0].routeName()).isEqualTo(s.route.getName());
        assertThat(mine.getBody()[0].plannedStartTime()).isEqualTo(LocalTime.of(7, 0));
    }

    @Test
    void update_plannedTime_notifiesFamilyOnce_andConflictsAreRejected() {
        LocalDate date = LocalDate.of(2026, 12, 4);
        Scenario s = scenario(RouteDirection.MORNING, date);
        Scenario other = scenario(RouteDirection.MORNING, date);
        String url = "/api/trips/" + s.trip.id();

        // DRIVER düzenleyemez.
        ResponseEntity<ApiError> forbidden = restTemplate().exchange(url, HttpMethod.PATCH,
                new HttpEntity<>(new TripUpdateRequest(LocalTime.of(7, 30), null, null, null, null),
                        auth(s.driver.getUser())), ApiError.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<TripResponse> updated = restTemplate().exchange(url, HttpMethod.PATCH,
                new HttpEntity<>(new TripUpdateRequest(LocalTime.of(7, 30), null, null, null, null), auth(s.admin)),
                TripResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().plannedStartTime()).isEqualTo(LocalTime.of(7, 30));
        String timeKey = "trip:" + s.trip.id() + ":v" + s.trip.version() + ":planned-time";
        assertThat(notificationCount(s.parentUser, timeKey)).isEqualTo(1);
        assertThat(notificationCount(s.studentUser, timeKey)).isEqualTo(1);

        // Aynı istek tekrar işlenirse (değişiklik yok) yeni bildirim oluşmaz.
        long before = notificationRepository.findByRecipientId(s.parentUser.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        restTemplate().exchange(url, HttpMethod.PATCH,
                new HttpEntity<>(new TripUpdateRequest(LocalTime.of(7, 30), null, null, null, null), auth(s.admin)),
                TripResponse.class);
        long after = notificationRepository.findByRecipientId(s.parentUser.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements();
        assertThat(after).isEqualTo(before);

        // Başka seferin aracı / şoförü / hostesi aynı gün+yönde: 409.
        assertThat(restTemplate().exchange(url, HttpMethod.PATCH, new HttpEntity<>(
                new TripUpdateRequest(null, other.vehicle.getId(), null, null, null), auth(s.admin)), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(restTemplate().exchange(url, HttpMethod.PATCH, new HttpEntity<>(
                new TripUpdateRequest(null, null, other.driver.getId(), null, null), auth(s.admin)), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(restTemplate().exchange(url, HttpMethod.PATCH, new HttpEntity<>(
                new TripUpdateRequest(null, null, null, other.hostess.getId(), null), auth(s.admin)), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_hostessAlreadyAssignedSameDateDirection_isRejected() {
        LocalDate date = LocalDate.of(2026, 12, 5);
        Scenario s = scenario(RouteDirection.MORNING, date);
        ResponseEntity<ApiError> second = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(new TripCreateRequest(route(RouteDirection.MORNING).getId(), vehicle().getId(),
                        driver().getId(), s.hostess.getId(), date, RouteDirection.MORNING), auth(s.admin)),
                ApiError.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void update_staffChange_notifiesOldAndNewStaff() {
        Scenario s = scenario(RouteDirection.AFTERNOON, LocalDate.of(2026, 12, 6));
        var newDriver = driver();
        ResponseEntity<TripResponse> updated = restTemplate().exchange("/api/trips/" + s.trip.id(), HttpMethod.PATCH,
                new HttpEntity<>(new TripUpdateRequest(null, null, newDriver.getId(), null, true), auth(s.admin)),
                TripResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().driverId()).isEqualTo(newDriver.getId());
        assertThat(updated.getBody().hostessId()).isNull();
        String prefix = "trip:" + s.trip.id() + ":v" + s.trip.version();
        assertThat(notificationCount(s.driver.getUser(), prefix + ":unassigned:" + s.driver.getUser().getId())).isEqualTo(1);
        assertThat(notificationCount(newDriver.getUser(), prefix + ":assigned:" + newDriver.getUser().getId())).isEqualTo(1);
        assertThat(notificationCount(s.hostess.getUser(), prefix + ":unassigned:" + s.hostess.getUser().getId())).isEqualTo(1);
        // Eski şoför artık seferi göremez/başlatamaz.
        assertThat(postError("/api/trips/" + s.trip.id() + "/start", null, auth(s.driver.getUser())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_afterStart_isRejected() {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 7));
        restTemplate().exchange("/api/trips/" + s.trip.id() + "/start", HttpMethod.POST,
                new HttpEntity<>(auth(s.driver.getUser())), TripResponse.class);
        ResponseEntity<ApiError> patch = restTemplate().exchange("/api/trips/" + s.trip.id(), HttpMethod.PATCH,
                new HttpEntity<>(new TripUpdateRequest(LocalTime.of(8, 0), null, null, null, null), auth(s.admin)),
                ApiError.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void concurrentStartAndUpdate_endInConsistentState() throws Exception {
        for (int round = 0; round < 5; round++) {
            Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2027, 1, 4 + round));
            Vehicle newVehicle = vehicle();
            CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Callable<HttpStatusCode> start = () -> {
                go.await();
                return restTemplate().exchange("/api/trips/" + s.trip.id() + "/start", HttpMethod.POST,
                        new HttpEntity<>(auth(s.driver.getUser())), String.class).getStatusCode();
            };
            Callable<HttpStatusCode> update = () -> {
                go.await();
                return restTemplate().exchange("/api/trips/" + s.trip.id(), HttpMethod.PATCH,
                        new HttpEntity<>(new TripUpdateRequest(null, newVehicle.getId(), null, null, null), auth(s.admin)),
                        String.class).getStatusCode();
            };
            Future<HttpStatusCode> startResult = pool.submit(start);
            Future<HttpStatusCode> updateResult = pool.submit(update);
            go.countDown();
            HttpStatusCode startStatus = startResult.get();
            HttpStatusCode updateStatus = updateResult.get();
            pool.shutdown();

            var trip = tripRepository.findById(s.trip.id()).orElseThrow();
            assertThat(startStatus.value()).isIn(200, 409);
            assertThat(updateStatus.value()).isIn(200, 409);
            if (updateStatus.value() == 200) {
                assertThat(trip.getVehicle().getId()).isEqualTo(newVehicle.getId());
            } else {
                assertThat(trip.getVehicle().getId()).isEqualTo(s.vehicle.getId());
            }
            if (startStatus.value() == 200) {
                assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
            } else {
                assertThat(trip.getStatus()).isEqualTo(TripStatus.PLANNED);
            }
        }
    }

    @Test
    void cancellationRequest_doesNotChangeTrip_isDeduplicated_andAdminResolvesOnce() {
        Scenario s = scenario(RouteDirection.MORNING, LocalDate.of(2026, 12, 8));
        String base = "/api/trips/" + s.trip.id() + "/incidents";
        TripIncidentCreateRequest request =
                new TripIncidentCreateRequest(TripIncidentType.CANCELLATION_REQUEST, null, "Araç arızalı");

        ResponseEntity<TripIncidentResponse> first = restTemplate().exchange(base, HttpMethod.POST,
                new HttpEntity<>(request, auth(s.driver.getUser())), TripIncidentResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody().status()).isEqualTo(TripIncidentStatus.OPEN);
        assertThat(tripRepository.findById(s.trip.id()).orElseThrow().getStatus()).isEqualTo(TripStatus.PLANNED);
        assertThat(notificationCount(s.admin, "trip-incident:" + first.getBody().id())).isEqualTo(1);

        ResponseEntity<TripIncidentResponse> second = restTemplate().exchange(base, HttpMethod.POST,
                new HttpEntity<>(request, auth(s.hostess.getUser())), TripIncidentResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

        ResponseEntity<TripResponse> detail = restTemplate().exchange("/api/trips/" + s.trip.id(), HttpMethod.GET,
                new HttpEntity<>(auth(s.driver.getUser())), TripResponse.class);
        assertThat(detail.getBody().openCancellationRequestId()).isEqualTo(first.getBody().id());

        // Atanmamış şoför: 404.
        assertThat(postError(base, request, auth(driver().getUser())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Şoför sonuçlandıramaz: 403.
        String resolveUrl = base + "/" + first.getBody().id() + "/resolve";
        assertThat(postError(resolveUrl, new TripIncidentResolveRequest(true, "ok"), auth(s.driver.getUser()))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<TripIncidentResponse> resolved = restTemplate().exchange(resolveUrl, HttpMethod.POST,
                new HttpEntity<>(new TripIncidentResolveRequest(true, "Yedek araç yok"), auth(s.admin)),
                TripIncidentResponse.class);
        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resolved.getBody().status()).isEqualTo(TripIncidentStatus.APPROVED);
        var trip = tripRepository.findById(s.trip.id()).orElseThrow();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(trip.getCancellationReason()).isEqualTo("Yedek araç yok");
        assertThat(notificationCount(s.parentUser, "trip:" + s.trip.id() + ":cancelled")).isEqualTo(1);
        assertThat(notificationCount(s.driver.getUser(), "trip-incident:" + first.getBody().id() + ":resolved"))
                .isEqualTo(1);

        assertThat(postError(resolveUrl, new TripIncidentResolveRequest(false, "tekrar"), auth(s.admin))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void delayIncident_notifiesFamily() {
        Scenario s = scenario(RouteDirection.AFTERNOON, LocalDate.of(2026, 12, 9));
        ResponseEntity<TripIncidentResponse> delay = restTemplate().exchange(
                "/api/trips/" + s.trip.id() + "/incidents", HttpMethod.POST,
                new HttpEntity<>(new TripIncidentCreateRequest(TripIncidentType.DELAY, 15, "Trafik"),
                        auth(s.hostess.getUser())), TripIncidentResponse.class);
        assertThat(delay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(delay.getBody().status()).isEqualTo(TripIncidentStatus.RECORDED);
        String key = "trip-incident:" + delay.getBody().id();
        assertThat(notificationCount(s.parentUser, key)).isEqualTo(1);
        assertThat(notificationCount(s.studentUser, key)).isEqualTo(1);

        // Gecikme dakikası olmadan: 400.
        assertThat(postError("/api/trips/" + s.trip.id() + "/incidents",
                new TripIncidentCreateRequest(TripIncidentType.DELAY, null, "Trafik"), auth(s.driver.getUser()))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Veli kayıt giremez: 403.
        assertThat(postError("/api/trips/" + s.trip.id() + "/incidents",
                new TripIncidentCreateRequest(TripIncidentType.ROAD_CLOSURE, null, "Yol"), auth(s.parentUser))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
