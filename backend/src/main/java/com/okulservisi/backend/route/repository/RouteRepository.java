package com.okulservisi.backend.route.repository;
import com.okulservisi.backend.route.entity.Route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RouteRepository extends JpaRepository<Route, Long>, JpaSpecificationExecutor<Route> {
}
