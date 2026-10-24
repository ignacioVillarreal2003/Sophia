package com.sophia.ai.application.prompt;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON Schemas for the structured features. They stick to what structured outputs support: closed objects, every
 * property required, no numeric bounds and no recursion (a mind map is a flat list of nodes with parent ids).
 * Bounds such as "at most 40 cards" are enforced in the prompt and again by the sanitizer.
 */
public final class OutputSchemas {

    public static final List<String> CARD_COLORS = List.of("slate", "blue", "green", "amber", "rose", "violet");

    private OutputSchemas() {
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> integer() {
        return Map.of("type", "integer");
    }

    private static Map<String, Object> number() {
        return Map.of("type", "number");
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    /** A closed object whose properties are all required, in the order given. */
    @SafeVarargs
    private static Map<String, Object> object(Map.Entry<String, Map<String, Object>>... properties) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> property : properties) {
            props.put(property.getKey(), property.getValue());
        }
        return Map.of("type", "object", "properties", props, "required", List.copyOf(props.keySet()),
                "additionalProperties", false);
    }

    private static Map.Entry<String, Map<String, Object>> p(String name, Map<String, Object> schema) {
        return Map.entry(name, schema);
    }

    public static Map<String, Object> deck() {
        return object(
                p("title", string()),
                p("slides", array(object(
                        p("title", string()),
                        p("body", string()),
                        p("color", enumOf(CARD_COLORS))))));
    }

    public static Map<String, Object> mindMap() {
        return object(
                p("title", string()),
                p("kind", enumOf(List.of("MINDMAP", "CONCEPT_MAP"))),
                p("nodes", array(object(
                        p("id", string()),
                        p("label", string()),
                        p("parent", string())))),
                p("links", array(object(
                        p("from", string()),
                        p("to", string()),
                        p("label", string())))));
    }

    public static Map<String, Object> quiz() {
        return object(
                p("title", string()),
                p("questions", array(object(
                        p("type", enumOf(List.of("MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER"))),
                        p("question", string()),
                        p("options", array(string())),
                        p("correctIndex", integer()),
                        p("answer", string()),
                        p("explanation", string()),
                        p("source", integer())))));
    }

    public static Map<String, Object> grade() {
        return object(
                p("results", array(object(
                        p("index", integer()),
                        p("score", number()),
                        p("feedback", string())))));
    }

    public static Map<String, Object> perspectives() {
        return object(
                p("title", string()),
                p("overview", string()),
                p("perspectives", array(object(
                        p("lens", string()),
                        p("summary", string()),
                        p("keyPoints", array(string())),
                        p("question", string())))),
                p("synthesis", string()));
    }

    private static Map<String, Object> debateSide() {
        return object(
                p("thesis", string()),
                p("arguments", array(object(
                        p("claim", string()),
                        p("support", string())))),
                p("rebuttal", string()));
    }

    public static Map<String, Object> debate() {
        return object(
                p("title", string()),
                p("motion", string()),
                p("pro", debateSide()),
                p("con", debateSide()),
                p("verdict", string()),
                p("openQuestions", array(string())));
    }

    public static Map<String, Object> gaps() {
        return object(
                p("title", string()),
                p("coverage", integer()),
                p("verdict", string()),
                p("covered", array(string())),
                p("gaps", array(object(
                        p("topic", string()),
                        p("why", string()),
                        p("suggestion", string()),
                        p("severity", enumOf(List.of("LOW", "MEDIUM", "HIGH")))))),
                p("errors", array(object(
                        p("quote", string()),
                        p("issue", string()),
                        p("fix", string())))),
                p("nextSteps", array(string())));
    }

    public static Map<String, Object> table() {
        return object(
                p("title", string()),
                p("headers", array(string())),
                p("rows", array(object(p("cells", array(string()))))));
    }

    public static Map<String, Object> diagram() {
        return object(
                p("title", string()),
                p("kind", enumOf(List.of("FLOWCHART", "TIMELINE", "CYCLE"))),
                p("nodes", array(object(
                        p("id", string()),
                        p("label", string()),
                        p("shape", enumOf(List.of("START", "END", "PROCESS", "DECISION", "EVENT"))),
                        p("detail", string())))),
                p("edges", array(object(
                        p("from", string()),
                        p("to", string()),
                        p("label", string())))));
    }

    public static Map<String, Object> tags() {
        return object(
                p("title", string()),
                p("summary", string()),
                p("tags", array(string())));
    }

    public static Map<String, Object> studyPlan() {
        return object(
                p("title", string()),
                p("summary", string()),
                p("days", array(object(
                        p("date", string()),
                        p("focus", string()),
                        p("tasks", array(object(
                                p("text", string()),
                                p("minutes", integer()))))))),
                p("tips", array(string())));
    }
}
