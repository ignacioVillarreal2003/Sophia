package com.sophia.ai.infrastructure.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.ai.domain.dto.response.Results.DebateArgument;
import com.sophia.ai.domain.dto.response.Results.DebateResult;
import com.sophia.ai.domain.dto.response.Results.DebateSide;
import com.sophia.ai.domain.dto.response.Results.DeckCard;
import com.sophia.ai.domain.dto.response.Results.DiagramEdge;
import com.sophia.ai.domain.dto.response.Results.DiagramNode;
import com.sophia.ai.domain.dto.response.Results.DiagramResult;
import com.sophia.ai.domain.dto.response.Results.TableResult;
import com.sophia.ai.domain.dto.response.Results.TableRow;
import com.sophia.ai.domain.dto.response.Results.TagsResult;
import com.sophia.ai.domain.dto.response.Results.Gap;
import com.sophia.ai.domain.dto.response.Results.GapsResult;
import com.sophia.ai.domain.dto.response.Results.NoteError;
import com.sophia.ai.domain.dto.response.Results.PerspectiveView;
import com.sophia.ai.domain.dto.response.Results.PerspectivesResult;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canned answers with the right shape, for working on the screens and for tests without spending anything. Selected
 * with {@code sophia.ai.provider=mock}. Everything it writes says it is a demo.
 */
public class MockGateway implements ModelGateway {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern DATES = Pattern.compile("Today is (\\d{4}-\\d{2}-\\d{2})\\. The exam is on (\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern QUESTIONS = Pattern.compile("with (\\d+) questions");
    private static final Pattern ITEM = Pattern.compile("<item index=\"(\\d+)\"");

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public String model() {
        return "mock";
    }

    @Override
    public Completion complete(ModelRequest request) {
        String text = request.jsonSchema() != null ? json(request) : markdown(request);
        return new Completion(text, usage(request, text), model(), false, false, false);
    }

    @Override
    public Completion stream(ModelRequest request, TextSink sink) {
        String text = markdown(request);
        boolean stopped = false;
        for (String piece : text.split("(?<=\\s)")) {
            if (!sink.accept(piece)) {
                stopped = true;
                break;
            }
            try {
                Thread.sleep(12);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopped = true;
                break;
            }
        }
        return new Completion(text, usage(request, text), model(), false, false, stopped);
    }

    private static TokenUsage usage(ModelRequest request, String text) {
        return new TokenUsage(request.user().length() / 4, text.length() / 4, 0, 0);
    }

    private static String markdown(ModelRequest request) {
        return switch (request.feature()) {
            case NOTES -> "# Apuntes de ejemplo (demo)\n\n## Idea principal\n\nEste texto lo genera el modo de prueba, "
                    + "sin llamar a ningún modelo.\n\n- Punto uno\n- Punto dos\n\n> [!tip] Consejo\n> Activá la clave "
                    + "real para obtener contenido verdadero.\n";
            case REVIEW_SUMMARY -> "# Resumen de repaso (demo)\n\n## Ideas clave\n\n- Idea uno\n- Idea dos\n\n"
                    + "## Autoevaluación\n\n1. ¿Qué es lo más importante?\n";
            case CONTINUE -> "Esta es una continuación de ejemplo (demo) que sigue el tono del texto anterior.";
            case CHAT -> request.system().contains("<document ")
                    ? "Según tus documentos, esta es una respuesta de ejemplo (demo) [1]. Segundo punto [1]."
                    : "Esta es una respuesta de ejemplo (demo), sin documentos.";
            case OCR -> "# Texto escaneado (demo)\n\nEsta transcripción es de ejemplo.\n\n"
                    + "| Tema | Fecha |\n| --- | --- |\n| Mitosis | 12/03 |\n";
            case LISTEN -> "Este es un texto de ejemplo para escuchar. Primero, la idea principal. Después, un ejemplo sencillo.";
            case SOCRATIC -> "Antes de seguir, ¿qué crees que pasaría si cambiamos esa condición? (demo)";
            case DEBATE_CHAT -> "Respeto tu punto, pero hay un argumento más fuerte en contra: el costo de oportunidad. "
                    + "¿Cómo lo respondés? (demo)";
            default -> "Texto reescrito de ejemplo (demo).";
        };
    }

    private static String json(ModelRequest request) {
        try {
            return JSON.writeValueAsString(switch (request.feature()) {
                case DECK, FLASHCARDS -> deck(request.feature().name().equals("FLASHCARDS"));
                case MINDMAP -> mindMap(request.user().contains("kind: CONCEPT_MAP"));
                case QUIZ, EXAM -> quiz(count(request.user()));
                case GRADE -> grade(request.user());
                case STUDY_PLAN -> plan(request.user());
                case PERSPECTIVES -> perspectives();
                case DEBATE -> debate();
                case GAPS -> gaps();
                case TABLE -> table();
                case DIAGRAM -> diagram(request.user());
                case TAGS -> new TagsResult("Título de ejemplo (demo)", "Resumen de ejemplo.", List.of("ejemplo", "Demo Tag", "biologia-celular"));
                default -> throw new IllegalStateException("No mock for " + request.feature());
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DeckResult deck(boolean flash) {
        String[] colors = {"blue", "green", "amber", "blue", "green"};
        List<DeckCard> cards = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cards.add(flash
                    ? new DeckCard("¿Pregunta de ejemplo número " + (i + 1) + "?", "Respuesta de ejemplo (demo).", colors[i])
                    : new DeckCard("Concepto " + (i + 1), "- Primera idea del concepto\n- **Término clave** de ejemplo", colors[i]));
        }
        return new DeckResult(flash ? "Flashcards de ejemplo (demo)" : "Tablero de ejemplo (demo)", cards);
    }

    private static MindMapResult mindMap(boolean concept) {
        if (concept) {
            return new MindMapResult("Mapa conceptual (demo)", "CONCEPT_MAP",
                    List.of(new MapNode("n1", "Concepto A", ""), new MapNode("n2", "Concepto B", ""),
                            new MapNode("n3", "Concepto C", "")),
                    List.of(new MapLink("n1", "n2", "se relaciona con"), new MapLink("n2", "n3", "produce")));
        }
        return new MindMapResult("Mapa mental (demo)", "MINDMAP",
                List.of(new MapNode("n1", "Tema central", ""), new MapNode("n2", "Rama 1", "n1"),
                        new MapNode("n3", "Rama 2", "n1"), new MapNode("n4", "Rama 3", "n1"),
                        new MapNode("n5", "Detalle 1", "n2"), new MapNode("n6", "Detalle 2", "n3")),
                List.of());
    }

    private static int count(String prompt) {
        Matcher m = QUESTIONS.matcher(prompt);
        return m.find() ? Math.min(Integer.parseInt(m.group(1)), 8) : 5;
    }

    private static QuizResult quiz(int count) {
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(switch (i % 3) {
                case 0 -> new QuizQuestion("MULTIPLE_CHOICE", "Pregunta de opción múltiple " + (i + 1) + " (demo)",
                        List.of("Opción A", "Opción B", "Opción C", "Opción D"), 1, "Opción B",
                        "Explicación de ejemplo.", 0);
                case 1 -> new QuizQuestion("TRUE_FALSE", "Afirmación de ejemplo número " + (i + 1) + " (demo)",
                        List.of(), 0, "true", "Explicación de ejemplo.", 0);
                default -> new QuizQuestion("SHORT_ANSWER", "Pregunta abierta " + (i + 1) + " (demo)", List.of(), -1,
                        "Respuesta modelo de ejemplo.", "Explicación de ejemplo.", 0);
            });
        }
        return new QuizResult("Cuestionario de ejemplo (demo)", questions);
    }

    private static GradeResult grade(String prompt) {
        Matcher m = ITEM.matcher(prompt);
        List<GradedAnswer> results = new ArrayList<>();
        while (m.find()) {
            int index = Integer.parseInt(m.group(1));
            double score = new double[]{1, 0.5, 0}[index % 3];
            results.add(new GradedAnswer(index, score, "Comentario de ejemplo (demo)."));
        }
        return new GradeResult(results);
    }

    private static PerspectivesResult perspectives() {
        List<PerspectiveView> views = new ArrayList<>();
        for (String lens : new String[]{"Práctica", "Crítica", "Histórica"}) {
            views.add(new PerspectiveView(lens, "Resumen de ejemplo desde la mirada " + lens.toLowerCase() + " (demo).",
                    List.of("Punto uno de ejemplo", "Punto dos de ejemplo"), "¿Qué cambiaría si miramos desde otro lado?"));
        }
        return new PerspectivesResult("Perspectivas de ejemplo (demo)", "Marco general de ejemplo.", views,
                "Integración de ejemplo entre las miradas.", "");
    }

    private static DebateResult debate() {
        DebateSide pro = new DebateSide("Postura a favor de ejemplo.", List.of(
                new DebateArgument("Primer argumento a favor", "Razonamiento de ejemplo."),
                new DebateArgument("Segundo argumento a favor", "Razonamiento de ejemplo.")), "Respuesta a la otra postura.");
        DebateSide con = new DebateSide("Postura en contra de ejemplo.", List.of(
                new DebateArgument("Primer argumento en contra", "Razonamiento de ejemplo."),
                new DebateArgument("Segundo argumento en contra", "Razonamiento de ejemplo.")), "Respuesta a la otra postura.");
        return new DebateResult("Debate de ejemplo (demo)", "Moción de ejemplo", pro, con,
                "Balance de ejemplo: depende de los valores en juego.", List.of("¿Qué evidencia falta?"), "");
    }

    private static GapsResult gaps() {
        return new GapsResult("Revisión de ejemplo (demo)", 60, "Tus apuntes cubren lo básico pero faltan ejemplos.",
                List.of("Definiciones", "Ideas principales"),
                List.of(new Gap("Ejemplos resueltos", "Sin ejemplos cuesta aplicar la teoría.", "Agregá dos ejemplos.", "HIGH"),
                        new Gap("Casos límite", "Se preguntan seguido.", "Sumá una sección.", "LOW")),
                List.of(new NoteError("Texto del apunte", "Afirmación imprecisa de ejemplo.", "Versión corregida.")),
                List.of("Agregar ejemplos", "Repasar con flashcards"), "");
    }

    private static TableResult table() {
        return new TableResult("Tabla de ejemplo (demo)", List.of("Concepto", "Definición", "Año"), List.of(
                new TableRow(List.of("Mitosis", "División celular", "1882")),
                new TableRow(List.of("Meiosis", "División para gametos", "1876")),
                new TableRow(List.of("Apoptosis", "Muerte celular programada", ""))));
    }

    private static DiagramResult diagram(String prompt) {
        if (prompt.contains("kind: TIMELINE")) {
            return new DiagramResult("Línea de tiempo (demo)", "TIMELINE", List.of(
                    new DiagramNode("n1", "Primer hecho", "EVENT", "1789"), new DiagramNode("n2", "Segundo hecho", "EVENT", "1799"),
                    new DiagramNode("n3", "Tercer hecho", "EVENT", "1815")), List.of());
        }
        if (prompt.contains("kind: CYCLE")) {
            return new DiagramResult("Ciclo (demo)", "CYCLE", List.of(
                    new DiagramNode("n1", "Etapa uno", "PROCESS", ""), new DiagramNode("n2", "Etapa dos", "PROCESS", ""),
                    new DiagramNode("n3", "Etapa tres", "PROCESS", "")), List.of());
        }
        return new DiagramResult("Diagrama de flujo (demo)", "FLOWCHART", List.of(
                new DiagramNode("n1", "Inicio", "START", ""), new DiagramNode("n2", "Hacer algo", "PROCESS", ""),
                new DiagramNode("n3", "¿Funciona?", "DECISION", ""), new DiagramNode("n4", "Terminar", "END", ""),
                new DiagramNode("n5", "Corregir", "PROCESS", "")), List.of(
                new DiagramEdge("n1", "n2", ""), new DiagramEdge("n2", "n3", ""), new DiagramEdge("n3", "n4", "sí"),
                new DiagramEdge("n3", "n5", "no"), new DiagramEdge("n5", "n2", "")));
    }

    private static StudyPlanResult plan(String prompt) {
        Matcher m = DATES.matcher(prompt);
        LocalDate today = LocalDate.now();
        LocalDate exam = today.plusDays(7);
        if (m.find()) {
            today = LocalDate.parse(m.group(1));
            exam = LocalDate.parse(m.group(2));
        }
        List<PlanDay> days = new ArrayList<>();
        long span = Math.min(ChronoUnit.DAYS.between(today, exam), 40);
        for (int i = 0; i <= span; i++) {
            days.add(new PlanDay(today.plusDays(i).toString(), "Foco del día " + (i + 1),
                    List.of(new PlanTask("Leer y resumir el tema " + (i + 1), 30),
                            new PlanTask("Hacer 5 flashcards", 15))));
        }
        return new StudyPlanResult("Plan de estudio de ejemplo (demo)", "Un plan de ejemplo para probar la pantalla.",
                days, List.of("Dormí bien antes del examen.", "Repasá en voz alta."), "");
    }
}
