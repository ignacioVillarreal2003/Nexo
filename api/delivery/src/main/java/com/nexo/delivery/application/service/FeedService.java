package com.nexo.delivery.application.service;

import com.nexo.delivery.application.mapper.DeliveryMapper;
import com.nexo.delivery.domain.dto.response.DayResponse;
import com.nexo.delivery.domain.dto.response.DeliveryRunResponse;
import com.nexo.delivery.domain.dto.response.DeliveryStatisticsResponse;
import com.nexo.delivery.domain.dto.response.FeedItemResponse;
import com.nexo.delivery.domain.dto.response.PageResponse;
import com.nexo.delivery.domain.entity.Delivery;
import com.nexo.delivery.domain.exception.ResourceNotFoundException;
import com.nexo.delivery.infrastructure.repository.DeliveryBatchRepository;
import com.nexo.delivery.infrastructure.repository.DeliveryRepository;
import com.nexo.delivery.infrastructure.repository.DeliveryRunRepository;
import com.nexo.delivery.infrastructure.repository.EmailRequestRepository;
import com.nexo.delivery.infrastructure.repository.specification.DeliverySpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** "Mi día", historial, favoritos y lecturas del usuario. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryBatchRepository batchRepository;
    private final DeliveryRunRepository runRepository;
    private final EmailRequestRepository emailRequestRepository;
    private final DeliveryMapper deliveryMapper;

    @Transactional(readOnly = true)
    public DayResponse day(UUID userId, LocalDate date, LocalDate today, UUID topicId) {
        List<DayResponse.TopicCount> topics = deliveryRepository.findTopicsOfDay(userId, date).stream()
                .map(row -> new DayResponse.TopicCount(row.getTopicId(), row.getTopicName(), row.getTotal()))
                .toList();
        List<FeedItemResponse> items = deliveryRepository.findDay(userId, date, topicId).stream()
                .map(deliveryMapper::toResponse)
                .toList();
        return new DayResponse(date, today, topics, items);
    }

    @Transactional(readOnly = true)
    public PageResponse<FeedItemResponse> history(UUID userId, UUID topicId, LocalDate from, LocalDate to,
                                                  Boolean read, String search, boolean favoritesOnly, int page,
                                                  int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, favoritesOnly ? "favoritedAt" : "deliveredAt");
        return PageResponse.from(deliveryRepository.findAll(
                        DeliverySpecifications.history(userId, topicId, from, to, read, search, favoritesOnly), PageRequest.of(page, size, sort)),
                deliveryMapper::toResponse);
    }

    @Transactional
    public void markRead(UUID userId, UUID deliveryId, boolean read) {
        find(userId, deliveryId).markRead(read);
    }

    @Transactional
    public void markFavorite(UUID userId, UUID deliveryId, boolean favorite) {
        find(userId, deliveryId).markFavorite(favorite);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryRunResponse> runs(int page, int size) {
        return PageResponse.from(runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(page, size)),
                deliveryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DeliveryStatisticsResponse statistics() {
        DeliveryRepository.StatisticsRow row = deliveryRepository.statistics();
        return new DeliveryStatisticsResponse(row.getLast24Hours(), row.getLast7Days(), row.getRead7Days(),
                row.getFavorites(), row.getUsers7Days());
    }

    @Transactional
    public void deleteAllFor(UUID userId) {
        int removed = deliveryRepository.deleteByUserId(userId);
        batchRepository.deleteByUserId(userId);
        emailRequestRepository.deleteByUserId(userId);
        log.info("Removed {} deliveries of deleted user {}", removed, userId);
    }

    private Delivery find(UUID userId, UUID deliveryId) {
        // Un contenido de otro usuario responde igual que uno inexistente.
        return deliveryRepository.findByIdAndUserId(deliveryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId));
    }
}
