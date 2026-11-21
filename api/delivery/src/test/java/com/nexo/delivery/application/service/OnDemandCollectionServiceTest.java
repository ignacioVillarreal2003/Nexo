package com.nexo.delivery.application.service;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.infrastructure.client.CollectorServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OnDemandCollectionServiceTest {

    private static final UUID TOPIC = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final CollectorServiceClient collector = mock(CollectorServiceClient.class);
    private final ApplicationProperties properties = mock(ApplicationProperties.class);
    private MutableClock clock;
    private OnDemandCollectionService service;

    @BeforeEach
    void setUp() {
        when(properties.collectorService()).thenReturn(
                new ApplicationProperties.CollectorService("http://collector", Duration.ofSeconds(5), true, Duration.ofMinutes(30)));
        clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
        service = new OnDemandCollectionService(collector, properties, clock);
    }

    @Test
    void requestsATopicOnceWithinTheCooldown() {
        when(collector.requestCollection(anyCollection())).thenReturn(true);

        assertThat(service.requestFor(List.of(TOPIC))).isTrue();
        clock.advance(Duration.ofMinutes(10));
        assertThat(service.requestFor(List.of(TOPIC))).isTrue(); // still on its way, not requested again

        verify(collector, times(1)).requestCollection(List.of(TOPIC));
    }

    @Test
    void requestsAgainAfterTheCooldown() {
        when(collector.requestCollection(anyCollection())).thenReturn(true);

        service.requestFor(List.of(TOPIC));
        clock.advance(Duration.ofMinutes(31));
        service.requestFor(List.of(TOPIC));

        verify(collector, times(2)).requestCollection(List.of(TOPIC));
    }

    @Test
    void aRejectedRequestIsRetriedOnTheNextCall() {
        when(collector.requestCollection(anyCollection())).thenReturn(false, true);

        assertThat(service.requestFor(List.of(TOPIC))).isFalse();
        assertThat(service.requestFor(List.of(TOPIC))).isTrue();

        verify(collector, times(2)).requestCollection(List.of(TOPIC));
    }

    @Test
    void doesNothingWhenDisabledOrWithoutTopics() {
        assertThat(service.requestFor(List.of())).isFalse();
        when(properties.collectorService()).thenReturn(
                new ApplicationProperties.CollectorService("http://collector", Duration.ofSeconds(5), false, Duration.ofMinutes(30)));
        assertThat(service.requestFor(List.of(TOPIC))).isFalse();

        verify(collector, never()).requestCollection(anyCollection());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
