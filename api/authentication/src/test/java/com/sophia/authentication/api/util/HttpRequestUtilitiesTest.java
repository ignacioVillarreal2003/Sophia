package com.sophia.authentication.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestUtilitiesTest {

    @Test
    @DisplayName("returns the remote address resolved by the container")
    void clientIpUsesRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.5");

        assertThat(HttpRequestUtilities.clientIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("ignores a spoofed X-Forwarded-For header")
    void clientIpIgnoresForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.setRemoteAddr("10.0.0.7");

        assertThat(HttpRequestUtilities.clientIp(request)).isEqualTo("10.0.0.7");
    }

    @Test
    @DisplayName("truncates an overlong remote address to 45 characters")
    void clientIpTruncatesLongValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("a".repeat(60));

        assertThat(HttpRequestUtilities.clientIp(request)).hasSize(45);
    }

    @Test
    @DisplayName("returns null user agent when the header is absent")
    void userAgentMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(HttpRequestUtilities.userAgent(request)).isNull();
    }

    @Test
    @DisplayName("truncates an overlong user agent to 512 characters")
    void userAgentTruncated() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "x".repeat(600));

        assertThat(HttpRequestUtilities.userAgent(request)).hasSize(512);
    }
}
