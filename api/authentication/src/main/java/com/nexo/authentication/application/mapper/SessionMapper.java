package com.nexo.authentication.application.mapper;

import com.nexo.authentication.domain.dto.response.SessionResponse;
import com.nexo.authentication.domain.entity.UserSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface SessionMapper {

    @Mapping(target = "current", source = "current")
    @Mapping(target = "id", source = "session.id")
    @Mapping(target = "createdAt", source = "session.createdAt")
    SessionResponse toResponse(UserSession session, boolean current);
}
