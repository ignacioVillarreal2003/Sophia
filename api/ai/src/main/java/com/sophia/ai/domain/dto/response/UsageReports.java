package com.sophia.ai.domain.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UsageReports {

    private UsageReports() {
    }

    /** Today's allowance, per user and (when the call names a workspace) per workspace. A limit of 0 is unlimited. */
    public record Quota(long userUsed, long userLimit, long workspaceUsed, long workspaceLimit, Instant resetsAt) {
    }

    public record Status(boolean enabled, boolean configured, boolean mock, String model, int maxSourceChars,
                         int maxSources, Quota quota) {
    }

    public record SettingsResponse(boolean enabled, long dailyTokensPerUser, long dailyTokensPerWorkspace,
                                   boolean configured, boolean mock, String model, Instant updatedAt, UUID updatedBy) {
    }

    public record DayRow(String day, long tokens, long requests) {
    }

    public record FeatureRow(String feature, long tokens, long requests) {
    }

    public record UserRow(UUID userId, String email, long tokens, long requests) {
    }

    public record AdminUsage(int days, long tokensToday, long tokensTotal, long requestsTotal, List<DayRow> perDay,
                             List<FeatureRow> perFeature, List<UserRow> topUsers) {
    }
}
