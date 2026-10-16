package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.domain.entity.YjsUpdate;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.infrastructure.repository.YjsUpdateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationService {

    private static final int MAX_UPDATE_BYTES = 2 * 1024 * 1024;

    private final YjsUpdateRepository yjsUpdateRepository;
    private final ArtifactAccessService artifactAccess;

    @Transactional
    public void appendUpdate(AuthenticatedUser user, UUID workspaceId, UUID documentId,
                             String base64Update) {
        byte[] payload = decode(base64Update);
        yjsUpdateRepository.save(YjsUpdate.of(documentId, workspaceId, payload, user.id(), false));
    }

    @Transactional(readOnly = true)
    public List<String> loadUpdates(AuthenticatedUser user, UUID workspaceId, UUID documentId) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.READ);
        return yjsUpdateRepository.findAllByDocumentIdOrderByIdAsc(documentId).stream()
                .map(update -> Base64.getEncoder().encodeToString(update.getPayload()))
                .toList();
    }

    @Transactional
    public void compact(AuthenticatedUser user, UUID workspaceId, UUID documentId,
                        String base64MergedState) {
        artifactAccess.require(user, workspaceId, documentId, AccessMode.WRITE);
        byte[] merged = decode(base64MergedState);

        List<YjsUpdate> existing = yjsUpdateRepository.findAllByDocumentIdOrderByIdAsc(documentId);
        if (existing.isEmpty()) {
            yjsUpdateRepository.save(YjsUpdate.of(documentId, workspaceId, merged, user.id(), true));
            return;
        }
        long maxId = existing.get(existing.size() - 1).getId();
        int removed = yjsUpdateRepository.deleteByDocumentIdUpTo(documentId, maxId);
        yjsUpdateRepository.save(YjsUpdate.of(documentId, workspaceId, merged, user.id(), true));
        log.debug("Compacted {} yjs updates of document {} into one snapshot", removed, documentId);
    }

    private byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new BadRequestException(ErrorCode.REALTIME_ERROR, "Empty CRDT update payload");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ErrorCode.REALTIME_ERROR, "CRDT update is not valid base64");
        }
        if (payload.length > MAX_UPDATE_BYTES) {
            throw new BadRequestException(ErrorCode.REALTIME_ERROR,
                    "CRDT update exceeds the maximum size");
        }
        return payload;
    }
}
