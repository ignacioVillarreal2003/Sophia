package com.sophia.authentication.application.security.oauth2;

import com.sophia.authentication.api.security.oauth2.CustomOAuth2UserService;
import com.sophia.authentication.application.service.GoogleAuthenticationService;
import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.infrastructure.security.oauth2.GoogleUserInfo;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private GoogleAuthenticationService googleAuthenticationService;

    private OAuth2UserRequest userRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/callback")
                .authorizationUri("https://provider.test/authorize")
                .tokenUri("https://provider.test/token")
                .userInfoUri("https://provider.test/userinfo")
                .userNameAttributeName("sub")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-token", Instant.now(), Instant.now().plusSeconds(60));
        return new OAuth2UserRequest(registration, accessToken);
    }

    private CustomOAuth2UserService serviceReturning(Map<String, Object> attributes) {
        OAuth2User remoteUser = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, "sub");
        return new CustomOAuth2UserService(googleAuthenticationService) {
            @Override
            protected OAuth2User fetchUser(OAuth2UserRequest request) {
                return remoteUser;
            }
        };
    }

    private Map<String, Object> googleAttributes() {
        return Map.of(
                "sub", "google-subject-1",
                "email", "jane@test.local",
                "email_verified", true,
                "given_name", "Jane",
                "family_name", "Doe",
                "picture", "https://img.test/jane.png");
    }

    @Test
    @DisplayName("provisions the local user and exposes its id as an attribute")
    void loadUserEnrichesAttributes() {
        User user = TestFixtures.user("jane@test.local", null);
        when(googleAuthenticationService.provisionUser(org.mockito.ArgumentMatchers.any())).thenReturn(user);

        OAuth2User result = serviceReturning(googleAttributes()).loadUser(userRequest("google"));

        assertThat(result.<String>getAttribute(CustomOAuth2UserService.LOCAL_USER_ID_ATTRIBUTE))
                .isEqualTo(user.getId().toString());
        assertThat(result.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_USER");

        ArgumentCaptor<GoogleUserInfo> captor = ArgumentCaptor.forClass(GoogleUserInfo.class);
        verify(googleAuthenticationService).provisionUser(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("google-subject-1");
        assertThat(captor.getValue().email()).isEqualTo("jane@test.local");
        assertThat(captor.getValue().emailVerified()).isTrue();
        assertThat(captor.getValue().firstName()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("rejects providers other than Google")
    void loadUserRejectsUnknownProvider() {
        CustomOAuth2UserService service = serviceReturning(googleAttributes());

        assertThatThrownBy(() -> service.loadUser(userRequest("github")))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("unsupported_provider"));
    }

    @Test
    @DisplayName("wraps provisioning failures into an OAuth2 error")
    void loadUserWrapsProvisioningFailure() {
        when(googleAuthenticationService.provisionUser(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("database down"));
        CustomOAuth2UserService service = serviceReturning(googleAttributes());
        OAuth2UserRequest request = userRequest("google");

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo("provisioning_failed"));
    }
}
