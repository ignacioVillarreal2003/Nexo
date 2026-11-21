package com.nexo.delivery.infrastructure.client;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.exception.ApiException;
import com.nexo.delivery.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;

/**
 * Token de servicio de este microservicio (grant client_credentials contra el servicio de autenticación),
 * cacheado hasta poco antes de vencer.
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenProvider {

    private static final long REFRESH_SLACK_SECONDS = 30;

    private final RestClient authenticationServiceRestClient;
    private final ApplicationProperties properties;

    private volatile String cachedToken;
    private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;

    private record TokenResponse(String access_token, Long expires_in) {
    }

    public synchronized String token() {
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt)) {
            return cachedToken;
        }
        ApplicationProperties.AuthenticationService config = properties.authenticationService();
        if (!config.enabled()) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "No service client is configured (AUTHENTICATION_SERVICE_CLIENT_ID)");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        try {
            TokenResponse token = authenticationServiceRestClient.post()
                    .uri("/api/v1/authentication/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (token == null || token.access_token() == null) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                        "The authentication service did not issue a service token");
            }
            long ttl = token.expires_in() == null ? 0 : token.expires_in();
            cachedToken = token.access_token();
            cachedTokenExpiresAt = Instant.now().plusSeconds(Math.max(0, ttl - REFRESH_SLACK_SECONDS));
            return cachedToken;
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not obtain a service token", Map.of(), e);
        }
    }

    public void invalidate() {
        cachedToken = null;
    }
}
