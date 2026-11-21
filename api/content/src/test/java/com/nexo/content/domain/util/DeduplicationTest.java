package com.nexo.content.domain.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeduplicationTest {

    @Test
    void urlNormalizationRemovesTrackingAndCosmeticDifferences() {
        String a = UrlNormalizer.normalize("http://www.Example.com/news/article/?utm_source=x&id=7&fbclid=abc#top");
        String b = UrlNormalizer.normalize("https://example.com/news/article?id=7");
        assertThat(a).isEqualTo(b).isEqualTo("https://example.com/news/article?id=7");
    }

    @Test
    void urlNormalizationKeepsMeaningfulParametersSorted() {
        assertThat(UrlNormalizer.normalize("https://x.org/p?b=2&a=1"))
                .isEqualTo(UrlNormalizer.normalize("https://x.org/p?a=1&b=2"));
        assertThat(UrlNormalizer.normalize("https://x.org/p?a=1"))
                .isNotEqualTo(UrlNormalizer.normalize("https://x.org/p?a=2"));
    }

    @Test
    void textNormalizationIgnoresCaseAccentsAndPunctuation() {
        assertThat(TextFingerprint.normalize("¡La Economía  crece, según el FMI!"))
                .isEqualTo(TextFingerprint.normalize("la economia crece segun el fmi"));
    }

    @Test
    void simhashIsCloseForMinorRewordingAndFarForDifferentText() {
        String base = "El banco central anunció hoy una subida de tipos de interés de medio punto para contener "
                + "la inflación, que en el último trimestre alcanzó su nivel más alto de la década según los datos oficiales";
        String reworded = base.replace("anunció hoy", "anunció este martes");
        String other = "Un equipo de astrónomos detectó vapor de agua en la atmósfera de un exoplaneta templado "
                + "situado a ciento veinte años luz gracias a las observaciones del telescopio espacial";

        long h1 = TextFingerprint.simhash(TextFingerprint.normalize(base));
        long h2 = TextFingerprint.simhash(TextFingerprint.normalize(reworded));
        long h3 = TextFingerprint.simhash(TextFingerprint.normalize(other));

        assertThat(TextFingerprint.hammingDistance(h1, h2)).isLessThan(TextFingerprint.hammingDistance(h1, h3));
        assertThat(TextFingerprint.hammingDistance(h1, h3)).isGreaterThan(10);
    }

    @Test
    void deduplicationPolicyUsesBothThresholds() {
        DeduplicationPolicy policy = new DeduplicationPolicy(0.92, 0.80);
        assertThat(policy.decide(null)).isEqualTo(DeduplicationPolicy.Decision.NEW);
        assertThat(policy.decide(0.95)).isEqualTo(DeduplicationPolicy.Decision.DUPLICATE);
        assertThat(policy.decide(0.92)).isEqualTo(DeduplicationPolicy.Decision.DUPLICATE);
        assertThat(policy.decide(0.85)).isEqualTo(DeduplicationPolicy.Decision.ASK_AI);
        assertThat(policy.decide(0.60)).isEqualTo(DeduplicationPolicy.Decision.NEW);
    }

    @Test
    void deduplicationPolicyRejectsInconsistentThresholds() {
        assertThatThrownBy(() -> new DeduplicationPolicy(0.7, 0.9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void vectorLiteralRoundTrips() {
        float[] vector = {0.25f, -1.5f, 3f};
        assertThat(Vectors.fromLiteral(Vectors.toLiteral(vector))).containsExactly(vector);
    }
}
