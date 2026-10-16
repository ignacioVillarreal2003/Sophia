package com.sophia.authentication.application.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sophia.authentication.api.security.JsonWebTokenAccessDeniedHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWebTokenAccessDeniedHandlerTest {

    @Test
    @DisplayName("writes a 403 JSON body describing the denied request")
    void handleWritesForbiddenBody() throws Exception {
        JsonWebTokenAccessDeniedHandler handler = new JsonWebTokenAccessDeniedHandler(
                new ObjectMapper().registerModule(new JavaTimeModule()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.setRequestURI("/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403")
                .contains("Access denied")
                .contains("/api/v1/users");
    }
}
