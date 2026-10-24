package com.sophia.ai.application.service;

import com.sophia.ai.domain.dto.response.Results.DebateArgument;
import com.sophia.ai.domain.dto.response.Results.DebateResult;
import com.sophia.ai.domain.dto.response.Results.DebateSide;
import com.sophia.ai.domain.dto.response.Results.Gap;
import com.sophia.ai.domain.dto.response.Results.GapsResult;
import com.sophia.ai.domain.dto.response.Results.NoteError;
import com.sophia.ai.domain.dto.response.Results.PerspectiveView;
import com.sophia.ai.domain.dto.response.Results.PerspectivesResult;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounds and renders the analytical features (perspectives, debate, gaps). Like {@link ResultSanitizer}, whatever
 * cannot be used is dropped, a result left with nothing is an error, and the Markdown the client turns into a
 * document is built here from the checked fields rather than taken from the model.
 */
@Component
public class AnalysisSanitizer {

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        return text.length() <= max ? text : text.substring(0, max).stripTrailing();
    }

    private static List<String> clipAll(List<String> values, int maxItems, int maxChars) {
        return values == null ? List.of()
                : values.stream().map(v -> clip(v, maxChars)).filter(v -> !v.isEmpty()).limit(maxItems).toList();
    }

    private static ApiException unusable() {
        return new ApiException(ErrorCode.GENERATION_FAILED, HttpStatus.BAD_GATEWAY,
                "The result could not be used. Try again, or ask for less at once.");
    }

    private static boolean english(String language) {
        return language != null && language.toLowerCase(Locale.ROOT).startsWith("en");
    }

    // ---- perspectives -----------------------------------------------------------------------------------------

    public PerspectivesResult perspectives(PerspectivesResult raw, String fallbackTitle, String language) {
        if (raw == null || raw.perspectives() == null) {
            throw unusable();
        }
        List<PerspectiveView> views = new ArrayList<>();
        for (PerspectiveView view : raw.perspectives()) {
            String lens = clip(view.lens(), 80);
            String summary = clip(view.summary(), 1200);
            if (lens.isEmpty() || summary.isEmpty()) {
                continue;
            }
            views.add(new PerspectiveView(lens, summary, clipAll(view.keyPoints(), 6, 300), clip(view.question(), 300)));
            if (views.size() == 9) {
                break;
            }
        }
        if (views.isEmpty()) {
            throw unusable();
        }
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        String overview = clip(raw.overview(), 900);
        String synthesis = clip(raw.synthesis(), 1500);

        boolean en = english(language);
        StringBuilder md = new StringBuilder("# ").append(title).append("\n\n");
        if (!overview.isEmpty()) {
            md.append(overview).append("\n\n");
        }
        for (PerspectiveView view : views) {
            md.append("## ").append(view.lens()).append("\n\n").append(view.summary()).append("\n\n");
            view.keyPoints().forEach(point -> md.append("- ").append(point).append('\n'));
            if (!view.keyPoints().isEmpty()) {
                md.append('\n');
            }
            if (!view.question().isEmpty()) {
                md.append("> [!tip] ").append(en ? "Think about it" : "Para pensar").append("\n> ")
                        .append(view.question()).append("\n\n");
            }
        }
        if (!synthesis.isEmpty()) {
            md.append("## ").append(en ? "Putting it together" : "Integración").append("\n\n").append(synthesis).append('\n');
        }
        return new PerspectivesResult(title, overview, views, synthesis, md.toString().stripTrailing() + "\n");
    }

    // ---- debate -----------------------------------------------------------------------------------------------

    public DebateResult debate(DebateResult raw, String motion, String language) {
        if (raw == null) {
            throw unusable();
        }
        DebateSide pro = side(raw.pro());
        DebateSide con = side(raw.con());
        String title = raw.title() == null || raw.title().isBlank() ? clip(motion, 120) : clip(raw.title(), 120);
        String verdict = clip(raw.verdict(), 1500);
        List<String> open = clipAll(raw.openQuestions(), 6, 300);
        String shownMotion = raw.motion() == null || raw.motion().isBlank() ? clip(motion, 500) : clip(raw.motion(), 500);

        boolean en = english(language);
        StringBuilder md = new StringBuilder("# ").append(title).append("\n\n> ").append(shownMotion).append("\n\n");
        render(md, en ? "In favour" : "A favor", pro, en);
        render(md, en ? "Against" : "En contra", con, en);
        if (!verdict.isEmpty()) {
            md.append("## ").append(en ? "Balance" : "Balance").append("\n\n").append(verdict).append("\n\n");
        }
        if (!open.isEmpty()) {
            md.append("## ").append(en ? "Open questions" : "Preguntas abiertas").append("\n\n");
            open.forEach(q -> md.append("- ").append(q).append('\n'));
        }
        return new DebateResult(title, shownMotion, pro, con, verdict, open, md.toString().stripTrailing() + "\n");
    }

    private static DebateSide side(DebateSide raw) {
        if (raw == null || raw.arguments() == null) {
            throw unusable();
        }
        List<DebateArgument> arguments = new ArrayList<>();
        for (DebateArgument argument : raw.arguments()) {
            String claim = clip(argument.claim(), 300);
            if (claim.isEmpty()) {
                continue;
            }
            arguments.add(new DebateArgument(claim, clip(argument.support(), 700)));
            if (arguments.size() == 6) {
                break;
            }
        }
        if (arguments.isEmpty()) {
            throw unusable();
        }
        return new DebateSide(clip(raw.thesis(), 400), arguments, clip(raw.rebuttal(), 900));
    }

    private static void render(StringBuilder md, String heading, DebateSide side, boolean en) {
        md.append("## ").append(heading).append("\n\n");
        if (!side.thesis().isEmpty()) {
            md.append("**").append(side.thesis()).append("**\n\n");
        }
        for (int i = 0; i < side.arguments().size(); i++) {
            DebateArgument argument = side.arguments().get(i);
            md.append(i + 1).append(". **").append(argument.claim()).append("**");
            if (!argument.support().isEmpty()) {
                md.append(" - ").append(argument.support());
            }
            md.append('\n');
        }
        md.append('\n');
        if (!side.rebuttal().isEmpty()) {
            md.append("> [!info] ").append(en ? "Reply to the other side" : "Respuesta a la otra postura").append("\n> ")
                    .append(side.rebuttal().replace("\n", "\n> ")).append("\n\n");
        }
    }

    // ---- gaps -------------------------------------------------------------------------------------------------

    public GapsResult gaps(GapsResult raw, String fallbackTitle, String language) {
        if (raw == null) {
            throw unusable();
        }
        List<Gap> gaps = new ArrayList<>();
        if (raw.gaps() != null) {
            for (Gap gap : raw.gaps()) {
                String topic = clip(gap.topic(), 160);
                if (topic.isEmpty()) {
                    continue;
                }
                String severity = gap.severity() != null && SEVERITIES.contains(gap.severity()) ? gap.severity() : "MEDIUM";
                gaps.add(new Gap(topic, clip(gap.why(), 500), clip(gap.suggestion(), 600), severity));
                if (gaps.size() == 20) {
                    break;
                }
            }
        }
        // High severity first; a stable sort keeps the model's order within a level.
        gaps = new ArrayList<>(gaps.stream().sorted((a, b) -> rank(b.severity()) - rank(a.severity())).toList());
        List<NoteError> errors = new ArrayList<>();
        if (raw.errors() != null) {
            for (NoteError error : raw.errors()) {
                String issue = clip(error.issue(), 500);
                if (issue.isEmpty()) {
                    continue;
                }
                errors.add(new NoteError(clip(error.quote(), 300), issue, clip(error.fix(), 500)));
                if (errors.size() == 15) {
                    break;
                }
            }
        }
        List<String> covered = clipAll(raw.covered(), 15, 200);
        List<String> next = clipAll(raw.nextSteps(), 8, 300);
        if (gaps.isEmpty() && errors.isEmpty() && covered.isEmpty()) {
            throw unusable();
        }
        int coverage = Math.max(0, Math.min(100, raw.coverage()));
        String title = raw.title() == null || raw.title().isBlank() ? fallbackTitle : clip(raw.title(), 120);
        String verdict = clip(raw.verdict(), 900);

        boolean en = english(language);
        StringBuilder md = new StringBuilder("# ").append(title).append("\n\n");
        md.append("**").append(en ? "Coverage" : "Cobertura").append(":** ").append(coverage).append("%\n\n");
        if (!verdict.isEmpty()) {
            md.append(verdict).append("\n\n");
        }
        if (!covered.isEmpty()) {
            md.append("## ").append(en ? "Already covered" : "Ya cubierto").append("\n\n");
            covered.forEach(c -> md.append("- ").append(c).append('\n'));
            md.append('\n');
        }
        if (!gaps.isEmpty()) {
            md.append("## ").append(en ? "What is missing" : "Lo que falta").append("\n\n");
            for (Gap gap : gaps) {
                md.append("- **").append(gap.topic()).append("** (").append(label(gap.severity(), en)).append(')');
                if (!gap.why().isEmpty()) {
                    md.append(": ").append(gap.why());
                }
                md.append('\n');
                if (!gap.suggestion().isEmpty()) {
                    md.append("  - ").append(en ? "Add: " : "Agregar: ").append(gap.suggestion()).append('\n');
                }
            }
            md.append('\n');
        }
        if (!errors.isEmpty()) {
            md.append("## ").append(en ? "Mistakes to fix" : "Errores a corregir").append("\n\n");
            for (NoteError error : errors) {
                if (!error.quote().isEmpty()) {
                    md.append("> ").append(error.quote().replace("\n", " ")).append("\n\n");
                }
                md.append(error.issue());
                if (!error.fix().isEmpty()) {
                    md.append(' ').append(en ? "Better: " : "Mejor: ").append(error.fix());
                }
                md.append("\n\n");
            }
        }
        if (!next.isEmpty()) {
            md.append("## ").append(en ? "Next steps" : "Próximos pasos").append("\n\n");
            next.forEach(n -> md.append("- [ ] ").append(n).append('\n'));
        }
        return new GapsResult(title, coverage, verdict, covered, gaps, errors, next, md.toString().stripTrailing() + "\n");
    }

    private static int rank(String severity) {
        return switch (severity) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private static String label(String severity, boolean en) {
        return switch (severity) {
            case "HIGH" -> en ? "important" : "importante";
            case "MEDIUM" -> en ? "useful" : "útil";
            default -> en ? "minor" : "menor";
        };
    }
}
