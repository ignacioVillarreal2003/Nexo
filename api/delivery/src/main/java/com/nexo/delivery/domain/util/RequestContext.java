package com.nexo.delivery.domain.util;

/**
 * Datos de la petición en curso que viajan en el MDC (logs, auditoría, respuestas de error y mensajes): el id de
 * correlación y el origen. Los completa el filtro HTTP de correlación; cualquier capa puede leerlos.
 */
public final class RequestContext {

    /** Cabecera con la que llega (y se devuelve) el id de correlación. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String CLIENT_IP_MDC_KEY = "clientIp";
    public static final String USER_AGENT_MDC_KEY = "userAgent";

    private RequestContext() {
    }
}
