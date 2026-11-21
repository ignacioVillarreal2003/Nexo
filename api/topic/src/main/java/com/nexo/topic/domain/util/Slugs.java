package com.nexo.topic.domain.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugs {

    private Slugs() {
    }

    /** "Economía y finanzas" → "economia-y-finanzas". */
    public static String slugify(String text) {
        return fold(text).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    /** Minúsculas y sin acentos, para comparar textos al buscar. */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
