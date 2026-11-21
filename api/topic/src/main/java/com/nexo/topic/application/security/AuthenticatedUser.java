package com.nexo.topic.application.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal autenticado. Para un token de usuario {@code id} es el usuario; para un token de servicio
 * (client_credentials) es el cliente de servicio y {@code permissions} son sus scopes.
 */
public record AuthenticatedUser(UUID id, String email, Set<String> globalRoles, Set<String> permissions,
                                boolean service) {
}
