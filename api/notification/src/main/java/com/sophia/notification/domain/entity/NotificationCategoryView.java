package com.sophia.notification.domain.entity;

public record NotificationCategoryView(
        String code,
        String name,
        String description,
        boolean mandatory,
        boolean system) {

    public static NotificationCategoryView from(NotificationCategory category) {
        return new NotificationCategoryView(
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.isMandatory(),
                category.isSystem());
    }
}
