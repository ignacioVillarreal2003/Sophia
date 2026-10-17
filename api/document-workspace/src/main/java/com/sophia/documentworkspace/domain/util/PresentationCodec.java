package com.sophia.documentworkspace.domain.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * A visual deck is stored as JSON: {@code {"version":1,"slides":[{"id","title","body","color"}]}} where the body is
 * Markdown. The deck is meant for studying (a horizontal board of cards, flashcards), not for presenting, but it can
 * be exported to and imported from PowerPoint and Markdown.
 */
public final class PresentationCodec {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_COLOR = "slate";

    private PresentationCodec() {
    }

    public static String empty() {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", 1);
        ArrayNode slides = root.putArray("slides");
        slides.add(slide("", "", DEFAULT_COLOR));
        return root.toString();
    }

    private static ObjectNode slide(String title, String body, String color) {
        ObjectNode slide = JSON.createObjectNode();
        slide.put("id", UUID.randomUUID().toString());
        slide.put("title", title);
        slide.put("body", body);
        slide.put("color", color);
        return slide;
    }

    /** True when the text is a JSON object with a {@code slides} array. */
    public static boolean isValid(String json) {
        try {
            JsonNode tree = JSON.readTree(json);
            return tree != null && tree.isObject() && tree.path("slides").isArray();
        } catch (IOException e) {
            return false;
        }
    }

    public static String searchText(String json) {
        StringBuilder text = new StringBuilder();
        for (JsonNode slide : slides(json)) {
            text.append(slide.path("title").asText("")).append('\n').append(MarkdownUtil.plainText(slide.path("body").asText(""))).append("\n\n");
        }
        return text.toString();
    }

    /** One section per slide, separated by horizontal rules. */
    public static String toMarkdown(String json) {
        StringBuilder md = new StringBuilder();
        boolean first = true;
        for (JsonNode slide : slides(json)) {
            if (!first) {
                md.append("\n---\n\n");
            }
            first = false;
            String title = slide.path("title").asText("").strip();
            String body = slide.path("body").asText("").strip();
            if (!title.isEmpty()) {
                md.append("## ").append(title).append("\n\n");
            }
            if (!body.isEmpty()) {
                md.append(body).append('\n');
            }
        }
        return md.toString();
    }

    /** Splits Markdown into slides at horizontal rules, or at level-2 headings when there are no rules. */
    public static String fromMarkdown(String markdown) {
        String text = markdown == null ? "" : markdown.replace("\r\n", "\n");
        boolean hasRules = text.lines().anyMatch(l -> l.strip().equals("---"));
        ArrayNode slides = JSON.createArrayNode();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            boolean boundary = hasRules ? line.strip().equals("---") : line.startsWith("## ") && current.length() > 0;
            if (boundary) {
                addSlide(slides, current.toString());
                current.setLength(0);
                if (!hasRules) {
                    current.append(line).append('\n');
                }
            } else {
                current.append(line).append('\n');
            }
        }
        addSlide(slides, current.toString());
        if (slides.isEmpty()) {
            slides.add(slide("", "", DEFAULT_COLOR));
        }
        ObjectNode root = JSON.createObjectNode();
        root.put("version", 1);
        root.set("slides", slides);
        return root.toString();
    }

    private static void addSlide(ArrayNode slides, String chunk) {
        String text = chunk.strip();
        if (text.isEmpty()) {
            return;
        }
        String title = "";
        String body = text;
        String[] lines = text.split("\n", 2);
        String first = lines[0].strip();
        if (first.startsWith("#")) {
            title = first.replaceFirst("^#+\\s*", "");
            body = lines.length > 1 ? lines[1].strip() : "";
        }
        slides.add(slide(title, body, DEFAULT_COLOR));
    }

    public static byte[] toPptx(String json) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            show.setPageSize(new Dimension(960, 540));
            for (JsonNode node : slides(json)) {
                XSLFSlide slide = show.createSlide();
                XSLFTextBox title = slide.createTextBox();
                title.setAnchor(new Rectangle(40, 24, 880, 70));
                XSLFTextRun titleRun = title.addNewTextParagraph().addNewTextRun();
                titleRun.setText(node.path("title").asText(""));
                titleRun.setFontSize(32.0);
                titleRun.setBold(true);

                XSLFTextBox body = slide.createTextBox();
                body.setAnchor(new Rectangle(40, 110, 880, 400));
                for (String line : MarkdownUtil.plainText(node.path("body").asText("")).split("\n")) {
                    if (line.isBlank()) {
                        continue;
                    }
                    XSLFTextParagraph paragraph = body.addNewTextParagraph();
                    paragraph.setBullet(true);
                    paragraph.setTextAlign(TextParagraph.TextAlign.LEFT);
                    XSLFTextRun run = paragraph.addNewTextRun();
                    run.setText(line.strip());
                    run.setFontSize(20.0);
                }
            }
            show.write(out);
            return out.toByteArray();
        }
    }

    /** First text shape of each slide becomes the title; the others become the body, one paragraph per line. */
    public static String fromPptx(byte[] bytes) throws IOException {
        ArrayNode slides = JSON.createArrayNode();
        try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            for (XSLFSlide slide : show.getSlides()) {
                String title = "";
                StringBuilder body = new StringBuilder();
                boolean titleTaken = false;
                for (XSLFShape shape : slide.getShapes()) {
                    if (!(shape instanceof XSLFTextShape textShape)) {
                        continue;
                    }
                    String text = textShape.getText() == null ? "" : textShape.getText().strip();
                    if (text.isEmpty()) {
                        continue;
                    }
                    if (!titleTaken) {
                        title = text.replace('\n', ' ');
                        titleTaken = true;
                    } else {
                        for (String line : text.split("\\R")) {
                            if (!line.isBlank()) {
                                body.append("- ").append(line.strip()).append('\n');
                            }
                        }
                    }
                }
                slides.add(slide(title, body.toString().strip(), DEFAULT_COLOR));
            }
        }
        if (slides.isEmpty()) {
            slides.add(slide("", "", DEFAULT_COLOR));
        }
        ObjectNode root = JSON.createObjectNode();
        root.put("version", 1);
        root.set("slides", slides);
        return root.toString();
    }

    private static Iterable<JsonNode> slides(String json) {
        try {
            JsonNode tree = JSON.readTree(json == null || json.isBlank() ? "{}" : json);
            return tree.path("slides");
        } catch (IOException e) {
            return JSON.createArrayNode();
        }
    }
}
