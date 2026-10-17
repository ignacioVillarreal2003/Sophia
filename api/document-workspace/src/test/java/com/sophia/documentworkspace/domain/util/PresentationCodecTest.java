package com.sophia.documentworkspace.domain.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PresentationCodecTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String DECK = """
            {"version":1,"slides":[
              {"id":"a","title":"Mitosis","body":"- Profase\\n- Anafase","color":"blue"},
              {"id":"b","title":"Meiosis","body":"Produce gametos.","color":"green"}]}""";

    @Test
    void anEmptyDeckIsValidAndHasOneCard() throws IOException {
        String json = PresentationCodec.empty();
        assertThat(PresentationCodec.isValid(json)).isTrue();
        assertThat(JSON.readTree(json).path("slides")).hasSize(1);
    }

    @Test
    void rejectsJsonWithoutSlides() {
        assertThat(PresentationCodec.isValid("{\"nope\":1}")).isFalse();
        assertThat(PresentationCodec.isValid("not json")).isFalse();
    }

    @Test
    void markdownRoundTripKeepsTitlesAndBodies() throws IOException {
        String markdown = PresentationCodec.toMarkdown(DECK);
        assertThat(markdown).contains("## Mitosis").contains("---").contains("## Meiosis");

        JsonNode slides = JSON.readTree(PresentationCodec.fromMarkdown(markdown)).path("slides");
        assertThat(slides).hasSize(2);
        assertThat(slides.get(0).path("title").asText()).isEqualTo("Mitosis");
        assertThat(slides.get(1).path("body").asText()).isEqualTo("Produce gametos.");
    }

    @Test
    void markdownWithoutRulesSplitsAtSecondLevelHeadings() throws IOException {
        JsonNode slides = JSON.readTree(PresentationCodec.fromMarkdown("## Uno\n\ntexto\n\n## Dos\n\notro")).path("slides");
        assertThat(slides).hasSize(2);
        assertThat(slides.get(1).path("title").asText()).isEqualTo("Dos");
    }

    @Test
    void pptxRoundTripKeepsSlideTitles() throws IOException {
        byte[] pptx = PresentationCodec.toPptx(DECK);
        JsonNode slides = JSON.readTree(PresentationCodec.fromPptx(pptx)).path("slides");
        assertThat(slides).hasSize(2);
        assertThat(slides.get(0).path("title").asText()).isEqualTo("Mitosis");
        assertThat(slides.get(0).path("body").asText()).contains("Profase");
    }

    @Test
    void searchTextIncludesTitlesAndBodies() {
        assertThat(PresentationCodec.searchText(DECK)).contains("Mitosis").contains("gametos");
    }

    @Test
    void whiteboardSearchTextCollectsElementTexts() {
        String board = "{\"version\":1,\"elements\":[{\"id\":\"1\",\"type\":\"note\",\"text\":\"Ribosoma\"},{\"id\":\"2\",\"type\":\"arrow\",\"text\":\"\"}]}";
        assertThat(WhiteboardCodec.isValid(board)).isTrue();
        assertThat(WhiteboardCodec.searchText(board).strip()).isEqualTo("Ribosoma");
        assertThat(WhiteboardCodec.toMarkdown(board).strip()).isEqualTo("- Ribosoma");
        assertThat(WhiteboardCodec.isValid("{\"slides\":[]}")).isFalse();
    }
}
