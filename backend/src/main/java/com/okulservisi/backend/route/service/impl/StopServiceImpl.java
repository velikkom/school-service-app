package com.okulservisi.backend.route.service.impl;

import com.okulservisi.backend.auth.security.AuthenticatedUser;
import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.route.dto.request.StopCreateRequest;
import com.okulservisi.backend.route.dto.request.StopUpdateRequest;
import com.okulservisi.backend.route.entity.Route;
import com.okulservisi.backend.route.entity.Stop;
import com.okulservisi.backend.route.repository.RouteRepository;
import com.okulservisi.backend.route.repository.StopRepository;
import com.okulservisi.backend.route.repository.StudentRouteAssignmentRepository;
import com.okulservisi.backend.route.service.RouteService;
import com.okulservisi.backend.route.service.StopService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * docs/API.md "7. Güzergâh/Durak" — Stop create/update/delete ADMIN-only, {@code sequenceNo} route
 * içinde benzersiz/sıralı (docs/DATABASE_DESIGN.md UNIQUE(route_id, sequence_no)). {@code list}/
 * {@code getByIdAndRoute} (principal'lı overload) DRIVER/HOSTESS'e de açık — erişim kontrolü
 * {@link RouteService#getByIdForPrincipal} ÜZERİNDEN devredilir.
 */
@Service
@RequiredArgsConstructor
public class StopServiceImpl implements StopService {

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final RouteService routeService;
    private final StudentRouteAssignmentRepository studentRouteAssignmentRepository;

    @Override
    public List<Stop> listByRoute(Long routeId) {
        assertRouteExists(routeId);
        return stopRepository.findByRouteIdOrderBySequenceNoAsc(routeId);
    }

    @Override
    public List<Stop> listByRoute(Long routeId, AuthenticatedUser principal) {
        routeService.getByIdForPrincipal(routeId, principal);
        return stopRepository.findByRouteIdOrderBySequenceNoAsc(routeId);
    }

    @Override
    public Stop getByIdAndRoute(Long routeId, Long stopId) {
        assertRouteExists(routeId);
        return stopRepository.findByIdAndRouteId(stopId, routeId)
                .orElseThrow(() -> NotFoundException.of("Durak", stopId));
    }

    @Override
    public Stop getByIdAndRoute(Long routeId, Long stopId, AuthenticatedUser principal) {
        routeService.getByIdForPrincipal(routeId, principal);
        return stopRepository.findByIdAndRouteId(stopId, routeId)
                .orElseThrow(() -> NotFoundException.of("Durak", stopId));
    }

    @Override
    @Transactional
    public Stop create(Long routeId, StopCreateRequest request) {
        Route route = routeRepository.findById(routeId).orElseThrow(() -> NotFoundException.of("Güzergâh", routeId));
        if (stopRepository.existsByRouteIdAndSequenceNo(routeId, request.sequenceNo())) {
            throw new ConflictException("Bu güzergâhta bu sıra numarası (sequenceNo) zaten kullanılıyor.");
        }
        Stop stop = new Stop();
        stop.setRoute(route);
        stop.setName(request.name());
        stop.setSequenceNo(request.sequenceNo());
        stop.setPlannedTime(request.plannedTime());
        return stopRepository.save(stop);
    }

    @Override
    @Transactional
    public Stop update(Long routeId, Long stopId, StopUpdateRequest request) {
        Stop stop = getByIdAndRoute(routeId, stopId);
        if (request.sequenceNo() != null && !request.sequenceNo().equals(stop.getSequenceNo())
                && stopRepository.existsByRouteIdAndSequenceNo(routeId, request.sequenceNo())) {
            throw new ConflictException("Bu güzergâhta bu sıra numarası (sequenceNo) zaten kullanılıyor.");
        }
        if (request.name() != null) {
            stop.setName(request.name());
        }
        if (request.sequenceNo() != null) {
            stop.setSequenceNo(request.sequenceNo());
        }
        if (request.plannedTime() != null) {
            stop.setPlannedTime(request.plannedTime());
        }
        return stopRepository.save(stop);
    }

    @Override
    @Transactional
    public void delete(Long routeId, Long stopId) {
        Stop stop = getByIdAndRoute(routeId, stopId);
        if (studentRouteAssignmentRepository.existsByStopId(stop.getId())) {
            throw new ConflictException("Bu durağa bağlı öğrenci atamaları olduğu için silinemez.");
        }
        stopRepository.delete(stop);
    }

    private void assertRouteExists(Long routeId) {
        if (!routeRepository.existsById(routeId)) {
            throw NotFoundException.of("Güzergâh", routeId);
        }
    }
}
