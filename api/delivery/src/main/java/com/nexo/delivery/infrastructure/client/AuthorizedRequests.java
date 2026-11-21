package com.nexo.delivery.infrastructure.client;

import com.nexo.delivery.domain.exception.ApiException;
import com.nexo.delivery.domain.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Function;

/** Ejecuta una petición con el token de servicio; ante un 401 lo renueva y reintenta una vez. */
@Component
@RequiredArgsConstructor
public class AuthorizedRequests {

    private final ServiceTokenProvider serviceTokenProvider;

    public <T> T execute(String service, Function<String, RestClient.ResponseSpec> request, Class<T> type) {
        try {
            return request.apply(authorization()).body(type);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 401) {
                throw unavailable(service, e);
            }
            serviceTokenProvider.invalidate();
            try {
                return request.apply(authorization()).body(type);
            } catch (RestClientException retry) {
                throw unavailable(service, retry);
            }
        } catch (RestClientException e) {
            throw unavailable(service, e);
        }
    }

    private String authorization() {
        return "Bearer " + serviceTokenProvider.token();
    }

    public static String header() {
        return HttpHeaders.AUTHORIZATION;
    }

    private static ApiException unavailable(String service, Exception cause) {
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                "The " + service + " is not available right now", Map.of(), cause);
    }
}
