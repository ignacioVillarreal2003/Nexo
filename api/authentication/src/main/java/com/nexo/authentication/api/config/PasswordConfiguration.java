package com.nexo.authentication.api.config;

import com.nexo.authentication.domain.config.ApplicationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * El codificador de contraseñas en una configuración propia: lo usan tanto la cadena de seguridad como el alta de
 * cuentas (que a su vez la seguridad necesita para el ingreso con Google), y definirlo en SecurityConfiguration
 * formaba un ciclo de dependencias.
 */
@Configuration
public class PasswordConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder(ApplicationProperties properties) {
        return new BCryptPasswordEncoder(properties.security().password().bcryptStrength());
    }
}
