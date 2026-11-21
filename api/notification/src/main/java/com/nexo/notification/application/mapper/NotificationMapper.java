package com.nexo.notification.application.mapper;

import com.nexo.notification.domain.dto.response.NotificationResponse;
import com.nexo.notification.domain.entity.Notification;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);
}
