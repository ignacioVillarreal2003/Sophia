package com.sophia.ai.application.service;

import com.sophia.ai.domain.dto.request.GenerationRequests.GradeItem;
import com.sophia.ai.domain.dto.response.Results.DebateArgument;
import com.sophia.ai.domain.dto.response.Results.DebateResult;
import com.sophia.ai.domain.dto.response.Results.DebateSide;
import com.sophia.ai.domain.dto.response.Results.DeckCard;
import com.sophia.ai.domain.dto.response.Results.DeckResult;
import com.sophia.ai.domain.dto.response.Results.Gap;
import com.sophia.ai.domain.dto.response.Results.GapsResult;
import com.sophia.ai.domain.dto.response.Results.GradeResult;
import com.sophia.ai.domain.dto.response.Results.GradedAnswer;
import com.sophia.ai.domain.dto.response.Results.MapLink;
import com.sophia.ai.domain.dto.response.Results.MapNode;
import com.sophia.ai.domain.dto.response.Results.MindMapResult;
import com.sophia.ai.domain.dto.response.Results.NoteError;
import com.sophia.ai.domain.dto.response.Results.PerspectiveView;
import com.sophia.ai.domain.dto.response.Results.PerspectivesResult;
import com.sophia.ai.domain.dto.response.Results.QuizQuestion;
import com.sophia.ai.domain.dto.response.Results.QuizResult;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.domain.model.Enums.MapKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SanitizersTest {

    private final ResultSanitizer results = new ResultSanitizer();
    private final AnalysisSanitizer analysis = new AnalysisSanitizer();

    private static void assertUnusable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.GENERATION_FAILED));
    }

    // ---- existing features ------------------------------------------------------------------------------------

    @Test
    void deckDropsEmptyCardsAndUnknownColors() {
        DeckResult deck = results.deck(new DeckResult(" ", List.of(new DeckCard("A", "b", "neon"), new DeckCard("", " ", "blue"),
                new DeckCard("C", "d", "blue"))), "Fallback");
        assertThat(deck.title()).isEqualTo("Fallback");
        assertThat(deck.slides()).extracting(DeckCard::title).containsExactly("A", "C");
        assertThat(deck.slides().get(0).color()).isEqualTo("slate");
    }

    @Test
    void deckWithNothingUsableIsAnError() {
        assertUnusable(() -> results.deck(new DeckResult("t", List.of(new DeckCard("", "", "blue"))), "x"));
    }

    @Test
    void mindMapBreaksCyclesAndReparentsOrphans() {
        MindMapResult map = results.mindMap(new MindMapResult("t", "MINDMAP", List.of(
                new MapNode("r", "Root", ""), new MapNode("a", "A", "b"), new MapNode("b", "B", "a"),
                new MapNode("c", "C", "ghost")), List.of()), MapKind.MINDMAP, "x");
        assertThat(map.nodes()).filteredOn(n -> n.parent().isEmpty()).hasSize(1);
        assertThat(map.nodes()).filteredOn(n -> n.id().equals("c")).first().extracting(MapNode::parent).isEqualTo("r");
    }

    @Test
    void conceptMapKeepsOnlyLinksBetweenRealNodes() {
        MindMapResult map = results.mindMap(new MindMapResult("t", "CONCEPT_MAP",
                List.of(new MapNode("a", "A", ""), new MapNode("b", "B", "")),
                List.of(new MapLink("a", "b", "causes"), new MapLink("a", "zzz", "x"), new MapLink("a", "a", "self"),
                        new MapLink("a", "b", "dup"))), MapKind.CONCEPT_MAP, "x");
        assertThat(map.links()).hasSize(1);
    }

    @Test
    void quizKeepsOnlyQuestionsWithAValidAnswer() {
        QuizResult quiz = results.quiz(new QuizResult("Q", List.of(
                new QuizQuestion("MULTIPLE_CHOICE", "ok?", List.of("a", "b", "c", "d"), 2, "", "e", 9),
                new QuizQuestion("MULTIPLE_CHOICE", "bad index", List.of("a", "b"), 5, "", "", 0),
                new QuizQuestion("MULTIPLE_CHOICE", "dup options", List.of("a", "a"), 0, "", "", 0),
                new QuizQuestion("TRUE_FALSE", "tf", List.of("x"), 1, "", "", 1),
                new QuizQuestion("SHORT_ANSWER", "open", List.of(), 0, "model", "", 0),
                new QuizQuestion("SHORT_ANSWER", "no answer", List.of(), -1, "", "", 0),
                new QuizQuestion("ESSAY", "unknown type", List.of(), 0, "x", "", 0))), 2, "x");
        assertThat(quiz.questions()).extracting(QuizQuestion::question).containsExactly("ok?", "tf", "open");
        QuizQuestion mc = quiz.questions().get(0);
        assertThat(mc.answer()).isEqualTo("c");
        assertThat(mc.source()).isZero(); // 9 is not one of the 2 documents
        assertThat(quiz.questions().get(1).options()).isEmpty();
        assertThat(quiz.questions().get(1).answer()).isEqualTo("false");
        assertThat(quiz.questions().get(2).correctIndex()).isEqualTo(-1);
    }

    @Test
    void gradingSnapsScoresAndFillsMissingItems() {
        List<GradeItem> items = List.of(new GradeItem("q", "r", "a"), new GradeItem("q", "r", "b"), new GradeItem("q", "r", ""));
        GradeResult graded = results.grade(new GradeResult(List.of(new GradedAnswer(0, 0.8, "bien"),
                new GradedAnswer(2, 1, "no debería puntuar"))), items);
        assertThat(graded.results()).extracting(GradedAnswer::score).containsExactly(1.0, 0.0, 0.0);
    }

    // ---- perspectives -----------------------------------------------------------------------------------------

    @Test
    void perspectivesAreRenderedAsADocument() {
        PerspectivesResult r = analysis.perspectives(new PerspectivesResult("Internet", "Marco", List.of(
                new PerspectiveView("Histórica", "Resumen", List.of("uno", " ", "dos"), "¿Y si?"),
                new PerspectiveView("", "sin nombre", List.of(), ""),
                new PerspectiveView("Crítica", "Otro", List.of(), "")), "Juntas", ""), "x", "es");
        assertThat(r.perspectives()).extracting(PerspectiveView::lens).containsExactly("Histórica", "Crítica");
        assertThat(r.perspectives().get(0).keyPoints()).containsExactly("uno", "dos");
        assertThat(r.markdown()).startsWith("# Internet").contains("## Histórica", "> [!tip] Para pensar", "## Integración");
    }

    @Test
    void perspectivesDocumentIsLocalised() {
        PerspectivesResult r = analysis.perspectives(new PerspectivesResult("T", "", List.of(
                new PerspectiveView("Historical", "S", List.of(), "Q?")), "Together", ""), "x", "en");
        assertThat(r.markdown()).contains("Think about it", "Putting it together");
    }

    @Test
    void perspectivesWithoutUsableViewsFail() {
        assertUnusable(() -> analysis.perspectives(new PerspectivesResult("t", "", List.of(), "", ""), "x", "es"));
        assertUnusable(() -> analysis.perspectives(null, "x", "es"));
    }

    // ---- debate -----------------------------------------------------------------------------------------------

    private static DebateSide side(String claim) {
        return new DebateSide("tesis", List.of(new DebateArgument(claim, "porque"), new DebateArgument("", "vacío")), "respuesta");
    }

    @Test
    void debateIsRenderedWithBothSides() {
        DebateResult r = analysis.debate(new DebateResult("", "La moción", side("a favor"), side("en contra"), "balance",
                List.of("¿qué falta?"), ""), "La moción", "es");
        assertThat(r.pro().arguments()).hasSize(1);
        assertThat(r.markdown()).contains("## A favor", "## En contra", "1. **a favor**", "## Balance", "## Preguntas abiertas");
        assertThat(r.title()).isEqualTo("La moción");
    }

    @Test
    void debateWithASideWithoutArgumentsFails() {
        assertUnusable(() -> analysis.debate(new DebateResult("t", "m", side("a"),
                new DebateSide("t", List.of(), ""), "", List.of(), ""), "m", "es"));
    }

    // ---- gaps -------------------------------------------------------------------------------------------------

    @Test
    void gapsAreOrderedBySeverityAndBounded() {
        GapsResult r = analysis.gaps(new GapsResult("Revisión", 250, "ok", List.of("bien"), List.of(
                new Gap("menor", "", "", "LOW"), new Gap("grave", "porque", "agregar", "HIGH"),
                new Gap("raro", "", "", "WHATEVER"), new Gap("", "sin tema", "", "HIGH")),
                List.of(new NoteError("cita", "mal", "bien"), new NoteError("x", "", "")), List.of("paso"), ""), "x", "es");
        assertThat(r.coverage()).isEqualTo(100);
        assertThat(r.gaps()).extracting(Gap::topic).containsExactly("grave", "raro", "menor");
        assertThat(r.gaps().get(1).severity()).isEqualTo("MEDIUM");
        assertThat(r.errors()).hasSize(1);
        assertThat(r.markdown()).contains("**Cobertura:** 100%", "## Lo que falta", "## Errores a corregir", "- [ ] paso");
    }

    @Test
    void noGapsAndNoErrorsIsAValidReviewWhenSomethingIsCovered() {
        GapsResult r = analysis.gaps(new GapsResult("t", -5, "", List.of("todo"), List.of(), List.of(), List.of(), ""), "x", "en");
        assertThat(r.coverage()).isZero();
        assertThat(r.markdown()).contains("Already covered").doesNotContain("What is missing");
    }

    @Test
    void anEmptyReviewFails() {
        assertUnusable(() -> analysis.gaps(new GapsResult("t", 50, "", List.of(), List.of(), List.of(), List.of(), ""), "x", "es"));
    }
}
