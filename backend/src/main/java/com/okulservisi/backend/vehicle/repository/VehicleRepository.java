package com.okulservisi.backend.vehicle.repository;
import com.okulservisi.backend.vehicle.entity.Vehicle;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByPlate(String plate);

    boolean existsByPlate(String plate);
}
