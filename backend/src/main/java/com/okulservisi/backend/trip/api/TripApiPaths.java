package com.okulservisi.backend.trip.api;

/** docs/API.md "9. Sefer" — {@code TripController} mapping sabitleri. */
public final class TripApiPaths {

    private TripApiPaths() {
    }

    public static final String BASE = "/api/trips";
    public static final String MINE = "/mine";
    public static final String BY_ID = "/{id}";
    public static final String START = "/{id}/start";
    public static final String COMPLETE = "/{id}/complete";
    public static final String CANCEL = "/{id}/cancel";
    public static final String ROSTER = "/{id}/roster";
}
