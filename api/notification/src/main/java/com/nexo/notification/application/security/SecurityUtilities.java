package com.nexo.notification.application.security;

import com.nexo.notification.domain.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtilities {

    private SecurityUtilities() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof UserAuthenticationToken token) {
            return Optional.of(token.getPrincipal());
        }
        return Optional.empty();
    }

    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(() -> new UnauthorizedException("No authenticated user in context"));
    }
}
