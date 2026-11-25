package com.okulservisi.backend.tracking;
import com.okulservisi.backend.tracking.entity.VehicleLocation;
import com.okulservisi.backend.tracking.repository.VehicleLocationRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.route.entity.Stop;
import com.okulservisi.backend.route.repository.StopRepository;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.tracking.dto.response.LocationAckResponse;
import com.okulservisi.backend.tracking.dto.response.LocationResponse;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
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
 * docs/API.md "16. Konum takibi" (bkz. docs/RBAC_MATRIX.md). "Aktif sefer" = {@code IN_PROGRESS}
 * yorumu burada da doğrulanır (bkz. {@code TrackingService} javadoc'u, gerekçe orada).
 */
class TrackingControllerTest extends AbstractIntegrationTest {

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
    private RouteRepository routeRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentRouteAssignmentRepository studentRouteAssignmentRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentLinkRepository parentStudentLinkRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private VehicleLocationRepository vehicleLocationRepository;

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

    private Vehicle createVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("TRK-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    private Route createRoute() {
        Route route = new Route();
        route.setName("Tracking Route " + UUID.randomUUID());
        route.setDirection(RouteDirection.MORNING);
        route.setActive(true);
        return routeRepository.save(route);
    }

    private Trip createTrip(Route route, Vehicle vehicle, Driver driver, Hostess hostess, LocalDate businessDate, TripStatus status) {
        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setHostess(hostess);
        trip.setBusinessDate(businessDate);
        trip.setDirection(RouteDirection.MORNING);
        trip.setStatus(status);
        if (status == TripStatus.IN_PROGRESS) {
            trip.setStartedAt(Instant.now());
        }
        return tripRepository.save(trip);
    }

    private VehicleLocation insertLocation(Trip trip, Instant recordedAt) {
        VehicleLocation location = new VehicleLocation();
        location.setTrip(trip);
        location.setLat(new java.math.BigDecimal("40.123456"));
        location.setLng(new java.math.BigDecimal("29.654321"));
        location.setRecordedAt(recordedAt);
        return vehicleLocationRepository.save(location);
    }

    private Map<String, Object> locationBody(double lat, double lng, Instant recordedAt) {
        return Map.of("lat", lat, "lng", lng, "recordedAt", recordedAt.toString());
    }

    @Test
    void recordLocation_asAssignedDriver_whenInProgress_succeeds() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Trip trip = createTrip(route, vehicle, driver, null, LocalDate.of(2026, 7, 1), TripStatus.IN_PROGRESS);

        ResponseEntity<LocationAckResponse> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/locations", HttpMethod.POST,
                new HttpEntity<>(locationBody(40.1, 29.2, Instant.now()), authHeaderFor(driver.getUser())),
                LocationAckResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().lat()).isEqualTo(40.1);
        assertThat(response.getBody().lng()).isEqualTo(29.2);
    }

    @Test
    void recordLocation_asUnassignedDriver_returns404() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver ownerDriver = createDriver();
        Driver otherDriver = createDriver();
        Trip trip = createTrip(route, vehicle, ownerDriver, null, LocalDate.of(2026, 7, 2), TripStatus.IN_PROGRESS);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/locations", HttpMethod.POST,
                new HttpEntity<>(locationBody(40.1, 29.2, Instant.now()), authHeaderFor(otherDriver.getUser())),
                ApiError.class);

        assertThat(response.getStatusCode())
                .as("bu sefere atanmamış DRIVER - 404 (kapsam dışı, bilgi sızdırılmaz)")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void recordLocation_asHostess_returns403() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Hostess hostess = createHostess();
        Trip trip = createTrip(route, vehicle, driver, hostess, LocalDate.of(2026, 7, 3), TripStatus.IN_PROGRESS);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/locations", HttpMethod.POST,
                new HttpEntity<>(locationBody(40.1, 29.2, Instant.now()), authHeaderFor(hostess.getUser())),
                ApiError.class);

        assertThat(response.getStatusCode())
                .as("HOSTESS konum GÖNDEREMEZ (DRIVER'dan farklı) - 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void recordLocation_whenTripNotInProgress_returns409() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Trip trip = createTrip(route, vehicle, driver, null, LocalDate.of(2026, 7, 4), TripStatus.PLANNED);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/locations", HttpMethod.POST,
                new HttpEntity<>(locationBody(40.1, 29.2, Instant.now()), authHeaderFor(driver.getUser())),
                ApiError.class);

        assertThat(response.getStatusCode())
                .as("sefer PLANNED (henüz IN_PROGRESS değil) - 409")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void latest_asAdmin_succeedsRegardlessOfTripStatus() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Trip trip = createTrip(route, vehicle, driver, null, LocalDate.of(2026, 7, 5), TripStatus.PLANNED);
        insertLocation(trip, Instant.now());

        ResponseEntity<LocationResponse> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), LocationResponse.class);

        assertThat(response.getStatusCode())
                .as("ADMIN sefer durumu fark etmeksizin (PLANNED dahi) izleyebilir")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void latest_asAssignedDriver_whenInProgress_computesStaleSecondsAndOffline() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Trip trip = createTrip(route, vehicle, driver, null, LocalDate.of(2026, 7, 6), TripStatus.IN_PROGRESS);
        insertLocation(trip, Instant.now().minusSeconds(200));

        ResponseEntity<LocationResponse> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), LocationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().staleSeconds()).isGreaterThan(90);
        assertThat(response.getBody().isOffline())
                .as("200 saniye önceki konum - 90s eşiğinin üzerinde, offline sayılmalı")
                .isTrue();
    }

    @Test
    void latest_freshLocation_isNotOffline() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Trip trip = createTrip(route, vehicle, driver, null, LocalDate.of(2026, 7, 7), TripStatus.IN_PROGRESS);
        insertLocation(trip, Instant.now());

        ResponseEntity<LocationResponse> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), LocationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().staleSeconds()).isLessThanOrEqualTo(90);
        assertThat(response.getBody().isOffline()).isFalse();
    }

    @Test
    void latest_asDriver_whenTripNotInProgress_returns404() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Trip trip = createTrip(route, vehicle, driver, null, LocalDate.of(2026, 7, 8), TripStatus.PLANNED);
        insertLocation(trip, Instant.now());

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), ApiError.class);

        assertThat(response.getStatusCode())
                .as("DRIVER için sefer IN_PROGRESS değilse 'aktif' sayılmaz - 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void latest_noLocationRecordedYet_returns404() {
        Route route = createRoute();
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        Trip trip = createTrip(route, vehicle, driver, null, LocalDate.of(2026, 7, 9), TripStatus.IN_PROGRESS);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), ApiError.class);

        assertThat(response.getStatusCode())
                .as("hiç konum kaydı yok - 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void latest_asUnrelatedParent_returns404_andAsRelatedParent_whenInProgress_succeeds() {
        Route route = createRoute();
        Stop stop = new Stop();
        stop.setRoute(route);
        stop.setName("Durak 1");
        stop.setSequenceNo(1);
        stop.setPlannedTime(LocalTime.of(7, 0));
        stop = stopRepository.save(stop);

        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        LocalDate businessDate = LocalDate.of(2026, 7, 10);
        Trip trip = createTrip(route, vehicle, driver, null, businessDate, TripStatus.IN_PROGRESS);
        insertLocation(trip, Instant.now());

        Student ownChild = new Student();
        ownChild.setFullName("Konum Kapsam Öğrenci");
        ownChild.setBirthDate(LocalDate.of(2015, 1, 1));
        ownChild.setActive(true);
        ownChild = studentRepository.save(ownChild);

        StudentRouteAssignment assignment = new StudentRouteAssignment();
        assignment.setStudent(ownChild);
        assignment.setRoute(route);
        assignment.setStop(stop);
        assignment.setDirection(RouteDirection.MORNING);
        assignment.setValidFrom(LocalDate.of(2026, 1, 1));
        studentRouteAssignmentRepository.save(assignment);

        User relatedParentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent relatedParent = new Parent();
        relatedParent.setUser(relatedParentUser);
        relatedParent.setFullName("Konum Kapsam Veli");
        relatedParent = parentRepository.save(relatedParent);
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(relatedParent);
        link.setStudent(ownChild);
        link.setRelationType("MOTHER");
        link.setActive(true);
        parentStudentLinkRepository.save(link);

        User unrelatedParentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<LocationResponse> relatedResponse = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(relatedParentUser)), LocationResponse.class);
        assertThat(relatedResponse.getStatusCode())
                .as("çocuğunun ataması bu seferin route+direction'ına uyuyor - erişilebilir")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> unrelatedResponse = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(unrelatedParentUser)), ApiError.class);
        assertThat(unrelatedResponse.getStatusCode())
                .as("bağlı olmayan veli için 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * docs/PROGRESS.md "bilinen açık konular" — STUDENT rolü için tracking/latest kapsamı daha önce
     * kod okunarak doğrulanmıştı (bkz. {@code StudentScopePolicy#accessibleStudentIds} STUDENT dalı:
     * kendi {@code Student} kaydıyla eşleşir), ama ayrı bir entegrasyon testi yazılmamıştı. PARENT
     * testiyle (yukarıda) AYNI senaryo/yapı — tek fark STUDENT'ın {@code Student.user} alanı
     * üzerinden DOĞRUDAN kendi kaydına bağlanması (veli-öğrenci bağlantı tablosu üzerinden değil).
     */
    @Test
    void latest_asUnrelatedStudent_returns404_andAsOwnStudent_whenInProgress_succeeds() {
        Route route = createRoute();
        Stop stop = new Stop();
        stop.setRoute(route);
        stop.setName("Durak 1");
        stop.setSequenceNo(1);
        stop.setPlannedTime(LocalTime.of(7, 0));
        stop = stopRepository.save(stop);

        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        LocalDate businessDate = LocalDate.of(2026, 7, 11);
        Trip trip = createTrip(route, vehicle, driver, null, businessDate, TripStatus.IN_PROGRESS);
        insertLocation(trip, Instant.now());

        User ownStudentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);
        Student ownStudent = new Student();
        ownStudent.setUser(ownStudentUser);
        ownStudent.setFullName("Konum Kapsam Öğrenci (Kendi Hesabı)");
        ownStudent.setBirthDate(LocalDate.of(2015, 1, 1));
        ownStudent.setActive(true);
        ownStudent = studentRepository.save(ownStudent);

        StudentRouteAssignment assignment = new StudentRouteAssignment();
        assignment.setStudent(ownStudent);
        assignment.setRoute(route);
        assignment.setStop(stop);
        assignment.setDirection(RouteDirection.MORNING);
        assignment.setValidFrom(LocalDate.of(2026, 1, 1));
        studentRouteAssignmentRepository.save(assignment);

        User unrelatedStudentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);
        Student unrelatedStudent = new Student();
        unrelatedStudent.setUser(unrelatedStudentUser);
        unrelatedStudent.setFullName("Kapsam Dışı Öğrenci");
        unrelatedStudent.setBirthDate(LocalDate.of(2015, 1, 1));
        unrelatedStudent.setActive(true);
        studentRepository.save(unrelatedStudent);

        ResponseEntity<LocationResponse> ownResponse = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(ownStudentUser)), LocationResponse.class);
        assertThat(ownResponse.getStatusCode())
                .as("kendi ataması bu seferin route+direction'ına uyuyor - erişilebilir")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> unrelatedResponse = restTemplate().exchange(
                "/api/tracking/trips/" + trip.getId() + "/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(unrelatedStudentUser)), ApiError.class);
        assertThat(unrelatedResponse.getStatusCode())
                .as("bu sefere/güzergâha atanmamış öğrenci için 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
