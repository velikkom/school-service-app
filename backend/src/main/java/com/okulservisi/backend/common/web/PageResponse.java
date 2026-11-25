package com.okulservisi.backend.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** docs/API.md "Pagination" bölümündeki ortak liste zarfı. Tüm liste endpoint'leri bunu döner. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <T, R> PageResponse<R> of(Page<T> page, List<R> mappedContent) {
        return new PageResponse<>(
                mappedContent,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
