package com.sophia.notification.application.mapper;

import com.sophia.notification.domain.dto.response.NotificationResponse;
import com.sophia.notification.domain.entity.Notification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);
}
