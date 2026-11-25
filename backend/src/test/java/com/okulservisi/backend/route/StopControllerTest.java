package com.okulservisi.backend.route;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.repository.RouteRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.route.dto.request.RouteCreateRequest;
import com.okulservisi.backend.route.dto.response.RouteResponse;
import com.okulservisi.backend.route.dto.request.StopCreateRequest;
import com.okulservisi.backend.route.dto.response.StopResponse;
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
import java.time.LocalDate;
import java.time.LocalTime;
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
 * docs/API.md "7. Güzergâh/Durak" — {@code sequenceNo} route içinde benzersiz. docs/TEST_PLAN.md
 * eşleştirmesi: T-39, T-40, T-110/T-111 (DRIVER/HOSTESS salt-okunur, {@code RouteControllerTest}'teki
 * {@code assignedRouteTrip_*} ile AYNI desen — bir Trip'in route'u kullanması "atanmışlık" sayılır).
 */
class StopControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private HostessRepository hostessRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private RouteRepository routeRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private Long createRoute(User admin, String name) {
        RouteCreateRequest request = new RouteCreateRequest(name, RouteDirection.MORNING);
        ResponseEntity<RouteResponse> response = restTemplate().exchange(
                "/api/routes", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), RouteResponse.class);
        return response.getBody().id();
    }

    private Driver createDriver() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Driver driver = new Driver();
        driver.setUser(user);
        driver.setLicenseNo("LIC-" + UUID.randomUUID());
        driver.setActive(true);
        return driverRepository.save(driver);
    }

    private Hostess createHostess() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.HOSTESS, true);
        Hostess hostess = new Hostess();
        hostess.setUser(user);
        hostess.setActive(true);
        return hostessRepository.save(hostess);
    }

    private Vehicle createVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    private void createTrip(Long routeId, Vehicle vehicle, Driver driver, Hostess hostess) {
        Route route = routeRepository.findById(routeId).orElseThrow();
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setHostess(hostess);
        trip.setBusinessDate(LocalDate.of(2026, 7, 1));
        trip.setDirection(route.getDirection());
        trip.setStatus(TripStatus.PLANNED);
        tripRepository.save(trip);
    }

    // ---- T-39 ----
    @Test
    void createStop_withDuplicateSequenceNoInSameRoute_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Long routeId = createRoute(admin, "Test Güzergahı 1");

        StopCreateRequest first = new StopCreateRequest("Durak A", 1, LocalTime.of(7, 30));
        ResponseEntity<StopResponse> firstResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.POST, new HttpEntity<>(first, authHeaderFor(admin)), StopResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        StopCreateRequest duplicate = new StopCreateRequest("Durak B", 1, LocalTime.of(7, 45));
        ResponseEntity<ApiError> duplicateResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.POST, new HttpEntity<>(duplicate, authHeaderFor(admin)), ApiError.class);

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-40 ----
    @Test
    void createStop_withSameSequenceNoInDifferentRoute_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Long routeA = createRoute(admin, "Test Güzergahı A");
        Long routeB = createRoute(admin, "Test Güzergahı B");

        StopCreateRequest stopOnA = new StopCreateRequest("A Durağı", 1, LocalTime.of(7, 0));
        ResponseEntity<StopResponse> responseA = restTemplate().exchange(
                "/api/routes/" + routeA + "/stops", HttpMethod.POST, new HttpEntity<>(stopOnA, authHeaderFor(admin)), StopResponse.class);
        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.OK);

        StopCreateRequest stopOnB = new StopCreateRequest("B Durağı", 1, LocalTime.of(7, 0));
        ResponseEntity<StopResponse> responseB = restTemplate().exchange(
                "/api/routes/" + routeB + "/stops", HttpMethod.POST, new HttpEntity<>(stopOnB, authHeaderFor(admin)), StopResponse.class);

        assertThat(responseB.getStatusCode())
                .as("sequenceNo kısıtı route-scoped olmalı, farklı route'ta aynı sıra serbest")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void stopEndpoints_asNonAdmin_return403() {
        User student = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/routes/1/stops", HttpMethod.GET, new HttpEntity<>(authHeaderFor(student)), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- T-110/T-111: DRIVER/HOSTESS kendi atandığı güzergâhın duraklarını salt-okunur görebilir ----
    @Test
    void listAndGetStops_asDriverAssignedToRouteViaTrip_returns200() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Long routeId = createRoute(admin, "Durak Driver Testi " + UUID.randomUUID());
        StopCreateRequest stopRequest = new StopCreateRequest("Durak A", 1, LocalTime.of(7, 30));
        ResponseEntity<StopResponse> created = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.POST, new HttpEntity<>(stopRequest, authHeaderFor(admin)), StopResponse.class);
        Long stopId = created.getBody().id();

        Driver driver = createDriver();
        createTrip(routeId, createVehicle(), driver, null);

        ResponseEntity<StopResponse[]> listResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), StopResponse[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(List.of(listResponse.getBody())).hasSize(1);

        ResponseEntity<StopResponse> detailResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops/" + stopId, HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), StopResponse.class);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listAndGetStops_asHostessAssignedToRouteViaTrip_returns200() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Long routeId = createRoute(admin, "Durak Hostess Testi " + UUID.randomUUID());
        restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.POST,
                new HttpEntity<>(new StopCreateRequest("Durak A", 1, LocalTime.of(7, 30)), authHeaderFor(admin)), StopResponse.class);

        Hostess hostess = createHostess();
        createTrip(routeId, createVehicle(), createDriver(), hostess);

        ResponseEntity<StopResponse[]> listResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.GET, new HttpEntity<>(authHeaderFor(hostess.getUser())), StopResponse[].class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(List.of(listResponse.getBody())).hasSize(1);
    }

    @Test
    void listAndGetStops_asDriverNotAssignedToRoute_return404() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Long routeId = createRoute(admin, "Durak Yetkisiz Driver Testi " + UUID.randomUUID());
        ResponseEntity<StopResponse> created = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.POST,
                new HttpEntity<>(new StopCreateRequest("Durak A", 1, LocalTime.of(7, 30)), authHeaderFor(admin)), StopResponse.class);
        Long stopId = created.getBody().id();

        Driver unrelatedDriver = createDriver();

        ResponseEntity<ApiError> listResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.GET, new HttpEntity<>(authHeaderFor(unrelatedDriver.getUser())), ApiError.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> detailResponse = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops/" + stopId, HttpMethod.GET, new HttpEntity<>(authHeaderFor(unrelatedDriver.getUser())), ApiError.class);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createStop_asDriverAssignedToRoute_stillReturns403() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Long routeId = createRoute(admin, "Durak CRUD Yetkisi Testi " + UUID.randomUUID());
        Driver driver = createDriver();
        createTrip(routeId, createVehicle(), driver, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/routes/" + routeId + "/stops", HttpMethod.POST,
                new HttpEntity<>(new StopCreateRequest("Durak A", 1, LocalTime.of(7, 30)), authHeaderFor(driver.getUser())), ApiError.class);

        assertThat(response.getStatusCode())
                .as("DRIVER salt-okunur erişim kazandı ama create/update/delete HÂLÂ ADMIN-only olmalı")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
