package com.okulservisi.backend.trip;
import com.okulservisi.backend.trip.entity.TripStatus;

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
import com.okulservisi.backend.trip.dto.request.TripCreateRequest;
import com.okulservisi.backend.trip.dto.response.TripResponse;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * docs/API.md "9. Sefer" (bkz. docs/RBAC_MATRIX.md). docs/TEST_PLAN.md: T-35/T-41 bu fazda (Faz F)
 * trip tablosu/modülü var olduğu için artık uygulanabilir hale geldi, ANCAK bu testler sadece Trip
 * modülünün KENDİ kurallarını (create çakışma, state machine, nesne bazlı kapsam) doğrular —
 * `/api/vehicles/mine` ve DRIVER/HOSTESS route salt-okunur erişimi (T-35/T-41'in kendisi)
 * VehicleController/RouteController'a dokunmayı gerektirir ve bu agent görevinin kapsamı DIŞINDADIR
 * (koordinatöre raporlandı — bkz. PROGRESS.md).
 */
class TripControllerTest extends AbstractIntegrationTest {

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
        vehicle.setPlate("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    private Route createRoute(RouteDirection direction) {
        Route route = new Route();
        route.setName("Route " + UUID.randomUUID());
        route.setDirection(direction);
        route.setActive(true);
        return routeRepository.save(route);
    }

    private Stop createStop(Route route, int sequenceNo) {
        Stop stop = new Stop();
        stop.setRoute(route);
        stop.setName("Durak " + sequenceNo);
        stop.setSequenceNo(sequenceNo);
        stop.setPlannedTime(LocalTime.of(7, 0));
        return stopRepository.save(stop);
    }

    private Student createStudent(String fullName) {
        Student student = new Student();
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2015, 6, 1));
        student.setActive(true);
        return studentRepository.save(student);
    }

    private TripCreateRequest createRequest(Route route, Vehicle vehicle, Driver driver, Hostess hostess,
            LocalDate businessDate, RouteDirection direction) {
        return new TripCreateRequest(route.getId(), vehicle.getId(), driver.getId(),
                hostess == null ? null : hostess.getId(), businessDate, direction);
    }

    @Test
    void create_asAdmin_startsInPlannedStatus() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route route = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();

        ResponseEntity<TripResponse> response = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle, driver, null, LocalDate.of(2026, 3, 1), RouteDirection.MORNING),
                        authHeaderFor(admin)),
                TripResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(TripStatus.PLANNED);
    }

    @Test
    void create_asNonAdmin_returns403() {
        User driverUser = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Route route = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle, driver, null, LocalDate.of(2026, 3, 1), RouteDirection.MORNING),
                        authHeaderFor(driverUser)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_directionMismatchWithRoute_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route morningRoute = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(morningRoute, vehicle, driver, null, LocalDate.of(2026, 3, 1), RouteDirection.AFTERNOON),
                        authHeaderFor(admin)),
                ApiError.class);

        assertThat(response.getStatusCode())
                .as("sefer yönü güzergâh yönüyle eşleşmiyor")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_sameVehicleSameDateDirection_isRejected() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route routeA = createRoute(RouteDirection.MORNING);
        Route routeB = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle();
        Driver driverA = createDriver();
        Driver driverB = createDriver();
        LocalDate businessDate = LocalDate.of(2026, 4, 1);

        ResponseEntity<TripResponse> first = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(routeA, vehicle, driverA, null, businessDate, RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> second = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(routeB, vehicle, driverB, null, businessDate, RouteDirection.MORNING), authHeaderFor(admin)),
                ApiError.class);

        assertThat(second.getStatusCode())
                .as("aynı araç + aynı tarih + aynı yön - ikinci sefer reddedilmeli")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_sameDriverSameDateDirection_isRejected() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route routeA = createRoute(RouteDirection.MORNING);
        Route routeB = createRoute(RouteDirection.MORNING);
        Vehicle vehicleA = createVehicle();
        Vehicle vehicleB = createVehicle();
        Driver driver = createDriver();
        LocalDate businessDate = LocalDate.of(2026, 4, 2);

        ResponseEntity<TripResponse> first = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(routeA, vehicleA, driver, null, businessDate, RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> second = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(routeB, vehicleB, driver, null, businessDate, RouteDirection.MORNING), authHeaderFor(admin)),
                ApiError.class);

        assertThat(second.getStatusCode())
                .as("aynı şoför + aynı tarih + aynı yön - ikinci sefer reddedilmeli")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void stateMachine_plannedToInProgressToCompleted_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route route = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();

        ResponseEntity<TripResponse> created = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle, driver, null, LocalDate.of(2026, 5, 1), RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);
        Long tripId = created.getBody().id();

        ResponseEntity<TripResponse> started = restTemplate().exchange("/api/trips/" + tripId + "/start", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), TripResponse.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(started.getBody().status()).isEqualTo(TripStatus.IN_PROGRESS);
        assertThat(started.getBody().startedAt()).isNotNull();

        ResponseEntity<TripResponse> completed = restTemplate().exchange("/api/trips/" + tripId + "/complete", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), TripResponse.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().status()).isEqualTo(TripStatus.COMPLETED);
        assertThat(completed.getBody().completedAt()).isNotNull();

        // Tamamlanmış sefer tekrar başlatılamaz.
        ResponseEntity<ApiError> restart = restTemplate().exchange("/api/trips/" + tripId + "/start", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), ApiError.class);
        assertThat(restart.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Tamamlanmış sefer iptal edilemez.
        ResponseEntity<ApiError> cancelAfterComplete = restTemplate().exchange("/api/trips/" + tripId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), ApiError.class);
        assertThat(cancelAfterComplete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancel_fromPlanned_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route route = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();

        ResponseEntity<TripResponse> created = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle, driver, null, LocalDate.of(2026, 5, 2), RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);
        Long tripId = created.getBody().id();

        ResponseEntity<TripResponse> cancelled = restTemplate().exchange("/api/trips/" + tripId + "/cancel", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), TripResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().status()).isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void driverOrHostess_canTransitionOwnTrip_butNotOthersTrip() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route route = createRoute(RouteDirection.MORNING);
        Vehicle vehicle = createVehicle();
        Driver ownDriver = createDriver();
        Driver otherDriver = createDriver();

        ResponseEntity<TripResponse> ownTrip = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle, ownDriver, null, LocalDate.of(2026, 5, 3), RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);
        Vehicle otherVehicle = createVehicle();
        ResponseEntity<TripResponse> otherTrip = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, otherVehicle, otherDriver, null, LocalDate.of(2026, 5, 3), RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);

        // Kendi atandığı sefer: başlatabilir.
        ResponseEntity<TripResponse> startOwn = restTemplate().exchange(
                "/api/trips/" + ownTrip.getBody().id() + "/start", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(ownDriver.getUser())), TripResponse.class);
        assertThat(startOwn.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Başkasının seferi: 404 (kapsam dışı, 403 DEĞİL).
        ResponseEntity<ApiError> startOther = restTemplate().exchange(
                "/api/trips/" + otherTrip.getBody().id() + "/start", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(ownDriver.getUser())), ApiError.class);
        assertThat(startOther.getStatusCode())
                .as("DRIVER kendi atanmadığı sefere erişince 404 (var/yok bilgisi sızdırılmaz)")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void mine_asDriver_returnsOnlyOwnTrips() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route route = createRoute(RouteDirection.MORNING);
        Driver driver = createDriver();
        Driver otherDriver = createDriver();
        Vehicle vehicle1 = createVehicle();
        Vehicle vehicle2 = createVehicle();
        LocalDate businessDate = LocalDate.of(2026, 5, 10);

        ResponseEntity<TripResponse> ownTrip = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle1, driver, null, businessDate, RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);
        restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle2, otherDriver, null, businessDate, RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);

        ResponseEntity<String> mine = restTemplate().exchange(
                "/api/trips/mine?businessDate=" + businessDate, HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), String.class);

        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).contains("\"id\":" + ownTrip.getBody().id());
    }

    @Test
    void getById_asParentOfAssignedChild_succeeds_andOtherChild_returns404() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Route route = createRoute(RouteDirection.MORNING);
        Stop stop = createStop(route, 1);
        Vehicle vehicle = createVehicle();
        Driver driver = createDriver();
        LocalDate businessDate = LocalDate.of(2026, 6, 1);

        ResponseEntity<TripResponse> trip = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(createRequest(route, vehicle, driver, null, businessDate, RouteDirection.MORNING), authHeaderFor(admin)),
                TripResponse.class);

        Student ownChild = createStudent("Sefer Kapsam Öğrenci 1");
        Student otherChild = createStudent("Sefer Kapsam Öğrenci 2");

        StudentRouteAssignment assignment = new StudentRouteAssignment();
        assignment.setStudent(ownChild);
        assignment.setRoute(route);
        assignment.setStop(stop);
        assignment.setDirection(RouteDirection.MORNING);
        assignment.setValidFrom(LocalDate.of(2026, 1, 1));
        studentRouteAssignmentRepository.save(assignment);

        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Sefer Kapsam Veli");
        parent = parentRepository.save(parent);
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(ownChild);
        link.setRelationType("MOTHER");
        link.setActive(true);
        parentStudentLinkRepository.save(link);

        ResponseEntity<TripResponse> accessible = restTemplate().exchange(
                "/api/trips/" + trip.getBody().id(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), TripResponse.class);
        assertThat(accessible.getStatusCode())
                .as("çocuğunun ataması bu seferin route+direction'ına ve tarihine uyuyor - erişilebilir olmalı")
                .isEqualTo(HttpStatus.OK);

        // otherChild hiçbir atamaya sahip değil - bu sefere erişemez.
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
                "/api/trips/" + trip.getBody().id(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(otherParentUser)), ApiError.class);
        assertThat(notAccessible.getStatusCode())
                .as("bağlı olmayan veli için sefer 404 dönmeli")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
