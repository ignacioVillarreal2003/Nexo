package com.nexo.notification.application.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, Set<String> globalRoles, Set<String> permissions) {
}
