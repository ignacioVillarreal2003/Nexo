package com.nexo.content.api.filter;

import com.nexo.content.domain.util.RequestContext;
import com.nexo.content.api.util.HttpRequestUtilities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ensures every request carries a correlation id: propagated from the caller when
 * present, generated otherwise. The id is exposed in the MDC (structured logs),
 * the response header, and error payloads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern ACCEPTABLE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(RequestContext.REQUEST_ID_HEADER);
        String requestId = StringUtils.hasText(incoming) && ACCEPTABLE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(RequestContext.REQUEST_ID_MDC_KEY, requestId);
        String clientIp = HttpRequestUtilities.clientIp(request);
        if (clientIp != null) {
            MDC.put(RequestContext.CLIENT_IP_MDC_KEY, clientIp);
        }
        String userAgent = HttpRequestUtilities.userAgent(request);
        if (userAgent != null) {
            MDC.put(RequestContext.USER_AGENT_MDC_KEY, userAgent);
        }
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
            MDC.remove(RequestContext.CLIENT_IP_MDC_KEY);
            MDC.remove(RequestContext.USER_AGENT_MDC_KEY);
        }
    }
}
