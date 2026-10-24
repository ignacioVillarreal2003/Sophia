package com.sophia.ai.application.service;

import com.sophia.ai.application.prompt.PromptFactory.SourceText;
import com.sophia.ai.domain.config.ApplicationProperties;
import com.sophia.ai.domain.dto.request.GenerationRequests.SourceRef;
import com.sophia.ai.domain.exception.ApiException;
import com.sophia.ai.domain.exception.BadRequestException;
import com.sophia.ai.domain.exception.ErrorCode;
import com.sophia.ai.infrastructure.client.WorkspaceDocumentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns the documents a request names into text for the prompt. Nothing is cut silently: when the material does not
 * fit, the request is refused and says how much there is, so the user can choose fewer documents instead of getting
 * an answer built from half of what they picked.
 */
@Component
@RequiredArgsConstructor
public class SourceLoader {

    /** A document that was read: which one, its title and its text. */
    public record LoadedSource(UUID id, String title, String text) {
    }

    private final WorkspaceDocumentClient documents;
    private final ApplicationProperties properties;

    public List<SourceText> load(String authorization, UUID workspaceId, List<SourceRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<LoadedSource> loaded = loadAll(authorization, workspaceId, refs.stream().map(SourceRef::artifactId).toList(),
                properties.ai().maxSourceChars());
        return loaded.stream().map(s -> new SourceText(s.title(), s.text())).toList();
    }

    /**
     * Reads the documents with the caller's token. {@code maxChars} bounds their total size; a request that exceeds
     * it is refused with the real figures rather than shortened.
     */
    public List<LoadedSource> loadAll(String authorization, UUID workspaceId, List<UUID> artifactIds, int maxChars) {
        if (artifactIds == null || artifactIds.isEmpty()) {
            return List.of();
        }
        if (workspaceId == null) {
            throw new BadRequestException("A workspace is needed to read documents");
        }
        var ids = new LinkedHashSet<>(artifactIds);
        int maxSources = properties.ai().maxSources();
        if (ids.size() > maxSources) {
            throw new BadRequestException("Choose at most %d documents".formatted(maxSources));
        }
        List<LoadedSource> out = new ArrayList<>();
        long total = 0;
        for (UUID id : ids) {
            var document = documents.load(authorization, workspaceId, id);
            String text = document.text() == null ? "" : document.text().strip();
            if (text.isEmpty()) {
                throw new ApiException(ErrorCode.SOURCE_NOT_SUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY,
                        "\"%s\" is empty, so there is nothing to work from".formatted(document.title()));
            }
            total += text.length();
            out.add(new LoadedSource(id, document.title(), text));
        }
        if (total > maxChars) {
            throw new ApiException(ErrorCode.SOURCE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                    "The selected documents are too long to use together. Choose fewer or shorter ones.",
                    Map.of("characters", total, "maxCharacters", maxChars));
        }
        return out;
    }
}
