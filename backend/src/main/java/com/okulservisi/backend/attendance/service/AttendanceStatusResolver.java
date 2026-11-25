package com.okulservisi.backend.attendance.service;

import com.okulservisi.backend.attendance.dto.response.RosterStatus;
import com.okulservisi.backend.attendance.entity.AttendanceEvent;
import com.okulservisi.backend.attendance.entity.AttendanceEventType;
import com.okulservisi.backend.attendance.repository.AttendanceEventRepository;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bir öğrencinin bir seferdeki GÜNCEL yoklama durumunun TEK kaynağı (roster, günlük servis, veli
 * bildirimi kuralı, düzeltme talebi). Sadece geçerli (voided olmayan, {@code @SQLRestriction}) ve
 * düzeltme-satırı olmayan kayıtlar sayılır. Öncelik: DROPPED_OFF > ABSENT > BOARDED > NONE.
 */
@Component
@RequiredArgsConstructor
public class AttendanceStatusResolver {

    private final AttendanceEventRepository attendanceEventRepository;

    public RosterStatus currentStatus(Long tripId, Long studentId) {
        Set<AttendanceEventType> types = EnumSet.noneOf(AttendanceEventType.class);
        for (AttendanceEvent event
                : attendanceEventRepository.findByTripIdAndStudentIdAndCorrectionFalse(tripId, studentId)) {
            types.add(event.getEventType());
        }
        return resolve(types);
    }

    public static RosterStatus resolve(Set<AttendanceEventType> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return RosterStatus.NONE;
        }
        if (eventTypes.contains(AttendanceEventType.DROPOFF)) {
            return RosterStatus.DROPPED_OFF;
        }
        if (eventTypes.contains(AttendanceEventType.ABSENCE)) {
            return RosterStatus.ABSENT;
        }
        if (eventTypes.contains(AttendanceEventType.BOARDING)) {
            return RosterStatus.BOARDED;
        }
        return RosterStatus.NONE;
    }

    /** Hedef durumu sağlayan olay türleri (düzeltme onayında; biniş/iniş/devamsızlık kurallarıyla tutarlı). */
    public static Set<AttendanceEventType> eventTypesFor(RosterStatus status) {
        return switch (status) {
            case NONE -> EnumSet.noneOf(AttendanceEventType.class);
            case BOARDED -> EnumSet.of(AttendanceEventType.BOARDING);
            case DROPPED_OFF -> EnumSet.of(AttendanceEventType.BOARDING, AttendanceEventType.DROPOFF);
            case ABSENT -> EnumSet.of(AttendanceEventType.ABSENCE);
        };
    }
}
