package com.okulservisi.backend.attendance;
import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import com.okulservisi.backend.attendance.dto.response.RosterStatus;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.attendance.dto.request.AbsenceRequest;
import com.okulservisi.backend.attendance.dto.response.AttendanceEventResponse;
import com.okulservisi.backend.attendance.dto.request.BoardingRequest;
import com.okulservisi.backend.attendance.dto.request.DropoffRequest;
import com.okulservisi.backend.attendance.dto.response.TripRosterEntryResponse;
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
 * docs/API.md "10. Biniş/İniş/Devamsızlık" (bkz. docs/RBAC_MATRIX.md, docs/DATABASE_DESIGN.md
 * "Sefer / Yoklama").
 */
class AttendanceEventControllerTest extends AbstractIntegrationTest {

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
    private StudentRepository studentRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentLinkRepository parentStudentLinkRepository;

    @Autowired
    private StopRepository stopRepository;

    @Autowired
    private StudentRouteAssignmentRepository studentRouteAssignmentRepository;

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

    private Trip createTrip(Driver driver, Hostess hostess, LocalDate businessDate) {
        Route route = new Route();
        route.setName("Route " + UUID.randomUUID());
        route.setDirection(RouteDirection.MORNING);
        route.setActive(true);
        route = routeRepository.save(route);

        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        vehicle = vehicleRepository.save(vehicle);

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setHostess(hostess);
        trip.setBusinessDate(businessDate);
        trip.setDirection(RouteDirection.MORNING);
        trip.setStatus(TripStatus.PLANNED);
        return tripRepository.save(trip);
    }

    private Student createStudent(String fullName) {
        Student student = new Student();
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2015, 6, 1));
        student.setActive(true);
        return studentRepository.save(student);
    }

    private Route createRoute() {
        Route route = new Route();
        route.setName("Roster Route " + UUID.randomUUID());
        route.setDirection(RouteDirection.MORNING);
        route.setActive(true);
        return routeRepository.save(route);
    }

    private Stop createStop(Route route, String name, int sequenceNo) {
        Stop stop = new Stop();
        stop.setRoute(route);
        stop.setName(name);
        stop.setSequenceNo(sequenceNo);
        stop.setPlannedTime(LocalTime.of(7, sequenceNo % 60));
        return stopRepository.save(stop);
    }

    private Trip createTripForRoute(Route route, Driver driver, Hostess hostess, LocalDate businessDate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("ROSTER-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(20);
        vehicle.setActive(true);
        vehicle = vehicleRepository.save(vehicle);

        Trip trip = new Trip();
        trip.setRoute(route);
        trip.setVehicle(vehicle);
        trip.setDriver(driver);
        trip.setHostess(hostess);
        trip.setBusinessDate(businessDate);
        trip.setDirection(RouteDirection.MORNING);
        trip.setStatus(TripStatus.PLANNED);
        return tripRepository.save(trip);
    }

    private void assignStudentToStop(Student student, Route route, Stop stop, LocalDate businessDate) {
        StudentRouteAssignment assignment = new StudentRouteAssignment();
        assignment.setStudent(student);
        assignment.setRoute(route);
        assignment.setStop(stop);
        assignment.setDirection(RouteDirection.MORNING);
        assignment.setValidFrom(businessDate.minusDays(30));
        assignment.setValidTo(businessDate.plusDays(30));
        studentRouteAssignmentRepository.save(assignment);
    }

    @Test
    void boarding_asAssignedDriver_succeeds() {
        Driver driver = createDriver();
        Trip trip = createTrip(driver, null, LocalDate.of(2026, 3, 1));
        Student student = createStudent("Yoklama Öğrenci 1");

        ResponseEntity<AttendanceEventResponse> response = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), authHeaderFor(driver.getUser())),
                AttendanceEventResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().eventType()).isEqualTo(AttendanceEventType.BOARDING);
    }

    @Test
    void boarding_asUnassignedDriver_returns404() {
        Driver assignedDriver = createDriver();
        Driver otherDriver = createDriver();
        Trip trip = createTrip(assignedDriver, null, LocalDate.of(2026, 3, 2));
        Student student = createStudent("Yoklama Öğrenci 2");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), authHeaderFor(otherDriver.getUser())),
                ApiError.class);

        assertThat(response.getStatusCode())
                .as("kendi atanmadığı sefere biniş kaydı eklemeye çalışan DRIVER için 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void boarding_asParentOrStudent_returns403() {
        Driver driver = createDriver();
        Trip trip = createTrip(driver, null, LocalDate.of(2026, 3, 3));
        Student student = createStudent("Yoklama Öğrenci 3");
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), authHeaderFor(parentUser)),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateBoarding_sameStudentSameTrip_isRejected() {
        Driver driver = createDriver();
        Trip trip = createTrip(driver, null, LocalDate.of(2026, 3, 4));
        Student student = createStudent("Yoklama Öğrenci 4");
        HttpHeaders headers = authHeaderFor(driver.getUser());

        ResponseEntity<AttendanceEventResponse> first = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), headers), AttendanceEventResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> second = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), headers), ApiError.class);
        assertThat(second.getStatusCode())
                .as("aynı sefer+öğrenci+BOARDING ikinci kez eklenemez")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void dropoff_withoutPriorBoarding_isRejected() {
        Driver driver = createDriver();
        Trip trip = createTrip(driver, null, LocalDate.of(2026, 3, 5));
        Student student = createStudent("Yoklama Öğrenci 5");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/attendance-events/dropoffs", HttpMethod.POST,
                new HttpEntity<>(new DropoffRequest(student.getId()), authHeaderFor(driver.getUser())),
                ApiError.class);

        assertThat(response.getStatusCode())
                .as("biniş kaydı olmadan iniş kaydedilemez")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void dropoff_afterBoarding_succeeds() {
        Driver driver = createDriver();
        Trip trip = createTrip(driver, null, LocalDate.of(2026, 3, 6));
        Student student = createStudent("Yoklama Öğrenci 6");
        HttpHeaders headers = authHeaderFor(driver.getUser());

        restTemplate().exchange("/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), headers), AttendanceEventResponse.class);

        ResponseEntity<AttendanceEventResponse> dropoff = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/attendance-events/dropoffs", HttpMethod.POST,
                new HttpEntity<>(new DropoffRequest(student.getId()), headers), AttendanceEventResponse.class);

        assertThat(dropoff.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dropoff.getBody().eventType()).isEqualTo(AttendanceEventType.DROPOFF);
    }

    @Test
    void absence_whenBoardingAlreadyExists_isRejected_andViceVersa() {
        Driver driver = createDriver();
        Trip tripA = createTrip(driver, null, LocalDate.of(2026, 3, 7));
        Trip tripB = createTrip(driver, null, LocalDate.of(2026, 3, 8));
        Student studentA = createStudent("Yoklama Öğrenci 7a");
        Student studentB = createStudent("Yoklama Öğrenci 7b");
        HttpHeaders headers = authHeaderFor(driver.getUser());

        // tripA: önce boarding, sonra absence -> reddedilmeli.
        restTemplate().exchange("/api/trips/" + tripA.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(studentA.getId()), headers), AttendanceEventResponse.class);
        ResponseEntity<ApiError> absenceAfterBoarding = restTemplate().exchange(
                "/api/trips/" + tripA.getId() + "/attendance-events/absences", HttpMethod.POST,
                new HttpEntity<>(new AbsenceRequest(studentA.getId(), null), headers), ApiError.class);
        assertThat(absenceAfterBoarding.getStatusCode())
                .as("biniş kaydı varken devamsızlık eklenemez")
                .isEqualTo(HttpStatus.CONFLICT);

        // tripB: önce absence, sonra boarding -> reddedilmeli.
        restTemplate().exchange("/api/trips/" + tripB.getId() + "/attendance-events/absences", HttpMethod.POST,
                new HttpEntity<>(new AbsenceRequest(studentB.getId(), "Hastalık"), headers), AttendanceEventResponse.class);
        ResponseEntity<ApiError> boardingAfterAbsence = restTemplate().exchange(
                "/api/trips/" + tripB.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(studentB.getId()), headers), ApiError.class);
        assertThat(boardingAfterAbsence.getStatusCode())
                .as("devamsızlık kaydı varken biniş eklenemez")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getStudentAttendance_asAdmin_seesAllRecords() {
        Driver driver = createDriver();
        Trip trip = createTrip(driver, null, LocalDate.of(2026, 3, 9));
        Student student = createStudent("Yoklama Öğrenci 8");
        restTemplate().exchange("/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), authHeaderFor(driver.getUser())),
                AttendanceEventResponse.class);

        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        ResponseEntity<String> response = restTemplate().exchange(
                "/api/students/" + student.getId() + "/attendance", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"eventType\":\"BOARDING\"");
    }

    @Test
    void getStudentAttendance_asUnrelatedParent_returns404() {
        Driver driver = createDriver();
        Trip trip = createTrip(driver, null, LocalDate.of(2026, 3, 10));
        Student student = createStudent("Yoklama Öğrenci 9");
        restTemplate().exchange("/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), authHeaderFor(driver.getUser())),
                AttendanceEventResponse.class);

        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("İlgisiz Veli");
        parentRepository.save(parent);
        // KASITLI: hiçbir ParentStudentLink oluşturulmadı.

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/students/" + student.getId() + "/attendance", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("bağlı olmayan veli için öğrencinin yoklama kaydı 404 dönmeli")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getStudentAttendance_asAssignedDriver_seesOnlyOwnTripRecords() {
        Driver driver = createDriver();
        Driver otherDriver = createDriver();
        Trip ownTrip = createTrip(driver, null, LocalDate.of(2026, 3, 11));
        Trip otherTrip = createTrip(otherDriver, null, LocalDate.of(2026, 3, 11));
        Student student = createStudent("Yoklama Öğrenci 10");

        restTemplate().exchange("/api/trips/" + ownTrip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(student.getId()), authHeaderFor(driver.getUser())),
                AttendanceEventResponse.class);
        restTemplate().exchange("/api/trips/" + otherTrip.getId() + "/attendance-events/absences", HttpMethod.POST,
                new HttpEntity<>(new AbsenceRequest(student.getId(), null), authHeaderFor(otherDriver.getUser())),
                AttendanceEventResponse.class);

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/students/" + student.getId() + "/attendance", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(driver.getUser())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("DRIVER sadece kendi atandığı sefere ait kaydı görmeli")
                .contains("\"tripId\":" + ownTrip.getId())
                .doesNotContain("\"tripId\":" + otherTrip.getId());
    }

    @Test
    void roster_asAdmin_returnsAssignedStudentsInSequenceOrderWithStatus() {
        Driver driver = createDriver();
        Route route = createRoute();
        Stop stop1 = createStop(route, "Durak 1", 1);
        Stop stop2 = createStop(route, "Durak 2", 2);
        Stop stop3 = createStop(route, "Durak 3", 3);
        Stop stop4 = createStop(route, "Durak 4", 4);
        LocalDate businessDate = LocalDate.of(2026, 4, 1);
        Trip trip = createTripForRoute(route, driver, null, businessDate);

        Student boarded = createStudent("Roster Öğrenci Binmiş");
        Student droppedOff = createStudent("Roster Öğrenci İnmiş");
        Student absent = createStudent("Roster Öğrenci Devamsız");
        Student none = createStudent("Roster Öğrenci Kayıt Yok");

        // KASITLI olarak ters sırada atandı - dönen listenin sequenceNo'ya göre sıralandığını doğrulamak için.
        assignStudentToStop(none, route, stop4, businessDate);
        assignStudentToStop(absent, route, stop3, businessDate);
        assignStudentToStop(droppedOff, route, stop2, businessDate);
        assignStudentToStop(boarded, route, stop1, businessDate);

        HttpHeaders driverHeaders = authHeaderFor(driver.getUser());
        restTemplate().exchange("/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(boarded.getId()), driverHeaders), AttendanceEventResponse.class);
        restTemplate().exchange("/api/trips/" + trip.getId() + "/attendance-events/boardings", HttpMethod.POST,
                new HttpEntity<>(new BoardingRequest(droppedOff.getId()), driverHeaders), AttendanceEventResponse.class);
        restTemplate().exchange("/api/trips/" + trip.getId() + "/attendance-events/dropoffs", HttpMethod.POST,
                new HttpEntity<>(new DropoffRequest(droppedOff.getId()), driverHeaders), AttendanceEventResponse.class);
        restTemplate().exchange("/api/trips/" + trip.getId() + "/attendance-events/absences", HttpMethod.POST,
                new HttpEntity<>(new AbsenceRequest(absent.getId(), "Hastalık"), driverHeaders), AttendanceEventResponse.class);

        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        ResponseEntity<TripRosterEntryResponse[]> response = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/roster", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), TripRosterEntryResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TripRosterEntryResponse[] roster = response.getBody();
        assertThat(roster).hasSize(4);

        assertThat(roster[0].studentId()).isEqualTo(boarded.getId());
        assertThat(roster[0].sequenceNo()).isEqualTo(1);
        assertThat(roster[0].status()).isEqualTo(RosterStatus.BOARDED);

        assertThat(roster[1].studentId()).isEqualTo(droppedOff.getId());
        assertThat(roster[1].sequenceNo()).isEqualTo(2);
        assertThat(roster[1].status())
                .as("boarding+dropoff ikisi de varsa DROPPED_OFF öncelikli gösterilmeli")
                .isEqualTo(RosterStatus.DROPPED_OFF);

        assertThat(roster[2].studentId()).isEqualTo(absent.getId());
        assertThat(roster[2].sequenceNo()).isEqualTo(3);
        assertThat(roster[2].status()).isEqualTo(RosterStatus.ABSENT);

        assertThat(roster[3].studentId()).isEqualTo(none.getId());
        assertThat(roster[3].sequenceNo()).isEqualTo(4);
        assertThat(roster[3].status())
                .as("hiç yoklama kaydı olmayan öğrenci için NONE")
                .isEqualTo(RosterStatus.NONE);
    }

    @Test
    void roster_asAssignedDriver_succeeds_andUnassignedDriver_returns404() {
        Driver assignedDriver = createDriver();
        Driver otherDriver = createDriver();
        Route route = createRoute();
        Stop stop = createStop(route, "Durak 1", 1);
        LocalDate businessDate = LocalDate.of(2026, 4, 2);
        Trip trip = createTripForRoute(route, assignedDriver, null, businessDate);
        Student student = createStudent("Roster Öğrenci Kapsam");
        assignStudentToStop(student, route, stop, businessDate);

        ResponseEntity<TripRosterEntryResponse[]> assignedResponse = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/roster", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(assignedDriver.getUser())), TripRosterEntryResponse[].class);
        assertThat(assignedResponse.getStatusCode())
                .as("sefere atanmış DRIVER roster'ı görebilmeli")
                .isEqualTo(HttpStatus.OK);
        assertThat(assignedResponse.getBody()).hasSize(1);

        ResponseEntity<ApiError> unassignedResponse = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/roster", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(otherDriver.getUser())), ApiError.class);
        assertThat(unassignedResponse.getStatusCode())
                .as("kendi atanmadığı sefer için DRIVER 404 almalı")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void roster_asHostess_assignedToTrip_succeeds() {
        Driver driver = createDriver();
        Hostess hostess = createHostess();
        Route route = createRoute();
        Stop stop = createStop(route, "Durak 1", 1);
        LocalDate businessDate = LocalDate.of(2026, 4, 3);
        Trip trip = createTripForRoute(route, driver, hostess, businessDate);
        Student student = createStudent("Roster Öğrenci Hostes");
        assignStudentToStop(student, route, stop, businessDate);

        ResponseEntity<TripRosterEntryResponse[]> response = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/roster", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(hostess.getUser())), TripRosterEntryResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].studentId()).isEqualTo(student.getId());
    }

    @Test
    void roster_nonexistentTrip_returns404() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/trips/999999/roster", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("var olmayan tripId - 404")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void roster_asParentOrStudent_returns403() {
        Driver driver = createDriver();
        Route route = createRoute();
        LocalDate businessDate = LocalDate.of(2026, 4, 4);
        Trip trip = createTripForRoute(route, driver, null, businessDate);

        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        User studentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);

        ResponseEntity<ApiError> parentResponse = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/roster", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ApiError.class);
        assertThat(parentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ApiError> studentResponse = restTemplate().exchange(
                "/api/trips/" + trip.getId() + "/roster", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(studentUser)), ApiError.class);
        assertThat(studentResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
