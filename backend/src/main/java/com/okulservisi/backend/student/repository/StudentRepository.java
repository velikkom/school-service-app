package com.okulservisi.backend.student.repository;
import com.okulservisi.backend.student.entity.Student;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByUserId(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByTcNo(String tcNo);

    /**
     * NOT (Faz G, additive): docs/DATABASE_DESIGN.md "Bakiye hesaplama" — ödeme eklenirken aynı
     * transaction + {@code SELECT ... FOR UPDATE} (pessimistic lock) ile öğrencinin satırı
     * kilitlenir, böylece aynı öğrenciye eşzamanlı 2 ödeme isteği (balance hesaplama + insert)
     * serialize edilir (bkz. {@code PaymentService.create}). Sadece bu kilitleme amacıyla
     * kullanılır; öğrencinin var olup olmadığı kontrolü de bu metodun {@code Optional} dönüşüyle
     * birlikte çağıran tarafta yapılır.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);
}
