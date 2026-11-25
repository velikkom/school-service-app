package com.okulservisi.backend.trip.service;

import com.okulservisi.backend.route.entity.RouteDirection;
import com.okulservisi.backend.trip.entity.Trip;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** 2026-11-18 — bildirim metinlerinde tutarlı Türkçe sefer etiketi (tr-TR tarih, Europe/Istanbul yerel saat). */
public final class TripLabels {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private TripLabels() {
    }

    public static String direction(RouteDirection direction) {
        return direction == RouteDirection.MORNING ? "Gidiş" : "Dönüş";
    }

    public static String date(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public static String time(LocalTime time) {
        return time == null ? "belirtilmemiş" : TIME.format(time);
    }

    /** Örn. "18.11.2026 Gidiş seferi (Merkez Hattı)". */
    public static String describe(Trip trip) {
        return date(trip.getBusinessDate()) + " " + direction(trip.getDirection()) + " seferi ("
                + trip.getRoute().getName() + ")";
    }
}
