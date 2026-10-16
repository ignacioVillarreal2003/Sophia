package com.sophia.authentication.application.mapper;

import com.sophia.authentication.domain.dto.response.UserResponse;
import com.sophia.authentication.domain.entity.Permission;
import com.sophia.authentication.domain.entity.Role;
import com.sophia.authentication.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "roles", expression = "java(roleNames(user))")
    @Mapping(target = "permissions", expression = "java(user.getEffectivePermissions())")
    @Mapping(target = "directPermissions", expression = "java(directPermissionNames(user))")
    @Mapping(target = "hasPassword", expression = "java(user.getPassword() != null)")
    @Mapping(target = "googleLinked", expression = "java(user.isGoogleLinked())")
    @Mapping(target = "googleEmail", expression = "java(user.getGoogleEmail())")
    @Mapping(target = "photoUrl", source = "preferences.photoUrl")
    @Mapping(target = "theme", source = "preferences.theme")
    @Mapping(target = "themeMode", source = "preferences.themeMode")
    @Mapping(target = "locale", source = "preferences.locale")
    @Mapping(target = "timezone", source = "preferences.timezone")
    @Mapping(target = "hiddenModules", source = "preferences.hiddenModules")
    UserResponse toResponse(User user);

    default Set<String> roleNames(User user) {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    }

    default Set<String> directPermissionNames(User user) {
        return user.getDirectPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
    }
}
