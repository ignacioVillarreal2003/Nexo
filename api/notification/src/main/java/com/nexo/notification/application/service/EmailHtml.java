package com.nexo.notification.application.service;

/** Utilidades para armar HTML de correo de forma segura. */
final class EmailHtml {

    private EmailHtml() {
    }

    static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Solo enlaces http(s): un {@code javascript:} que llegue en un feed no debe terminar en el correo. */
    static String safeUrl(String url) {
        String trimmed = url == null ? "" : url.trim();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://") ? trimmed : "#";
    }
}
