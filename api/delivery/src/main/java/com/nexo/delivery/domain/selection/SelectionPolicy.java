package com.nexo.delivery.domain.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Antirrepetición por usuario. Recorre los candidatos (ordenados por puntuación y sin los que el usuario
 * ya recibió) y descarta los demasiado parecidos a algo de su historial o de la propia selección.
 * Excepción: una actualización de un hecho que el usuario ya conoce sí se entrega.
 */
public final class SelectionPolicy {

    /** @param update si content lo marcó como actualización de {@code relatedContentId} */
    public record Candidate(UUID id, boolean update, UUID relatedContentId, float[] embedding) {

        boolean isUpdateOfSomething() {
            return update && relatedContentId != null;
        }
    }

    public record Selected(Candidate candidate, boolean update) {
    }

    private final double userSimilarityThreshold;

    public SelectionPolicy(double userSimilarityThreshold) {
        this.userSimilarityThreshold = userSimilarityThreshold;
    }

    /**
     * @param historySimilarity similitud máxima del candidato con el historial del usuario (null sin historial)
     * @param alreadyDelivered  si el usuario ya recibió un contenido dado
     */
    public List<Selected> select(List<Candidate> candidates, int limit, Function<float[], Double> historySimilarity,
                                 Predicate<UUID> alreadyDelivered) {
        List<Selected> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (selected.size() >= limit) {
                break;
            }
            boolean updateOfKnown = candidate.isUpdateOfSomething() && alreadyDelivered.test(candidate.relatedContentId());
            if (!updateOfKnown && candidate.embedding() != null) {
                Double similarity = historySimilarity.apply(candidate.embedding());
                if (similarity != null && similarity >= userSimilarityThreshold) {
                    continue;
                }
            }
            if (candidate.embedding() != null && tooCloseToSelection(candidate, selected)) {
                continue;
            }
            selected.add(new Selected(candidate, updateOfKnown));
        }
        return selected;
    }

    private boolean tooCloseToSelection(Candidate candidate, List<Selected> selected) {
        for (Selected previous : selected) {
            float[] other = previous.candidate().embedding();
            if (other != null && cosine(candidate.embedding(), other) >= userSimilarityThreshold) {
                return true;
            }
        }
        return false;
    }

    public static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
