package com.sophia.authentication.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.infrastructure.repository.RoleRepository;
import com.sophia.authentication.infrastructure.repository.UserRepository;
import com.sophia.authentication.infrastructure.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for full-stack integration tests. Boots the real application
 * against throwaway PostgreSQL and Redis containers; only outbound email is
 * mocked (and used to capture verification tokens).
 */
@SpringBootTest(properties = {
        // Empty key -> ephemeral RSA key pair, generated once per test JVM.
        "sophia.security.jwt.private-key=",
        "sophia.security.rate-limit.enabled=false",
        "sophia.security.require-email-verification=true",
        "sophia.notifications.enabled=false",
        "sophia.events.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
public abstract class AbstractIntegrationTest {

    /**
     * Singleton containers shared by every integration test class. They are
     * intentionally NOT managed by the @Testcontainers extension so they
     * outlive individual test classes and match the cached Spring context;
     * Ryuk reaps them when the JVM exits.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @MockitoBean
    protected EmailService emailService;

    protected static final String PASSWORD = "S3cure!Password";

    @BeforeEach
    void resetEmailMock() {
        reset(emailService);
    }

    protected void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/authentication/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Jane",
                                "lastName", "Doe",
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    protected String capturedVerificationToken() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce())
                .sendVerificationEmail(org.mockito.ArgumentMatchers.any(), captor.capture());
        return captor.getValue();
    }

    protected void verifyEmail(String rawToken) throws Exception {
        mockMvc.perform(post("/api/v1/authentication/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
                .andExpect(status().isOk());
    }

    /** Registers, verifies and logs in; returns the auth payload (data node). */
    protected JsonNode registerVerifyAndLogin(String email) throws Exception {
        register(email);
        verifyEmail(capturedVerificationToken());
        return login(email, PASSWORD);
    }

    protected JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/authentication/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    /** Registers a user, promotes it to ADMIN in the database and returns its access token. */
    protected String loginAsAdmin(String email) throws Exception {
        register(email);
        verifyEmail(capturedVerificationToken());
        transactionTemplate.executeWithoutResult(tx -> {
            User user = userRepository.findByEmailWithAuthorities(email).orElseThrow();
            user.updateRoles(Set.of(roleRepository.findByName("ADMIN").orElseThrow()));
        });
        return login(email, PASSWORD).path("accessToken").asText();
    }
}
