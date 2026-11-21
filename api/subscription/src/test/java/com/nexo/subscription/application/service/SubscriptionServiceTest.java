package com.nexo.subscription.application.service;

import com.nexo.subscription.application.security.AuthenticatedUser;
import com.nexo.subscription.domain.config.ApplicationProperties;
import com.nexo.subscription.domain.dto.request.UpdateDeliverySettingsRequest;
import com.nexo.subscription.domain.dto.response.DeliveryPlanResponse;
import com.nexo.subscription.domain.dto.response.SubscriptionResponse;
import com.nexo.subscription.domain.entity.Subscription;
import com.nexo.subscription.domain.exception.BadRequestException;
import com.nexo.subscription.domain.exception.ResourceNotFoundException;
import com.nexo.subscription.infrastructure.client.TopicCatalogClient;
import com.nexo.subscription.infrastructure.client.TopicCatalogClient.TopicSummary;
import com.nexo.subscription.infrastructure.repository.DeliverySettingsRepository;
import com.nexo.subscription.infrastructure.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    private static final UUID TOPIC = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AuthenticatedUser USER = new AuthenticatedUser(
            UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "ana@nexo.local", Set.of(), Set.of(), false);

    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final DeliverySettingsRepository settings = mock(DeliverySettingsRepository.class);
    private final TopicCatalogClient catalog = mock(TopicCatalogClient.class);
    private final AuditService audit = mock(AuditService.class);
    private final ApplicationProperties properties = mock(ApplicationProperties.class);
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        when(properties.defaults()).thenReturn(new ApplicationProperties.Defaults(5, 7, "America/Montevideo", 25));
        when(catalog.publishedTopics()).thenReturn(Map.of(TOPIC,
                new TopicSummary(TOPIC, "ciberseguridad", "Ciberseguridad", "security", "Tecnología")));
        when(subscriptions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new SubscriptionService(subscriptions, settings, catalog, audit, properties);
    }

    @Test
    void aNewSubscriptionUsesTheDefaultDailyLimit() {
        when(subscriptions.findByUserIdAndTopicId(USER.id(), TOPIC)).thenReturn(Optional.empty());

        SubscriptionResponse response = service.subscribe(USER, TOPIC, null);

        assertThat(response.topicName()).isEqualTo("Ciberseguridad");
        assertThat(response.dailyLimit()).isEqualTo(5);
    }

    @Test
    void followingAgainReactivatesTheCancelledSubscriptionInsteadOfDuplicatingIt() {
        Subscription cancelled = Subscription.create(USER.id(), TOPIC, 3);
        cancelled.cancel();
        when(subscriptions.findByUserIdAndTopicId(USER.id(), TOPIC)).thenReturn(Optional.of(cancelled));

        service.subscribe(USER, TOPIC, 8);

        assertThat(cancelled.isActive()).isTrue();
        assertThat(cancelled.getDailyContentLimit()).isEqualTo(8);
        verify(subscriptions).save(cancelled);
    }

    @Test
    void anUnpublishedTopicCannotBeFollowed() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.subscribe(USER, unknown, null)).isInstanceOf(ResourceNotFoundException.class);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void anUnknownTimeZoneIsRejected() {
        assertThatThrownBy(() -> service.updateSettings(USER.id(),
                new UpdateDeliverySettingsRequest("Mars/Olympus", 7, 20, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void theDeliveryPlanFallsBackToTheDefaultsAndListsActiveTopics() {
        when(settings.findById(USER.id())).thenReturn(Optional.empty());
        when(subscriptions.findByUserIdAndStatusOrderBySubscribedAtAsc(any(), any()))
                .thenReturn(List.of(Subscription.create(USER.id(), TOPIC, 4)));

        DeliveryPlanResponse plan = service.deliveryPlan(USER.id());

        assertThat(plan.timezone()).isEqualTo("America/Montevideo");
        assertThat(plan.deliveryHour()).isEqualTo(7);
        assertThat(plan.maximumDailyItems()).isEqualTo(25);
        assertThat(plan.topics()).singleElement().satisfies(t -> {
            assertThat(t.topicId()).isEqualTo(TOPIC);
            assertThat(t.dailyLimit()).isEqualTo(4);
        });
    }
}
