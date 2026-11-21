package com.nexo.notification.application.service;

import com.nexo.notification.domain.entity.UserContact;
import com.nexo.notification.infrastructure.messaging.UserEventType;
import com.nexo.notification.infrastructure.messaging.UserLifecycleEvent;
import com.nexo.notification.infrastructure.observability.NotificationMetrics;
import com.nexo.notification.infrastructure.repository.NotificationCategoryChannelPreferenceRepository;
import com.nexo.notification.infrastructure.repository.NotificationChannelPreferenceRepository;
import com.nexo.notification.infrastructure.repository.NotificationRepository;
import com.nexo.notification.infrastructure.repository.UserContactRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContactProjectionServiceTest {

    @Mock
    private UserContactRepository userContactRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationChannelPreferenceRepository channelPreferenceRepository;
    @Mock
    private NotificationCategoryChannelPreferenceRepository categoryChannelPreferenceRepository;
    @Mock
    private NotificationMetrics metrics;

    @InjectMocks
    private UserContactProjectionService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void userCreatedUpsertsContact() {
        when(userContactRepository.findById(userId)).thenReturn(Optional.empty());

        service.apply(new UserLifecycleEvent(UserEventType.USER_CREATED, userId,
                "ada@nexo.local", "Ada", "Lovelace", null, Instant.now()));

        ArgumentCaptor<UserContact> captor = ArgumentCaptor.forClass(UserContact.class);
        verify(userContactRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ada@nexo.local");
        assertThat(captor.getValue().displayName()).isEqualTo("Ada Lovelace");
        verify(metrics).userEventApplied("USER_CREATED");
    }

    @Test
    void userUpdatedKeepsExistingEmailWhenEventOmitsIt() {
        when(userContactRepository.findById(userId)).thenReturn(Optional.of(contact(userId, "ada@nexo.local")));

        service.apply(new UserLifecycleEvent(UserEventType.USER_UPDATED, userId,
                null, "Ada", null, null, Instant.now()));

        ArgumentCaptor<UserContact> captor = ArgumentCaptor.forClass(UserContact.class);
        verify(userContactRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("ada@nexo.local");
        assertThat(captor.getValue().getFirstName()).isEqualTo("Ada");
    }

    @Test
    void emailChangedUpdatesAddress() {
        when(userContactRepository.findById(userId)).thenReturn(Optional.of(contact(userId, "old@nexo.local")));

        service.apply(new UserLifecycleEvent(UserEventType.USER_EMAIL_CHANGED, userId,
                "new@nexo.local", null, null, null, Instant.now()));

        ArgumentCaptor<UserContact> captor = ArgumentCaptor.forClass(UserContact.class);
        verify(userContactRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@nexo.local");
    }

    @Test
    void userDeletedRemovesAllUserData() {
        service.apply(new UserLifecycleEvent(UserEventType.USER_DELETED, userId,
                "ada@nexo.local", null, null, null, Instant.now()));

        verify(notificationRepository).deleteAllForRecipient(userId);
        verify(categoryChannelPreferenceRepository).deleteAllForUser(userId);
        verify(channelPreferenceRepository).deleteAllForUser(userId);
        verify(userContactRepository).deleteById(userId);
    }

    @Test
    void eventWithoutUserIdIsIgnored() {
        service.apply(new UserLifecycleEvent(UserEventType.USER_CREATED, null,
                "ada@nexo.local", null, null, null, Instant.now()));

        verify(userContactRepository, never()).save(any());
    }

    private UserContact contact(UUID userId, String email) {
        UserContact contact = UserContact.createFor(userId);
        contact.updateEmail(email);
        return contact;
    }
}
