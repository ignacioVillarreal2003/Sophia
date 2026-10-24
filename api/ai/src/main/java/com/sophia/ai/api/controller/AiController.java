package com.sophia.ai.api.controller;

import com.sophia.ai.api.security.CurrentUser;
import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.application.service.GenerationService;
import com.sophia.ai.application.service.SseStreamer;
import com.sophia.ai.domain.dto.request.GenerationRequests.ContinueRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.DebateRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.DiagramRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.ListenRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.TableRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.TagsRequest;
import com.sophia.ai.domain.dto.response.Results.DiagramResult;
import com.sophia.ai.domain.dto.response.Results.TableResult;
import com.sophia.ai.domain.dto.response.Results.TagsResult;
import com.sophia.ai.domain.model.Enums.OcrMode;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import com.sophia.ai.domain.dto.request.GenerationRequests.DeckRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.GapsRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.GradeRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.MindMapRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.NotesRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.PerspectivesRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.QuizRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.ReviewSummaryRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.RewriteRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.StudyPlanRequest;
import com.sophia.ai.domain.dto.response.ApiResponse;
import com.sophia.ai.domain.dto.response.Results.DebateResult;
import com.sophia.ai.domain.dto.response.Results.DeckResult;
import com.sophia.ai.domain.dto.response.Results.GapsResult;
import com.sophia.ai.domain.dto.response.Results.GradeResult;
import com.sophia.ai.domain.dto.response.Results.MindMapResult;
import com.sophia.ai.domain.dto.response.Results.PerspectivesResult;
import com.sophia.ai.domain.dto.response.Results.QuizResult;
import com.sophia.ai.domain.dto.response.Results.StudyPlanResult;
import com.sophia.ai.domain.dto.response.UsageReports.Status;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "Study content generation")
public class AiController {

    private static final String LANGUAGE = "^[a-z]{2}(-[A-Za-z]{2})?$";

    private final GenerationService generation;
    private final SseStreamer streamer;

    @GetMapping("/status")
    @Operation(summary = "Whether the AI features are available, and the caller's allowance for today")
    public ResponseEntity<ApiResponse<Status>> status(@CurrentUser AuthenticatedUser user,
                                                      @RequestParam(required = false) UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(generation.status(user, workspaceId)));
    }

    // ---- streamed text (server-sent events: meta, delta*, done | error) ----------------------------------------

    @PostMapping(value = "/notes", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Write study notes from a topic and/or documents (streamed Markdown)")
    public SseEmitter notes(@CurrentUser AuthenticatedUser user, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                            @Valid @RequestBody NotesRequest request) {
        return streamer.stream(user, generation.notes(user, authorization, request));
    }

    @PostMapping(value = "/rewrite", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Rewrite or explain a passage (streamed)")
    public SseEmitter rewrite(@CurrentUser AuthenticatedUser user, @Valid @RequestBody RewriteRequest request) {
        return streamer.stream(user, generation.rewrite(user, request));
    }

    @PostMapping(value = "/continue", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Continue writing a document from the cursor (streamed)")
    public SseEmitter continueText(@CurrentUser AuthenticatedUser user, @Valid @RequestBody ContinueRequest request) {
        return streamer.stream(user, generation.continueText(user, request));
    }

    @PostMapping(value = "/review-summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "A one-page revision sheet from documents, with extra attention on weak topics (streamed)")
    public SseEmitter reviewSummary(@CurrentUser AuthenticatedUser user,
                                    @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @Valid @RequestBody ReviewSummaryRequest request) {
        return streamer.stream(user, generation.reviewSummary(user, authorization, request));
    }

    // ---- structured results ------------------------------------------------------------------------------------

    @PostMapping("/deck")
    @Operation(summary = "Cards for a board: concept cards or flashcards, from a topic and/or documents")
    public ResponseEntity<ApiResponse<DeckResult>> deck(@CurrentUser AuthenticatedUser user,
                                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                        @Valid @RequestBody DeckRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(generation.runDeck(user, generation.deck(user, authorization, request))));
    }

    @PostMapping("/mindmap")
    @Operation(summary = "A mind map or concept map as nodes (and labelled links)")
    public ResponseEntity<ApiResponse<MindMapResult>> mindMap(@CurrentUser AuthenticatedUser user,
                                                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                              @Valid @RequestBody MindMapRequest request) {
        var prepared = generation.mindMap(user, authorization, request);
        return ResponseEntity.ok(ApiResponse.ok(generation.runMindMap(user, prepared, request.kind())));
    }

    @PostMapping("/quiz")
    @Operation(summary = "A quiz or a mock exam, with the answers and explanations")
    public ResponseEntity<ApiResponse<QuizResult>> quiz(@CurrentUser AuthenticatedUser user,
                                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                        @Valid @RequestBody QuizRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(generation.runQuiz(user, generation.quiz(user, authorization, request))));
    }

    @PostMapping("/quiz/grade")
    @Operation(summary = "Grade short answers against their reference answers")
    public ResponseEntity<ApiResponse<GradeResult>> grade(@CurrentUser AuthenticatedUser user,
                                                          @Valid @RequestBody GradeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(generation.runGrade(user, generation.grade(user, request), request)));
    }

    @PostMapping("/perspectives")
    @Operation(summary = "Look at a subject from several points of view, with a synthesis")
    public ResponseEntity<ApiResponse<PerspectivesResult>> perspectives(@CurrentUser AuthenticatedUser user,
                                                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                        @Valid @RequestBody PerspectivesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                generation.runPerspectives(user, generation.perspectives(user, authorization, request))));
    }

    @PostMapping("/debate")
    @Operation(summary = "The strongest case for and against a motion, with a balanced verdict")
    public ResponseEntity<ApiResponse<DebateResult>> debate(@CurrentUser AuthenticatedUser user,
                                                            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                            @Valid @RequestBody DebateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(generation.runDebate(user, generation.debate(user, authorization, request))));
    }

    @PostMapping("/gaps")
    @Operation(summary = "Review notes for what is missing or wrong, optionally against reference material")
    public ResponseEntity<ApiResponse<GapsResult>> gaps(@CurrentUser AuthenticatedUser user,
                                                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                        @Valid @RequestBody GapsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(generation.runGaps(user, generation.gaps(user, authorization, request))));
    }

    // ---- conversions ------------------------------------------------------------------------------------------

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Transcribe a scan, a photo of notes or a PDF into Markdown (streamed)")
    public SseEmitter ocr(@CurrentUser AuthenticatedUser user,
                          @RequestPart("file") MultipartFile file,
                          @RequestParam(required = false) UUID workspaceId,
                          @RequestParam(required = false) OcrMode mode,
                          @RequestParam(required = false) @Pattern(regexp = LANGUAGE) String language) {
        return streamer.stream(user, generation.ocr(user, workspaceId, file, mode, language));
    }

    @PostMapping(value = "/tables", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "A table from pasted text and/or documents")
    public ResponseEntity<ApiResponse<TableResult>> table(@CurrentUser AuthenticatedUser user,
                                                          @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                          @Valid @RequestBody TableRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                generation.runTable(user, generation.table(user, authorization, request, null))));
    }

    @PostMapping(value = "/tables/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "A table from a photo, a scan or a PDF")
    public ResponseEntity<ApiResponse<TableResult>> tableFromFile(@CurrentUser AuthenticatedUser user,
                                                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                  @RequestPart("file") MultipartFile file,
                                                                  @RequestParam(required = false) UUID workspaceId,
                                                                  @RequestParam(required = false) @Size(max = 500) String instructions,
                                                                  @RequestParam(required = false) @Pattern(regexp = LANGUAGE) String language) {
        var request = new TableRequest(workspaceId, null, null, instructions, language);
        return ResponseEntity.ok(ApiResponse.ok(
                generation.runTable(user, generation.table(user, authorization, request, file))));
    }

    @PostMapping("/diagrams")
    @Operation(summary = "A flowchart, timeline or cycle drawn from text and/or documents")
    public ResponseEntity<ApiResponse<DiagramResult>> diagram(@CurrentUser AuthenticatedUser user,
                                                              @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                              @Valid @RequestBody DiagramRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(generation.runDiagram(user, generation.diagram(user, authorization, request))));
    }

    @PostMapping("/tags")
    @Operation(summary = "A title, a summary and tags for a note")
    public ResponseEntity<ApiResponse<TagsResult>> tags(@CurrentUser AuthenticatedUser user,
                                                        @Valid @RequestBody TagsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(generation.runTags(user, generation.tags(user, request))));
    }

    @PostMapping(value = "/listen", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Rewrite a text so it sounds good read aloud (streamed)")
    public SseEmitter listen(@CurrentUser AuthenticatedUser user, @Valid @RequestBody ListenRequest request) {
        return streamer.stream(user, generation.listen(user, request));
    }

    @PostMapping("/study-plan")
    @Operation(summary = "A day-by-day study plan up to an exam date, as tasks and as a ready checklist")
    public ResponseEntity<ApiResponse<StudyPlanResult>> studyPlan(@CurrentUser AuthenticatedUser user,
                                                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                  @Valid @RequestBody StudyPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                generation.runStudyPlan(user, generation.studyPlan(user, authorization, request))));
    }
}
