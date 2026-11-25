package com.okulservisi.backend.trip.policy;

import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.route.entity.StudentRouteAssignment;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 2026-11-18 — bir sefer olayının bildirim alıcılarını çözer. Kural (docs/API.md §21): "ilgili veli ve
 * öğrenci" = seferin güzergâh+yön+iş gününde AKTİF ataması olan öğrenciler (roster ile AYNI sorgu,
 * {@link StudentRouteAssignmentRepository#findActiveByRouteAndDirectionAndBusinessDate}), bu
 * öğrencilerin kullanıcı hesabı (varsa) ve AKTİF veli bağı olan velileri. Personel = seferin şoförü ve
 * hostesi. Çağıran tarafın transaction'ı içinde kullanılmalıdır (lazy ilişkiler okunur).
 */
@Component
@RequiredArgsConstructor
public class TripAudienceResolver {

    private final StudentRouteAssignmentRepository assignmentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final UserRepository userRepository;

    public List<Student> rosterStudents(Trip trip) {
        Map<Long, Student> students = new LinkedHashMap<>();
        for (StudentRouteAssignment assignment : assignmentRepository.findActiveByRouteAndDirectionAndBusinessDate(
                trip.getRoute().getId(), trip.getDirection(), trip.getBusinessDate())) {
            students.putIfAbsent(assignment.getStudent().getId(), assignment.getStudent());
        }
        return new ArrayList<>(students.values());
    }

    /** Roster öğrencileri + aktif bağlı velileri (kullanıcı hesapları). */
    public List<User> familyUsers(Trip trip) {
        List<User> users = new ArrayList<>();
        for (Student student : rosterStudents(trip)) {
            users.addAll(familyUsersOfStudent(student));
        }
        return users;
    }

    public List<User> familyUsersOfStudent(Student student) {
        List<User> users = new ArrayList<>();
        if (student.getUser() != null) {
            users.add(student.getUser());
        }
        for (ParentStudentLink link : parentStudentLinkRepository.findByStudentIdAndActiveTrue(student.getId())) {
            users.add(link.getParent().getUser());
        }
        return users;
    }

    public List<User> parentUsersOfStudent(Long studentId) {
        List<User> users = new ArrayList<>();
        for (ParentStudentLink link : parentStudentLinkRepository.findByStudentIdAndActiveTrue(studentId)) {
            users.add(link.getParent().getUser());
        }
        return users;
    }

    public List<User> staffUsers(Trip trip) {
        List<User> users = new ArrayList<>();
        users.add(trip.getDriver().getUser());
        if (trip.getHostess() != null) {
            users.add(trip.getHostess().getUser());
        }
        return users;
    }

    public List<User> adminUsers() {
        return userRepository.findByRoleAndActiveTrue(Role.ADMIN);
    }
}
