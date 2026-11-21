package com.nexo.notification.application.service;

import com.nexo.notification.application.security.AuthenticatedUser;
import com.nexo.notification.domain.dto.request.UpdateNotificationPreferencesRequest;
import com.nexo.notification.domain.dto.response.NotificationPreferencesResponse;
import com.nexo.notification.domain.entity.NotificationCategoryChannelPreference;
import com.nexo.notification.domain.entity.NotificationCategoryChannelPreferenceId;
import com.nexo.notification.domain.entity.NotificationCategoryView;
import com.nexo.notification.domain.entity.NotificationChannel;
import com.nexo.notification.domain.entity.NotificationChannelCodes;
import com.nexo.notification.domain.entity.NotificationChannelPreference;
import com.nexo.notification.domain.entity.NotificationChannelPreferenceId;
import com.nexo.notification.domain.exception.BadRequestException;
import com.nexo.notification.infrastructure.repository.NotificationCategoryChannelPreferenceRepository;
import com.nexo.notification.infrastructure.repository.NotificationChannelPreferenceRepository;
import com.nexo.notification.infrastructure.repository.NotificationChannelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

    private static final NotificationCategoryView SECURITY = new NotificationCategoryView(
            "AUTHENTICATION_SECURITY", "Security", null, true, true);
    private static final NotificationCategoryView MENTION = new NotificationCategoryView(
            "DELIVERY_DAILY_DIGEST", "Daily digest", null, false, true);

    @Mock
    private NotificationChannelRepository channelRepository;
    @Mock
    private NotificationChannelPreferenceRepository channelPreferenceRepository;
    @Mock
    private NotificationCategoryChannelPreferenceRepository categoryChannelPreferenceRepository;
    @Mock
    private NotificationCategoryService categoryService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private NotificationPreferenceService service;

    private final UUID userId = UUID.randomUUID();
    private final AuthenticatedUser actor =
            new AuthenticatedUser(UUID.randomUUID(), "admin@nexo.test", Set.of(), Set.of());

    @BeforeEach
    void setUp() {
        lenient().when(channelRepository.findAll()).thenReturn(List.of(
                NotificationChannel.of(NotificationChannelCodes.IN_APP, "In-app"),
                NotificationChannel.of(NotificationChannelCodes.EMAIL, "Email")));
        lenient().when(channelRepository.existsById(NotificationChannelCodes.IN_APP)).thenReturn(true);
        lenient().when(channelRepository.existsById(NotificationChannelCodes.EMAIL)).thenReturn(true);
        lenient().when(categoryService.findAll()).thenReturn(List.of(SECURITY, MENTION));
        lenient().when(categoryService.findByCode("AUTHENTICATION_SECURITY")).thenReturn(SECURITY);
        lenient().when(categoryService.findByCode("DELIVERY_DAILY_DIGEST")).thenReturn(MENTION);
    }

    @Test
    void defaultsAllowEverything() {
        when(channelPreferenceRepository.findAllByIdUserId(userId)).thenReturn(List.of());
        when(categoryChannelPreferenceRepository.findAllByIdUserId(userId)).thenReturn(List.of());

        NotificationPreferencesResponse ownResponse = service.getOwn(userId);
        NotificationPreferencesResponse unrestrictedResponse = service.getUnrestricted(userId, actor);

        for (NotificationPreferencesResponse response : List.of(ownResponse, unrestrictedResponse)) {
            assertThat(response.channels().get(NotificationChannelCodes.EMAIL)).isTrue();
            assertThat(response.channels().get(NotificationChannelCodes.IN_APP)).isTrue();
            assertThat(response.categories()).doesNotContainKey("AUTHENTICATION_SECURITY");
            assertThat(response.categories().get("DELIVERY_DAILY_DIGEST").get(NotificationChannelCodes.EMAIL)).isTrue();
        }
    }

    @Test
    void securityCategoryIsAlwaysAllowed() {
        assertThat(service.emailAllowed(userId, "AUTHENTICATION_SECURITY")).isTrue();
        assertThat(service.inAppAllowed(userId, "AUTHENTICATION_SECURITY")).isTrue();
        verify(channelPreferenceRepository, never()).findById(any());
    }

    @Test
    void masterSwitchDisablesCategoryDelivery() {
        when(channelPreferenceRepository.findById(new NotificationChannelPreferenceId(userId, "EMAIL")))
                .thenReturn(Optional.of(NotificationChannelPreference.create(
                        new NotificationChannelPreferenceId(userId, "EMAIL"), false)));

        assertThat(service.emailAllowed(userId, "DELIVERY_DAILY_DIGEST")).isFalse();
    }

    @Test
    void categoryOverrideDisablesSingleCategory() {
        NotificationCategoryChannelPreferenceId id =
                new NotificationCategoryChannelPreferenceId(userId, "DELIVERY_DAILY_DIGEST", "EMAIL");
        when(categoryChannelPreferenceRepository.findById(id)).thenReturn(Optional.of(
                NotificationCategoryChannelPreference.create(id, false)));

        assertThat(service.emailAllowed(userId, "DELIVERY_DAILY_DIGEST")).isFalse();
        assertThat(service.inAppAllowed(userId, "DELIVERY_DAILY_DIGEST")).isTrue();
    }

    @Test
    void updatingSecurityCategoryIsRejected() {
        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest(
                null, Map.of("AUTHENTICATION_SECURITY", Map.of("EMAIL", false)));

        assertThatThrownBy(() -> service.updateOwn(userId, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateRejectsAnUnknownChannel() {
        when(channelRepository.existsById("SMS")).thenReturn(false);
        UpdateNotificationPreferencesRequest request =
                new UpdateNotificationPreferencesRequest(Map.of("SMS", false), null);

        assertThatThrownBy(() -> service.updateOwn(userId, request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updatePersistsMasterAndCategoryChanges() {
        when(channelPreferenceRepository.findById(any())).thenReturn(Optional.empty());
        when(channelPreferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryChannelPreferenceRepository.findById(any())).thenReturn(Optional.empty());
        when(categoryChannelPreferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelPreferenceRepository.findAllByIdUserId(userId)).thenReturn(List.of(
                NotificationChannelPreference.create(
                        new NotificationChannelPreferenceId(userId, "EMAIL"), false)));
        when(categoryChannelPreferenceRepository.findAllByIdUserId(userId)).thenReturn(List.of(
                NotificationCategoryChannelPreference.create(
                        new NotificationCategoryChannelPreferenceId(userId, "DELIVERY_DAILY_DIGEST", "EMAIL"),
                        false)));

        NotificationPreferencesResponse response = service.updateUnrestricted(userId,
                new UpdateNotificationPreferencesRequest(
                        Map.of("EMAIL", false),
                        Map.of("DELIVERY_DAILY_DIGEST", Map.of("EMAIL", false))),
                actor);

        assertThat(response.channels().get("EMAIL")).isFalse();
        assertThat(response.channels().get("IN_APP")).isTrue();
        assertThat(response.categories().get("DELIVERY_DAILY_DIGEST").get("EMAIL")).isFalse();

        ArgumentCaptor<NotificationChannelPreference> channelCaptor =
                ArgumentCaptor.forClass(NotificationChannelPreference.class);
        verify(channelPreferenceRepository).save(channelCaptor.capture());
        assertThat(channelCaptor.getValue().getUpdatedBy()).isEqualTo(actor.id());

        ArgumentCaptor<NotificationCategoryChannelPreference> categoryCaptor =
                ArgumentCaptor.forClass(NotificationCategoryChannelPreference.class);
        verify(categoryChannelPreferenceRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getUpdatedBy()).isEqualTo(actor.id());
    }

    @Test
    void updateOwnRecordsTheCallerAsTheUpdater() {
        when(channelPreferenceRepository.findById(any())).thenReturn(Optional.empty());
        when(channelPreferenceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelPreferenceRepository.findAllByIdUserId(userId)).thenReturn(List.of());
        when(categoryChannelPreferenceRepository.findAllByIdUserId(userId)).thenReturn(List.of());

        service.updateOwn(userId, new UpdateNotificationPreferencesRequest(Map.of("EMAIL", false), null));

        ArgumentCaptor<NotificationChannelPreference> channelCaptor =
                ArgumentCaptor.forClass(NotificationChannelPreference.class);
        verify(channelPreferenceRepository).save(channelCaptor.capture());
        assertThat(channelCaptor.getValue().getUpdatedBy()).isEqualTo(userId);
    }
}
