package com.sophia.ai.application.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalyzerTest {

    @Test
    void accentsAndCaseDoNotMatter() {
        assertThat(TextAnalyzer.terms("Fotosíntesis")).isEqualTo(TextAnalyzer.terms("fotosintesis"));
    }

    @Test
    void pluralsMeetTheirSingular() {
        assertThat(TextAnalyzer.terms("networks")).isEqualTo(TextAnalyzer.terms("network"));
        assertThat(TextAnalyzer.terms("celulas")).isEqualTo(TextAnalyzer.terms("celula"));
        assertThat(TextAnalyzer.terms("cities")).isEqualTo(TextAnalyzer.terms("city"));
    }

    @Test
    void shortWordsAreLeftAlone() {
        assertThat(TextAnalyzer.stem("red")).isEqualTo("red");
        assertThat(TextAnalyzer.stem("gas")).isEqualTo("gas");
    }

    @Test
    void questionTermsDropStopWordsAndDuplicates() {
        assertThat(TextAnalyzer.queryTerms("Qué es la fase luminosa de la fotosíntesis?"))
                .containsExactly("fase", "luminosa", TextAnalyzer.terms("fotosíntesis").get(0));
        assertThat(TextAnalyzer.queryTerms("the the the cell")).containsExactly("cell");
    }

    @Test
    void aQuestionOfOnlyStopWordsHasNoTerms() {
        assertThat(TextAnalyzer.queryTerms("y el de la")).isEmpty();
        assertThat(TextAnalyzer.queryTerms(null)).isEmpty();
    }

    @Test
    void queryTermsAreCapped() {
        String many = String.join(" ", java.util.stream.IntStream.range(0, 100).mapToObj(i -> "palabra" + i).toList());
        assertThat(TextAnalyzer.queryTerms(many)).hasSize(24);
    }

    @Test
    void termsAreOnlyLettersAndDigits() {
        assertThat(TextAnalyzer.terms("a'b | c:d; (e&f)")).allMatch(t -> t.matches("[\\p{L}\\p{N}]+"));
    }

    @Test
    void chunksStayNearTheTargetAndLoseNothing() {
        String paragraph = "Una oración de ejemplo para rellenar el párrafo. ";
        String text = String.join("\n\n", java.util.Collections.nCopies(60, paragraph.repeat(4)));
        List<String> chunks = TextAnalyzer.chunks(text, 1000);
        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allMatch(c -> c.length() <= 1600);
        String joined = String.join(" ", chunks).replaceAll("\\s+", " ");
        assertThat(joined.split("Una oración").length - 1).isEqualTo(240);
    }

    @Test
    void aChunkKnowsWhichHeadingItBelongsTo() {
        String text = "# Biología\n\nIntro.\n\n## Mitosis\n\n" + "Texto sobre la mitosis. ".repeat(100)
                + "\n\n## Meiosis\n\nTexto sobre la meiosis.";
        List<String> chunks = TextAnalyzer.chunks(text, 600);
        assertThat(chunks.get(chunks.size() - 1)).contains("Meiosis").contains("meiosis");
        assertThat(chunks).anyMatch(c -> c.startsWith("[Mitosis]"));
    }

    @Test
    void aSingleHugeParagraphIsStillSplit() {
        List<String> chunks = TextAnalyzer.chunks("x".repeat(10000), 1000);
        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allMatch(c -> c.length() <= 1500);
    }

    @Test
    void emptyTextHasNoChunks() {
        assertThat(TextAnalyzer.chunks("  \n\n ", 1000)).isEmpty();
        assertThat(TextAnalyzer.chunks(null, 1000)).isEmpty();
    }
}
