package com.okulservisi.backend.notification.api;

/** docs/API.md "15. Bildirim" — {@code NotificationController} mapping sabitleri. */
public final class NotificationApiPaths {

    private NotificationApiPaths() {
    }

    public static final String BASE = "/api/notifications";
    public static final String READ = "/{id}/read";
    public static final String BROADCAST = "/broadcast";
}
