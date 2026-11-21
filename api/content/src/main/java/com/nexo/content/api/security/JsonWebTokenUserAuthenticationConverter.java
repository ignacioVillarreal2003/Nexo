package com.nexo.content.api.security;

import com.nexo.content.application.security.UserAuthenticationToken;
import com.nexo.content.application.security.AuthenticatedUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Traduce el JWT emitido por el servicio de autenticación a un {@link AuthenticatedUser}.
 * Los tokens de usuario aportan roles y permisos; los tokens de servicio ({@code type=service})
 * aportan sus scopes, que se exponen como authorities con el mismo nombre.
 */
public class JsonWebTokenUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_SCOPES = "scopes";
    private static final String TYPE_SERVICE = "service";
    private static final String ROLE_SERVICE = "SERVICE";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID subjectId = parseSubject(jwt);
        boolean service = TYPE_SERVICE.equals(jwt.getClaimAsString(CLAIM_TYPE));

        Set<String> roles = service ? Set.of(ROLE_SERVICE) : claimSet(jwt, CLAIM_ROLES);
        Set<String> permissions = claimSet(jwt, service ? CLAIM_SCOPES : CLAIM_PERMISSIONS);

        Set<GrantedAuthority> authorities = Stream.concat(
                        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                        permissions.stream().map(SimpleGrantedAuthority::new))
                .collect(Collectors.toUnmodifiableSet());

        AuthenticatedUser principal = new AuthenticatedUser(subjectId,
                service ? null : jwt.getClaimAsString(CLAIM_EMAIL), roles, permissions, service);
        return new UserAuthenticationToken(principal, jwt, authorities);
    }

    private static Set<String> claimSet(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private UUID parseSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new BadJwtException("Token is missing the sub claim");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw new BadJwtException("Token sub claim is not a valid UUID", e);
        }
    }
}
