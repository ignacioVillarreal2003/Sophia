package com.sophia.authentication.application.security.oauth2;

import com.sophia.authentication.api.security.oauth2.OAuth2LoginFailureHandler;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class OAuth2LoginFailureHandlerTest {

    @Test
    @DisplayName("redirects to the frontend callback with an error marker")
    void redirectsWithError() throws Exception {
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(TestFixtures.appProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response,
                new BadCredentialsException("denied"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:4200/oauth2/callback?error=oauth2_failed");
    }
}
