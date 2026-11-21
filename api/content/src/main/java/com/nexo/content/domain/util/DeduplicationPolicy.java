package com.nexo.content.domain.util;

/**
 * Decide qué hacer con un contenido según su similitud semántica con el vecino más cercano.
 * <ul>
 *   <li>{@code >= duplicado}: es el mismo contenido.</li>
 *   <li>{@code >= relacionado}: zona gris, la IA distingue duplicado, actualización o nuevo.</li>
 *   <li>por debajo: tema relacionado pero información distinta.</li>
 * </ul>
 */
public final class DeduplicationPolicy {

    public enum Decision { DUPLICATE, ASK_AI, NEW }

    private final double duplicateThreshold;
    private final double relatedThreshold;

    public DeduplicationPolicy(double duplicateThreshold, double relatedThreshold) {
        if (relatedThreshold > duplicateThreshold) {
            throw new IllegalArgumentException("The related threshold cannot exceed the duplicate threshold");
        }
        this.duplicateThreshold = duplicateThreshold;
        this.relatedThreshold = relatedThreshold;
    }

    public Decision decide(Double nearestSimilarity) {
        if (nearestSimilarity == null) {
            return Decision.NEW;
        }
        if (nearestSimilarity >= duplicateThreshold) {
            return Decision.DUPLICATE;
        }
        if (nearestSimilarity >= relatedThreshold) {
            return Decision.ASK_AI;
        }
        return Decision.NEW;
    }
}
