package com.sophia.ai.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.ai.application.prompt.AnalysisPrompts;
import com.sophia.ai.application.prompt.ConvertPrompts;
import com.sophia.ai.domain.dto.request.GenerationRequests.DiagramRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.ListenRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.TableRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.TagsRequest;
import com.sophia.ai.domain.dto.response.Results.DiagramResult;
import com.sophia.ai.domain.dto.response.Results.TableResult;
import com.sophia.ai.domain.dto.response.Results.TagsResult;
import com.sophia.ai.domain.model.Enums.DiagramKind;
import com.sophia.ai.domain.model.Enums.OcrMode;
import com.sophia.ai.infrastructure.llm.ModelGateway.Attachment;
import org.springframework.web.multipart.MultipartFile;
import com.sophia.ai.application.prompt.PromptFactory;
import com.sophia.ai.domain.dto.request.GenerationRequests.DebateRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.GapsRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.PerspectivesRequest;
import com.sophia.ai.domain.dto.response.Results.DebateResult;
import com.sophia.ai.domain.dto.response.Results.GapsResult;
import com.sophia.ai.domain.dto.response.Results.PerspectivesResult;
import com.sophia.ai.application.prompt.PromptFactory.SourceText;
import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.request.GenerationRequests.ContinueRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.DeckRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.GradeRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.MindMapRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.NotesRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.QuizRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.ReviewSummaryRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.RewriteRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.SourceRef;
import com.sophia.ai.domain.dto.request.GenerationRequests.StudyPlanRequest;
import com.sophia.ai.domain.dto.response.Results.DeckResult;
import com.sophia.ai.domain.dto.response.Results.GradeResult;
import com.sophia.ai.domain.dto.response.Results.MindMapResult;
import com.sophia.ai.domain.dto.response.Results.QuizResult;
import com.sophia.ai.domain.dto.response.Results.StudyPlanResult;
import com.sophia.ai.domain.dto.response.UsageReports.Quota;
import com.sophia.ai.domain.dto.response.UsageReports.Status;
import com.sophia.ai.domain.entity.AiSettings;
import com.sophia.ai.domain.entity.enums.GenerationStatus;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.BadRequestException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.llm.ModelGateway;
import com.sophia.ai.infrastructure.llm.ModelGateway.Completion;
import com.sophia.ai.infrastructure.llm.ModelGateway.ModelRequest;
import com.sophia.ai.infrastructure.llm.ModelGateway.TextSink;
import com.sophia.ai.infrastructure.llm.ModelGateway.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates one generation: is the service on and the caller within their allowance, read the documents, build the
 * prompt, call the model, log what it cost, check what came back. The {@code prepare*} methods do everything that can
 * fail before any model call, so a streaming endpoint can still answer with a proper HTTP error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationService {

    /** A request ready to run: the prompt, plus what the usage log and the quota need to know. */
    public record Prepared(ModelRequest request, UUID workspaceId, int sources, String fallbackTitle, String language,
                           Map<String, Object> context, PostProcess post) {

        public Prepared(ModelRequest request, UUID workspaceId, int sources, String fallbackTitle, String language,
                        Map<String, Object> context) {
            this(request, workspaceId, sources, fallbackTitle, language, context, null);
        }
    }

    /**
     * Work that belongs to a streamed generation but not to the model call: a conversation saves the answer and tells
     * the client what it was saved as. Exactly one of {@code complete} and {@code abort} is called.
     */
    public interface PostProcess {

        /** Extra fields for the first event of the stream. */
        Map<String, Object> meta();

        /** The stream ended (cleanly, cut off, or because the reader left); returns extra fields for the last event. */
        Map<String, Object> complete(Completion completion);

        /** The generation failed before it produced an answer. */
        void abort();
    }

    private final ModelGateway gateway;
    private final AiSettingsService settingsService;
    private final UsageService usage;
    private final ConcurrencyGuard concurrency;
    private final SourceLoader sourceLoader;
    private final PromptFactory prompts;
    private final ResultSanitizer sanitizer;
    private final AnalysisSanitizer analysisSanitizer;
    private final AnalysisPrompts analysisPrompts;
    private final ConvertPrompts convertPrompts;
    private final ConvertSanitizer convertSanitizer;
    private final AttachmentReader attachmentReader;
    private final ObjectMapper mapper;
    private final ApplicationProperties properties;

    // ---- status -----------------------------------------------------------------------------------------------

    public Status status(AuthenticatedUser user, UUID workspaceId) {
        AiSettings settings = settingsService.get();
        Quota quota = usage.quota(user.id(), workspaceId);
        var ai = properties.ai();
        return new Status(settings.isEnabled(), gateway.configured(), ai.isMock(), gateway.model(),
                ai.maxSourceChars(), ai.maxSources(), quota);
    }

    // ---- prepare ----------------------------------------------------------------------------------------------

    public Prepared notes(AuthenticatedUser user, String authorization, NotesRequest r) {
        requireMaterial(r.topic(), r.sources());
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(prompts.notes(r, sources), r.workspaceId(), sources.size(), titleOf(r.topic(), sources),
                r.language(), Map.of());
    }

    public Prepared rewrite(AuthenticatedUser user, RewriteRequest r) {
        ensureReady(user, r.workspaceId());
        return new Prepared(prompts.rewrite(r), r.workspaceId(), 0, "", r.language(), Map.of());
    }

    public Prepared continueText(AuthenticatedUser user, ContinueRequest r) {
        ensureReady(user, r.workspaceId());
        return new Prepared(prompts.continueText(r), r.workspaceId(), 0, "", r.language(), Map.of());
    }

    public Prepared reviewSummary(AuthenticatedUser user, String authorization, ReviewSummaryRequest r) {
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(prompts.reviewSummary(r, sources), r.workspaceId(), sources.size(), titleOf(null, sources),
                r.language(), Map.of());
    }

    public Prepared deck(AuthenticatedUser user, String authorization, DeckRequest r) {
        requireMaterial(r.topic(), r.sources());
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(prompts.deck(r, sources), r.workspaceId(), sources.size(), titleOf(r.topic(), sources),
                r.language(), Map.of());
    }

    public Prepared mindMap(AuthenticatedUser user, String authorization, MindMapRequest r) {
        requireMaterial(r.topic(), r.sources());
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(prompts.mindMap(r, sources), r.workspaceId(), sources.size(), titleOf(r.topic(), sources),
                r.language(), Map.of());
    }

    public Prepared quiz(AuthenticatedUser user, String authorization, QuizRequest r) {
        requireMaterial(r.topic(), r.sources());
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(prompts.quiz(r, sources), r.workspaceId(), sources.size(), titleOf(r.topic(), sources),
                r.language(), Map.of());
    }

    public Prepared grade(AuthenticatedUser user, GradeRequest r) {
        ensureReady(user, r.workspaceId());
        return new Prepared(prompts.grade(r), r.workspaceId(), 0, "", r.language(), Map.of());
    }

    public Prepared studyPlan(AuthenticatedUser user, String authorization, StudyPlanRequest r) {
        requireMaterial(r.topic(), r.sources());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // The browser knows the user's calendar day; trust it only within a day of ours.
        if (r.today() != null && Math.abs(ChronoUnit.DAYS.between(today, r.today())) <= 1) {
            today = r.today();
        }
        long span = ChronoUnit.DAYS.between(today, r.examDate()) + 1;
        if (span < 1) {
            throw new BadRequestException("The exam date has to be today or later");
        }
        if (span > PromptFactory.MAX_PLAN_DAYS) {
            throw new BadRequestException("The plan can cover at most %d days".formatted(PromptFactory.MAX_PLAN_DAYS));
        }
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        int capacity = r.minutesPerDay() == null ? 60 : r.minutesPerDay();
        return new Prepared(prompts.studyPlan(r, sources, today), r.workspaceId(), sources.size(),
                titleOf(r.topic(), sources), r.language(),
                Map.of("today", today, "exam", r.examDate(), "capacity", capacity));
    }

    public Prepared perspectives(AuthenticatedUser user, String authorization, PerspectivesRequest r) {
        requireMaterial(r.topic(), r.sources());
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(analysisPrompts.perspectives(r, sources), r.workspaceId(), sources.size(),
                titleOf(r.topic(), sources), r.language(), Map.of());
    }

    public Prepared debate(AuthenticatedUser user, String authorization, DebateRequest r) {
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(analysisPrompts.debate(r, sources), r.workspaceId(), sources.size(), r.motion().strip(),
                r.language(), Map.of());
    }

    public Prepared gaps(AuthenticatedUser user, String authorization, GapsRequest r) {
        ensureReady(user, r.workspaceId());
        int total = r.notes().size() + (r.reference() == null ? 0 : r.reference().size());
        if (total > properties.ai().maxSources()) {
            throw new BadRequestException("Choose at most %d documents".formatted(properties.ai().maxSources()));
        }
        if (r.reference() != null) {
            var noteIds = r.notes().stream().map(SourceRef::artifactId).collect(java.util.stream.Collectors.toSet());
            if (r.reference().stream().anyMatch(ref -> noteIds.contains(ref.artifactId()))) {
                throw new BadRequestException("A document cannot be both the notes and the reference");
            }
        }
        // One read for both lists, so the size limit applies to what is actually sent together.
        List<SourceRef> all = new java.util.ArrayList<>(r.notes());
        if (r.reference() != null) {
            all.addAll(r.reference());
        }
        List<SourceText> loaded = sourceLoader.load(authorization, r.workspaceId(), all);
        int noteCount = (int) r.notes().stream().map(SourceRef::artifactId).distinct().count();
        List<SourceText> notes = loaded.subList(0, Math.min(noteCount, loaded.size()));
        List<SourceText> reference = loaded.subList(notes.size(), loaded.size());
        return new Prepared(analysisPrompts.gaps(r, notes, reference), r.workspaceId(), loaded.size(),
                titleOf(r.topic(), notes), r.language(), Map.of());
    }

    public Prepared ocr(AuthenticatedUser user, UUID workspaceId, MultipartFile file, OcrMode mode, String language) {
        // Read and check the file first: a bad upload should not cost a quota lookup, and a quota refusal should not
        // be what tells someone their file was wrong.
        Attachment attachment = attachmentReader.read(file);
        ensureReady(user, workspaceId);
        return new Prepared(convertPrompts.ocr(mode == null ? OcrMode.TRANSCRIBE : mode, attachment), workspaceId, 1,
                "", language, Map.of());
    }

    public Prepared table(AuthenticatedUser user, String authorization, TableRequest r, MultipartFile file) {
        boolean noText = r.text() == null || r.text().isBlank();
        boolean noSources = r.sources() == null || r.sources().isEmpty();
        if (noText && noSources && (file == null || file.isEmpty())) {
            throw new BadRequestException("Paste some text, choose a document or upload a file");
        }
        Attachment attachment = file == null || file.isEmpty() ? null : attachmentReader.read(file);
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(convertPrompts.table(r, sources, attachment), r.workspaceId(),
                sources.size() + (attachment == null ? 0 : 1), titleOf(null, sources), r.language(), Map.of());
    }

    public Prepared diagram(AuthenticatedUser user, String authorization, DiagramRequest r) {
        boolean noText = r.text() == null || r.text().isBlank();
        if (noText) {
            requireMaterial(r.topic(), r.sources());
        }
        ensureReady(user, r.workspaceId());
        List<SourceText> sources = sourceLoader.load(authorization, r.workspaceId(), r.sources());
        return new Prepared(convertPrompts.diagram(r, sources), r.workspaceId(), sources.size(),
                titleOf(r.topic(), sources), r.language(), Map.of("kind", r.kind()));
    }

    public Prepared tags(AuthenticatedUser user, TagsRequest r) {
        ensureReady(user, r.workspaceId());
        return new Prepared(convertPrompts.tags(r), r.workspaceId(), 0, "", r.language(), Map.of());
    }

    public Prepared listen(AuthenticatedUser user, ListenRequest r) {
        ensureReady(user, r.workspaceId());
        return new Prepared(convertPrompts.listen(r), r.workspaceId(), 0, "", r.language(), Map.of());
    }

    private static void requireMaterial(String topic, List<SourceRef> sources) {
        boolean noTopic = topic == null || topic.isBlank();
        boolean noSources = sources == null || sources.isEmpty();
        if (noTopic && noSources) {
            throw new BadRequestException("Write a topic or choose at least one document");
        }
    }

    private static String titleOf(String topic, List<SourceText> sources) {
        if (topic != null && !topic.isBlank()) {
            return topic.strip().length() > 100 ? topic.strip().substring(0, 100) : topic.strip();
        }
        return sources.isEmpty() ? "" : sources.get(0).title();
    }

    /** Fails fast, before anything is read or called, when the service cannot or may not run this for the caller. */
    public void ensureReady(AuthenticatedUser user, UUID workspaceId) {
        if (!settingsService.get().isEnabled()) {
            throw new ApiException(ErrorCode.AI_DISABLED, HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI features are turned off");
        }
        if (!gateway.configured()) {
            throw new ApiException(ErrorCode.AI_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "The AI provider is not configured yet");
        }
        usage.assertWithinQuota(user.id(), workspaceId);
    }

    // ---- run --------------------------------------------------------------------------------------------------

    /** Streams text to the sink. The returned completion says whether it ended cleanly, was cut, or was stopped. */
    public Completion stream(AuthenticatedUser user, Prepared prepared, TextSink sink) {
        return execute(user, prepared, sink);
    }

    private Completion execute(AuthenticatedUser user, Prepared prepared, TextSink sink) {
        try (ConcurrencyGuard.Lease ignored = concurrency.acquire(user.id())) {
            long started = System.nanoTime();
            Completion completion;
            try {
                completion = sink == null ? gateway.complete(prepared.request())
                        : gateway.stream(prepared.request(), sink);
            } catch (ApiException e) {
                usage.record(user, prepared.workspaceId(), prepared.request().feature(), GenerationStatus.FAILED,
                        gateway.model(), TokenUsage.NONE, millisSince(started), e.getCode().name());
                throw e;
            }
            GenerationStatus status = completion.refused() ? GenerationStatus.REFUSED
                    : completion.stopped() ? GenerationStatus.CANCELLED : GenerationStatus.SUCCESS;
            usage.record(user, prepared.workspaceId(), prepared.request().feature(), status, completion.model(),
                    completion.usage(), millisSince(started), completion.refused() ? ErrorCode.GENERATION_REFUSED.name() : null);
            if (completion.refused()) {
                throw new ApiException(ErrorCode.GENERATION_REFUSED, HttpStatus.UNPROCESSABLE_ENTITY,
                        "The AI declined to work on this content.");
            }
            return completion;
        }
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** One structured call; if the JSON cannot be read (a cut-off answer, say) it tries once more before giving up. */
    private <T> T structured(AuthenticatedUser user, Prepared prepared, Class<T> type) {
        ApiException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            Completion completion = execute(user, prepared, null);
            try {
                return mapper.readValue(completion.text(), type);
            } catch (JsonProcessingException e) {
                log.warn("Unreadable {} answer (attempt {}, truncated={})", prepared.request().feature(), attempt + 1,
                        completion.truncated(), e);
                last = new ApiException(ErrorCode.GENERATION_FAILED, HttpStatus.BAD_GATEWAY,
                        completion.truncated() ? "The result was too long. Ask for less at once."
                                : "The result could not be read. Try again.");
                if (completion.truncated()) {
                    break;
                }
            }
        }
        throw last;
    }

    public DeckResult runDeck(AuthenticatedUser user, Prepared prepared) {
        return sanitizer.deck(structured(user, prepared, DeckResult.class), prepared.fallbackTitle());
    }

    public MindMapResult runMindMap(AuthenticatedUser user, Prepared prepared, com.sophia.ai.domain.model.Enums.MapKind kind) {
        return sanitizer.mindMap(structured(user, prepared, MindMapResult.class), kind, prepared.fallbackTitle());
    }

    public QuizResult runQuiz(AuthenticatedUser user, Prepared prepared) {
        return sanitizer.quiz(structured(user, prepared, QuizResult.class), prepared.sources(), prepared.fallbackTitle());
    }

    public GradeResult runGrade(AuthenticatedUser user, Prepared prepared, GradeRequest request) {
        return sanitizer.grade(structured(user, prepared, GradeResult.class), request.items());
    }

    public PerspectivesResult runPerspectives(AuthenticatedUser user, Prepared prepared) {
        return analysisSanitizer.perspectives(structured(user, prepared, PerspectivesResult.class),
                prepared.fallbackTitle(), prepared.language());
    }

    public DebateResult runDebate(AuthenticatedUser user, Prepared prepared) {
        return analysisSanitizer.debate(structured(user, prepared, DebateResult.class), prepared.fallbackTitle(),
                prepared.language());
    }

    public GapsResult runGaps(AuthenticatedUser user, Prepared prepared) {
        return analysisSanitizer.gaps(structured(user, prepared, GapsResult.class), prepared.fallbackTitle(),
                prepared.language());
    }

    public TableResult runTable(AuthenticatedUser user, Prepared prepared) {
        return convertSanitizer.table(structured(user, prepared, TableResult.class), prepared.fallbackTitle());
    }

    public DiagramResult runDiagram(AuthenticatedUser user, Prepared prepared) {
        return convertSanitizer.diagram(structured(user, prepared, DiagramResult.class),
                (DiagramKind) prepared.context().get("kind"), prepared.fallbackTitle());
    }

    public TagsResult runTags(AuthenticatedUser user, Prepared prepared) {
        return convertSanitizer.tags(structured(user, prepared, TagsResult.class), prepared.fallbackTitle());
    }

    public StudyPlanResult runStudyPlan(AuthenticatedUser user, Prepared prepared) {
        LocalDate today = (LocalDate) prepared.context().get("today");
        LocalDate exam = (LocalDate) prepared.context().get("exam");
        int capacity = (int) prepared.context().get("capacity");
        return sanitizer.studyPlan(structured(user, prepared, StudyPlanResult.class), today, exam, capacity,
                prepared.language(), prepared.fallbackTitle());
    }
}
