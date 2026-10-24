package com.sophia.ai.application.service;

import com.sophia.ai.application.prompt.OutputSchemas;
import com.sophia.ai.domain.dto.request.GenerationRequests.GradeItem;
import com.sophia.ai.domain.dto.response.Results.DeckCard;
import com.sophia.ai.domain.dto.response.Results.DeckResult;
import com.sophia.ai.domain.dto.response.Results.GradeResult;
import com.sophia.ai.domain.dto.response.Results.GradedAnswer;
import com.sophia.ai.domain.dto.response.Results.MapLink;
import com.sophia.ai.domain.dto.response.Results.MapNode;
import com.sophia.ai.domain.dto.response.Results.MindMapResult;
import com.sophia.ai.domain.dto.response.Results.PlanDay;
import com.sophia.ai.domain.dto.response.Results.PlanTask;
import com.sophia.ai.domain.dto.response.Results.QuizQuestion;
import com.sophia.ai.domain.dto.response.Results.QuizResult;
import com.sophia.ai.domain.dto.response.Results.StudyPlanResult;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.domain.model.Enums.MapKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The model is asked for JSON that follows a schema, but a schema cannot say "at most 40 cards" or "the answer must be
 * one of the options". Whatever reaches the client has been trimmed, bounded and checked here, and anything unusable is
 * dropped rather than shown. A result with nothing left is an error, not an empty success.
 */
@Component
public class ResultSanitizer {

    static final int MAX_CARDS = 40;
    static final int MAX_NODES = 80;
    static final int MAX_QUESTIONS = 30;

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        return text.length() <= max ? text : text.substring(0, max).stripTrailing();
    }

    private static ApiException unusable() {
        return new ApiException(ErrorCode.GENERATION_FAILED, HttpStatus.BAD_GATEWAY,
                "The result could not be used. Try again, or ask for less at once.");
    }

    // ---- deck -------------------------------------------------------------------------------------------------

    public DeckResult deck(DeckResult raw, String fallbackTitle) {
        List<DeckCard> cards = new ArrayList<>();
        if (raw != null && raw.slides() != null) {
            for (DeckCard card : raw.slides()) {
                String title = clip(card.title(), 160);
                String body = clip(card.body(), 1400);
                if (title.isEmpty() && body.isEmpty()) {
                    continue;
                }
                String color = card.color() != null && OutputSchemas.CARD_COLORS.contains(card.color()) ? card.color() : "slate";
                cards.add(new DeckCard(title, body, color));
                if (cards.size() == MAX_CARDS) {
                    break;
                }
            }
        }
        if (cards.isEmpty()) {
            throw unusable();
        }
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        return new DeckResult(title, cards);
    }

    // ---- maps -------------------------------------------------------------------------------------------------

    public MindMapResult mindMap(MindMapResult raw, MapKind kind, String fallbackTitle) {
        if (raw == null || raw.nodes() == null) {
            throw unusable();
        }
        Map<String, MapNode> nodes = new LinkedHashMap<>();
        for (MapNode node : raw.nodes()) {
            String id = clip(node.id(), 40);
            String label = clip(node.label(), 90);
            if (id.isEmpty() || label.isEmpty() || nodes.containsKey(id)) {
                continue;
            }
            nodes.put(id, new MapNode(id, label, clip(node.parent(), 40)));
            if (nodes.size() == MAX_NODES) {
                break;
            }
        }
        if (nodes.size() < 2) {
            throw unusable();
        }
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);

        if (kind == MapKind.CONCEPT_MAP) {
            List<MapNode> flat = nodes.values().stream().map(n -> new MapNode(n.id(), n.label(), "")).toList();
            List<MapLink> links = validLinks(raw.links(), nodes.keySet());
            if (links.isEmpty()) {
                throw unusable();
            }
            return new MindMapResult(title, kind.name(), flat, links);
        }
        return new MindMapResult(title, kind.name(), tree(nodes), List.of());
    }

    /** Exactly one root, every other node hanging from a real node, no cycles. */
    private List<MapNode> tree(Map<String, MapNode> nodes) {
        String root = nodes.values().stream().filter(n -> n.parent().isEmpty() || !nodes.containsKey(n.parent()))
                .map(MapNode::id).findFirst().orElse(nodes.keySet().iterator().next());
        List<MapNode> out = new ArrayList<>();
        for (MapNode node : nodes.values()) {
            String parent = node.parent();
            if (node.id().equals(root)) {
                parent = "";
            } else if (parent.isEmpty() || !nodes.containsKey(parent) || parent.equals(node.id()) || cyclic(nodes, node.id())) {
                parent = root;
            }
            out.add(new MapNode(node.id(), node.label(), parent));
        }
        return out;
    }

    private boolean cyclic(Map<String, MapNode> nodes, String start) {
        String current = start;
        for (int hops = 0; hops <= nodes.size(); hops++) {
            MapNode node = nodes.get(current);
            if (node == null || node.parent().isEmpty() || !nodes.containsKey(node.parent())) {
                return false;
            }
            current = node.parent();
            if (current.equals(start)) {
                return true;
            }
        }
        return true;
    }

    private List<MapLink> validLinks(List<MapLink> raw, Set<String> ids) {
        List<MapLink> links = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (raw == null) {
            return links;
        }
        for (MapLink link : raw) {
            String from = clip(link.from(), 40);
            String to = clip(link.to(), 40);
            if (!ids.contains(from) || !ids.contains(to) || from.equals(to) || !seen.add(from + ">" + to)) {
                continue;
            }
            links.add(new MapLink(from, to, clip(link.label(), 60)));
            if (links.size() == 120) {
                break;
            }
        }
        return links;
    }

    // ---- quiz -------------------------------------------------------------------------------------------------

    public QuizResult quiz(QuizResult raw, int sources, String fallbackTitle) {
        List<QuizQuestion> questions = new ArrayList<>();
        if (raw != null && raw.questions() != null) {
            for (QuizQuestion q : raw.questions()) {
                QuizQuestion clean = question(q, sources);
                if (clean != null) {
                    questions.add(clean);
                }
                if (questions.size() == MAX_QUESTIONS) {
                    break;
                }
            }
        }
        if (questions.isEmpty()) {
            throw unusable();
        }
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        return new QuizResult(title, questions);
    }

    private QuizQuestion question(QuizQuestion q, int sources) {
        String text = clip(q.question(), 600);
        String explanation = clip(q.explanation(), 700);
        if (text.isEmpty() || q.type() == null) {
            return null;
        }
        int source = q.source() == null || q.source() < 1 || q.source() > sources ? 0 : q.source();
        switch (q.type()) {
            case "MULTIPLE_CHOICE" -> {
                List<String> options = new ArrayList<>();
                if (q.options() != null) {
                    q.options().forEach(o -> options.add(clip(o, 300)));
                }
                options.removeIf(String::isEmpty);
                if (options.size() < 2 || options.size() > 6 || q.correctIndex() < 0 || q.correctIndex() >= options.size()
                        || options.stream().distinct().count() != options.size()) {
                    return null;
                }
                return new QuizQuestion(q.type(), text, options, q.correctIndex(), options.get(q.correctIndex()),
                        explanation, source);
            }
            case "TRUE_FALSE" -> {
                if (q.correctIndex() != 0 && q.correctIndex() != 1) {
                    return null;
                }
                return new QuizQuestion(q.type(), text, List.of(), q.correctIndex(),
                        q.correctIndex() == 0 ? "true" : "false", explanation, source);
            }
            case "SHORT_ANSWER" -> {
                String answer = clip(q.answer(), 500);
                if (answer.isEmpty()) {
                    return null;
                }
                return new QuizQuestion(q.type(), text, List.of(), -1, answer, explanation, source);
            }
            default -> {
                return null;
            }
        }
    }

    // ---- grading ----------------------------------------------------------------------------------------------

    /** One entry per item, in order; an item the model skipped comes back with score 0 and no feedback. */
    public GradeResult grade(GradeResult raw, List<GradeItem> items) {
        Map<Integer, GradedAnswer> byIndex = new HashMap<>();
        if (raw != null && raw.results() != null) {
            for (GradedAnswer r : raw.results()) {
                if (r.index() >= 0 && r.index() < items.size()) {
                    byIndex.putIfAbsent(r.index(), new GradedAnswer(r.index(), snap(r.score()), clip(r.feedback(), 600)));
                }
            }
        }
        List<GradedAnswer> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            boolean blank = items.get(i).answer() == null || items.get(i).answer().isBlank();
            GradedAnswer graded = byIndex.get(i);
            out.add(blank ? new GradedAnswer(i, 0, graded == null ? "" : graded.feedback())
                    : graded != null ? graded : new GradedAnswer(i, 0, ""));
        }
        return new GradeResult(out);
    }

    private static double snap(double score) {
        if (Double.isNaN(score)) {
            return 0;
        }
        return score >= 0.75 ? 1 : score >= 0.25 ? 0.5 : 0;
    }

    // ---- study plan -------------------------------------------------------------------------------------------

    public StudyPlanResult studyPlan(StudyPlanResult raw, LocalDate today, LocalDate exam, int capacity, String language,
                                     String fallbackTitle) {
        TreeMap<LocalDate, PlanDay> days = new TreeMap<>();
        if (raw != null && raw.days() != null) {
            for (PlanDay day : raw.days()) {
                LocalDate date = parse(day.date());
                if (date == null || date.isBefore(today) || date.isAfter(exam) || day.tasks() == null) {
                    continue;
                }
                List<PlanTask> tasks = new ArrayList<>();
                for (PlanTask task : day.tasks()) {
                    String text = clip(task.text(), 300);
                    if (!text.isEmpty()) {
                        tasks.add(new PlanTask(text, Math.max(5, Math.min(capacity, task.minutes()))));
                    }
                    if (tasks.size() == 6) {
                        break;
                    }
                }
                if (tasks.isEmpty()) {
                    continue;
                }
                PlanDay existing = days.get(date);
                if (existing != null) {
                    tasks.addAll(0, existing.tasks());
                }
                days.put(date, new PlanDay(date.toString(), clip(day.focus(), 120), tasks));
            }
        }
        if (days.isEmpty()) {
            throw unusable();
        }
        List<PlanDay> ordered = List.copyOf(days.values());
        List<String> tips = raw.tips() == null ? List.of()
                : raw.tips().stream().map(t -> clip(t, 300)).filter(t -> !t.isEmpty()).limit(6).toList();
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        String summary = clip(raw.summary(), 700);
        return new StudyPlanResult(title, summary, ordered, tips, markdown(title, summary, ordered, tips, language));
    }

    private static LocalDate parse(String text) {
        try {
            return text == null ? null : LocalDate.parse(text.strip());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The plan as a checklist, grouped by day: what becomes the document. */
    static String markdown(String title, String summary, List<PlanDay> days, List<String> tips, String language) {
        Locale locale = Locale.forLanguageTag(language == null || language.isBlank() ? "es" : language);
        StringBuilder md = new StringBuilder("# ").append(title).append("\n\n");
        if (!summary.isEmpty()) {
            md.append(summary).append("\n\n");
        }
        for (PlanDay day : days) {
            LocalDate date = LocalDate.parse(day.date());
            md.append("## ").append(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale)).append(' ')
                    .append(date.format(DateTimeFormatter.ofPattern("d/M"))).append(" - ").append(day.focus()).append("\n\n");
            for (PlanTask task : day.tasks()) {
                md.append("- [ ] ").append(task.text()).append(" (").append(task.minutes()).append(" min)\n");
            }
            md.append('\n');
        }
        if (!tips.isEmpty()) {
            md.append("## ").append(locale.getLanguage().equals("en") ? "Tips" : "Consejos").append("\n\n");
            tips.forEach(tip -> md.append("- ").append(tip).append('\n'));
        }
        return md.toString().stripTrailing() + "\n";
    }
}
