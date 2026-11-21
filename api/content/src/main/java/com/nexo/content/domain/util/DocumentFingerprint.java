package com.nexo.content.domain.util;

/**
 * Huellas de un documento para la deduplicación sin IA: la URL normalizada (exacta), el texto normalizado (exacta)
 * y el SimHash (textual, tolera diferencias menores de redacción).
 */
public record DocumentFingerprint(String urlHash, String contentHash, long simhash, int words) {

    /** Con menos palabras el SimHash no es fiable. */
    private static final int MINIMUM_WORDS_FOR_SIMHASH = 12;

    public static DocumentFingerprint of(String url, String title, String text) {
        String normalized = TextFingerprint.normalize(title + " " + text);
        return new DocumentFingerprint(TextFingerprint.sha256(UrlNormalizer.normalize(url)),
                TextFingerprint.sha256(normalized), TextFingerprint.simhash(normalized),
                normalized.isBlank() ? 0 : normalized.split(" ").length);
    }

    public boolean simhashReliable() {
        return words >= MINIMUM_WORDS_FOR_SIMHASH;
    }
}
