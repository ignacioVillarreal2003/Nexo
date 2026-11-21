package com.nexo.notification.application.mapper;

import com.nexo.notification.domain.dto.response.NotificationDispatchResponse;
import com.nexo.notification.domain.entity.NotificationDispatch;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface NotificationDispatchMapper {

    NotificationDispatchResponse toResponse(NotificationDispatch dispatch);

    List<NotificationDispatchResponse> toResponseList(List<NotificationDispatch> dispatches);
}
