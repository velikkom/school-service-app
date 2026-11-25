package com.okulservisi.backend.student;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.school.entity.School;
import com.okulservisi.backend.school.repository.SchoolRepository;
import com.okulservisi.backend.student.dto.request.StudentCreateRequest;
import com.okulservisi.backend.student.dto.response.StudentResponse;
import com.okulservisi.backend.student.dto.request.StudentUpdateRequest;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
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
 * docs/API.md "3. Öğrenci" + docs/RBAC_MATRIX.md (ADMIN tam erişim; PARENT/STUDENT nesne bazlı,
 * salt-okunur kendi kapsamı). docs/TEST_PLAN.md eşleştirmesi: T-24..T-30.
 *
 * <p>Backend Agent'ın Faz E'de eklediği {@code StudentController}/{@code StudentService}/
 * {@code StudentAccessGuard} üçlüsünü gerçek Testcontainers Postgres'e karşı doğrular.
 * {@code /api/parents} ve {@code /api/parent-student-links} controller'ları bu turda henüz
 * backend'de YOK (bkz. docs/TEST_PLAN.md T-31/T-32 BLOCKED notu); bu testlerde Parent/
 * ParentStudentLink fixture'ları doğrudan repository ile oluşturulur ({@link StudentFixtures}) -
 * StudentController/StudentService tarafının PARENT kapsamı/nesne bazlı erişim davranışını test
 * etmek için bu yeterlidir.
 */
class StudentControllerTest extends AbstractIntegrationTest {

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private ParentStudentLinkRepository parentStudentLinkRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    // ---- T-24 ----
    @Test
    void createStudent_asAdmin_returns2xxWithoutSystemFields() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        StudentCreateRequest request = new StudentCreateRequest(
                "Yeni Öğrenci " + UUID.randomUUID(), LocalDate.of(2016, 9, 1), null, null, "5-A");

        ResponseEntity<StudentResponse> response = restTemplate().exchange(
                "/api/students", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), StudentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StudentResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.fullName()).isEqualTo(request.fullName());
        assertThat(body.isActive()).isTrue();
    }

    @Test
    void createStudent_withDuplicateTcNo_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        String tcNo = "1" + String.format("%010d", System.nanoTime() % 10_000_000_000L);
        StudentCreateRequest first = new StudentCreateRequest("Öğrenci Bir", LocalDate.of(2015, 1, 1), tcNo, null, null);
        restTemplate().exchange("/api/students", HttpMethod.POST, new HttpEntity<>(first, authHeaderFor(admin)), StudentResponse.class);

        StudentCreateRequest duplicate = new StudentCreateRequest("Öğrenci İki", LocalDate.of(2015, 2, 2), tcNo, null, null);
        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/students", HttpMethod.POST, new HttpEntity<>(duplicate, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-25 ----
    @Test
    void adminCrud_updateAndDeactivate_succeeds() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = StudentFixtures.createStudent(studentRepository, "Güncellenecek Öğrenci");

        StudentUpdateRequest update = new StudentUpdateRequest("Güncellenmiş Ad", null, null, null, "6-C");
        ResponseEntity<StudentResponse> updateResponse = restTemplate().exchange(
                "/api/students/" + student.getId(), HttpMethod.PATCH,
                new HttpEntity<>(update, authHeaderFor(admin)), StudentResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().fullName()).isEqualTo("Güncellenmiş Ad");
        assertThat(updateResponse.getBody().grade()).isEqualTo("6-C");

        ResponseEntity<StudentResponse> deactivateResponse = restTemplate().exchange(
                "/api/students/" + student.getId() + "/deactivate", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), StudentResponse.class);
        assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deactivateResponse.getBody().isActive()).isFalse();
    }

    // 2026-11-04: ADMIN formunda serbest metin schoolName yerine schoolId dropdown'u (docs/API.md §3).
    @Test
    void createAndUpdateStudent_withSchoolId_resolvesSchoolAndSyncsSchoolName() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        School school = new School();
        school.setName("Dropdown Test Okulu " + UUID.randomUUID());
        school.setActive(true);
        school = schoolRepository.save(school);

        StudentCreateRequest createRequest = new StudentCreateRequest(
                "Okullu Öğrenci " + UUID.randomUUID(), LocalDate.of(2016, 9, 1), null, school.getId(), "5-A");
        ResponseEntity<StudentResponse> createResponse = restTemplate().exchange(
                "/api/students", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaderFor(admin)), StudentResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody().schoolId()).isEqualTo(school.getId());
        assertThat(createResponse.getBody().schoolName()).isEqualTo(school.getName());

        School otherSchool = new School();
        otherSchool.setName("Dropdown Test Okulu 2 " + UUID.randomUUID());
        otherSchool.setActive(true);
        otherSchool = schoolRepository.save(otherSchool);

        StudentUpdateRequest updateRequest = new StudentUpdateRequest(null, null, null, otherSchool.getId(), null);
        ResponseEntity<StudentResponse> updateResponse = restTemplate().exchange(
                "/api/students/" + createResponse.getBody().id(), HttpMethod.PATCH,
                new HttpEntity<>(updateRequest, authHeaderFor(admin)), StudentResponse.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().schoolId()).isEqualTo(otherSchool.getId());
        assertThat(updateResponse.getBody().schoolName()).isEqualTo(otherSchool.getName());
    }

    @Test
    void createStudent_withInvalidSchoolId_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        StudentCreateRequest request = new StudentCreateRequest(
                "Geçersiz Okul Denemesi " + UUID.randomUUID(), LocalDate.of(2016, 9, 1), null, 999_999_999L, null);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/students", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStudent_withoutSchoolId_preservesExistingSchool() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        School school = new School();
        school.setName("Korunan Okul " + UUID.randomUUID());
        school.setActive(true);
        school = schoolRepository.save(school);
        Student student = StudentFixtures.createStudent(studentRepository, "Okulu Korunacak Öğrenci");
        student.setSchool(school);
        student.setSchoolName(school.getName());
        student = studentRepository.save(student);

        StudentUpdateRequest updateRequest = new StudentUpdateRequest("Güncellenmiş Ad 2", null, null, null, null);
        ResponseEntity<StudentResponse> response = restTemplate().exchange(
                "/api/students/" + student.getId(), HttpMethod.PATCH,
                new HttpEntity<>(updateRequest, authHeaderFor(admin)), StudentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().schoolId()).isEqualTo(school.getId());
        assertThat(response.getBody().schoolName()).isEqualTo(school.getName());
    }

    @Test
    void getStudentById_asAdmin_returns200() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Student student = StudentFixtures.createStudent(studentRepository, "Detay Öğrenci");

        ResponseEntity<StudentResponse> response = restTemplate().exchange(
                "/api/students/" + student.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), StudentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(student.getId());
    }

    // ---- T-26 ----
    @Test
    void listStudents_asDriverOrHostess_returns403() {
        for (Role role : new Role[] {Role.DRIVER, Role.HOSTESS}) {
            User user = TestUsers.createUser(userRepository, passwordEncoder, role, true);
            ResponseEntity<ApiError> response = restTemplate().exchange(
                    "/api/students", HttpMethod.GET, new HttpEntity<>(authHeaderFor(user)), ApiError.class);
            assertThat(response.getStatusCode()).as("role=%s", role).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    // ---- T-27 ----
    @Test
    void listStudents_asParent_onlyReturnsOwnActiveLinkedChildren() {
        Parent parent1 = StudentFixtures.createParentWithUser(parentRepository, userRepository, passwordEncoder, "Veli Bir");
        Parent parent2 = StudentFixtures.createParentWithUser(parentRepository, userRepository, passwordEncoder, "Veli İki");
        Student ownChild = StudentFixtures.createStudent(studentRepository, "Kendi Çocuğu");
        Student otherChild = StudentFixtures.createStudent(studentRepository, "Başkasının Çocuğu");
        StudentFixtures.linkActive(parentStudentLinkRepository, parent1, ownChild);
        StudentFixtures.linkActive(parentStudentLinkRepository, parent2, otherChild);

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/students", HttpMethod.GET, new HttpEntity<>(authHeaderFor(parent1.getUser())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Kendi Çocuğu");
        assertThat(response.getBody()).doesNotContain("Başkasının Çocuğu");
    }

    // ---- T-28 ----
    @Test
    void getStudentById_asParent_forUnlinkedChild_returns404NotForbidden() {
        Parent parent = StudentFixtures.createParentWithUser(parentRepository, userRepository, passwordEncoder, "Veli Üç");
        Student otherChild = StudentFixtures.createStudent(studentRepository, "İlişkisiz Öğrenci");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/students/" + otherChild.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parent.getUser())), ApiError.class);

        assertThat(response.getStatusCode())
                .as("docs/API.md: kapsam dışı ID 404 döner, 403 değil")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getStudentById_asParent_forOwnChild_returns200() {
        Parent parent = StudentFixtures.createParentWithUser(parentRepository, userRepository, passwordEncoder, "Veli Dört");
        Student ownChild = StudentFixtures.createStudent(studentRepository, "Bağlı Öğrenci");
        StudentFixtures.linkActive(parentStudentLinkRepository, parent, ownChild);

        ResponseEntity<StudentResponse> response = restTemplate().exchange(
                "/api/students/" + ownChild.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parent.getUser())), StudentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ---- T-29 ----
    @Test
    void getStudentById_asStudent_forOtherStudent_returns404() {
        Student self = StudentFixtures.createStudentWithUser(studentRepository, userRepository, passwordEncoder, "Kendim");
        Student other = StudentFixtures.createStudent(studentRepository, "Diğer Öğrenci");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/students/" + other.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(self.getUser())), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getStudentById_asStudent_forSelf_returns200() {
        Student self = StudentFixtures.createStudentWithUser(studentRepository, userRepository, passwordEncoder, "Kendim İki");

        ResponseEntity<StudentResponse> response = restTemplate().exchange(
                "/api/students/" + self.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(self.getUser())), StudentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(self.getId());
    }

    @Test
    void listStudents_asStudent_onlyContainsSelf() {
        Student self = StudentFixtures.createStudentWithUser(studentRepository, userRepository, passwordEncoder, "Kendim Liste");
        StudentFixtures.createStudent(studentRepository, "Görünmemesi Gereken Öğrenci");

        ResponseEntity<String> response = restTemplate().exchange(
                "/api/students", HttpMethod.GET, new HttpEntity<>(authHeaderFor(self.getUser())), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Kendim Liste");
        assertThat(response.getBody()).doesNotContain("Görünmemesi Gereken Öğrenci");
    }

    // ---- T-30 ----
    @Test
    void writeEndpoints_asParentOrStudent_return403() {
        Parent parent = StudentFixtures.createParentWithUser(parentRepository, userRepository, passwordEncoder, "Veli Beş");
        Student studentWithUser = StudentFixtures.createStudentWithUser(studentRepository, userRepository, passwordEncoder, "Yazamayan Öğrenci");
        Student target = StudentFixtures.createStudent(studentRepository, "Hedef Öğrenci");
        StudentFixtures.linkActive(parentStudentLinkRepository, parent, target);

        StudentCreateRequest createRequest = new StudentCreateRequest("Yasak Deneme", LocalDate.of(2017, 1, 1), null, null, null);
        for (User actor : new User[] {parent.getUser(), studentWithUser.getUser()}) {
            ResponseEntity<ApiError> createResponse = restTemplate().exchange(
                    "/api/students", HttpMethod.POST, new HttpEntity<>(createRequest, authHeaderFor(actor)), ApiError.class);
            assertThat(createResponse.getStatusCode()).as("actor role=%s create", actor.getRole()).isEqualTo(HttpStatus.FORBIDDEN);

            StudentUpdateRequest updateRequest = new StudentUpdateRequest("Yasak", null, null, null, null);
            ResponseEntity<ApiError> updateResponse = restTemplate().exchange(
                    "/api/students/" + target.getId(), HttpMethod.PATCH,
                    new HttpEntity<>(updateRequest, authHeaderFor(actor)), ApiError.class);
            assertThat(updateResponse.getStatusCode()).as("actor role=%s update", actor.getRole()).isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<ApiError> deactivateResponse = restTemplate().exchange(
                    "/api/students/" + target.getId() + "/deactivate", HttpMethod.POST,
                    new HttpEntity<>(authHeaderFor(actor)), ApiError.class);
            assertThat(deactivateResponse.getStatusCode()).as("actor role=%s deactivate", actor.getRole()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
