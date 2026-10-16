package com.sophia.documentworkspace.domain.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownUtilTest {

    private static final String NOTE = """
            # Plan

            Some **bold** text, a [link](https://example.com) and `code`.

            - [x] done
            - [ ] todo

            | a | b |
            |---|---|
            | 1 | 2 |

            > quoted

            ```java
            int x = 1;
            ```
            """;

    @Test
    void rendersGithubFlavouredMarkdown() {
        String html = MarkdownUtil.markdownToHtml(NOTE);
        assertThat(html).contains("<h1>Plan</h1>").contains("<strong>bold</strong>")
                .contains("<table>").contains("task-list-item").contains("<blockquote>");
    }

    @Test
    void rawHtmlIsEscaped() {
        assertThat(MarkdownUtil.markdownToHtml("<script>alert(1)</script>")).doesNotContain("<script>");
    }

    @Test
    void plainTextDropsSyntaxButKeepsWords() {
        String text = MarkdownUtil.plainText("# Title\n\n- [ ] buy [milk](http://x) and [[Other note|alias]]\n```js\nsecret()\n```\n");
        assertThat(text).contains("Title").contains("buy milk").contains("alias").doesNotContain("[");
    }

    @Test
    void docxExportContainsTheText() throws Exception {
        byte[] docx = MarkdownDocxWriter.write("Plan", NOTE);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
            assertThat(text.toString()).contains("Plan").contains("bold").contains("todo").contains("int x = 1;");
            assertThat(document.getTables()).hasSize(1);
        }
    }
}
