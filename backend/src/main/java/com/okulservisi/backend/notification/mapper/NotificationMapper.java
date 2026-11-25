package com.okulservisi.backend.notification.mapper;

import com.okulservisi.backend.notification.dto.response.NotificationResponse;
import com.okulservisi.backend.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "recipientUserId", expression = "java(notification.getRecipient().getId())")
    NotificationResponse toResponse(Notification notification);
}
