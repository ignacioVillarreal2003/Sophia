package com.sophia.notification.domain.entity.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum NotificationSeverity {

    INFO,
    SUCCESS,
    WARNING,
    ERROR;

    public static final Set<String> ALL = Arrays.stream(values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
}
