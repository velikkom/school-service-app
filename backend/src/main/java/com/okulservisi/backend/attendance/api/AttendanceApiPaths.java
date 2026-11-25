package com.okulservisi.backend.attendance.api;

/**
 * docs/API.md "10. Biniş/İniş/Devamsızlık" — attendance domain'indeki iki controller'ın
 * ({@code AttendanceEventController}, {@code StudentAttendanceController}) mapping sabitleri.
 */
public final class AttendanceApiPaths {

    private AttendanceApiPaths() {
    }

    // --- AttendanceEventController: /api/trips/{tripId}/attendance-events ---
    public static final String TRIP_ATTENDANCE_EVENTS_BASE = "/api/trips/{tripId}/attendance-events";
    public static final String BOARDINGS = "/boardings";
    public static final String DROPOFFS = "/dropoffs";
    public static final String ABSENCES = "/absences";

    // --- StudentAttendanceController: /api/students/{studentId}/attendance ---
    public static final String STUDENT_ATTENDANCE_BASE = "/api/students/{studentId}/attendance";
}
