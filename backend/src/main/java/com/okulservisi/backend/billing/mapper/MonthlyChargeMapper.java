package com.okulservisi.backend.billing.mapper;

import com.okulservisi.backend.billing.dto.response.MonthlyChargeResponse;
import com.okulservisi.backend.billing.entity.MonthlyCharge;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MonthlyChargeMapper {

    @Mapping(target = "studentId", expression = "java(charge.getStudent().getId())")
    @Mapping(target = "contractId", expression = "java(charge.getContract().getId())")
    MonthlyChargeResponse toResponse(MonthlyCharge charge);
}
