package com.sophia.authentication.application.security.oauth2;

import com.sophia.authentication.api.security.oauth2.CustomOAuth2UserService;
import com.sophia.authentication.api.security.oauth2.OAuth2LoginSuccessHandler;
import com.sophia.authentication.infrastructure.service.OneTimeCodeService;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock private OneTimeCodeService oneTimeCodeService;
    @Mock private Authentication authentication;

    private OAuth2LoginSuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(oneTimeCodeService, TestFixtures.appProperties());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("redirects to the frontend callback with a one-time code")
    void redirectsWithCode() throws Exception {
        UUID userId = UUID.randomUUID();
        when(authentication.getPrincipal()).thenReturn(new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-subject-1",
                        CustomOAuth2UserService.LOCAL_USER_ID_ATTRIBUTE, userId.toString()),
                "sub"));
        when(oneTimeCodeService.issue(userId)).thenReturn("one-time-code-1");

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:4200/oauth2/callback?code=one-time-code-1");
    }

    @Test
    @DisplayName("redirects with an error when the local user id attribute is missing")
    void redirectsWithErrorWhenAttributeMissing() throws Exception {
        when(authentication.getPrincipal()).thenReturn(new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-subject-1"),
                "sub"));

        handler.onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:4200/oauth2/callback?error=oauth2_failed");
        verifyNoInteractions(oneTimeCodeService);
    }
}
