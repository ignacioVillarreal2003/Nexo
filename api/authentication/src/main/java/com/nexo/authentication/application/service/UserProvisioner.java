package com.nexo.authentication.application.service;

import com.nexo.authentication.domain.entity.Role;
import com.nexo.authentication.domain.entity.User;
import com.nexo.authentication.domain.entity.UserPreferences;
import com.nexo.authentication.domain.entity.enums.AuthenticationProvider;
import com.nexo.authentication.infrastructure.messaging.UserEventPublisher;
import com.nexo.authentication.infrastructure.messaging.UserEventType;
import com.nexo.authentication.infrastructure.repository.RoleRepository;
import com.nexo.authentication.infrastructure.repository.UserPreferencesRepository;
import com.nexo.authentication.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Alta de cuentas: el único lugar donde se crea un usuario. Lo usan el registro, el ingreso con Google, la
 * administración y el administrador inicial; cada uno agrega su propia auditoría y métricas.
 *
 * <p>Normaliza el email, codifica la contraseña, guarda el usuario con sus preferencias (en el idioma con el que se
 * registró, si se conoce) y publica {@code USER_CREATED} para que los demás servicios lo conozcan.
 */
@Component
@RequiredArgsConstructor
public class UserProvisioner {

    /** Rol de toda cuenta creada por la propia persona. */
    public static final String DEFAULT_ROLE = "USER";

    /**
     * Una cuenta nueva.
     *
     * @param rawPassword {@code null} para una cuenta sin contraseña propia (ingreso con Google)
     * @param locale      idioma de la app al registrarse; {@code null} deja el de las preferencias por defecto
     */
    public record NewAccount(String firstName, String lastName, String email, String rawPassword,
                             AuthenticationProvider provider, boolean active, boolean emailVerified, Set<Role> roles,
                             String locale) {
    }

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher userEventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public User provision(NewAccount account) {
        User user = userRepository.save(User.create(
                trimToEmpty(account.firstName()),
                trimToEmpty(account.lastName()),
                normalizeEmail(account.email()),
                account.rawPassword() == null ? null : passwordEncoder.encode(account.rawPassword()),
                account.provider(), account.active(), account.emailVerified(), new HashSet<>(account.roles())));
        UserPreferences preferences = UserPreferences.create(user);
        if (account.locale() != null) {
            preferences.updateSettings(null, null, account.locale(), null);
        }
        user.assignPreferences(userPreferencesRepository.save(preferences));
        userEventPublisher.publish(UserEventType.USER_CREATED, user);
        return user;
    }

    public Set<Role> defaultRoles() {
        return Set.of(roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_ROLE + " is not seeded")));
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
