package com.sophia.authentication.support;

import com.sophia.authentication.domain.entity.BaseEntity;

import java.lang.reflect.Field;
import java.util.UUID;

public final class TestIds {

    private TestIds() {
    }

    public static <T extends BaseEntity> T withId(T entity, UUID id) {
        try {
            Field field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to assign test id", e);
        }
    }

    public static <T extends BaseEntity> T withRandomId(T entity) {
        return withId(entity, UUID.randomUUID());
    }
}
