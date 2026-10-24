package com.sophia.ai.api.controller;

import com.sophia.ai.api.security.CurrentUser;
import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.application.service.ChatService;
import com.sophia.ai.application.service.SseStreamer;
import com.sophia.ai.domain.dto.request.GenerationRequests.CreateConversationRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.SendMessageRequest;
import com.sophia.ai.domain.dto.request.GenerationRequests.UpdateConversationRequest;
import com.sophia.ai.domain.dto.response.ApiResponse;
import com.sophia.ai.domain.dto.response.ChatViews.ConversationSummary;
import com.sophia.ai.domain.dto.response.ChatViews.ConversationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
@Tag(name = "AI conversations", description = "Chat with documents, Socratic tutor and debate partner")
public class ChatController {

    private final ChatService chat;
    private final SseStreamer streamer;

    @GetMapping
    @Operation(summary = "The caller's conversations, most recent first")
    public ResponseEntity<ApiResponse<List<ConversationSummary>>> list(@CurrentUser AuthenticatedUser user,
                                                                       @RequestParam(required = false) UUID workspaceId) {
        return ResponseEntity.ok(ApiResponse.ok(chat.list(user, workspaceId)));
    }

    @PostMapping
    @Operation(summary = "Start a conversation")
    public ResponseEntity<ApiResponse<ConversationView>> create(@CurrentUser AuthenticatedUser user,
                                                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(chat.create(user, authorization, request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "A conversation with all its messages")
    public ResponseEntity<ApiResponse<ConversationView>> get(@CurrentUser AuthenticatedUser user, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(chat.get(user, id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename a conversation or change the documents it draws on")
    public ResponseEntity<ApiResponse<ConversationSummary>> update(@CurrentUser AuthenticatedUser user,
                                                                   @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                                                   @PathVariable UUID id,
                                                                   @Valid @RequestBody UpdateConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(chat.update(user, authorization, id, request)));
    }

    @DeleteMapping
    @Operation(summary = "Delete every conversation of the caller")
    public ResponseEntity<ApiResponse<Integer>> deleteAll(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(chat.deleteAll(user)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a conversation and its messages")
    public ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser user, @PathVariable UUID id) {
        chat.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send a message; the answer is streamed (meta, delta*, done | error) and saved")
    public SseEmitter send(@CurrentUser AuthenticatedUser user,
                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                           @PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        return streamer.stream(user, chat.send(user, authorization, id, request));
    }
}
