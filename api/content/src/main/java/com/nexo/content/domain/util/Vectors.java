package com.nexo.content.domain.util;

/** Literal de texto de pgvector ({@code [0.1,0.2,...]}) para las consultas nativas. */
public final class Vectors {

    private Vectors() {
    }

    public static String toLiteral(float[] vector) {
        if (vector == null) {
            return null;
        }
        StringBuilder literal = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }

    public static float[] fromLiteral(String literal) {
        if (literal == null || literal.length() < 2) {
            return null;
        }
        String body = literal.substring(1, literal.length() - 1);
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
