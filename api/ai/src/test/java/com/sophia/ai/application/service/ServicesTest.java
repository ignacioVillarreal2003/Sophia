package com.sophia.ai.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.ai.TestSupport;
import com.sophia.ai.application.prompt.AnalysisPrompts;
import com.sophia.ai.application.prompt.ChatPrompts;
import com.sophia.ai.application.prompt.ConvertPrompts;
import com.sophia.ai.application.prompt.PromptFactory;
import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.application.service.GenerationService.Prepared;
import com.sophia.ai.application.service.SourceLoader.LoadedSource;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.request.GenerationRequests.CreateConversationRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.GapsRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.SendMessageRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.SourceRef;
import com.sophia.ai.domain.dto.request.GenerationRequests.StudyPlanRequest;
import com.sophia.ai.domain.dto.response.Results.QuizResult;
import com.sophia.ai.domain.dto.response.UsageReports.Quota;
import com.sophia.ai.domain.entity.AiSettings;
import com.sophia.ai.domain.entity.ChatMessage;
import com.sophia.ai.domain.entity.Conversation;
import com.sophia.ai.domain.entity.Conversation.SourceEntry;
import com.sophia.ai.domain.entity.enums.AiFeature;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.BadRequestException;
import com.sophia.ai.domain.exception.ConflictException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.domain.exception.ResourceNotFoundException;
import com.sophia.ai.domain.model.Enums.ChatMode;
import com.sophia.ai.domain.model.Enums.Stance;
import com.sophia.ai.infrastructure.llm.ModelGateway;
import com.sophia.ai.infrastructure.llm.ModelGateway.Completion;
import com.sophia.ai.infrastructure.llm.ModelGateway.TokenUsage;
import com.sophia.ai.infrastructure.llm.MockGateway;
import com.sophia.ai.infrastructure.lock.DistributedLocks;
import com.sophia.ai.infrastructure.repository.AiGenerationRepository;
import com.sophia.ai.infrastructure.repository.ChatMessageRepository;
import com.sophia.ai.infrastructure.repository.ConversationRepository;
import com.sophia.ai.infrastructure.retrieval.DocumentIndex;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServicesTest {

    private final ApplicationProperties properties = TestSupport.properties("mock", 2);
    private final AuthenticatedUser user = TestSupport.user();
    private final UUID workspace = UUID.randomUUID();

    private final DistributedLocks locks = new DistributedLocks(null);
    private AiSettingsService settings;
    private UsageService usage;
    private SourceLoader loader;
    private ModelGateway gateway;
    private GenerationService generation;
    private AiGenerationRepository generations;

    @BeforeEach
    void setUp() {
        settings = mock(AiSettingsService.class);
        when(settings.get()).thenReturn(AiSettings.of(true, 1000, 5000));
        generations = mock(AiGenerationRepository.class);
        usage = new UsageService(generations, settings, properties, new SimpleMeterRegistry());
        loader = mock(SourceLoader.class);
        gateway = new MockGateway();
        generation = new GenerationService(gateway, settings, usage, new ConcurrencyGuard(properties, locks), loader,
                new PromptFactory(), new ResultSanitizer(), new AnalysisSanitizer(), new AnalysisPrompts(), new ConvertPrompts(),
                new ConvertSanitizer(), new AttachmentReader(properties),
                new ObjectMapper(), properties);
    }

    // ---- concurrency ------------------------------------------------------------------------------------------

    @Test
    void aPersonCannotRunMoreGenerationsThanAllowedAtOnce() {
        ConcurrencyGuard guard = new ConcurrencyGuard(properties, locks);
        UUID id = UUID.randomUUID();
        var first = guard.acquire(id);
        var second = guard.acquire(id);
        assertThatThrownBy(() -> guard.acquire(id)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TOO_MANY_CONCURRENT));
        first.close();
        guard.acquire(id).close();
        second.close();
        guard.acquire(id).close(); // counters are released, nothing leaks
        assertThat(guard.acquire(UUID.randomUUID())).isNotNull();
    }

    // ---- quota ------------------------------------------------------------------------------------------------

    @Test
    void aSpentUserAllowanceRefusesTheNextGeneration() {
        when(generations.tokensUsedByUser(eq(user.id()), any())).thenReturn(1000L);
        assertThatThrownBy(() -> usage.assertWithinQuota(user.id(), null)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo(ErrorCode.QUOTA_EXCEEDED);
            assertThat(e.getDetails()).containsEntry("scope", "USER");
        });
    }

    @Test
    void aSpentWorkspaceAllowanceRefusesEveryoneInIt() {
        when(generations.tokensUsedByUser(eq(user.id()), any())).thenReturn(10L);
        when(generations.tokensUsedInWorkspace(eq(workspace), any())).thenReturn(5000L);
        assertThatThrownBy(() -> usage.assertWithinQuota(user.id(), workspace)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getDetails()).containsEntry("scope", "WORKSPACE"));
    }

    @Test
    void zeroMeansNoLimit() {
        when(settings.get()).thenReturn(AiSettings.of(true, 0, 0));
        when(generations.tokensUsedByUser(eq(user.id()), any())).thenReturn(Long.MAX_VALUE / 2);
        usage.assertWithinQuota(user.id(), workspace);
    }

    @Test
    void quotaReportsTodaysUsage() {
        when(generations.tokensUsedByUser(eq(user.id()), any())).thenReturn(300L);
        Quota quota = usage.quota(user.id(), null);
        assertThat(quota.userUsed()).isEqualTo(300);
        assertThat(quota.userLimit()).isEqualTo(1000);
        assertThat(quota.workspaceUsed()).isZero();
    }

    // ---- generation -------------------------------------------------------------------------------------------

    @Test
    void aDisabledServiceRefusesBeforeDoingAnything() {
        when(settings.get()).thenReturn(AiSettings.of(false, 1000, 5000));
        assertThatThrownBy(() -> generation.rewrite(user, new com.sophia.ai.domain.dto.request.GenerationRequests.RewriteRequest(
                null, "hola", com.sophia.ai.domain.model.Enums.RewriteAction.FIX, null, "es")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_DISABLED));
    }

    @Test
    void anUnconfiguredProviderIsReportedAsSuch() {
        ModelGateway none = mock(ModelGateway.class);
        when(none.configured()).thenReturn(false);
        GenerationService service = new GenerationService(none, settings, usage, new ConcurrencyGuard(properties, locks), loader,
                new PromptFactory(), new ResultSanitizer(), new AnalysisSanitizer(), new AnalysisPrompts(), new ConvertPrompts(),
                new ConvertSanitizer(), new AttachmentReader(properties), new ObjectMapper(),
                properties);
        assertThatThrownBy(() -> service.ensureReady(user, null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_NOT_CONFIGURED));
    }

    @Test
    void quizRunsEndToEndAndIsRecorded() {
        var request = new com.sophia.ai.domain.dto.request.GenerationRequests.QuizRequest(null, "Algo", null, 6, null, null,
                false, "es");
        QuizResult result = generation.runQuiz(user, generation.quiz(user, "Bearer x", request));
        assertThat(result.questions()).hasSize(6);
        verify(generations).save(any());
    }

    @Test
    void planRejectsPastDatesAndOverlongSpans() {
        var past = new StudyPlanRequest(null, "t", null, LocalDate.now().minusDays(3), null, null, null, null, "es");
        assertThatThrownBy(() -> generation.studyPlan(user, "Bearer x", past)).isInstanceOf(BadRequestException.class);
        var far = new StudyPlanRequest(null, "t", null, LocalDate.now().plusDays(400), null, null, null, null, "es");
        assertThatThrownBy(() -> generation.studyPlan(user, "Bearer x", far)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void gapsRefuseADocumentThatIsBothNotesAndReference() {
        UUID doc = UUID.randomUUID();
        var request = new GapsRequest(workspace, List.of(new SourceRef(doc)), List.of(new SourceRef(doc)), null, null, "es");
        assertThatThrownBy(() -> generation.gaps(user, "Bearer x", request)).isInstanceOf(BadRequestException.class);
        verify(loader, never()).load(any(), any(), anyList());
    }

    // ---- conversations ----------------------------------------------------------------------------------------

    private ConversationRepository conversations;
    private ChatMessageRepository messages;
    private DocumentIndex index;
    private ChatService chat;

    private void chatSetUp() {
        conversations = mock(ConversationRepository.class);
        messages = mock(ChatMessageRepository.class);
        index = mock(DocumentIndex.class);
        when(conversations.save(any())).thenAnswer(i -> i.getArgument(0));
        when(messages.save(any())).thenAnswer(i -> i.getArgument(0));
        chat = new ChatService(conversations, messages, generation, loader, index, new ChatPrompts(), properties, locks);
    }

    private Conversation existing(ChatMode mode, List<SourceEntry> sources) {
        Conversation c = Conversation.start(user.id(), workspace, mode, "", null, mode == ChatMode.DEBATE ? Stance.PRO : null,
                "es", sources);
        when(conversations.findByIdAndUserId(c.getId(), user.id())).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void aDebateNeedsAMotionAndASide() {
        chatSetUp();
        assertThatThrownBy(() -> chat.create(user, "Bearer x", new CreateConversationRequest(workspace, ChatMode.DEBATE, null,
                null, Stance.PRO, null, "es"))).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> chat.create(user, "Bearer x", new CreateConversationRequest(workspace, ChatMode.DEBATE, null,
                "Moción", null, null, "es"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void creatingTitlesAfterTheFirstDocumentAndIsLimited() {
        chatSetUp();
        UUID doc = UUID.randomUUID();
        when(loader.loadAll(any(), eq(workspace), anyList(), anyInt())).thenReturn(List.of(new LoadedSource(doc, "Apuntes", "x")));
        var view = chat.create(user, "Bearer x", new CreateConversationRequest(workspace, ChatMode.DOCUMENTS, null, null, null,
                List.of(new SourceRef(doc)), "es"));
        assertThat(view.conversation().title()).isEqualTo("Apuntes");
        assertThat(view.conversation().sources()).extracting(SourceEntry::artifactId).containsExactly(doc);

        when(conversations.countByUserId(user.id())).thenReturn(100L);
        assertThatThrownBy(() -> chat.create(user, "Bearer x", new CreateConversationRequest(workspace, ChatMode.DOCUMENTS, null,
                null, null, null, "es"))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void someoneElsesConversationDoesNotExist() {
        chatSetUp();
        UUID id = UUID.randomUUID();
        when(conversations.findByIdAndUserId(id, user.id())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> chat.get(user, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chat.send(user, "Bearer x", id, new SendMessageRequest("hola", false)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> chat.delete(user, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aSecondMessageWaitsForTheAnswerInProgress() {
        chatSetUp();
        Conversation c = existing(ChatMode.SOCRATIC, List.of());
        Prepared first = chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("uno", false));
        assertThatThrownBy(() -> chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("dos", false)))
                .isInstanceOf(ConflictException.class);
        first.post().abort();
        chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("dos", false)).post().abort();
    }

    @Test
    void aFailedPreparationReleasesTheConversation() {
        chatSetUp();
        Conversation c = existing(ChatMode.SOCRATIC, List.of());
        when(messages.countByConversationId(c.getId())).thenReturn(400L);
        assertThatThrownBy(() -> chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("x", false)))
                .isInstanceOf(BadRequestException.class);
        when(messages.countByConversationId(c.getId())).thenReturn(0L);
        chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("x", false)).post().abort();
    }

    @Test
    void theFirstMessageNamesAnUntitledConversation() {
        chatSetUp();
        Conversation c = existing(ChatMode.SOCRATIC, List.of());
        chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("¿Cómo funciona la fotosíntesis?\nQuiero entenderla", false))
                .post().abort();
        verify(conversations).retitle(c.getId(), "¿Cómo funciona la fotosíntesis?");
    }

    @Test
    void shortDocumentsTravelWholeAndAnswersCiteOnlyWhatExists() {
        chatSetUp();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Conversation c = existing(ChatMode.DOCUMENTS, List.of(new SourceEntry(a, "A"), new SourceEntry(b, "B")));
        when(loader.loadAll(any(), eq(workspace), anyList(), anyInt())).thenReturn(List.of(
                new LoadedSource(a, "A", "texto a"), new LoadedSource(b, "B", "texto b")));

        Prepared prepared = chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("Compará", false));
        assertThat(prepared.request().system()).contains("texto a", "texto b");
        assertThat(prepared.request().feature()).isEqualTo(AiFeature.CHAT);
        verify(index, never()).search(anyList(), any(), anyInt());

        var done = prepared.post().complete(new Completion("A dice X [1], B dice Y [2] y [1] otra vez [7].",
                new TokenUsage(10, 5, 0, 0), "mock", false, false, false));
        @SuppressWarnings("unchecked")
        var citations = (List<ChatMessage.Citation>) done.get("citations");
        assertThat(citations).extracting(ChatMessage.Citation::index).containsExactly(1, 2);
        assertThat(citations).extracting(ChatMessage.Citation::artifactId).containsExactly(a, b);

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ChatMessage::getRole).containsExactly("USER", "ASSISTANT");
        verify(conversations).touch(eq(c.getId()), any());
        // the conversation is free again
        chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("otra", false)).post().abort();
    }

    @Test
    void longDocumentsAreSearchedInsteadOfSentWhole() {
        chatSetUp();
        UUID a = UUID.randomUUID();
        Conversation c = existing(ChatMode.DOCUMENTS, List.of(new SourceEntry(a, "Enorme")));
        when(loader.loadAll(any(), eq(workspace), anyList(), anyInt())).thenReturn(List.of(
                new LoadedSource(a, "Enorme", "x".repeat(70000))));
        when(index.search(anyList(), any(), anyInt())).thenReturn(List.of(
                new DocumentIndex.Passage(a, 7, "pasaje relevante", 0.4)));

        Prepared prepared = chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("¿Qué dice del tema?", false));
        verify(index).ensureIndexed(eq(workspace), eq(a), any());
        assertThat(prepared.request().system()).doesNotContain("xxxxxxxx");
        assertThat(prepared.request().user()).contains("<passage document=\"1\"", "pasaje relevante");
        prepared.post().abort();
    }

    @Test
    void anEmptyAnswerIsNotSaved() {
        chatSetUp();
        Conversation c = existing(ChatMode.SOCRATIC, List.of());
        Prepared prepared = chat.send(user, "Bearer x", c.getId(), new SendMessageRequest("hola", false));
        assertThat(prepared.post().complete(new Completion("  ", TokenUsage.NONE, "mock", false, false, true))).isEmpty();
        verify(messages, org.mockito.Mockito.times(1)).save(any()); // only the question
        verify(conversations, never()).touch(any(), any());
    }
}
