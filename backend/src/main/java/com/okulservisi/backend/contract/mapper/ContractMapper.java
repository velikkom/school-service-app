package com.okulservisi.backend.contract.mapper;

import com.okulservisi.backend.contract.dto.response.ContractResponse;
import com.okulservisi.backend.contract.entity.Contract;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ContractMapper {

    /**
     * {@code studentFullName} ayrı bir parametre: {@code contract.getStudent()} {@code
     * open-in-view: false} altında lazy proxy olabilir (bkz. {@code VehicleService#getActiveVehicleForDriver}
     * javadoc'undaki AYNI kısıtlama) — gerçek alanlarına (fullName) erişim
     * {@code LazyInitializationException} fırlatır. Çağıran taraf ({@code ContractController})
     * {@code StudentRepository} ile ayrıca yüklenmiş (proxy olmayan) ismi geçirir.
     */
    @Mapping(target = "studentId", expression = "java(contract.getStudent().getId())")
    @Mapping(target = "studentFullName", source = "studentFullName")
    ContractResponse toResponse(Contract contract, String studentFullName);
}
