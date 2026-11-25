package com.okulservisi.backend.vehicle.service.impl;

import com.okulservisi.backend.common.exception.ConflictException;
import com.okulservisi.backend.common.exception.NotFoundException;
import com.okulservisi.backend.driver.entity.Driver;
import com.okulservisi.backend.driver.repository.DriverRepository;
import com.okulservisi.backend.trip.entity.Trip;
import com.okulservisi.backend.trip.entity.TripStatus;
import com.okulservisi.backend.trip.repository.TripRepository;
import com.okulservisi.backend.vehicle.dto.request.VehicleCreateRequest;
import com.okulservisi.backend.vehicle.dto.request.VehicleUpdateRequest;
import com.okulservisi.backend.vehicle.entity.Vehicle;
import com.okulservisi.backend.vehicle.mapper.VehicleMapper;
import com.okulservisi.backend.vehicle.repository.VehicleRepository;
import com.okulservisi.backend.vehicle.repository.VehicleSpecifications;
import com.okulservisi.backend.vehicle.service.VehicleService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** docs/API.md "5. Araç" — ADMIN CRUD, plaka normalize (büyük harf, boşluksuz) + unique. */
@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;
    private final DriverRepository driverRepository;
    private final TripRepository tripRepository;

    @Override
    public Page<Vehicle> list(String q, Boolean isActive, Pageable pageable) {
        Specification<Vehicle> spec = Specification.allOf(
                VehicleSpecifications.matchesQuery(q),
                VehicleSpecifications.isActive(isActive));
        return vehicleRepository.findAll(spec, pageable);
    }

    @Override
    public Vehicle getById(Long id) {
        return vehicleRepository.findById(id).orElseThrow(() -> NotFoundException.of("Araç", id));
    }

    @Override
    public Vehicle getActiveVehicleForDriver(Long userId) {
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> NotFoundException.of("Şoför profili", userId));
        Trip trip = tripRepository
                .findFirstByDriverIdAndStatusNotOrderByBusinessDateDescIdDesc(driver.getId(), TripStatus.CANCELLED)
                .orElseThrow(() -> new NotFoundException(
                        "Bu şoföre şu anda atanmış (iptal edilmemiş) bir sefer/araç bulunamadı."));
        // NOT: trip.getVehicle() lazy proxy'dir (open-in-view: false); mapper bu nesnenin GERÇEK
        // alanlarına (plate/capacity/...) erişiyor, sadece getId()'ye değil — bu yüzden proxy'yi
        // olduğu gibi döndürmek yerine tam yüklü bir Vehicle'ı repository'den tekrar çekiyoruz
        // (aksi halde controller mapping sırasında LazyInitializationException -> 500 olurdu).
        Long vehicleId = trip.getVehicle().getId();
        return vehicleRepository.findById(vehicleId).orElseThrow(() -> NotFoundException.of("Araç", vehicleId));
    }

    @Override
    @Transactional
    public Vehicle create(VehicleCreateRequest request) {
        String normalizedPlate = normalizePlate(request.plate());
        if (vehicleRepository.existsByPlate(normalizedPlate)) {
            throw new ConflictException("Bu plaka ile zaten bir araç kayıtlı.");
        }
        Vehicle vehicle = new Vehicle();
        vehicle.setPlate(normalizedPlate);
        vehicle.setCapacity(request.capacity());
        vehicle.setModel(request.model());
        vehicle.setActive(true);
        return vehicleRepository.save(vehicle);
    }

    @Override
    @Transactional
    public Vehicle update(Long id, VehicleUpdateRequest request) {
        Vehicle vehicle = getById(id);
        if (request.plate() != null) {
            String normalizedPlate = normalizePlate(request.plate());
            if (!normalizedPlate.equals(vehicle.getPlate()) && vehicleRepository.existsByPlate(normalizedPlate)) {
                throw new ConflictException("Bu plaka ile zaten bir araç kayıtlı.");
            }
            vehicle.setPlate(normalizedPlate);
        }
        vehicleMapper.updateFromRequest(vehicle, request);
        return vehicleRepository.save(vehicle);
    }

    private String normalizePlate(String plate) {
        return plate.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
