package com.sophia.ai.application.service;

import com.sophia.ai.domain.dto.response.Results.DiagramEdge;
import com.sophia.ai.domain.dto.response.Results.DiagramNode;
import com.sophia.ai.domain.dto.response.Results.DiagramResult;
import com.sophia.ai.domain.dto.response.Results.TableResult;
import com.sophia.ai.domain.dto.response.Results.TableRow;
import com.sophia.ai.domain.dto.response.Results.TagsResult;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.domain.model.Enums.DiagramKind;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Bounds and checks what the conversion tools return, like the other sanitizers: usable parts stay, the rest goes. */
@Component
public class ConvertSanitizer {

    static final int MAX_COLUMNS = 40;
    static final int MAX_ROWS = 500;
    static final int MAX_NODES = 60;
    static final int MAX_TAGS = 8;

    private static final Set<String> SHAPES = Set.of("START", "END", "PROCESS", "DECISION", "EVENT");
    private static final Pattern NOT_TAG_CHAR = Pattern.compile("[^\\p{L}\\p{N}_-]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

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

    // ---- table ------------------------------------------------------------------------------------------------

    public TableResult table(TableResult raw, String fallbackTitle) {
        if (raw == null || raw.headers() == null || raw.rows() == null) {
            throw unusable();
        }
        List<String> headers = new ArrayList<>();
        for (String header : raw.headers()) {
            headers.add(clip(header, 120));
            if (headers.size() == MAX_COLUMNS) {
                break;
            }
        }
        if (headers.isEmpty() || headers.stream().allMatch(String::isEmpty)) {
            throw unusable();
        }
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).isEmpty()) {
                headers.set(i, "Column " + (i + 1));
            }
        }
        List<TableRow> rows = new ArrayList<>();
        for (TableRow row : raw.rows()) {
            if (row == null || row.cells() == null) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                cells.add(i < row.cells().size() ? clip(row.cells().get(i), 500) : "");
            }
            if (cells.stream().allMatch(String::isEmpty)) {
                continue;
            }
            rows.add(new TableRow(cells));
            if (rows.size() == MAX_ROWS) {
                break;
            }
        }
        if (rows.isEmpty()) {
            throw unusable();
        }
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        return new TableResult(title, headers, rows);
    }

    // ---- diagram ----------------------------------------------------------------------------------------------

    public DiagramResult diagram(DiagramResult raw, DiagramKind kind, String fallbackTitle) {
        if (raw == null || raw.nodes() == null) {
            throw unusable();
        }
        Map<String, DiagramNode> nodes = new LinkedHashMap<>();
        for (DiagramNode node : raw.nodes()) {
            String id = clip(node.id(), 40);
            String label = clip(node.label(), 120);
            if (id.isEmpty() || label.isEmpty() || nodes.containsKey(id)) {
                continue;
            }
            String shape = node.shape() != null && SHAPES.contains(node.shape()) ? node.shape() : "PROCESS";
            // A kind has the shapes it has: a timeline is events, a cycle is stages.
            if (kind == DiagramKind.TIMELINE) {
                shape = "EVENT";
            } else if (kind == DiagramKind.CYCLE) {
                shape = "PROCESS";
            }
            nodes.put(id, new DiagramNode(id, label, shape, clip(node.detail(), 120)));
            if (nodes.size() == MAX_NODES) {
                break;
            }
        }
        if (nodes.size() < 2) {
            throw unusable();
        }
        List<DiagramEdge> edges = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (raw.edges() != null) {
            for (DiagramEdge edge : raw.edges()) {
                String from = clip(edge.from(), 40);
                String to = clip(edge.to(), 40);
                if (!nodes.containsKey(from) || !nodes.containsKey(to) || from.equals(to) || !seen.add(from + ">" + to)) {
                    continue;
                }
                edges.add(new DiagramEdge(from, to, clip(edge.label(), 60)));
            }
        }
        if (kind != DiagramKind.FLOWCHART) {
            // These two are chains by definition, so the order of the nodes is the truth and the edges are rebuilt from it.
            edges = chain(new ArrayList<>(nodes.keySet()), kind == DiagramKind.CYCLE);
        } else if (edges.isEmpty()) {
            edges = chain(new ArrayList<>(nodes.keySet()), false);
        }
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        return new DiagramResult(title, kind.name(), new ArrayList<>(nodes.values()), edges);
    }

    private static List<DiagramEdge> chain(List<String> ids, boolean closed) {
        List<DiagramEdge> edges = new ArrayList<>();
        for (int i = 0; i + 1 < ids.size(); i++) {
            edges.add(new DiagramEdge(ids.get(i), ids.get(i + 1), ""));
        }
        if (closed && ids.size() > 2) {
            edges.add(new DiagramEdge(ids.get(ids.size() - 1), ids.get(0), ""));
        }
        return edges;
    }

    // ---- tags -------------------------------------------------------------------------------------------------

    public TagsResult tags(TagsResult raw, String fallbackTitle) {
        if (raw == null) {
            throw unusable();
        }
        Set<String> tags = new LinkedHashSet<>();
        if (raw.tags() != null) {
            for (String tag : raw.tags()) {
                String clean = tag(tag);
                if (!clean.isEmpty()) {
                    tags.add(clean);
                }
                if (tags.size() == MAX_TAGS) {
                    break;
                }
            }
        }
        if (tags.isEmpty()) {
            throw unusable();
        }
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        return new TagsResult(title, clip(raw.summary(), 400), new ArrayList<>(tags));
    }

    /** A tag as the editor writes it: lower case, no leading #, spaces turned into hyphens, only letters, digits, - and _. */
    static String tag(String value) {
        if (value == null) {
            return "";
        }
        String text = value.strip().replaceFirst("^#+", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
        text = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        text = NOT_TAG_CHAR.matcher(text).replaceAll("").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return text.length() > 40 ? text.substring(0, 40) : text;
    }
}
