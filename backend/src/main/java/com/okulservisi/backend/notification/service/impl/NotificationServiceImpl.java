package com.okulservisi.backend.notification.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.BadRequestException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.notification.dto.request.NotificationBroadcastRequest;
import com.okulservisi.backend.notification.entity.Notification;
import com.okulservisi.backend.notification.entity.NotificationChannel;
import com.okulservisi.backend.notification.repository.NotificationRepository;
import com.okulservisi.backend.notification.service.NotificationMailService;
import com.okulservisi.backend.notification.service.NotificationService;
import com.okulservisi.backend.parent.entity.Parent;
import com.okulservisi.backend.parent.entity.ParentStudentLink;
import com.okulservisi.backend.parent.repository.ParentRepository;
import com.okulservisi.backend.parent.repository.ParentStudentLinkRepository;
import com.okulservisi.backend.student.entity.Student;
import com.okulservisi.backend.student.repository.StudentRepository;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "15. Bildirim" (bkz. docs/RBAC_MATRIX.md: {@code /api/notifications/**} TÜM roller
 * 🟡 "kendine gelen"; ADMIN ayrıca broadcast yapabilir).
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentLinkRepository parentStudentLinkRepository;
    private final NotificationMailService notificationMailService;

    @Override
    public Page<Notification> listMine(AuthenticatedUser principal, Pageable pageable) {
        return notificationRepository.findByRecipientId(principal.getUserId(), pageable);
    }

    @Override
    @Transactional
    public Notification markRead(Long id, AuthenticatedUser principal) {
        Notification notification = notificationRepository.findByIdAndRecipientId(id, principal.getUserId())
                .orElseThrow(() -> NotFoundException.of("Bildirim", id));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }
        return notification;
    }

    @Override
    @Transactional
    public int broadcast(NotificationBroadcastRequest request) {
        assertAtLeastOneTarget(request);

        Map<Long, User> recipientsById = new LinkedHashMap<>();

        if (request.targetRole() != null) {
            for (User user : userRepository.findByRoleAndActiveTrue(request.targetRole())) {
                recipientsById.put(user.getId(), user);
            }
        }

        if (request.studentIds() != null) {
            for (Long studentId : request.studentIds()) {
                Student student = studentRepository.findById(studentId)
                        .orElseThrow(() -> new BadRequestException(
                                "Belirtilen studentId'ye sahip öğrenci bulunamadı: " + studentId));
                if (student.getUser() != null && student.getUser().isActive()) {
                    recipientsById.put(student.getUser().getId(), student.getUser());
                }
                for (ParentStudentLink link : parentStudentLinkRepository.findByStudentIdAndActiveTrue(studentId)) {
                    User parentUser = link.getParent().getUser();
                    if (parentUser.isActive()) {
                        recipientsById.put(parentUser.getId(), parentUser);
                    }
                }
            }
        }

        if (request.parentIds() != null) {
            for (Long parentId : request.parentIds()) {
                Parent parent = parentRepository.findById(parentId)
                        .orElseThrow(() -> new BadRequestException(
                                "Belirtilen parentId'ye sahip veli bulunamadı: " + parentId));
                if (parent.getUser().isActive()) {
                    recipientsById.put(parent.getUser().getId(), parent.getUser());
                }
            }
        }

        for (User recipient : recipientsById.values()) {
            saveNotification(recipient, NotificationChannel.IN_APP, request.title(), request.body());
            saveNotification(recipient, NotificationChannel.EMAIL, request.title(), request.body());
            notificationMailService.sendBroadcastEmail(recipient.getEmail(), request.title(), request.body());
        }

        return recipientsById.size();
    }

    private void saveNotification(User recipient, NotificationChannel channel, String title, String body) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setChannel(channel);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRead(false);
        notificationRepository.save(notification);
    }

    private void assertAtLeastOneTarget(NotificationBroadcastRequest request) {
        boolean hasRole = request.targetRole() != null;
        boolean hasStudents = hasEntries(request.studentIds());
        boolean hasParents = hasEntries(request.parentIds());
        if (!hasRole && !hasStudents && !hasParents) {
            throw new BadRequestException(
                    "targetRole, studentIds veya parentIds alanlarından en az biri belirtilmelidir.");
        }
    }

    private boolean hasEntries(List<Long> ids) {
        return ids != null && !ids.isEmpty();
    }
}
