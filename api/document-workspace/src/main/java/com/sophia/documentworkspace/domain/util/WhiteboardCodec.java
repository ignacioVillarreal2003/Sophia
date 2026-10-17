package com.sophia.documentworkspace.domain.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/**
 * A whiteboard (concept map, diagram) is stored as JSON: {@code {"version":1,"elements":[...]}}. Elements are
 * notes, boxes, ellipses, text, arrows and freehand strokes; only the ones carrying text matter outside the editor.
 */
public final class WhiteboardCodec {

    private static final ObjectMapper JSON = new ObjectMapper();

    private WhiteboardCodec() {
    }

    public static String empty() {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", 1);
        root.putArray("elements");
        return root.toString();
    }

    public static boolean isValid(String json) {
        try {
            JsonNode tree = JSON.readTree(json);
            return tree != null && tree.isObject() && tree.path("elements").isArray();
        } catch (IOException e) {
            return false;
        }
    }

    /** Every text on the board, one per line, for search. */
    public static String searchText(String json) {
        StringBuilder text = new StringBuilder();
        try {
            for (JsonNode element : JSON.readTree(json == null || json.isBlank() ? "{}" : json).path("elements")) {
                String value = element.path("text").asText("").strip();
                if (!value.isEmpty()) {
                    text.append(value).append('\n');
                }
            }
        } catch (IOException e) {
            return "";
        }
        return text.toString();
    }

    /** A plain outline of the board's texts, used by the anonymous public view and PDF export. */
    public static String toMarkdown(String json) {
        StringBuilder md = new StringBuilder();
        for (String line : searchText(json).split("\n")) {
            if (!line.isBlank()) {
                md.append("- ").append(line.strip()).append('\n');
            }
        }
        return md.toString();
    }
}
