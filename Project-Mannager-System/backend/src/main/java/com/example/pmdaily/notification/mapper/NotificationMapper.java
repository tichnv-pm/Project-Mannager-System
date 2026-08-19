package com.example.pmdaily.notification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.pmdaily.notification.Notification;
import com.example.pmdaily.notification.dto.NotificationResponse;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "id", source = "notification.id")
    @Mapping(target = "isRead", source = "notification.read")
    NotificationResponse toResponse(Notification notification);
}
