package com.example.pmdaily.user.mapper;

import java.util.List;
import java.util.Set;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.example.pmdaily.user.Permission;
import com.example.pmdaily.user.Role;
import com.example.pmdaily.user.User;
import com.example.pmdaily.user.dto.UserResponse;

/**
 * User → UserResponse; roles/permissions gom thành danh sách code
 * (không map lazy collection trực tiếp — docs/design/02 muc 4).
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", source = "roles", qualifiedByName = "roleCodes")
    @Mapping(target = "permissions", source = "roles", qualifiedByName = "permissionCodes")
    UserResponse toResponse(User user);

    @Named("roleCodes")
    default List<String> mapRoleCodes(Set<Role> roles) {
        return roles.stream()
                .map(Role::getCode)
                .distinct()
                .sorted()
                .toList();
    }

    @Named("permissionCodes")
    default List<String> mapPermissionCodes(Set<Role> roles) {
        return roles.stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .sorted()
                .toList();
    }
}
