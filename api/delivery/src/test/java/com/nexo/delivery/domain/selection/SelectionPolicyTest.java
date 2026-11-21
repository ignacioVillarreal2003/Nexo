package com.nexo.delivery.domain.selection;

import com.nexo.delivery.domain.selection.SelectionPolicy.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionPolicyTest {

    private final SelectionPolicy policy = new SelectionPolicy(0.9);

    private static final float[] ECONOMY = {1f, 0f, 0f};
    private static final float[] ECONOMY_REWORDED = {0.99f, 0.05f, 0f};
    private static final float[] SPACE = {0f, 1f, 0f};
    private static final float[] SPORTS = {0f, 0f, 1f};

    @Test
    void respectsTheLimitInOrder() {
        Candidate a = new Candidate(UUID.randomUUID(), false, null, ECONOMY);
        Candidate b = new Candidate(UUID.randomUUID(), false, null, SPACE);
        Candidate c = new Candidate(UUID.randomUUID(), false, null, SPORTS);

        List<SelectionPolicy.Selected> result = policy.select(List.of(a, b, c), 2, e -> null, id -> false);

        assertThat(result).extracting(s -> s.candidate().id()).containsExactly(a.id(), b.id());
    }

    @Test
    void skipsCandidatesTooSimilarToTheUsersHistory() {
        Candidate known = new Candidate(UUID.randomUUID(), false, null, ECONOMY_REWORDED);
        Candidate fresh = new Candidate(UUID.randomUUID(), false, null, SPACE);

        List<SelectionPolicy.Selected> result = policy.select(List.of(known, fresh), 5,
                e -> SelectionPolicy.cosine(e, ECONOMY), id -> false);

        assertThat(result).extracting(s -> s.candidate().id()).containsExactly(fresh.id());
    }

    @Test
    void deliversUpdatesOfKnownStoriesEvenIfSimilar() {
        UUID previous = UUID.randomUUID();
        Candidate update = new Candidate(UUID.randomUUID(), true, previous, ECONOMY_REWORDED);

        List<SelectionPolicy.Selected> result = policy.select(List.of(update), 5,
                e -> SelectionPolicy.cosine(e, ECONOMY), Set.of(previous)::contains);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().update()).isTrue();
    }

    @Test
    void avoidsNearDuplicatesWithinTheSameSelection() {
        Candidate a = new Candidate(UUID.randomUUID(), false, null, ECONOMY);
        Candidate b = new Candidate(UUID.randomUUID(), false, null, ECONOMY_REWORDED);
        Candidate c = new Candidate(UUID.randomUUID(), false, null, SPORTS);

        List<SelectionPolicy.Selected> result = policy.select(List.of(a, b, c), 5, e -> null, id -> false);

        assertThat(result).extracting(s -> s.candidate().id()).containsExactly(a.id(), c.id());
    }
}
