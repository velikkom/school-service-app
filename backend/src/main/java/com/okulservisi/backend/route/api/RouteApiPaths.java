package com.okulservisi.backend.route.api;

/**
 * docs/API.md "7. Güzergâh/Durak" ve "8. Öğrenci atamaları" — route domain'indeki üç controller'ın
 * ({@code RouteController}, {@code StopController}, {@code AssignmentController}) mapping sabitleri.
 */
public final class RouteApiPaths {

    private RouteApiPaths() {
    }

    // --- RouteController: /api/routes ---
    public static final String ROUTE_BASE = "/api/routes";
    public static final String ROUTE_BY_ID = "/{id}";

    // --- StopController: /api/routes/{routeId}/stops ---
    public static final String STOP_BASE = "/api/routes/{routeId}/stops";
    public static final String STOP_BY_ID = "/{stopId}";

    // --- AssignmentController: /api/assignments ---
    public static final String ASSIGNMENT_BASE = "/api/assignments";
    public static final String ASSIGNMENT_BY_ID = "/{id}";
}
