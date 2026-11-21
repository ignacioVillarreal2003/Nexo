package com.nexo.subscription.application.service;

import com.nexo.subscription.application.security.AuthenticatedUser;
import com.nexo.subscription.domain.config.ApplicationProperties;
import com.nexo.subscription.domain.dto.response.DeliveryPlanResponse;
import com.nexo.subscription.domain.dto.response.DeliverySettingsResponse;
import com.nexo.subscription.domain.dto.response.SubscriptionResponse;
import com.nexo.subscription.domain.dto.response.SubscriptionStatisticsResponse;
import com.nexo.subscription.domain.dto.response.TopicSubscribersResponse;
import com.nexo.subscription.domain.dto.request.UpdateDeliverySettingsRequest;
import com.nexo.subscription.domain.entity.AuditLog;
import com.nexo.subscription.domain.entity.DeliverySettings;
import com.nexo.subscription.domain.entity.Subscription;
import com.nexo.subscription.domain.entity.enums.SubscriptionStatus;
import com.nexo.subscription.domain.exception.BadRequestException;
import com.nexo.subscription.domain.exception.ResourceNotFoundException;
import com.nexo.subscription.infrastructure.client.TopicCatalogClient;
import com.nexo.subscription.infrastructure.client.TopicCatalogClient.TopicSummary;
import com.nexo.subscription.infrastructure.repository.DeliverySettingsRepository;
import com.nexo.subscription.infrastructure.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    public static final String SUBSCRIBED = "TOPIC_SUBSCRIBED";
    public static final String UNSUBSCRIBED = "TOPIC_UNSUBSCRIBED";

    private final SubscriptionRepository subscriptionRepository;
    private final DeliverySettingsRepository deliverySettingsRepository;
    private final TopicCatalogClient topicCatalogClient;
    private final AuditService auditService;
    private final ApplicationProperties properties;

    // ---------------------------------------------------------------- usuario

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> mine(UUID userId) {
        List<Subscription> subscriptions = subscriptionRepository
                .findByUserIdAndStatusOrderBySubscribedAtAsc(userId, SubscriptionStatus.ACTIVE);
        Map<UUID, TopicSummary> topics = subscriptions.isEmpty() ? Map.of() : topicCatalogClient.publishedTopics();
        return subscriptions.stream().map(s -> toResponse(s, topics.get(s.getTopicId()))).toList();
    }

    @Transactional
    public SubscriptionResponse subscribe(AuthenticatedUser user, UUID topicId, Integer dailyLimit) {
        TopicSummary topic = topicCatalogClient.publishedTopics().get(topicId);
        if (topic == null) {
            throw new ResourceNotFoundException("Topic", topicId);
        }
        int limit = dailyLimit != null ? dailyLimit : properties.defaults().dailyLimit();
        Subscription subscription = subscriptionRepository.findByUserIdAndTopicId(user.id(), topicId)
                .orElseGet(() -> Subscription.create(user.id(), topicId, limit));
        subscription.activate();
        if (dailyLimit != null) {
            subscription.changeDailyContentLimit(dailyLimit);
        }
        Subscription saved = subscriptionRepository.save(subscription);
        auditService.record(user.id(), user.email(), SUBSCRIBED, "Topic", topicId.toString(),
                AuditLog.Result.SUCCESS, null);
        return toResponse(saved, topic);
    }

    @Transactional
    public SubscriptionResponse changeDailyLimit(UUID userId, UUID topicId, int dailyLimit) {
        Subscription subscription = subscriptionRepository.findByUserIdAndTopicId(userId, topicId)
                .filter(Subscription::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", topicId));
        subscription.changeDailyContentLimit(dailyLimit);
        return toResponse(subscription, topicCatalogClient.publishedTopics().get(topicId));
    }

    @Transactional
    public void unsubscribe(AuthenticatedUser user, UUID topicId) {
        subscriptionRepository.findByUserIdAndTopicId(user.id(), topicId).ifPresent(subscription -> {
            subscription.cancel();
            auditService.record(user.id(), user.email(), UNSUBSCRIBED, "Topic", topicId.toString(),
                    AuditLog.Result.SUCCESS, null);
        });
    }

    @Transactional(readOnly = true)
    public DeliverySettingsResponse settings(UUID userId) {
        return deliverySettingsRepository.findById(userId)
                .map(SubscriptionService::toResponse)
                .orElseGet(() -> new DeliverySettingsResponse(properties.defaults().timezone(),
                        properties.defaults().deliveryHour(), properties.defaults().maximumDailyItems(), false));
    }

    @Transactional
    public DeliverySettingsResponse updateSettings(UUID userId, UpdateDeliverySettingsRequest request) {
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone: " + request.timezone());
        }
        DeliverySettings settings = deliverySettingsRepository.findById(userId)
                .orElseGet(() -> DeliverySettings.create(userId, request.timezone(), request.deliveryHour(),
                        request.maximumDailyItems()));
        settings.update(request.timezone(), request.deliveryHour(), request.maximumDailyItems(), request.paused());
        return toResponse(deliverySettingsRepository.save(settings));
    }

    // ---------------------------------------------------------------- servicios

    @Transactional(readOnly = true)
    public DeliveryPlanResponse deliveryPlan(UUID userId) {
        DeliverySettingsResponse settings = settings(userId);
        List<DeliveryPlanResponse.PlannedTopic> topics = subscriptionRepository
                .findByUserIdAndStatusOrderBySubscribedAtAsc(userId, SubscriptionStatus.ACTIVE).stream()
                .map(s -> new DeliveryPlanResponse.PlannedTopic(s.getTopicId(), s.getDailyContentLimit()))
                .toList();
        return new DeliveryPlanResponse(userId, settings.timezone(), settings.deliveryHour(),
                settings.maximumDailyItems(), settings.paused(), topics);
    }

    @Transactional(readOnly = true)
    public List<UUID> usersDueAt(Instant at) {
        return deliverySettingsRepository.findUsersDueAt(at, properties.defaults().timezone(),
                properties.defaults().deliveryHour());
    }

    @Transactional(readOnly = true)
    public List<UUID> subscribedUsers() {
        return subscriptionRepository.findDistinctUserIdsByStatus(SubscriptionStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<TopicSubscribersResponse> subscribedTopics() {
        return subscriptionRepository.countByTopic(SubscriptionStatus.ACTIVE).stream()
                .map(row -> new TopicSubscribersResponse((UUID) row[0], null, ((Number) row[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionStatisticsResponse statistics() {
        Map<UUID, TopicSummary> topics = topicCatalogClient.publishedTopics();
        List<TopicSubscribersResponse> top = subscribedTopics().stream().limit(10)
                .map(t -> new TopicSubscribersResponse(t.topicId(),
                        topics.containsKey(t.topicId()) ? topics.get(t.topicId()).name() : null, t.subscribers()))
                .toList();
        return new SubscriptionStatisticsResponse(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE),
                subscriptionRepository.countDistinctUsersByStatus(SubscriptionStatus.ACTIVE), top);
    }

    @Transactional
    public void deleteAllFor(UUID userId) {
        int removed = subscriptionRepository.deleteByUserId(userId);
        deliverySettingsRepository.deleteById(userId);
        log.info("Removed {} subscriptions of deleted user {}", removed, userId);
    }

    // ---------------------------------------------------------------- mapeo

    private static SubscriptionResponse toResponse(Subscription s, TopicSummary topic) {
        return new SubscriptionResponse(s.getId(), s.getTopicId(),
                topic == null ? null : topic.name(),
                topic == null ? null : topic.slug(),
                topic == null ? null : topic.icon(),
                topic == null ? null : topic.categoryName(),
                s.getDailyContentLimit(), s.getSubscribedAt());
    }

    private static DeliverySettingsResponse toResponse(DeliverySettings d) {
        return new DeliverySettingsResponse(d.getTimezone(), d.getDeliveryHour(), d.getMaximumDailyItems(), d.isPaused());
    }
}
