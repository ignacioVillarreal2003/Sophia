package com.sophia.authentication.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "category", length = 50)
    private String category;

    public static Permission create(String name, String description, String category) {
        Permission permission = new Permission();
        permission.name = name;
        permission.description = description;
        permission.category = category;
        return permission;
    }

    public void update(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }
}
