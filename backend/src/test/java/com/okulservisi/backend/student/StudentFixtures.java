package com.okulservisi.backend.student;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;

import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Faz E test fixture yardımcıları: öğrenci/veli/veli-öğrenci-bağlantı testleri için gerçek DB'de
 * (Testcontainers) kayıt oluşturur. {@code TestUsers} ile aynı desen (benzersiz UUID'li veri,
 * sınıflar arası izolasyon). `/api/parents` ve `/api/parent-student-links` controller'ları henüz
 * backend'de olmadığı için (bkz. docs/TEST_PLAN.md T-31/T-32 BLOCKED notu) Parent/ParentStudentLink
 * fixture'ları burada DOĞRUDAN repository ile oluşturulur - bu, StudentController/StudentService
 * tarafının (PARENT kapsamı/nesne bazlı erişim) test edilmesi için yeterlidir ve üretim kodu
 * DEĞİŞTİRİLMEZ.
 */
final class StudentFixtures {

    private StudentFixtures() {
    }

    static Student createStudent(StudentRepository studentRepository, String fullName) {
        Student student = new Student();
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2015, 5, 1));
        student.setSchoolName("Test İlkokulu");
        student.setGrade("3-A");
        student.setActive(true);
        return studentRepository.save(student);
    }

    static Student createStudentWithUser(StudentRepository studentRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, String fullName) {
        User user = new User();
        user.setEmail("student-" + UUID.randomUUID() + "@ssp-test.local");
        user.setPasswordHash(passwordEncoder.encode("Str0ng-Test-Passw0rd!"));
        user.setFullName(fullName);
        user.setRole(Role.STUDENT);
        user.setActive(true);
        User savedUser = userRepository.save(user);

        Student student = new Student();
        student.setUser(savedUser);
        student.setFullName(fullName);
        student.setBirthDate(LocalDate.of(2014, 3, 10));
        student.setSchoolName("Test Ortaokulu");
        student.setGrade("6-B");
        student.setActive(true);
        return studentRepository.save(student);
    }

    static Parent createParentWithUser(ParentRepository parentRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, String fullName) {
        User user = new User();
        user.setEmail("parent-" + UUID.randomUUID() + "@ssp-test.local");
        user.setPasswordHash(passwordEncoder.encode("Str0ng-Test-Passw0rd!"));
        user.setFullName(fullName);
        user.setRole(Role.PARENT);
        user.setActive(true);
        User savedUser = userRepository.save(user);

        Parent parent = new Parent();
        parent.setUser(savedUser);
        parent.setFullName(fullName);
        return parentRepository.save(parent);
    }

    static ParentStudentLink linkActive(ParentStudentLinkRepository repository, Parent parent, Student student) {
        ParentStudentLink link = new ParentStudentLink();
        link.setParent(parent);
        link.setStudent(student);
        link.setRelationType("MOTHER");
        link.setActive(true);
        return repository.save(link);
    }
}
