package com.sophia.notification.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextRendererTest {

    @Test
    void linksKeepTheirLabelAndTheirTarget() {
        String text = PlainTextRenderer.fromHtml(
                "<p>Hi Ada,</p><p><a href=\"https://sophia.local/verify?token=abc\">Verify my email</a></p>");

        assertThat(text).isEqualTo("""
                Hi Ada,

                Verify my email (https://sophia.local/verify?token=abc)""");
    }

    @Test
    void aLinkLabelledWithItsOwnUrlIsNotRepeated() {
        String text = PlainTextRenderer.fromHtml(
                "<a href=\"https://sophia.local\">https://sophia.local</a>");

        assertThat(text).isEqualTo("https://sophia.local");
    }

    @Test
    void blockElementsAndBreaksBecomeLineBreaks() {
        String text = PlainTextRenderer.fromHtml("<p>One</p><p>Two<br/>Three</p><hr/>");

        assertThat(text).isEqualTo("""
                One

                Two
                Three""");
    }

    @Test
    void escapedCharactersComeBackAsThemselves() {
        String text = PlainTextRenderer.fromHtml("<p>Tom &amp; Jerry said &quot;hi&quot; &lt;here&gt;</p>");

        assertThat(text).isEqualTo("Tom & Jerry said \"hi\" <here>");
    }

    @Test
    void anEscapedAmpersandDoesNotResurrectAnEntity() {
        String text = PlainTextRenderer.fromHtml("<p>&amp;lt;script&amp;gt;</p>");

        assertThat(text).isEqualTo("&lt;script&gt;");
    }

    @Test
    void emptyInputRendersEmpty() {
        assertThat(PlainTextRenderer.fromHtml(null)).isEmpty();
        assertThat(PlainTextRenderer.fromHtml("   ")).isEmpty();
    }
}
