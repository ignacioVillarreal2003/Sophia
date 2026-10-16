package com.sophia.authentication.api.security.oauth2;

import com.sophia.authentication.domain.config.ApplicationProperties;
import com.sophia.authentication.infrastructure.service.OneTimeCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OneTimeCodeService oneTimeCodeService;
    private final ApplicationProperties properties;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        String userId = principal.getAttribute(CustomOAuth2UserService.LOCAL_USER_ID_ATTRIBUTE);
        if (userId == null) {
            log.error("OAuth2 principal is missing the local user id attribute");
            getRedirectStrategy().sendRedirect(request, response, errorRedirect());
            return;
        }

        String code = oneTimeCodeService.issue(UUID.fromString(userId));
        String target = UriComponentsBuilder
                .fromUriString(properties.oauth2().authorizedRedirectUris().getFirst())
                .queryParam("code", code)
                .build()
                .toUriString();

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private String errorRedirect() {
        return UriComponentsBuilder
                .fromUriString(properties.oauth2().authorizedRedirectUris().getFirst())
                .queryParam("error", "oauth2_failed")
                .build()
                .toUriString();
    }
}
