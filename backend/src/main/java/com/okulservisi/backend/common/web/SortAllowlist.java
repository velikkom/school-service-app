package com.okulservisi.backend.common.web;

import com.okulservisi.backend.common.exception.BadRequestException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * docs/API.md pagination kuralı: {@code sort} parametresi sadece her endpoint'in izin verdiği alan
 * adlarıyla kabul edilir, aksi halde {@code 400}. Her liste endpoint'i kendi izinli alan kümesini
 * (allowlist) tanımlayıp bu sınıf üzerinden {@link Pageable} üretir — rastgele alan adıyla SQL
 * enjeksiyonu/hataya izin verilmez.
 */
public final class SortAllowlist {

    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_SIZE = 20;

    private SortAllowlist() {
    }

    public static Pageable buildPageable(int page, Integer size, String sort, Set<String> allowedSortFields, String defaultSort) {
        int effectiveSize = size == null ? DEFAULT_SIZE : size;
        if (page < 0) {
            throw new BadRequestException("page 0 veya daha büyük olmalıdır.");
        }
        if (effectiveSize < 1 || effectiveSize > MAX_SIZE) {
            throw new BadRequestException("size 1 ile " + MAX_SIZE + " arasında olmalıdır.");
        }
        Sort resolvedSort = resolveSort(sort, allowedSortFields, defaultSort);
        return PageRequest.of(page, effectiveSize, resolvedSort);
    }

    private static Sort resolveSort(String sort, Set<String> allowedSortFields, String defaultSort) {
        String spec = (sort == null || sort.isBlank()) ? defaultSort : sort;
        if (spec == null || spec.isBlank()) {
            return Sort.unsorted();
        }
        String[] parts = spec.split(",");
        String field = parts[0].trim();
        if (!allowedSortFields.contains(field)) {
            throw new BadRequestException("Geçersiz sort alanı: " + field);
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.DESC;
        }
        return Sort.by(direction, field);
    }
}
