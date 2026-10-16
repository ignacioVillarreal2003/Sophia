package com.sophia.notification.application.mapper;

import com.sophia.notification.domain.dto.response.NotificationCategoryResponse;
import com.sophia.notification.domain.entity.NotificationCategory;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface NotificationCategoryMapper {

    NotificationCategoryResponse toResponse(NotificationCategory category);

    List<NotificationCategoryResponse> toResponseList(List<NotificationCategory> categories);
}
