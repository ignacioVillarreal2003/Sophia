package com.sophia.ai.application.service;

import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.response.UsageReports.AdminUsage;
import com.sophia.ai.domain.dto.response.UsageReports.DayRow;
import com.sophia.ai.domain.dto.response.UsageReports.FeatureRow;
import com.sophia.ai.domain.dto.response.UsageReports.Quota;
import com.sophia.ai.domain.dto.response.UsageReports.UserRow;
import com.sophia.ai.domain.entity.AiGeneration;
import com.sophia.ai.domain.entity.AiSettings;
import com.sophia.ai.domain.entity.enums.AiFeature;
import com.sophia.ai.domain.entity.enums.GenerationStatus;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.llm.ModelGateway.TokenUsage;
import com.sophia.ai.infrastructure.repository.AiGenerationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Daily token quotas (per user and per workspace) and the log of what was spent. The day is a UTC day so that every
 * instance and every user agrees on when the quota resets. A limit of 0 means no limit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final AiGenerationRepository repository;
    private final AiSettingsService settingsService;
    private final ApplicationProperties properties;
    private final MeterRegistry meters;

    static Instant startOfToday() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    @Transactional(readOnly = true)
    public Quota quota(UUID userId, UUID workspaceId) {
        AiSettings settings = settingsService.get();
        Instant since = startOfToday();
        long workspaceUsed = workspaceId == null ? 0 : repository.tokensUsedInWorkspace(workspaceId, since);
        return new Quota(repository.tokensUsedByUser(userId, since), settings.getDailyTokensPerUser(),
                workspaceUsed, settings.getDailyTokensPerWorkspace(), since.plus(Duration.ofDays(1)));
    }

    /** Refuses a new generation once a limit is already spent; what one call may overshoot is bounded by max_tokens. */
    public void assertWithinQuota(UUID userId, UUID workspaceId) {
        Quota quota = quota(userId, workspaceId);
        boolean userOut = quota.userLimit() > 0 && quota.userUsed() >= quota.userLimit();
        boolean workspaceOut = workspaceId != null && quota.workspaceLimit() > 0
                && quota.workspaceUsed() >= quota.workspaceLimit();
        if (userOut || workspaceOut) {
            throw new ApiException(ErrorCode.QUOTA_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS,
                    userOut ? "You have used today's AI allowance" : "This workspace has used today's AI allowance",
                    Map.of("scope", userOut ? "USER" : "WORKSPACE", "resetsAt", quota.resetsAt().toString()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthenticatedUser user, UUID workspaceId, AiFeature feature, GenerationStatus status,
                       String model, TokenUsage usage, long durationMs, String errorCode) {
        repository.save(AiGeneration.of(user.id(), user.email(), workspaceId, feature, status, model, usage.input(),
                usage.output(), usage.cacheRead(), usage.cacheWrite(), durationMs, errorCode));
        meters.counter("ai.generations", "feature", feature.name(), "status", status.name()).increment();
        meters.counter("ai.tokens", "kind", "input").increment(usage.input() + usage.cacheRead() + usage.cacheWrite());
        meters.counter("ai.tokens", "kind", "output").increment(usage.output());
    }

    @Transactional(readOnly = true)
    public AdminUsage report(AuthenticatedUser admin, int days) {
        settingsService.requireManage(admin);
        int window = Math.max(1, Math.min(days, 90));
        Instant since = startOfToday().minus(Duration.ofDays(window - 1L));
        var perDay = repository.byDay(since).stream()
                .map(d -> new DayRow(d.getDay(), d.getTokens(), d.getRequests())).toList();
        var perFeature = repository.byFeature(since).stream()
                .map(f -> new FeatureRow(f.getFeature(), f.getTokens(), f.getRequests())).toList();
        var topUsers = repository.topUsers(since, PageRequest.of(0, 10)).stream()
                .map(u -> new UserRow(u.getUserId(), u.getUserEmail(), u.getTokens(), u.getRequests())).toList();
        long today = perDay.stream().filter(d -> d.day().equals(LocalDate.now(ZoneOffset.UTC).toString()))
                .mapToLong(DayRow::tokens).sum();
        return new AdminUsage(window, today, perDay.stream().mapToLong(DayRow::tokens).sum(),
                perDay.stream().mapToLong(DayRow::requests).sum(), perDay, perFeature, topUsers);
    }

    /** The log has no value past the point where billing questions stop. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeOld() {
        Instant cutoff = Instant.now().minus(properties.ai().usageRetention());
        int removed = repository.deleteOlderThan(cutoff);
        if (removed > 0) {
            log.info("Deleted {} usage rows older than {}", removed, cutoff);
        }
    }
}
