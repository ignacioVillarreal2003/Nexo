package com.nexo.notification.application.service;

import com.nexo.notification.application.mapper.NotificationCategoryMapper;
import com.nexo.notification.application.security.AuthenticatedUser;
import com.nexo.notification.domain.dto.request.CreateNotificationCategoryRequest;
import com.nexo.notification.domain.dto.request.UpdateNotificationCategoryRequest;
import com.nexo.notification.domain.entity.NotificationCategory;
import com.nexo.notification.domain.entity.NotificationCategoryView;
import com.nexo.notification.domain.exception.BadRequestException;
import com.nexo.notification.domain.exception.ConflictException;
import com.nexo.notification.domain.exception.ResourceNotFoundException;
import com.nexo.notification.infrastructure.repository.NotificationCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCategoryServiceTest {

    private static final AuthenticatedUser ACTOR =
            new AuthenticatedUser(UUID.randomUUID(), "admin@nexo.test", Set.of(), Set.of());

    @Mock
    private NotificationCategoryRepository categoryRepository;

    @Mock
    private NotificationCategoryMapper categoryMapper;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private NotificationCategoryService categoryService;

    @Test
    void findByCodeThrowsWhenMissing() {
        when(categoryRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findByCode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByCodeReturnsACacheableView() {
        when(categoryRepository.findById("SYSTEM")).thenReturn(Optional.of(
                NotificationCategory.create("SYSTEM", "System", "Platform notices", false, true, null)));

        assertThat(categoryService.findByCode("SYSTEM"))
                .isEqualTo(new NotificationCategoryView("SYSTEM", "System", "Platform notices", false, true));
    }

    @Test
    void theMandatoryFlagIsCarriedOnTheView() {
        when(categoryRepository.findById("AUTHENTICATION_SECURITY")).thenReturn(Optional.of(
                NotificationCategory.create("AUTHENTICATION_SECURITY", "Security", null, true, true, null)));

        assertThat(categoryService.findByCode("AUTHENTICATION_SECURITY").mandatory()).isTrue();
    }

    @Test
    void createRejectsADuplicateCode() {
        when(categoryRepository.existsById("CONTENT_UPDATES")).thenReturn(true);
        CreateNotificationCategoryRequest request = new CreateNotificationCategoryRequest(
                "CONTENT_UPDATES", "Content updates", null, false);

        assertThatThrownBy(() -> categoryService.create(request, ACTOR))
                .isInstanceOf(ConflictException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateRejectsMandatoryChangeOnASystemCategory() {
        NotificationCategory security = NotificationCategory.create(
                "AUTHENTICATION_SECURITY", "Security", null, true, true, null);
        when(categoryRepository.findById("AUTHENTICATION_SECURITY")).thenReturn(Optional.of(security));
        UpdateNotificationCategoryRequest request = new UpdateNotificationCategoryRequest(null, null, false);

        assertThatThrownBy(() -> categoryService.update("AUTHENTICATION_SECURITY", request, ACTOR))
                .isInstanceOf(BadRequestException.class);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateAllowsRenamingASystemCategory() {
        NotificationCategory system = NotificationCategory.create("SYSTEM", "System", null, false, true, null);
        when(categoryRepository.findById("SYSTEM")).thenReturn(Optional.of(system));
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateNotificationCategoryRequest request =
                new UpdateNotificationCategoryRequest("Platform", "New description", null);

        categoryService.update("SYSTEM", request, ACTOR);

        ArgumentCaptor<NotificationCategory> saved = ArgumentCaptor.forClass(NotificationCategory.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Platform");
        assertThat(saved.getValue().getDescription()).isEqualTo("New description");
        assertThat(saved.getValue().isMandatory()).isFalse();
    }

    @Test
    void updateRecordsThePreviousStateInTheAuditTrail() {
        NotificationCategory system = NotificationCategory.create("SYSTEM", "System", null, false, true, null);
        when(categoryRepository.findById("SYSTEM")).thenReturn(Optional.of(system));
        when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateNotificationCategoryRequest request =
                new UpdateNotificationCategoryRequest("Platform", "New description", null);

        categoryService.update("SYSTEM", request, ACTOR);

        verify(auditService).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteRejectsASystemCategory() {
        NotificationCategory security = NotificationCategory.create(
                "AUTHENTICATION_SECURITY", "Security", null, true, true, null);
        when(categoryRepository.findById("AUTHENTICATION_SECURITY")).thenReturn(Optional.of(security));

        assertThatThrownBy(() -> categoryService.delete("AUTHENTICATION_SECURITY", ACTOR))
                .isInstanceOf(BadRequestException.class);
        verify(categoryRepository, never()).delete(any(NotificationCategory.class));
    }

    @Test
    void deleteRemovesANonSystemCategory() {
        NotificationCategory documentWorkspace = NotificationCategory.create(
                "CONTENT_UPDATES", "Content updates", null, false, false, null);
        when(categoryRepository.findById("CONTENT_UPDATES")).thenReturn(Optional.of(documentWorkspace));

        categoryService.delete("CONTENT_UPDATES", ACTOR);

        verify(categoryRepository).delete(documentWorkspace);
    }
}
