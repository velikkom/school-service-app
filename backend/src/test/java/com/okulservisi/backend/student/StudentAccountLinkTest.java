package com.okulservisi.backend.student;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.okulservisi.backend.auth.dto.request.LoginRequest;
import com.okulservisi.backend.auth.dto.request.RefreshRequest;
import com.okulservisi.backend.auth.dto.response.LoginResponse;
import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.student.dto.request.StudentAccountLinkRequest;
import com.okulservisi.backend.student.dto.response.StudentResponse;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.dto.request.UserRoleChangeRequest;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Sprint 3 — STUDENT hesabı ↔ öğrenci kaydı bağı (docs/API.md §3 "Öğrenci hesabı bağı"): yetki, rol/aktiflik
 * doğrulaması, 1-1 kuralı, idempotent tekrar, 409 çakışmaları, eşzamanlılık, bağ kaldırmanın erişimi ve
 * oturum yenilemeyi kesmesi, rol değişikliği koruması. Gerçek PostgreSQL (Testcontainers) üzerinde.
 */
class StudentAccountLinkTest extends AbstractIntegrationTest {

    @Autowired private StudentRepository studentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private HttpHeaders auth(User user) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole()));
        return h;
    }

    private User user(Role role, boolean active) {
        return TestUsers.createUser(userRepository, passwordEncoder, role, active);
    }

    private Student student(boolean active) {
        Student s = new Student();
        s.setFullName("Bağ Öğrencisi " + UUID.randomUUID().toString().substring(0, 8));
        s.setBirthDate(LocalDate.of(2015, 3, 1));
        s.setActive(active);
        return studentRepository.save(s);
    }

    private ResponseEntity<String> link(User actor, Long studentId, Long userId) {
        return restTemplate().exchange("/api/students/" + studentId + "/account", HttpMethod.PUT,
                new HttpEntity<>(new StudentAccountLinkRequest(userId), auth(actor)), String.class);
    }

    private ResponseEntity<String> unlink(User actor, Long studentId) {
        return restTemplate().exchange("/api/students/" + studentId + "/account", HttpMethod.DELETE,
                new HttpEntity<>(auth(actor)), String.class);
    }

    private Long linkedUserId(Long studentId) {
        return studentRepository.findById(studentId).map(s -> s.getUser() == null ? null : s.getUser().getId()).orElse(null);
    }

    @Test
    void admin_links_student_account_and_student_sees_only_own_record() {
        User admin = user(Role.ADMIN, true);
        User account = user(Role.STUDENT, true);
        Student s = student(true);
        Student other = student(true);

        ResponseEntity<StudentResponse> res = restTemplate().exchange("/api/students/" + s.getId() + "/account", HttpMethod.PUT,
                new HttpEntity<>(new StudentAccountLinkRequest(account.getId()), auth(admin)), StudentResponse.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().userId()).isEqualTo(account.getId());

        ResponseEntity<StudentResponse> own = restTemplate().exchange("/api/students/" + s.getId(), HttpMethod.GET,
                new HttpEntity<>(auth(account)), StudentResponse.class);
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> foreign = restTemplate().exchange("/api/students/" + other.getId(), HttpMethod.GET,
                new HttpEntity<>(auth(account)), String.class);
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void repeating_the_same_pair_is_idempotent() {
        User admin = user(Role.ADMIN, true);
        User account = user(Role.STUDENT, true);
        Student s = student(true);
        assertThat(link(admin, s.getId(), account.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(link(admin, s.getId(), account.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(linkedUserId(s.getId())).isEqualTo(account.getId());
    }

    @Test
    void non_admin_roles_get_403_and_nothing_changes() {
        User account = user(Role.STUDENT, true);
        Student s = student(true);
        for (Role role : List.of(Role.PARENT, Role.STUDENT, Role.DRIVER, Role.HOSTESS)) {
            User actor = user(role, true);
            assertThat(link(actor, s.getId(), account.getId()).getStatusCode()).as(role.name()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(unlink(actor, s.getId()).getStatusCode()).as(role.name()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(linkedUserId(s.getId())).isNull();
        ResponseEntity<String> anon = restTemplate().exchange("/api/students/" + s.getId() + "/account", HttpMethod.PUT,
                new HttpEntity<>(new StudentAccountLinkRequest(account.getId())), String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void conflicting_links_return_409_and_existing_link_is_not_replaced() {
        User admin = user(Role.ADMIN, true);
        User first = user(Role.STUDENT, true);
        User second = user(Role.STUDENT, true);
        Student s = student(true);
        Student s2 = student(true);
        assertThat(link(admin, s.getId(), first.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> otherAccount = restTemplate().exchange("/api/students/" + s.getId() + "/account", HttpMethod.PUT,
                new HttpEntity<>(new StudentAccountLinkRequest(second.getId()), auth(admin)), JsonNode.class);
        assertThat(otherAccount.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(otherAccount.getBody().get("error").asText()).isEqualTo("CONFLICT");
        assertThat(otherAccount.getBody().get("path").asText()).isEqualTo("/api/students/" + s.getId() + "/account");

        assertThat(link(admin, s2.getId(), first.getId()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(linkedUserId(s.getId())).isEqualTo(first.getId());
        assertThat(linkedUserId(s2.getId())).isNull();
    }

    @Test
    void wrong_role_inactive_account_inactive_student_or_missing_ids_are_rejected() {
        User admin = user(Role.ADMIN, true);
        Student s = student(true);
        assertThat(link(admin, s.getId(), user(Role.PARENT, true).getId()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(link(admin, s.getId(), user(Role.STUDENT, false).getId()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(link(admin, student(false).getId(), user(Role.STUDENT, true).getId()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(link(admin, s.getId(), 987_654_321L).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(link(admin, 987_654_321L, user(Role.STUDENT, true).getId()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> missingBody = restTemplate().exchange("/api/students/" + s.getId() + "/account", HttpMethod.PUT,
                new HttpEntity<>(Map.of(), auth(admin)), String.class);
        assertThat(missingBody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(linkedUserId(s.getId())).isNull();
    }

    @Test
    void unlink_removes_access_immediately_revokes_refresh_and_is_idempotent() {
        User admin = user(Role.ADMIN, true);
        User account = user(Role.STUDENT, true);
        Student s = student(true);
        assertThat(link(admin, s.getId(), account.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        LoginResponse login = restTemplate().postForObject("/api/auth/login",
                new LoginRequest(account.getEmail(), TestUsers.DEFAULT_RAW_PASSWORD, false), LoginResponse.class);
        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.set("Authorization", "Bearer " + login.accessToken());
        assertThat(restTemplate().exchange("/api/students/" + s.getId(), HttpMethod.GET, new HttpEntity<>(sessionHeaders), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(unlink(admin, s.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(linkedUserId(s.getId())).isNull();

        // Aynı (süresi dolmamış) access token artık kaydı göremez; liste boş döner.
        assertThat(restTemplate().exchange("/api/students/" + s.getId(), HttpMethod.GET, new HttpEntity<>(sessionHeaders), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<JsonNode> list = restTemplate().exchange("/api/students", HttpMethod.GET, new HttpEntity<>(sessionHeaders), JsonNode.class);
        assertThat(list.getBody().get("totalElements").asLong()).isZero();
        // Eski oturum yenilenemez.
        assertThat(restTemplate().postForEntity("/api/auth/refresh", new RefreshRequest(login.refreshToken()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(unlink(admin, s.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Bağ kaldırılınca hesap başka bir öğrenciye bağlanabilir.
        assertThat(link(admin, student(true).getId(), account.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void role_change_of_linked_account_requires_unlink_first() {
        User admin = user(Role.ADMIN, true);
        User account = user(Role.STUDENT, true);
        Student s = student(true);
        assertThat(link(admin, s.getId(), account.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> blocked = restTemplate().exchange("/api/users/" + account.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.PARENT, null), auth(admin)), String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(userRepository.findById(account.getId()).orElseThrow().getRole()).isEqualTo(Role.STUDENT);
        // Aynı role "ensure" çağrısı değişiklik sayılmaz.
        assertThat(restTemplate().exchange("/api/users/" + account.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.STUDENT, null), auth(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(unlink(admin, s.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate().exchange("/api/users/" + account.getId() + "/role", HttpMethod.POST,
                new HttpEntity<>(new UserRoleChangeRequest(Role.PARENT, null), auth(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void deactivated_account_keeps_link_but_loses_access_until_reactivated() {
        User admin = user(Role.ADMIN, true);
        User account = user(Role.STUDENT, true);
        Student s = student(true);
        assertThat(link(admin, s.getId(), account.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate().exchange("/api/users/" + account.getId() + "/deactivate", HttpMethod.POST,
                new HttpEntity<>(auth(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate().exchange("/api/students/" + s.getId(), HttpMethod.GET, new HttpEntity<>(auth(account)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(linkedUserId(s.getId())).isEqualTo(account.getId());

        assertThat(restTemplate().exchange("/api/users/" + account.getId() + "/activate", HttpMethod.POST,
                new HttpEntity<>(auth(admin)), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate().exchange("/api/students/" + s.getId(), HttpMethod.GET, new HttpEntity<>(auth(account)), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void user_list_filters_unlinked_student_accounts() {
        User admin = user(Role.ADMIN, true);
        User linked = user(Role.STUDENT, true);
        User free = user(Role.STUDENT, true);
        assertThat(link(admin, student(true).getId(), linked.getId()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> res = restTemplate().exchange("/api/users?role=STUDENT&isActive=true&studentLinked=false&size=100&sort=id,desc",
                HttpMethod.GET, new HttpEntity<>(auth(admin)), JsonNode.class);
        List<Long> ids = new ArrayList<>();
        res.getBody().get("content").forEach(n -> ids.add(n.get("id").asLong()));
        assertThat(ids).contains(free.getId()).doesNotContain(linked.getId());
    }

    @Test
    void concurrent_conflicting_links_leave_exactly_one_link() throws Exception {
        User admin = user(Role.ADMIN, true);
        // Aynı öğrenciye iki farklı hesap
        Student s = student(true);
        List<User> accounts = List.of(user(Role.STUDENT, true), user(Role.STUDENT, true), user(Role.STUDENT, true), user(Role.STUDENT, true));
        List<HttpStatus> statuses = race(accounts.stream().map(a -> (Callable<HttpStatus>) () ->
                HttpStatus.valueOf(link(admin, s.getId(), a.getId()).getStatusCode().value())).toList());
        assertThat(statuses).filteredOn(st -> st == HttpStatus.OK).hasSize(1);
        assertThat(statuses).filteredOn(st -> st == HttpStatus.CONFLICT).hasSize(accounts.size() - 1);
        assertThat(linkedUserId(s.getId())).isIn(accounts.stream().map(User::getId).toList());

        // Aynı hesabı iki farklı öğrenciye
        User account = user(Role.STUDENT, true);
        List<Student> students = List.of(student(true), student(true), student(true), student(true));
        List<HttpStatus> statuses2 = race(students.stream().map(st -> (Callable<HttpStatus>) () ->
                HttpStatus.valueOf(link(admin, st.getId(), account.getId()).getStatusCode().value())).toList());
        assertThat(statuses2).filteredOn(st -> st == HttpStatus.OK).hasSize(1);
        assertThat(statuses2).filteredOn(st -> st == HttpStatus.CONFLICT).hasSize(students.size() - 1);
        assertThat(studentRepository.findAll().stream().filter(st -> st.getUser() != null && st.getUser().getId().equals(account.getId())))
                .hasSize(1);
    }

    private List<HttpStatus> race(List<Callable<HttpStatus>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HttpStatus>> futures = new ArrayList<>();
            for (Callable<HttpStatus> t : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return t.call();
                }));
            }
            start.countDown();
            List<HttpStatus> out = new ArrayList<>();
            for (Future<HttpStatus> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }
}
