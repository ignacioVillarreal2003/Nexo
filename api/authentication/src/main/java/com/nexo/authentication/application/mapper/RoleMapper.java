package com.nexo.authentication.application.mapper;

import com.nexo.authentication.domain.dto.response.RoleResponse;
import com.nexo.authentication.domain.entity.Permission;
import com.nexo.authentication.domain.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public interface RoleMapper {

    @Mapping(target = "permissions", expression = "java(permissionNames(role))")
    RoleResponse toResponse(Role role);

    @Mapping(target = "permissions", expression = "java(java.util.Set.of())")
    RoleResponse toSummaryResponse(Role role);

    default Set<String> permissionNames(Role role) {
        return role.getPermissions().stream().map(Permission::getName).collect(Collectors.toSet());
    }
}
