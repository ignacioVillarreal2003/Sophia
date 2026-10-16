package com.sophia.documentworkspace.api.util;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpRequestUtilities {

    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private HttpRequestUtilities() {
    }

    public static String clientIp(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        if (address == null) {
            return null;
        }
        return address.length() <= MAX_IP_LENGTH ? address : address.substring(0, MAX_IP_LENGTH);
    }

    public static String userAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_USER_AGENT_LENGTH
                ? userAgent
                : userAgent.substring(0, MAX_USER_AGENT_LENGTH);
    }
}
