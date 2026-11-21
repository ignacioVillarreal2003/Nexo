package com.nexo.delivery.application.mapper;

import com.nexo.delivery.domain.dto.response.DeliveryRunResponse;
import com.nexo.delivery.domain.dto.response.FeedItemResponse;
import com.nexo.delivery.domain.entity.Delivery;
import com.nexo.delivery.domain.entity.DeliveryRun;
import org.mapstruct.Mapper;

@Mapper
public interface DeliveryMapper {

    FeedItemResponse toResponse(Delivery delivery);

    DeliveryRunResponse toResponse(DeliveryRun run);
}
