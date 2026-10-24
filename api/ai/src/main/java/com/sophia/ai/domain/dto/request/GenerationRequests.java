package com.sophia.ai.domain.dto.request;

import com.sophia.ai.domain.model.Enums.ChatMode;
import com.sophia.ai.domain.model.Enums.DiagramKind;
import com.sophia.ai.domain.model.Enums.Difficulty;
import com.sophia.ai.domain.model.Enums.DeckMode;
import com.sophia.ai.domain.model.Enums.Length;
import com.sophia.ai.domain.model.Enums.Lens;
import com.sophia.ai.domain.model.Enums.MapKind;
import com.sophia.ai.domain.model.Enums.NotesInclude;
import com.sophia.ai.domain.model.Enums.QuestionType;
import com.sophia.ai.domain.model.Enums.RewriteAction;
import com.sophia.ai.domain.model.Enums.Stance;
import com.sophia.ai.domain.model.Enums.StudyLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Every request body of the generation endpoints, in one place. */
public final class GenerationRequests {

    private GenerationRequests() {
    }

    /** A document of the workspace to work from; its text is read with the caller's own permissions. */
    public record SourceRef(@NotNull UUID artifactId) {
    }

    private static final String LANGUAGE = "^[a-z]{2}(-[A-Za-z]{2})?$";

    public record NotesRequest(
            UUID workspaceId,
            @Size(max = 500) String topic,
            StudyLevel level,
            Length length,
            @Pattern(regexp = LANGUAGE) String language,
            Set<NotesInclude> include,
            @Size(max = 1000) String instructions,
            @Valid @Size(max = 12) List<SourceRef> sources) {
    }

    public record RewriteRequest(
            UUID workspaceId,
            @NotBlank @Size(max = 20000) String text,
            @NotNull RewriteAction action,
            @Size(max = 40) String targetLanguage,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record ContinueRequest(
            UUID workspaceId,
            @NotBlank @Size(max = 12000) String before,
            @Size(max = 4000) String after,
            Length length,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record ReviewSummaryRequest(
            @NotNull UUID workspaceId,
            @Valid @NotEmpty @Size(max = 12) List<SourceRef> sources,
            @Size(max = 20) List<@Size(max = 200) String> weakTopics,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record DeckRequest(
            UUID workspaceId,
            @NotNull DeckMode mode,
            @Size(max = 500) String topic,
            @Valid @Size(max = 12) List<SourceRef> sources,
            @Min(3) @Max(40) Integer count,
            StudyLevel level,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record MindMapRequest(
            UUID workspaceId,
            @NotNull MapKind kind,
            @Size(max = 500) String topic,
            @Valid @Size(max = 12) List<SourceRef> sources,
            @Min(2) @Max(4) Integer depth,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record QuizRequest(
            UUID workspaceId,
            @Size(max = 500) String topic,
            @Valid @Size(max = 12) List<SourceRef> sources,
            @Min(3) @Max(30) Integer count,
            Difficulty difficulty,
            Set<QuestionType> types,
            /** True for a mock exam: questions spread over every source, with application questions. */
            boolean exam,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record GradeItem(
            @NotBlank @Size(max = 1500) String question,
            @NotBlank @Size(max = 1500) String reference,
            @Size(max = 3000) String answer) {
    }

    public record GradeRequest(
            UUID workspaceId,
            @Valid @NotEmpty @Size(max = 30) List<GradeItem> items,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record StudyPlanRequest(
            UUID workspaceId,
            @Size(max = 500) String topic,
            @Valid @Size(max = 12) List<SourceRef> sources,
            @NotNull LocalDate examDate,
            /** The caller calendar day, since a day starts at different moments around the world. */
            LocalDate today,
            @Min(15) @Max(600) Integer minutesPerDay,
            /** ISO weekdays (1 = Monday ... 7 = Sunday) without study. */
            @Size(max = 6) Set<@Min(1) @Max(7) Integer> daysOff,
            StudyLevel level,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record PerspectivesRequest(
            UUID workspaceId,
            @Size(max = 500) String topic,
            @Valid @Size(max = 12) List<SourceRef> sources,
            /** Ready-made lenses; with none chosen (and no custom ones) the service picks four general ones. */
            @Size(max = 6) Set<Lens> lenses,
            /** Points of view of the user's own, such as "a nurse" or "someone from 1900". */
            @Size(max = 3) List<@NotBlank @Size(max = 60) String> customLenses,
            StudyLevel level,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record DebateRequest(
            UUID workspaceId,
            /** The motion or question to argue, such as "Remote work is better than office work". */
            @NotBlank @Size(max = 500) String motion,
            @Valid @Size(max = 12) List<SourceRef> sources,
            StudyLevel level,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record GapsRequest(
            UUID workspaceId,
            /** The student's own notes, which are what gets reviewed. */
            @Valid @NotEmpty @Size(max = 6) List<SourceRef> notes,
            /** Optional trusted material (a textbook chapter, slides) to check the notes against. */
            @Valid @Size(max = 6) List<SourceRef> reference,
            @Size(max = 500) String topic,
            StudyLevel level,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record TableRequest(
            UUID workspaceId,
            /** Text to pull a table out of; used together with the documents when both are given. */
            @Size(max = 30000) String text,
            @Valid @Size(max = 12) List<SourceRef> sources,
            @Size(max = 500) String instructions,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record DiagramRequest(
            UUID workspaceId,
            @NotNull DiagramKind kind,
            @Size(max = 500) String topic,
            @Size(max = 30000) String text,
            @Valid @Size(max = 12) List<SourceRef> sources,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record TagsRequest(
            UUID workspaceId,
            @NotBlank @Size(max = 60000) String text,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record ListenRequest(
            UUID workspaceId,
            @NotBlank @Size(max = 20000) String text,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record CreateConversationRequest(
            UUID workspaceId,
            @NotNull ChatMode mode,
            @Size(max = 160) String title,
            /** What to be taught (SOCRATIC) or the motion to argue (DEBATE, required there). */
            @Size(max = 500) String topic,
            /** The side the person takes in a DEBATE; the assistant argues the other. */
            Stance stance,
            @Valid @Size(max = 12) List<SourceRef> sources,
            @Pattern(regexp = LANGUAGE) String language) {
    }

    public record UpdateConversationRequest(
            @Size(min = 1, max = 160) String title,
            /** When present, replaces the documents the conversation draws on. */
            @Valid @Size(max = 12) List<SourceRef> sources) {
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 4000) String content,
            /** Ends a SOCRATIC or DEBATE session: the assistant answers with a debrief of how it went. */
            boolean finish) {
    }

    @Schema(description = "Limits administrators can change at runtime; 0 means no limit")
    public record UpdateSettingsRequest(
            boolean enabled,
            @Min(0) long dailyTokensPerUser,
            @Min(0) long dailyTokensPerWorkspace) {
    }
}
