package com.sophia.ai.application.service;

import com.sophia.ai.application.prompt.ChatPrompts;
import com.sophia.ai.application.prompt.ChatPrompts.PassageText;
import com.sophia.ai.application.prompt.PromptFactory.SourceText;
import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.application.service.GenerationService.PostProcess;
import com.sophia.ai.application.service.GenerationService.Prepared;
import com.sophia.ai.application.service.SourceLoader.LoadedSource;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.request.GenerationRequests.CreateConversationRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.SendMessageRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.SourceRef;
import com.sophia.ai.domain.dto.request.GenerationRequests.UpdateConversationRequest;
import com.sophia.ai.domain.dto.response.ChatViews.ConversationSummary;
import com.sophia.ai.domain.dto.response.ChatViews.ConversationView;
import com.sophia.ai.domain.dto.response.ChatViews.MessageView;
import com.sophia.ai.domain.entity.ChatMessage;
import com.sophia.ai.domain.entity.ChatMessage.Citation;
import com.sophia.ai.domain.entity.Conversation;
import com.sophia.ai.domain.entity.Conversation.SourceEntry;
import com.sophia.ai.domain.exception.BadRequestException;
import com.sophia.ai.domain.exception.ConflictException;
import com.sophia.ai.domain.exception.ResourceNotFoundException;
import com.sophia.ai.domain.model.Enums.ChatMode;
import com.sophia.ai.infrastructure.llm.ModelGateway.Completion;
import com.sophia.ai.infrastructure.llm.ModelGateway.Turn;
import com.sophia.ai.infrastructure.lock.DistributedLocks;
import com.sophia.ai.infrastructure.repository.ChatMessageRepository;
import com.sophia.ai.infrastructure.repository.ConversationRepository;
import com.sophia.ai.infrastructure.retrieval.DocumentIndex;
import com.sophia.ai.infrastructure.retrieval.DocumentIndex.Passage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversations: chat with documents, a Socratic tutor and a debate partner. A conversation belongs to the person who
 * started it and to nobody else. Documents are re-read with the person's own token on every message, so an answer can
 * never rest on something they have since lost access to, and edits to a document show up in the next answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,2})]");

    private final ConversationRepository conversations;
    private final ChatMessageRepository messages;
    private final GenerationService generation;
    private final SourceLoader sourceLoader;
    private final DocumentIndex index;
    private final ChatPrompts prompts;
    private final ApplicationProperties properties;

    /** An answer is being written in a conversation: another message to it has to wait, on any instance. */
    private static final Duration WRITING_TTL = Duration.ofMinutes(15);

    private final DistributedLocks locks;

    // ---- conversations ----------------------------------------------------------------------------------------

    public ConversationView create(AuthenticatedUser user, String authorization, CreateConversationRequest r) {
        var chat = properties.ai().chat();
        if (r.mode() == ChatMode.DEBATE) {
            if (r.topic() == null || r.topic().isBlank()) {
                throw new BadRequestException("A debate needs a motion to argue");
            }
            if (r.stance() == null) {
                throw new BadRequestException("Choose which side you take in the debate");
            }
        }
        if (conversations.countByUserId(user.id()) >= chat.maxConversations()) {
            throw new BadRequestException("You have %d conversations. Delete some to start another."
                    .formatted(chat.maxConversations()));
        }
        List<SourceEntry> sources = resolveSources(authorization, r.workspaceId(), r.sources());
        String title = r.title() != null && !r.title().isBlank() ? r.title().strip()
                : r.topic() != null && !r.topic().isBlank() ? r.topic().strip()
                : sources.isEmpty() ? "" : sources.get(0).title();
        Conversation conversation = conversations.save(Conversation.start(user.id(), r.workspaceId(), r.mode(),
                clip(title, 160), r.topic() == null || r.topic().isBlank() ? null : r.topic().strip(),
                r.mode() == ChatMode.DEBATE ? r.stance() : null, r.language(), sources));
        return new ConversationView(summary(conversation), List.of());
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(AuthenticatedUser user, UUID workspaceId) {
        return conversations.recent(user.id(), workspaceId, PageRequest.of(0, 100)).stream().map(ChatService::summary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationView get(AuthenticatedUser user, UUID id) {
        Conversation conversation = owned(user, id);
        List<MessageView> history = messages.findByConversationIdOrderByCreatedAtAsc(id).stream().map(ChatService::view).toList();
        return new ConversationView(summary(conversation), history);
    }

    public ConversationSummary update(AuthenticatedUser user, String authorization, UUID id, UpdateConversationRequest r) {
        Conversation conversation = owned(user, id);
        if (r.title() != null && !r.title().isBlank()) {
            conversation.rename(clip(r.title().strip(), 160));
        }
        if (r.sources() != null) {
            conversation.useSources(resolveSources(authorization, conversation.getWorkspaceId(), r.sources()));
        }
        conversation.touch();
        return summary(conversations.save(conversation));
    }

    @Transactional
    public void delete(AuthenticatedUser user, UUID id) {
        conversations.delete(owned(user, id));
    }

    /** Removes every conversation of the caller, with its messages (the messages go with them through the foreign key). */
    public int deleteAll(AuthenticatedUser user) {
        return conversations.deleteAllOf(user.id());
    }

    /** Conversations nobody has touched for the retention period are deleted with their messages. */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void purgeIdle() {
        int removed = conversations.deleteIdleSince(Instant.now().minus(properties.ai().chat().retention()));
        if (removed > 0) {
            log.info("Deleted {} idle conversations", removed);
        }
    }

    private Conversation owned(AuthenticatedUser user, UUID id) {
        // Someone else's conversation looks exactly like one that does not exist.
        return conversations.findByIdAndUserId(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private List<SourceEntry> resolveSources(String authorization, UUID workspaceId, List<SourceRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<SourceEntry> out = new ArrayList<>();
        for (LoadedSource source : sourceLoader.loadAll(authorization, workspaceId,
                refs.stream().map(SourceRef::artifactId).toList(), properties.ai().chat().maxIndexedChars())) {
            out.add(new SourceEntry(source.id(), source.title()));
        }
        return out;
    }

    // ---- a message --------------------------------------------------------------------------------------------

    /**
     * Everything that can fail before the model is called: the allowance, the documents, the limits. The question is
     * saved here and the answer after it is written, so a failure leaves no half-finished turn behind.
     */
    public Prepared send(AuthenticatedUser user, String authorization, UUID id, SendMessageRequest r) {
        Conversation conversation = owned(user, id);
        if (!locks.tryLock(writeKey(id), WRITING_TTL)) {
            throw new ConflictException("Wait for the current answer before sending another message");
        }
        boolean handedOver = false;
        try {
            var chat = properties.ai().chat();
            generation.ensureReady(user, conversation.getWorkspaceId());
            long count = messages.countByConversationId(id);
            if (count >= chat.maxMessages()) {
                throw new BadRequestException("This conversation is full. Start a new one to keep going.");
            }
            List<Turn> history = history(id, chat.historyMessages());
            String question = r.content().strip();
            Built built = build(conversation, authorization, question, history);

            var request = prompts.chat(conversation, history, question, r.finish(), built.whole(), built.passages(),
                    built.retrieval());
            ChatMessage asked = messages.save(ChatMessage.of(id, ChatMessage.USER, question, List.of()));
            if (count == 0 && conversation.getTitle().isBlank()) {
                conversations.retitle(id, clip(firstLine(question), 160));
            }
            Prepared prepared = new Prepared(request, conversation.getWorkspaceId(), built.sources().size(), "",
                    conversation.getLanguage(), Map.of(), new Saver(id, asked.getId(), built.sources(),
                    conversation.getMode() == ChatMode.DOCUMENTS || !built.sources().isEmpty()));
            handedOver = true;
            return prepared;
        } finally {
            if (!handedOver) {
                locks.unlock(writeKey(id));
            }
        }
    }

    /** What the model gets to see of the documents this turn. */
    private record Built(List<LoadedSource> sources, List<SourceText> whole, List<PassageText> passages,
                         boolean retrieval) {
    }

    private Built build(Conversation conversation, String authorization, String question, List<Turn> history) {
        if (conversation.getSources().isEmpty()) {
            return new Built(List.of(), List.of(), List.of(), false);
        }
        var chat = properties.ai().chat();
        List<LoadedSource> loaded = sourceLoader.loadAll(authorization, conversation.getWorkspaceId(),
                conversation.getSources().stream().map(SourceEntry::artifactId).toList(), chat.maxIndexedChars());
        long total = loaded.stream().mapToLong(s -> s.text().length()).sum();
        if (total <= chat.fullContextChars()) {
            return new Built(loaded, loaded.stream().map(s -> new SourceText(s.title(), s.text())).toList(), List.of(),
                    false);
        }
        List<UUID> ids = new ArrayList<>();
        Map<UUID, Integer> numbers = new LinkedHashMap<>();
        for (int i = 0; i < loaded.size(); i++) {
            LoadedSource source = loaded.get(i);
            index.ensureIndexed(conversation.getWorkspaceId(), source.id(), source.text());
            ids.add(source.id());
            numbers.put(source.id(), i + 1);
        }
        // A follow-up such as "and the second one?" has no searchable words of its own, so the question before it helps.
        String query = question + " " + history.stream().filter(Turn::user).reduce((a, b) -> b).map(Turn::text).orElse("");
        List<Passage> found = index.search(ids, query, chat.topChunks());
        List<PassageText> passages = found.stream()
                .sorted(Comparator.comparingInt((Passage p) -> numbers.get(p.artifactId())).thenComparingInt(Passage::position))
                .map(p -> new PassageText(numbers.get(p.artifactId()), loaded.get(numbers.get(p.artifactId()) - 1).title(),
                        p.position(), p.content()))
                .toList();
        return new Built(loaded, List.of(), passages, true);
    }

    private List<Turn> history(UUID id, int limit) {
        List<ChatMessage> latest = new ArrayList<>(messages.latest(id, PageRequest.of(0, limit)));
        Collections.reverse(latest);
        List<Turn> turns = new ArrayList<>();
        for (ChatMessage message : latest) {
            // The conversation has to open with the student; a window cut mid-exchange can start on an answer.
            if (turns.isEmpty() && !message.isUser()) {
                continue;
            }
            turns.add(new Turn(message.isUser(), message.getContent()));
        }
        return turns;
    }

    // ---- saving the answer ------------------------------------------------------------------------------------

    private final class Saver implements PostProcess {

        private final UUID conversationId;
        private final UUID questionId;
        private final List<LoadedSource> sources;
        private final boolean cites;

        Saver(UUID conversationId, UUID questionId, List<LoadedSource> sources, boolean cites) {
            this.conversationId = conversationId;
            this.questionId = questionId;
            this.sources = sources;
            this.cites = cites;
        }

        @Override
        public Map<String, Object> meta() {
            return Map.of("conversationId", conversationId, "messageId", questionId);
        }

        @Override
        public Map<String, Object> complete(Completion completion) {
            try {
                String text = completion.text() == null ? "" : completion.text().strip();
                if (text.isEmpty()) {
                    return Map.of();
                }
                List<Citation> citations = cites ? citationsIn(text) : List.of();
                ChatMessage answer = save(text, citations);
                return Map.of("messageId", answer.getId(), "citations", citations);
            } finally {
                locks.unlock(writeKey(conversationId));
            }
        }

        @Override
        public void abort() {
            locks.unlock(writeKey(conversationId));
        }

        private ChatMessage save(String text, List<Citation> citations) {
            ChatMessage answer = messages.save(ChatMessage.of(conversationId, ChatMessage.ASSISTANT, text, citations));
            conversations.touch(conversationId, Instant.now());
            return answer;
        }

        /** The documents the answer cites, in the order it first cites them; numbers that match no document are ignored. */
        private List<Citation> citationsIn(String text) {
            Set<Integer> seen = new LinkedHashSet<>();
            Matcher matcher = CITATION.matcher(text);
            while (matcher.find()) {
                int number = Integer.parseInt(matcher.group(1));
                if (number >= 1 && number <= sources.size()) {
                    seen.add(number);
                }
            }
            return seen.stream().map(n -> new Citation(n, sources.get(n - 1).id(), sources.get(n - 1).title())).toList();
        }
    }

    // ---- mapping ----------------------------------------------------------------------------------------------

    private static String writeKey(UUID conversationId) {
        return "writing:" + conversationId;
    }

    private static ConversationSummary summary(Conversation c) {
        return new ConversationSummary(c.getId(), c.getWorkspaceId(), c.getMode(), c.getTitle(), c.getTopic(),
                c.getStance(), c.getLanguage(), c.getSources(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private static MessageView view(ChatMessage m) {
        return new MessageView(m.getId(), m.getRole(), m.getContent(), m.getCitations(), m.getCreatedAt());
    }

    private static String clip(String value, int max) {
        String text = value == null ? "" : value.strip();
        return text.length() <= max ? text : text.substring(0, max).stripTrailing();
    }

    private static String firstLine(String text) {
        String line = text.lines().findFirst().orElse(text).strip();
        return line.length() > 60 ? line.substring(0, 60).stripTrailing() + "…" : line;
    }
}
