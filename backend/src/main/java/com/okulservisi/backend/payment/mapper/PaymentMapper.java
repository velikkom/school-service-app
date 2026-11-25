package com.okulservisi.backend.payment.mapper;

import com.okulservisi.backend.payment.dto.response.PaymentResponse;
import com.okulservisi.backend.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "studentId", expression = "java(payment.getStudent().getId())")
    PaymentResponse toResponse(Payment payment);
}
