package com.nexo.content.domain.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normaliza URLs para la deduplicación exacta: esquema https, host en minúsculas sin {@code www.},
 * sin fragmento, sin parámetros de seguimiento y sin barra final.
 */
public final class UrlNormalizer {

    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "fbclid", "gclid", "dclid", "mc_cid", "mc_eid", "igshid", "ref", "ref_src", "cmpid", "ocid", "smid", "mbid");

    private UrlNormalizer() {
    }

    public static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (scheme.equals("http")) {
                scheme = "https";
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                    .filter(p -> !p.isBlank())
                    .filter(p -> {
                        String key = p.split("=", 2)[0].toLowerCase(Locale.ROOT);
                        return !key.startsWith("utm_") && !TRACKING_PARAMETERS.contains(key);
                    })
                    .sorted()
                    .collect(Collectors.joining("&"));
            int port = uri.getPort();
            String portPart = port == -1 || port == 443 || port == 80 ? "" : ":" + port;
            return scheme + "://" + host + portPart + path + (query.isEmpty() ? "" : "?" + query);
        } catch (URISyntaxException e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }
}
