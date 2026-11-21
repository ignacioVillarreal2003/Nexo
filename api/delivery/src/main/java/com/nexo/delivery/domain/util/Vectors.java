package com.nexo.delivery.domain.util;

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
}
