package com.okulservisi.backend.user.repository;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    /**
     * {@code UserService#changeRole} — rol+domain profili değişikliğini AYNI transaction'da
     * serileştirmek için {@code SELECT ... FOR UPDATE} (Payment/Student modülleriyle AYNI desen,
     * bkz. {@code StudentRepository#findByIdForUpdate}). Aynı kullanıcıya eşzamanlı rol değiştirme
     * istekleri bu kilitle sıraya girer — ikinci istek birincinin commit'inden SONRA güncel durumu
     * (profil zaten var mı) görerek devam eder, mükerrer profil satırı oluşmaz.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(Role role);

    long countByRole(Role role);

    /**
     * EKLENDİ (Faz H — Notification): {@code POST /api/notifications/broadcast} hedefi
     * {@code targetRole} olduğunda "o rolün TÜM AKTİF kullanıcıları" (docs/API.md "15. Bildirim")
     * burada bulunur — pasif (isActive=false) kullanıcılara bildirim/e-posta gönderilmez.
     */
    List<User> findByRoleAndActiveTrue(Role role);
}
