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
import com.okulservisi.backend.route.dto.request.RouteUpdateRequest;
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
 * docs/API.md "7. Güzergâh/Durak" {@code /api/routes} — ADMIN CRUD pozitif kontrol. (Durak/Stop
 * tarafı zaten {@code StopControllerTest}'te T-39/T-40 ile kapsanıyor.) docs/TEST_PLAN.md T-41:
 * DRIVER/HOSTESS'in kendi atandığı route'u salt-okunur görmesi — Faz F takip turunda (trip modülü
 * artık var) uygulandı, bkz. {@code assignedRouteTrip_driverOrHostess_*} testleri altta.
 */
class RouteControllerTest extends AbstractIntegrationTest {

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

    private Route createRoute(RouteDirection direction) {
        Route route = new Route();
        route.setName("Route " + UUID.randomUUID());
        route.setDirection(direction);
        route.setActive(true);
        return routeRepository.save(route);
    }

    private Vehicle createVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    private Trip createTrip(Route route, Vehicle vehicle, Driver driver, Hostess hostess, LocalDate businessDate) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setHostess(hostess);
        trip.setBusinessDate(businessDate);
        trip.setDirection(route.getDirection());
        trip.setStatus(TripStatus.PLANNED);
        return tripRepository.save(trip);
    }

    @Test
    void createRoute_asAdmin_returns200() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        RouteCreateRequest request = new RouteCreateRequest("Yeni Güzergah " + UUID.randomUUID(), RouteDirection.MORNING);

        ResponseEntity<RouteResponse> response = restTemplate().exchange(
                "/api/routes", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), RouteResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo(request.name());
        assertThat(response.getBody().direction()).isEqualTo(RouteDirection.MORNING);
        assertThat(response.getBody().isActive()).isTrue();
    }

    @Test
    void adminCrud_getListAndUpdate_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        RouteCreateRequest createRequest = new RouteCreateRequest("Güncellenecek Güzergah " + UUID.randomUUID(), RouteDirection.AFTERNOON);
        ResponseEntity<RouteResponse> createResponse = restTemplate().exchange(
                "/api/routes", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaderFor(admin)), RouteResponse.class);
        Long routeId = createResponse.getBody().id();

        ResponseEntity<RouteResponse> getResponse = restTemplate().exchange(
                "/api/routes/" + routeId, HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), RouteResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().id()).isEqualTo(routeId);

        // size=100 (MAX_SIZE, bkz. SortAllowlist) ACIKCA istenir: paylasilan test container'inda ayni
        // suite icindeki diger test siniflari (StopControllerTest/AssignmentControllerTest vb.) da
        // route olusturuyor; varsayilan sayfa boyutu (20) ile bu test, olusturulan kaydin ilk sayfada
        // olup olmamasina (yani diger testlerin kac route olusturdugu gibi KENDI KAPSAMI DISI bir
        // detaya) bagimli hale gelirdi - bu assertion'in amaci (yeni olusturulan kaydin listede
        // gorunmesi) icin sayfa boyutunun yeterince buyuk olmasi yeterlidir, siralama/sayim onemli
        // degildir.
        ResponseEntity<String> listResponse = restTemplate().exchange(
                "/api/routes?size=100", HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).contains("\"id\":" + routeId);

        RouteUpdateRequest updateRequest = new RouteUpdateRequest("Güncellenmiş Ad", RouteDirection.MORNING, false);
        ResponseEntity<RouteResponse> updateResponse = restTemplate().exchange(
                "/api/routes/" + routeId, HttpMethod.PATCH, new HttpEntity<>(updateRequest, authHeaderFor(admin)), RouteResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Güncellenmiş Ad");
        assertThat(updateResponse.getBody().direction()).isEqualTo(RouteDirection.MORNING);
        assertThat(updateResponse.getBody().isActive()).isFalse();
    }

    @Test
    void createRoute_asDriverOrHostess_returns403() {
        for (Role role : new Role[] {Role.DRIVER, Role.HOSTESS}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);
            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/routes", HttpMethod.POST,
                    new HttpEntity<>(new RouteCreateRequest("X " + UUID.randomUUID(), RouteDirection.MORNING), authHeaderFor(user)),
                    ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void routeEndpoints_asParentOrStudent_return403() {
        for (Role role : new Role[] {Role.PARENT, Role.STUDENT}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);
            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/routes", HttpMethod.GET, new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ---- T-41 ----
    @Test
    void assignedRouteTrip_driver_seesOwnRouteInListAndDetail_butNotOthers() {
        Driver driver = createDriver();
        Driver otherDriver = createDriver();
        Route ownRoute = createRoute(RouteDirection.MORNING);
        Route otherRoute = createRoute(RouteDirection.MORNING);
        createTrip(ownRoute, createVehicle(), driver, null, LocalDate.of(2026, 7, 1));
        createTrip(otherRoute, createVehicle(), otherDriver, null, LocalDate.of(2026, 7, 1));

        ResponseEntity<String> listResponse = restTemplate().exchange(
                "/api/routes?size=100", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .as("DRIVER sadece kendi atandığı (bir Trip'inin kullandığı) route'u görmeli")
                .contains("\"id\":" + ownRoute.getId())
                .doesNotContain("\"id\":" + otherRoute.getId());

        ResponseEntity<RouteResponse> ownDetail = restTemplate().exchange(
                "/api/routes/" + ownRoute.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), RouteResponse.class);
        assertThat(ownDetail.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> otherDetail = restTemplate().exchange(
                "/api/routes/" + otherRoute.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), ApiError.class);
        assertThat(otherDetail.getStatusCode())
                .as("kendi atanmadığı route'a erişim 404 dönmeli (403 DEĞİL — var/yok bilgisi sızdırılmaz)")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignedRouteTrip_hostess_seesOwnRouteInListAndDetail() {
        Hostess hostess = createHostess();
        Route ownRoute = createRoute(RouteDirection.AFTERNOON);
        Route otherRoute = createRoute(RouteDirection.AFTERNOON);
        createTrip(ownRoute, createVehicle(), createDriver(), hostess, LocalDate.of(2026, 7, 2));

        ResponseEntity<String> listResponse = restTemplate().exchange(
                "/api/routes?size=100", HttpMethod.GET, new HttpEntity<>(authHeaderFor(hostess.getUser())), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .contains("\"id\":" + ownRoute.getId())
                .doesNotContain("\"id\":" + otherRoute.getId());

        ResponseEntity<ApiError> otherDetail = restTemplate().exchange(
                "/api/routes/" + otherRoute.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(hostess.getUser())), ApiError.class);
        assertThat(otherDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void driverWithNoTrips_seesEmptyListAndGets404ForAnyRoute() {
        Driver driver = createDriver();
        Route someRoute = createRoute(RouteDirection.MORNING);

        ResponseEntity<String> listResponse = restTemplate().exchange(
                "/api/routes", HttpMethod.GET, new HttpEntity<>(authHeaderFor(driver.getUser())), String.class);
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .as("hiç seferi olmayan DRIVER için liste tamamen boş olmalı (null/sınırsız filtreye DÜŞMEMELİ)")
                .contains("\"totalElements\":0");

        ResponseEntity<ApiError> detail = restTemplate().exchange(
                "/api/routes/" + someRoute.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), ApiError.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
