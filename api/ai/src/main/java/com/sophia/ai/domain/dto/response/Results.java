package com.sophia.ai.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * What the model returns for the structured features. The same records are what the API answers with, after
 * {@code ResultSanitizer} has trimmed and bounded them, so the client never sees a malformed card or question.
 */
public final class Results {

    private Results() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeckCard(String title, String body, String color) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeckResult(String title, List<DeckCard> slides) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MapNode(String id, String label, String parent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MapLink(String from, String to, String label) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MindMapResult(String title, String kind, List<MapNode> nodes, List<MapLink> links) {
    }

    /**
     * @param correctIndex for MULTIPLE_CHOICE the index of the right option; for TRUE_FALSE 0 when the statement is
     *                     true and 1 when it is false; -1 for SHORT_ANSWER
     * @param answer       the right option's text, or the model answer of a short question
     * @param source       1-based number of the source document the question comes from, when there are several
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuizQuestion(String type, String question, List<String> options, int correctIndex, String answer,
                               String explanation, Integer source) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuizResult(String title, List<QuizQuestion> questions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GradedAnswer(int index, double score, String feedback) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GradeResult(List<GradedAnswer> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanTask(String text, int minutes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlanDay(String date, String focus, List<PlanTask> tasks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerspectiveView(String lens, String summary, List<String> keyPoints, String question) {
    }

    /** @param markdown the whole analysis rendered as a document */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PerspectivesResult(String title, String overview, List<PerspectiveView> perspectives, String synthesis,
                                     String markdown) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DebateArgument(String claim, String support) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DebateSide(String thesis, List<DebateArgument> arguments, String rebuttal) {
    }

    /**
     * @param verdict       an even-handed reading of where the strongest points are, not a winner picked for effect
     * @param openQuestions what would have to be settled to decide
     * @param markdown      the debate rendered as a document
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DebateResult(String title, String motion, DebateSide pro, DebateSide con, String verdict,
                               List<String> openQuestions, String markdown) {
    }

    /** @param severity LOW, MEDIUM or HIGH: how much the missing piece would hurt someone studying from the notes */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Gap(String topic, String why, String suggestion, String severity) {
    }

    /** @param quote the passage of the notes that is wrong or misleading, copied as written */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NoteError(String quote, String issue, String fix) {
    }

    /**
     * @param coverage percentage (0-100) of what the subject needs that the notes already cover
     * @param markdown the review rendered as a document
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GapsResult(String title, int coverage, String verdict, List<String> covered, List<Gap> gaps,
                             List<NoteError> errors, List<String> nextSteps, String markdown) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableRow(List<String> cells) {
    }

    /** A rectangular table: every row has exactly as many cells as there are headers. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TableResult(String title, List<String> headers, List<TableRow> rows) {
    }

    /** @param shape START, END, PROCESS, DECISION or EVENT; @param detail a date or a short note (empty when none) */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiagramNode(String id, String label, String shape, String detail) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiagramEdge(String from, String to, String label) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiagramResult(String title, String kind, List<DiagramNode> nodes, List<DiagramEdge> edges) {
    }

    /** @param tags lower-case words without spaces, usable as {@code #tag} in a note */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TagsResult(String title, String summary, List<String> tags) {
    }

    /** @param markdown the plan rendered as a checklist, ready to become a document */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StudyPlanResult(String title, String summary, List<PlanDay> days, List<String> tips, String markdown) {
    }
}
