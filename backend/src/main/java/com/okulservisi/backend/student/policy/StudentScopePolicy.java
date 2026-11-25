package com.okulservisi.backend.student.policy;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * docs/RBAC_MATRIX.md "Nesne bazlı erişim ilkesi" — öğrenci kapsamına dayalı erişim kuralının TEK
 * kaynağı. Bu kural önceden {@code StudentService.scopeSpecification},
 * {@code AssignmentService.scopeSpecification} ve {@link StudentAccessGuard#assertReadable} içinde
 * BAĞIMSIZ olarak üç kez yazılmıştı; artık ikisi de (liste kapsamı ve tekil kayıt kapsamı) buradan
 * türetilir.
 *
 * <p>Kural: ADMIN sınırsız erişime sahiptir; STUDENT sadece kendi öğrenci kaydına erişir (kullanıcı
 * hesabına bağlı {@code Student} yoksa erişimi yoktur); PARENT sadece {@code ParentStudentLink}
 * üzerinden KENDİSİNE bağlı öğrencilere erişir; DİĞER roller (DRIVER/HOSTESS) hiçbir öğrenciye
 * erişemez (controller seviyesindeki {@code @PreAuthorize} bu rolleri zaten 403 ile reddeder, ama
 * burada da savunma amaçlı boş küme/false döner — asla sınırsız filtreye düşülmez).
 *
 * <p><b>ÖNEMLİ — "aktif" bağlantı şartı kasıtlıdır:</b> PARENT için sadece {@code active=true} olan
 * {@code ParentStudentLink} kayıtları sayılır ({@link ParentStudentLinkRepository#findByParentIdAndActiveTrue}
 * / {@link ParentStudentLinkRepository#existsByParentIdAndStudentIdAndActiveTrue}) — iptal edilmiş
 * (revoke edilmiş, {@code active=false}) bir bağlantı erişim VERMEZ. Bu iki metot ({@link
 * #accessibleStudentIds} ve {@link #canAccess}) AYNI kuralı uygular; biri değiştirilirse diğeri de
 * güncellenmelidir.
 */
@Component
@RequiredArgsConstructor
public class StudentScopePolicy {

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;

    /**
     * Liste/Specification senaryosu için erişilebilir öğrenci id'lerini döner.
     *
     * @return {@code null} ise sınırsız erişim (filtre uygulanmaz, sadece ADMIN); aksi halde
     *     erişilebilir öğrenci id'lerinin kümesi (boş küme = erişim yok). Bkz. {@link #canAccess}
     *     (tekil kayıt için aynı kuralın verimli/targeted uygulaması).
     */
    public Set<Long> accessibleStudentIds(AuthenticatedUser principal) {
        return switch (principal.getRole()) {
            case ADMIN -> null;
            case STUDENT -> {
                Student self = studentRepository.findByUserId(principal.getUserId()).orElse(null);
                yield self == null ? Set.of() : Set.of(self.getId());
            }
            case PARENT -> {
                Parent parent = parentRepository.findByUserId(principal.getUserId()).orElse(null);
                if (parent == null) {
                    yield Set.of();
                }
                yield parentStudentLinkRepository.findByParentIdAndActiveTrue(parent.getId())
                        .stream().map(ParentStudentLink::getStudent).map(Student::getId).collect(Collectors.toSet());
            }
            default -> Set.of();
        };
    }

    /**
     * Tekil kayıt (object-level) senaryosu için principal'ın verilen öğrenciye erişip erişemediğini
     * döner. {@link #accessibleStudentIds} ile AYNI kuralı uygular, ancak tüm erişilebilir küme
     * yüklenmeden (performans için) VERİMLİ/targeted {@code exists} sorgularıyla çalışır.
     *
     * <p>Bu metot öğrencinin VAR OLUP OLMADIĞINI kontrol etmez — sadece kapsam/yetki kontrolüdür;
     * "kayıt var mı" kontrolü çağıran tarafın (bkz. {@link StudentAccessGuard#assertReadable})
     * sorumluluğundadır.
     */
    public boolean canAccess(AuthenticatedUser principal, Long studentId) {
        return switch (principal.getRole()) {
            case ADMIN -> true;
            case STUDENT -> studentRepository.existsByIdAndUserId(studentId, principal.getUserId());
            case PARENT -> {
                Parent parent = parentRepository.findByUserId(principal.getUserId()).orElse(null);
                yield parent != null
                        && parentStudentLinkRepository.existsByParentIdAndStudentIdAndActiveTrue(parent.getId(), studentId);
            }
            default -> false;
        };
    }
}
