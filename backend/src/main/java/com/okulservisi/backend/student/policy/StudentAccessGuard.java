package com.okulservisi.backend.student.policy;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Nesne bazlı (ownership) yetkilendirme ÖRNEK PATERNİ (docs/RBAC_MATRIX.md "Nesne bazlı erişim
 * ilkesi"). {@code @PreAuthorize} sadece ROL bazlı kaba kuralı kontrol eder (örn. "ADMIN veya
 * PARENT olabilir"); asıl "bu PARENT, bu öğrenciye gerçekten bağlı mı" kontrolü service katmanında
 * bu tür bir "guard" bileşeniyle yapılır. Gelecekteki her domain (vehicle/route/trip/contract/
 * payment/...) kendi service'inde aynı şekilde: rol ADMIN ise serbest, değilse ilişki tablosundan
 * (parent_student_link, driver/hostess ataması, vb.) sahiplik doğrulanır. Bulunamama/kapsam dışı
 * ayrımı yapılmaz — ikisi de 404 döner (docs/API.md: "bu ID var ama yetkin yok" bilgisi sızdırılmaz).
 */
@Component
@RequiredArgsConstructor
public class StudentAccessGuard {

    private final StudentRepository studentRepository;
    private final StudentScopePolicy studentScopePolicy;

    /** @throws NotFoundException studentId bulunamıyorsa veya principal bu öğrenciye erişemiyorsa. */
    public void assertReadable(Long studentId, AuthenticatedUser principal) {
        if (!studentRepository.existsById(studentId)) {
            throw NotFoundException.of("Öğrenci", studentId);
        }
        if (!studentScopePolicy.canAccess(principal, studentId)) {
            throw NotFoundException.of("Öğrenci", studentId);
        }
    }
}
