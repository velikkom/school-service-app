package com.okulservisi.backend.support;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.contract.entity.Contract;
import com.okulservisi.backend.contract.entity.ContractStatus;
import com.okulservisi.backend.contract.repository.ContractRepository;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.hostess.entity.Hostess;
import com.okulservisi.backend.hostess.repository.HostessRepository;
import com.okulservisi.backend.notification.repository.NotificationRepository;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.route.entity.Stop;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.route.repository.StopRepository;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.trip.dto.request.TripCreateRequest;
import com.okulservisi.backend.trip.dto.response.TripResponse;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 2026-11-18 — günlük operasyon testleri için ortak fixture'lar (sefer + yolcu listesi + veli bağı).
 * Her çağrı benzersiz veri üretir (paylaşılan Testcontainers DB'de metotlar arası çakışma yok).
 */
public abstract class AbstractOperationsTest extends AbstractIntegrationTest {

    protected static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;
    @Autowired protected DriverRepository driverRepository;
    @Autowired protected HostessRepository hostessRepository;
    @Autowired protected VehicleRepository vehicleRepository;
    @Autowired protected RouteRepository routeRepository;
    @Autowired protected StopRepository stopRepository;
    @Autowired protected StudentRepository studentRepository;
    @Autowired protected StudentRouteAssignmentRepository assignmentRepository;
    @Autowired protected ParentRepository parentRepository;
    @Autowired protected ParentStudentLinkRepository parentStudentLinkRepository;
    @Autowired protected NotificationRepository notificationRepository;
    @Autowired protected ContractRepository contractRepository;

    protected HttpHeaders auth(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    protected User user(Role role) {
        return TestUsers.createUser(userRepository, passwordEncoder, role, true);
    }

    protected Driver driver() {
        Driver driver = new Driver();
        driver.setUser(user(Role.DRIVER));
        driver.setLicenseNo("LIC-" + UUID.randomUUID());
        driver.setActive(true);
        return driverRepository.save(driver);
    }

    protected Hostess hostess() {
        Hostess hostess = new Hostess();
        hostess.setUser(user(Role.HOSTESS));
        hostess.setActive(true);
        return hostessRepository.save(hostess);
    }

    protected Vehicle vehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate("OPS-" + UUID.randomUUID().toString().substring(0, 8));
        vehicle.setCapacity(16);
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    protected Route route(RouteDirection direction) {
        Route route = new Route();
        route.setName("Hat " + UUID.randomUUID().toString().substring(0, 8));
        route.setDirection(direction);
        route.setActive(true);
        return routeRepository.save(route);
    }

    protected Stop stop(Route route, int sequenceNo) {
        Stop stop = new Stop();
        stop.setRoute(route);
        stop.setName("Durak " + sequenceNo + " " + UUID.randomUUID().toString().substring(0, 4));
        stop.setSequenceNo(sequenceNo);
        stop.setPlannedTime(LocalTime.of(7, 10 * sequenceNo % 60));
        return stopRepository.save(stop);
    }

    protected Student student(String name, User studentUser) {
        Student student = new Student();
        student.setFullName(name);
        student.setBirthDate(LocalDate.of(2015, 6, 1));
        student.setActive(true);
        student.setUser(studentUser);
        return studentRepository.save(student);
    }

    protected StudentRouteAssignment assign(Student student, Stop stop, LocalDate from, LocalDate to) {
        StudentRouteAssignment assignment = new StudentRouteAssignment();
        assignment.setStudent(student);
        assignment.setRoute(stop.getRoute());
        assignment.setStop(stop);
        assignment.setDirection(stop.getRoute().getDirection());
        assignment.setValidFrom(from);
        assignment.setValidTo(to);
        return assignmentRepository.save(assignment);
    }

    protected Parent parentOf(Student... students) {
        User parentUser = user(Role.PARENT);
        Parent parent = new Parent();
        parent.setUser(parentUser);
        parent.setFullName("Veli " + UUID.randomUUID().toString().substring(0, 6));
        parent = parentRepository.save(parent);
        for (Student student : students) {
            ParentStudentLink link = new ParentStudentLink();
            link.setParent(parent);
            link.setStudent(student);
            link.setRelationType("MOTHER");
            link.setActive(true);
            parentStudentLinkRepository.save(link);
        }
        return parent;
    }

    protected Contract activeContract(Student student) {
        Contract contract = new Contract();
        contract.setStudent(student);
        contract.setMonthlyFee(new BigDecimal("1250.00"));
        contract.setDiscountAmount(BigDecimal.ZERO);
        contract.setStartDate(LocalDate.now(ISTANBUL).minusMonths(1));
        contract.setStatus(ContractStatus.ACTIVE);
        return contractRepository.save(contract);
    }

    protected LocalDate today() {
        return LocalDate.now(ISTANBUL);
    }

    protected long notificationCount(User recipient, String eventKey) {
        return notificationRepository.countByRecipientIdAndEventKey(recipient.getId(), eventKey);
    }

    /** Bir sefer + yolcu listesinde bir öğrenci + o öğrencinin velisi (+ öğrenci hesabı). */
    protected Scenario scenario(RouteDirection direction, LocalDate businessDate) {
        Scenario s = new Scenario();
        s.admin = user(Role.ADMIN);
        s.route = route(direction);
        s.stop1 = stop(s.route, 1);
        s.stop2 = stop(s.route, 2);
        s.vehicle = vehicle();
        s.driver = driver();
        s.hostess = hostess();
        s.studentUser = user(Role.STUDENT);
        s.student = student("Öğrenci " + UUID.randomUUID().toString().substring(0, 6), s.studentUser);
        s.assignment = assign(s.student, s.stop1, businessDate.minusDays(30), null);
        s.parent = parentOf(s.student);
        s.parentUser = s.parent.getUser();
        ResponseEntity<TripResponse> created = restTemplate().exchange("/api/trips", HttpMethod.POST,
                new HttpEntity<>(new TripCreateRequest(s.route.getId(), s.vehicle.getId(), s.driver.getId(),
                        s.hostess.getId(), businessDate, direction, LocalTime.of(7, 0)), auth(s.admin)),
                TripResponse.class);
        if (created.getStatusCode() != HttpStatus.OK) {
            throw new IllegalStateException("Sefer oluşturulamadı: " + created.getStatusCode());
        }
        s.trip = created.getBody();
        return s;
    }

    protected static class Scenario {
        public User admin;
        public Route route;
        public Stop stop1;
        public Stop stop2;
        public Vehicle vehicle;
        public Driver driver;
        public Hostess hostess;
        public User studentUser;
        public Student student;
        public StudentRouteAssignment assignment;
        public Parent parent;
        public User parentUser;
        public TripResponse trip;
    }
}
