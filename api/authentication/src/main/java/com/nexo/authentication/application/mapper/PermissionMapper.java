package com.nexo.authentication.application.mapper;

import com.nexo.authentication.domain.dto.response.PermissionResponse;
import com.nexo.authentication.domain.entity.Permission;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface PermissionMapper {

    PermissionResponse toResponse(Permission permission);

    List<PermissionResponse> toResponseList(List<Permission> permissions);
}
