package com.nexo.topic.domain.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void slugifyRemovesAccentsSymbolsAndEdgeDashes() {
        assertThat(Slugs.slugify("Economía y finanzas")).isEqualTo("economia-y-finanzas");
        assertThat(Slugs.slugify("  ¿Inteligencia Artificial? ")).isEqualTo("inteligencia-artificial");
        assertThat(Slugs.slugify("C++ & Rust")).isEqualTo("c-rust");
    }

    @Test
    void foldIsCaseAndAccentInsensitiveAndNullSafe() {
        assertThat(Slugs.fold("Ciberseguridad")).isEqualTo(Slugs.fold("CIBERSEGURIDAD"));
        assertThat(Slugs.fold("Tecnología")).isEqualTo("tecnologia");
        assertThat(Slugs.fold(null)).isEmpty();
    }
}
