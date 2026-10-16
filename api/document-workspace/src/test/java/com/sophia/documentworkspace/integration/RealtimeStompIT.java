package com.sophia.documentworkspace.integration;

import com.sophia.documentworkspace.domain.dto.realtime.SyncBroadcast;
import com.sophia.documentworkspace.domain.dto.realtime.SyncMessage;
import com.sophia.documentworkspace.support.AbstractIntegrationTest;
import com.sophia.documentworkspace.support.ApiClient;
import com.sophia.documentworkspace.support.TestJwtFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * STOMP collaboration channel: JWT-authenticated CONNECT, per-destination
 * authorization, CRDT update relay and persistence for late joiners.
 */
class RealtimeStompIT extends AbstractIntegrationTest {

    private UUID userId;
    private ApiClient api;
    private String workspaceId;
    private String documentId;

    @BeforeAll
    void setUpDocument() {
        userId = UUID.randomUUID();
        api = ApiClient.as(baseUrl(), userId, "rt-" + userId + "@x.com");
        workspaceId = api.post("/api/v1/workspaces", Map.of("name", "RT WS"))
                .data().path("id").asText();
        documentId = api.post("/api/v1/workspaces/" + workspaceId + "/artifacts",
                        Map.of("type", "MARKDOWN_DOCUMENT", "name", "rt-doc"))
                .data().path("id").asText();
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return stompClient()
                .connectAsync("ws://localhost:" + port + "/ws",
                        new org.springframework.web.socket.WebSocketHttpHeaders(), headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS);
    }

    @Test
    void syncUpdatesAreBroadcastAndPersisted() throws Exception {
        StompSession session = connect(TestJwtFactory.token(userId, "rt@x.com"));
        BlockingQueue<SyncBroadcast> received = new LinkedBlockingQueue<>();

        String topic = "/topic/workspaces/" + workspaceId + "/documents/" + documentId + "/sync";
        session.subscribe(topic, new StompSessionHandlerAdapter() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return SyncBroadcast.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((SyncBroadcast) payload);
            }
        });
        Thread.sleep(300); // allow the subscription to register on the broker

        String update = Base64.getEncoder()
                .encodeToString("yjs-update-bytes".getBytes(StandardCharsets.UTF_8));
        session.send("/app/workspaces/" + workspaceId + "/documents/" + documentId + "/sync",
                new SyncMessage("client-1", update));

        SyncBroadcast broadcast = received.poll(10, TimeUnit.SECONDS);
        assertThat(broadcast).isNotNull();
        assertThat(broadcast.update()).isEqualTo(update);
        assertThat(broadcast.actorId()).isEqualTo(userId);
        session.disconnect();

        // A late joiner can replay the persisted update log via REST.
        var updates = api.get("/api/v1/workspaces/" + workspaceId + "/documents/" + documentId
                + "/realtime/updates").data();
        assertThat(updates.size()).isGreaterThan(0);
        assertThat(updates.get(updates.size() - 1).asText()).isEqualTo(update);
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> {
            StompHeaders headers = new StompHeaders();
            stompClient().connectAsync("ws://localhost:" + port + "/ws",
                            new org.springframework.web.socket.WebSocketHttpHeaders(), headers,
                            new StompSessionHandlerAdapter() {
                            })
                    .get(10, TimeUnit.SECONDS);
        }).isInstanceOf(ExecutionException.class);
    }

    @Test
    void subscribingToAForeignDocumentIsRejected() throws Exception {
        UUID strangerId = UUID.randomUUID();
        BlockingQueue<String> errorFrames = new LinkedBlockingQueue<>();
        BlockingQueue<SyncBroadcast> received = new LinkedBlockingQueue<>();

        // Session-level handler: STOMP ERROR frames land in handleException/handleFrame.
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization",
                "Bearer " + TestJwtFactory.token(strangerId, "stranger@x.com"));
        StompSession session = stompClient()
                .connectAsync("ws://localhost:" + port + "/ws",
                        new org.springframework.web.socket.WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleException(StompSession s, StompCommand command,
                                                        StompHeaders headers, byte[] payload,
                                                        Throwable exception) {
                                errorFrames.add("exception: " + exception.getMessage());
                            }

                            @Override
                            public void handleTransportError(StompSession s, Throwable exception) {
                                errorFrames.add("transport: " + exception.getMessage());
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                errorFrames.add("frame: " + headers);
                            }
                        })
                .get(10, TimeUnit.SECONDS);

        session.subscribe(
                "/topic/workspaces/" + workspaceId + "/documents/" + documentId + "/sync",
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return SyncBroadcast.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        received.add((SyncBroadcast) payload);
                    }
                });

        // The rejected SUBSCRIBE yields an ERROR frame and/or a closed session.
        long deadline = System.currentTimeMillis() + 5_000;
        while (errorFrames.isEmpty() && session.isConnected()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(errorFrames.isEmpty() && session.isConnected())
                .withFailMessage("expected an ERROR frame or a terminated session")
                .isFalse();

        // Either way, the stranger never receives document traffic.
        api.post("/api/v1/workspaces/" + workspaceId + "/documents/" + documentId
                + "/realtime/compact", java.util.Map.of("mergedState",
                Base64.getEncoder().encodeToString("state".getBytes(StandardCharsets.UTF_8))));
        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }
}
