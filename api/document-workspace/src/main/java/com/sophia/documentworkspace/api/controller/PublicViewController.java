package com.sophia.documentworkspace.api.controller;

import com.sophia.documentworkspace.application.service.PublicLinkService;
import com.sophia.documentworkspace.domain.dto.response.ApiResponse;
import com.sophia.documentworkspace.domain.dto.response.PublicViewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The only unauthenticated read endpoint of the service: the token in the URL is the credential. */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public view", description = "Anonymous read access through a public link")
public class PublicViewController {

    private final PublicLinkService publicLinkService;

    @GetMapping("/{token}")
    @Operation(summary = "Read the artifact behind a public link")
    public ResponseEntity<ApiResponse<PublicViewResponse>> view(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(publicLinkService.view(token)));
    }
}
