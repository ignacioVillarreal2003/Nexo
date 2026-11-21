package com.nexo.content.domain.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/** Huellas del texto: SHA-256 para la deduplicación exacta y SimHash para la textual. */
public final class TextFingerprint {

    private static final int SHINGLE = 3;
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private TextFingerprint() {
    }

    /** Minúsculas, sin acentos ni puntuación y con los espacios colapsados. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String folded = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * SimHash de 64 bits sobre shingles de {@value #SHINGLE} palabras: textos con diferencias
     * menores de redacción quedan a poca distancia de Hamming.
     */
    public static long simhash(String normalizedText) {
        String[] words = normalizedText.isEmpty() ? new String[0] : normalizedText.split(" ");
        int[] weights = new int[64];
        int shingles = Math.max(1, words.length - SHINGLE + 1);
        for (int i = 0; i < shingles; i++) {
            StringBuilder shingle = new StringBuilder();
            for (int j = i; j < Math.min(words.length, i + SHINGLE); j++) {
                shingle.append(words[j]).append(' ');
            }
            long hash = fnv1a(shingle.toString());
            for (int bit = 0; bit < 64; bit++) {
                weights[bit] += ((hash >>> bit) & 1L) == 1L ? 1 : -1;
            }
        }
        long result = 0L;
        for (int bit = 0; bit < 64; bit++) {
            if (weights[bit] > 0) {
                result |= 1L << bit;
            }
        }
        return result;
    }

    public static int hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static long fnv1a(String text) {
        long hash = FNV_OFFSET;
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (b & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
