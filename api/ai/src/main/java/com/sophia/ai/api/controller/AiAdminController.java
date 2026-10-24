package com.sophia.ai.api.controller;

import com.sophia.ai.api.security.CurrentUser;
import com.sophia.ai.application.security.AuthenticatedUser;
import com.sophia.ai.application.service.AiSettingsService;
import com.sophia.ai.application.service.UsageService;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.request.GenerationRequests.UpdateSettingsRequest;
import com.sophia.ai.domain.dto.response.ApiResponse;
import com.sophia.ai.domain.dto.response.UsageReports.AdminUsage;
import com.sophia.ai.domain.dto.response.UsageReports.SettingsResponse;
import com.sophia.ai.domain.entity.AiSettings;
import com.sophia.ai.infrastructure.llm.ModelGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
@Tag(name = "AI administration", description = "Limits and usage (WORKSPACE_MANAGE)")
public class AiAdminController {

    private final AiSettingsService settingsService;
    private final UsageService usageService;
    private final ModelGateway gateway;
    private final ApplicationProperties properties;

    @GetMapping("/settings")
    @Operation(summary = "The runtime limits and whether the provider is configured")
    public ResponseEntity<ApiResponse<SettingsResponse>> settings(@CurrentUser AuthenticatedUser user) {
        settingsService.requireManage(user);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(settingsService.get())));
    }

    @PutMapping("/settings")
    @Operation(summary = "Turn the AI features on or off and change the daily token limits (0 = unlimited)")
    public ResponseEntity<ApiResponse<SettingsResponse>> update(@CurrentUser AuthenticatedUser user,
                                                                @Valid @RequestBody UpdateSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(toResponse(settingsService.update(user, request))));
    }

    @GetMapping("/usage")
    @Operation(summary = "Tokens and requests per day, per feature and per user over the last days")
    public ResponseEntity<ApiResponse<AdminUsage>> usage(@CurrentUser AuthenticatedUser user,
                                                         @RequestParam(defaultValue = "14") int days) {
        return ResponseEntity.ok(ApiResponse.ok(usageService.report(user, days)));
    }

    private SettingsResponse toResponse(AiSettings s) {
        return new SettingsResponse(s.isEnabled(), s.getDailyTokensPerUser(), s.getDailyTokensPerWorkspace(),
                gateway.configured(), properties.ai().isMock(), gateway.model(), s.getUpdatedAt(), s.getUpdatedBy());
    }
}
