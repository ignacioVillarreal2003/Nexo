package com.nexo.authentication.application.service;

import com.nexo.authentication.domain.entity.User;
import com.nexo.authentication.domain.entity.UserPreferences;
import com.nexo.authentication.domain.entity.enums.AuthenticationProvider;
import com.nexo.authentication.infrastructure.messaging.UserEventPublisher;
import com.nexo.authentication.infrastructure.messaging.UserEventType;
import com.nexo.authentication.infrastructure.repository.RoleRepository;
import com.nexo.authentication.infrastructure.repository.UserPreferencesRepository;
import com.nexo.authentication.infrastructure.repository.UserRepository;
import com.nexo.authentication.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProvisionerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserPreferencesRepository preferences = mock(UserPreferencesRepository.class);
    private final UserEventPublisher events = mock(UserEventPublisher.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserProvisioner provisioner;

    @BeforeEach
    void setUp() {
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(preferences.save(any(UserPreferences.class))).thenAnswer(invocation -> invocation.getArgument(0));
        provisioner = new UserProvisioner(users, roles, preferences, encoder, events);
    }

    @Test
    void aNewAccountIsNormalizedSavedWithPreferencesAndAnnounced() {
        User user = provisioner.provision(new UserProvisioner.NewAccount("  Ana ", null, "  Ana@Nexo.Local ",
                "secreto-123", AuthenticationProvider.LOCAL, true, false, Set.of(TestFixtures.role("USER")), "es"));

        assertThat(user.getEmail()).isEqualTo("ana@nexo.local");
        assertThat(user.getFirstName()).isEqualTo("Ana");
        assertThat(user.getLastName()).isEmpty();
        assertThat(encoder.matches("secreto-123", user.getPassword())).isTrue();
        assertThat(user.getPreferences().getLocale()).isEqualTo("es");
        verify(events).publish(UserEventType.USER_CREATED, user);
    }

    @Test
    void anAccountWithoutItsOwnPasswordKeepsItEmpty() {
        User user = provisioner.provision(new UserProvisioner.NewAccount("Ana", "Pérez", "ana@nexo.local", null,
                AuthenticationProvider.GOOGLE, true, true, Set.of(TestFixtures.role("USER")), null));

        assertThat(user.getPassword()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
    }

    @Test
    void theDefaultRoleMustBeSeeded() {
        when(roles.findByName(UserProvisioner.DEFAULT_ROLE)).thenReturn(Optional.empty());

        assertThatThrownBy(provisioner::defaultRoles).isInstanceOf(IllegalStateException.class);
    }
}
