package com.sophia.authentication.api.security.oauth2;

import com.sophia.authentication.domain.entity.User;
import com.sophia.authentication.application.service.GoogleAuthenticationService;
import com.sophia.authentication.infrastructure.security.oauth2.GoogleUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    public static final String LOCAL_USER_ID_ATTRIBUTE = "localUserId";

    private final GoogleAuthenticationService googleAuthenticationService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = fetchUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"google".equals(registrationId)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider", "Unsupported provider: " + registrationId, null));
        }

        try {
            Map<String, Object> attributes = oauth2User.getAttributes();
            GoogleUserInfo info = new GoogleUserInfo(
                    String.valueOf(attributes.get("sub")),
                    (String) attributes.get("email"),
                    Boolean.TRUE.equals(attributes.get("email_verified")),
                    attributes.get("given_name") != null
                            ? String.valueOf(attributes.get("given_name"))
                            : String.valueOf(attributes.get("email")),
                    attributes.get("family_name") != null ? String.valueOf(attributes.get("family_name")) : "",
                    attributes.get("picture") != null ? String.valueOf(attributes.get("picture")) : null);

            User user = googleAuthenticationService.provisionUser(info);

            Map<String, Object> enriched = new HashMap<>(attributes);
            enriched.put(LOCAL_USER_ID_ATTRIBUTE, user.getId().toString());
            return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), enriched, "sub");
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to provision OAuth2 user", e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("provisioning_failed", "Unable to complete Google sign-in", null));
        }
    }

    protected OAuth2User fetchUser(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}
