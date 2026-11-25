package com.okulservisi.backend.notification;
import com.okulservisi.backend.notification.entity.Notification;
import com.okulservisi.backend.notification.entity.NotificationChannel;
import com.okulservisi.backend.notification.repository.NotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;

import com.okulservisi.backend.auth.security.JwtService;
import com.okulservisi.backend.common.web.ApiError;
import com.okulservisi.backend.notification.dto.response.NotificationBroadcastResponse;
import com.okulservisi.backend.notification.dto.response.NotificationResponse;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.support.AbstractIntegrationTest;
import com.okulservisi.backend.support.TestUsers;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
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
 * docs/API.md "15. Bildirim" (bkz. docs/RBAC_MATRIX.md: TÜM roller 🟡 "kendine gelen", ADMIN ayrıca
 * broadcast). E-postanın GERÇEKTEN gönderilip gönderilmediği (Mailpit) burada doğrulanmaz — sadece
 * {@code notification} tablosundaki IN_APP/EMAIL satırlarının doğru oluştuğu (bkz. görev talimatı).
 */
class NotificationControllerTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private StudentRepository studentRepository;

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

    private Notification createNotification(User recipient, NotificationChannel channel, String title) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setChannel(channel);
        notification.setTitle(title);
        notification.setBody("gövde " + title);
        notification.setRead(false);
        return notificationRepository.save(notification);
    }

    private Student createStudent(String fullName, User linkedUser) {
        Student student = new Student();
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2015, 6, 1));
        student.setActive(true);
        student.setUser(linkedUser);
        return studentRepository.save(student);
    }

    private Parent createParent(User user, String fullName) {
        Parent parent = new Parent();
        parent.setUser(user);
        parent.setFullName(fullName);
        return parentRepository.save(parent);
    }

    private void linkParentToStudent(Parent parent, Student student, boolean active) {
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationType("MOTHER");
        link.setActive(active);
        parentStudentLinkRepository.save(link);
    }

    @Test
    void list_returnsOnlyPrincipalsOwnNotifications() {
        User userA = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        User userB = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        String ownTitle = "Kendi Bildirimim " + UUID.randomUUID();
        String otherTitle = "Başkasının Bildirimi " + UUID.randomUUID();
        createNotification(userA, NotificationChannel.IN_APP, ownTitle);
        createNotification(userB, NotificationChannel.IN_APP, otherTitle);

        ResponseEntity<String> response = restTemplate().exchange("/api/notifications", HttpMethod.GET,
                new HttpEntity<>(authHeaderFor(userA)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(ownTitle);
        assertThat(response.getBody()).doesNotContain(otherTitle);
    }

    @Test
    void markRead_ownNotification_succeeds_andIsIdempotent() {
        User user = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Notification notification = createNotification(user, NotificationChannel.IN_APP, "Okunacak Bildirim");

        ResponseEntity<NotificationResponse> first = restTemplate().exchange(
                "/api/notifications/" + notification.getId() + "/read", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(user)), NotificationResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().read()).isTrue();

        // İkinci çağrı - zaten okunmuş, yine de 200 (idempotent, hata değil).
        ResponseEntity<NotificationResponse> second = restTemplate().exchange(
                "/api/notifications/" + notification.getId() + "/read", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(user)), NotificationResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().read()).isTrue();
    }

    @Test
    void markRead_othersNotification_returns404() {
        User owner = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        User intruder = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Notification notification = createNotification(owner, NotificationChannel.IN_APP, "Başkasının Bildirimi");

        ResponseEntity<ApiError> response = restTemplate().exchange(
                "/api/notifications/" + notification.getId() + "/read", HttpMethod.POST,
                new HttpEntity<>(authHeaderFor(intruder)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("başka kullanıcının bildirimini okundu işaretlemeye çalışmak 404 dönmeli")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void broadcast_asNonAdmin_returns403() {
        User driver = TestUsers.createUser(userRepository, passwordEncoder, Role.DRIVER, true);
        Map<String, Object> body = Map.of("title", "t", "body", "b", "targetRole", "PARENT");

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/notifications/broadcast", HttpMethod.POST,
                new HttpEntity<>(body, authHeaderFor(driver)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void broadcast_noTargetSpecified_returns400() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        Map<String, Object> body = Map.of("title", "t", "body", "b");

        ResponseEntity<ApiError> response = restTemplate().exchange("/api/notifications/broadcast", HttpMethod.POST,
                new HttpEntity<>(body, authHeaderFor(admin)), ApiError.class);

        assertThat(response.getStatusCode())
                .as("targetRole/studentIds/parentIds hiçbiri verilmemiş - 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void broadcast_byTargetRole_onlyActiveUsersOfThatRole_receiveInAppAndEmailRows() {
        // NOT: targetRole broadcast'i KASITLI OLARAK global'dir (tüm aktif PARENT'ler) - bu test
        // sınıfındaki DİĞER test metotları da PARENT kullanıcı oluşturabildiği için (paylaşılan
        // Testcontainers DB, bkz. sınıf javadoc'u emsali) MUTLAK sayı yerine ÖNCESİ/SONRASI farkı
        // (delta) doğrulanır.
        long activeParentCountBefore = userRepository.countByRoleAndActiveTrue(Role.PARENT);

        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User activeParentA = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        User activeParentB = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        User inactiveParent = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, false);

        String title = "Duyuru " + UUID.randomUUID();
        Map<String, Object> body = Map.of("title", title, "body", "Genel duyuru metni", "targetRole", "PARENT");

        ResponseEntity<NotificationBroadcastResponse> response = restTemplate().exchange(
                "/api/notifications/broadcast", HttpMethod.POST,
                new HttpEntity<>(body, authHeaderFor(admin)), NotificationBroadcastResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        long activeParentCountAfter = userRepository.countByRoleAndActiveTrue(Role.PARENT);
        assertThat(activeParentCountAfter - activeParentCountBefore)
                .as("bu testte eklenen 2 AKTİF PARENT - pasif olan sayılmaz")
                .isEqualTo(2);
        assertThat(response.getBody().recipientCount())
                .as("broadcast hedef sayısı, o andaki TÜM aktif PARENT sayısına eşit olmalı")
                .isEqualTo((int) activeParentCountAfter);

        assertThat(notificationRepository.findByRecipientId(activeParentA.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent()).hasSize(2); // IN_APP + EMAIL
        assertThat(notificationRepository.findByRecipientId(activeParentB.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent()).hasSize(2);
        assertThat(notificationRepository.findByRecipientId(inactiveParent.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent())
                .as("pasif kullanıcıya bildirim oluşturulmamalı")
                .isEmpty();
    }

    @Test
    void broadcast_byStudentIds_targetsStudentSelfAndActiveLinkedParent_notRevokedLink() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User studentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.STUDENT, true);
        Student student = createStudent("Bildirim Kapsam Öğrenci", studentUser);

        User activeParentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent activeParent = createParent(activeParentUser, "Aktif Veli");
        linkParentToStudent(activeParent, student, true);

        User revokedParentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent revokedParent = createParent(revokedParentUser, "Pasif Bağlantılı Veli");
        linkParentToStudent(revokedParent, student, false);

        Map<String, Object> body = Map.of(
                "title", "Öğrenciye Özel Duyuru", "body", "metin", "studentIds", List.of(student.getId()));

        ResponseEntity<NotificationBroadcastResponse> response = restTemplate().exchange(
                "/api/notifications/broadcast", HttpMethod.POST,
                new HttpEntity<>(body, authHeaderFor(admin)), NotificationBroadcastResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().recipientCount())
                .as("öğrencinin kendisi + aktif bağlantılı veli = 2 (revoke edilmiş bağlantı HARİÇ)")
                .isEqualTo(2);
        assertThat(notificationRepository.findByRecipientId(studentUser.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent()).hasSize(2);
        assertThat(notificationRepository.findByRecipientId(activeParentUser.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent()).hasSize(2);
        assertThat(notificationRepository.findByRecipientId(revokedParentUser.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent())
                .as("bağlantısı REVOKE edilmiş veliye bildirim gitmemeli")
                .isEmpty();
    }

    @Test
    void broadcast_byParentIds_directTarget_inactiveParentExcluded() {
        User admin = TestUsers.createUser(userRepository, passwordEncoder, Role.ADMIN, true);
        User activeParentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, true);
        Parent activeParent = createParent(activeParentUser, "Doğrudan Hedef Veli");
        User inactiveParentUser = TestUsers.createUser(userRepository, passwordEncoder, Role.PARENT, false);
        Parent inactiveParent = createParent(inactiveParentUser, "Pasif Veli");

        Map<String, Object> body = Map.of("title", "Doğrudan Veli Duyurusu", "body", "metin",
                "parentIds", List.of(activeParent.getId(), inactiveParent.getId()));

        ResponseEntity<NotificationBroadcastResponse> response = restTemplate().exchange(
                "/api/notifications/broadcast", HttpMethod.POST,
                new HttpEntity<>(body, authHeaderFor(admin)), NotificationBroadcastResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().recipientCount())
                .as("pasif veli hariç tutulmalı, sadece aktif veli sayılmalı")
                .isEqualTo(1);
    }
}
