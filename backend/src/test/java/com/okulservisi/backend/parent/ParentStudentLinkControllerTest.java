package com.okulservisi.backend.parent;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.parent.dto.request.ParentStudentLinkCreateRequest;
import com.okulservisi.backend.parent.dto.response.ParentStudentLinkResponse;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.student.dto.response.StudentResponse;
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
 * docs/API.md "4. Veli" {@code /api/parent-student-links} — aynı çift tekrar edemez (unique),
 * revoke ile pasife alınır (erişim kesilir). {@code GET} liste ADMIN only ve studentId/parentId ile
 * filtrelenebilir. docs/TEST_PLAN.md eşleştirmesi: T-31, T-31b, T-32.
 */
class ParentStudentLinkControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ParentRepository parentRepository;

    @Autowired
    private StudentRepository studentRepository;

    private HttpHeaders authHeaderFor(User user) {
        String token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
    }

    private Parent createParentWithUser(String fullName) {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent parent = new Parent();
        parent.setUser(user);
        parent.setFullName(fullName);
        return parentRepository.save(parent);
    }

    private Student createStudent(String fullName) {
        Student student = new Student();
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2015, 6, 1));
        student.setActive(true);
        return studentRepository.save(student);
    }

    // ---- T-31 ----
    @Test
    void createLink_withSameParentStudentPairTwice_returns409() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Parent parent = createParentWithUser("Veli Link Bir " + UUID.randomUUID());
        Student student = createStudent("Bağlanacak Öğrenci");

        ParentStudentLinkCreateRequest request = new ParentStudentLinkCreateRequest(parent.getId(), student.getId(), "MOTHER");
        ResponseEntity<ParentStudentLinkResponse> firstResponse = restTemplate().exchange(
                "/api/parent-student-links", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ParentStudentLinkResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> duplicateResponse = restTemplate().exchange(
                "/api/parent-student-links", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ApiError.class);

        assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ---- T-32 ----
    @Test
    void revokeLink_thenParentNoLongerHasAccessToStudent() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Parent parent = createParentWithUser("Veli Link İki " + UUID.randomUUID());
        Student student = createStudent("Erişimi Kesilecek Öğrenci");

        ParentStudentLinkCreateRequest request = new ParentStudentLinkCreateRequest(parent.getId(), student.getId(), "FATHER");
        ResponseEntity<ParentStudentLinkResponse> createResponse = restTemplate().exchange(
                "/api/parent-student-links", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ParentStudentLinkResponse.class);
        Long linkId = createResponse.getBody().id();

        // Revoke'dan ÖNCE: veli öğrenciye erişebilir.
        ResponseEntity<StudentResponse> beforeRevoke = restTemplate().exchange(
                "/api/students/" + student.getId(), HttpMethod.GET, new HttpEntity<>(authHeaderFor(parent.getUser())), StudentResponse.class);
        assertThat(beforeRevoke.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ParentStudentLinkResponse> revokeResponse = restTemplate().exchange(
                "/api/parent-student-links/" + linkId + "/revoke", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(admin)), ParentStudentLinkResponse.class);
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Revoke'dan SONRA: aynı veli artık o öğrenciye erişemez (404 - kapsam dışı).
        ResponseEntity<ApiError> afterRevoke = restTemplate().exchange(
                "/api/students/" + student.getId(), HttpMethod.GET, new HttpEntity<>(authHeaderFor(parent.getUser())), ApiError.class);
        assertThat(afterRevoke.getStatusCode())
                .as("revoke sonrası veli artık öğrenciye erişememeli")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createLink_asNonAdmin_returns403() {
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        ParentStudentLinkCreateRequest request = new ParentStudentLinkCreateRequest(1L, 1L, "MOTHER");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parent-student-links", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- T-31b: GET /api/parent-student-links?studentId=&parentId= (ADMIN only) ----
    @Test
    void list_filteredByStudentIdAndParentId_returnsMatchingLinks() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Parent parentA = createParentWithUser("Veli Liste A " + UUID.randomUUID());
        Parent parentB = createParentWithUser("Veli Liste B " + UUID.randomUUID());
        Student studentA = createStudent("Liste Öğrenci A");
        Student studentB = createStudent("Liste Öğrenci B");

        createLink(admin, parentA, studentA, "MOTHER");
        createLink(admin, parentA, studentB, "MOTHER");
        createLink(admin, parentB, studentA, "FATHER");

        // Sadece parentId: parentA'nın 2 bağlantısı (studentA + studentB).
        ResponseEntity<ParentStudentLinkResponse[]> byParent = restTemplate().exchange(
                "/api/parent-student-links?parentId=" + parentA.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), ParentStudentLinkResponse[].class);
        assertThat(byParent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byParent.getBody()).hasSize(2);
        assertThat(byParent.getBody()).extracting(ParentStudentLinkResponse::studentId)
                .containsExactlyInAnyOrder(studentA.getId(), studentB.getId());

        // Sadece studentId: studentA'ya bağlı 2 veli (parentA + parentB).
        ResponseEntity<ParentStudentLinkResponse[]> byStudent = restTemplate().exchange(
                "/api/parent-student-links?studentId=" + studentA.getId(), HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(admin)), ParentStudentLinkResponse[].class);
        assertThat(byStudent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byStudent.getBody()).hasSize(2);
        assertThat(byStudent.getBody()).extracting(ParentStudentLinkResponse::parentId)
                .containsExactlyInAnyOrder(parentA.getId(), parentB.getId());

        // Her ikisi birlikte: tam olarak tek eşleşme (parentA + studentA).
        ResponseEntity<ParentStudentLinkResponse[]> byBoth = restTemplate().exchange(
                "/api/parent-student-links?studentId=" + studentA.getId() + "&parentId=" + parentA.getId(),
                HttpMethod.GET, new HttpEntity<>(authHeaderFor(admin)), ParentStudentLinkResponse[].class);
        assertThat(byBoth.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(byBoth.getBody()).hasSize(1);
        assertThat(byBoth.getBody()[0].parentId()).isEqualTo(parentA.getId());
        assertThat(byBoth.getBody()[0].studentId()).isEqualTo(studentA.getId());
    }

    @Test
    void list_asNonAdmin_returns403() {
        User parentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/parent-student-links?parentId=1", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(parentUser)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ParentStudentLinkResponse createLink(User admin, Parent parent, Student student, String relationType) {
        ParentStudentLinkCreateRequest request = new ParentStudentLinkCreateRequest(parent.getId(), student.getId(), relationType);
        ResponseEntity<ParentStudentLinkResponse> response = restTemplate().exchange(
                "/api/parent-student-links", HttpMethod.POST, new HttpEntity<>(request, authHeaderFor(admin)), ParentStudentLinkResponse.class);
        return response.getBody();
    }
}
