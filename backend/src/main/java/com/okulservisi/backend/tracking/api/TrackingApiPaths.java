package com.okulservisi.backend.tracking.api;

/** docs/API.md "16. Konum takibi" — {@code TrackingController} mapping sabitleri. */
public final class TrackingApiPaths {

    private TrackingApiPaths() {
    }

    public static final String BASE = "/api/tracking/trips/{tripId}";
    public static final String LOCATIONS = "/locations";
    public static final String LATEST = "/latest";
}
