package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.ArtifactMapper;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.ResourceAccessEvaluator;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.dto.request.CompleteMultipartUploadRequest;
import com.sophia.documentworkspace.domain.dto.request.InitiateMultipartUploadRequest;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.MultipartUploadResponse;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.FileVersion;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.UploadSession;
import com.sophia.documentworkspace.domain.entity.enums.AccessMode;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.domain.entity.enums.WorkspacePermission;
import com.sophia.documentworkspace.domain.event.ArtifactEvents;
import com.sophia.documentworkspace.domain.event.FileEvents;
import com.sophia.documentworkspace.domain.exception.ApiException;
import com.sophia.documentworkspace.domain.exception.BadRequestException;
import com.sophia.documentworkspace.domain.exception.ErrorCode;
import com.sophia.documentworkspace.domain.exception.ResourceNotFoundException;
import com.sophia.documentworkspace.domain.util.FileTypeResolver;
import com.sophia.documentworkspace.infrastructure.messaging.DomainEventPublisher;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.repository.FileVersionRepository;
import com.sophia.documentworkspace.infrastructure.repository.UploadSessionRepository;
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Large-file uploads: the server issues presigned part URLs so bytes flow
 * directly from the client to object storage, then completion verifies size
 * and SHA-256 by streaming the assembled object once, runs the virus-scan
 * hook, and materializes the {@link FileArtifact}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultipartUploadService {

    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final UploadSessionRepository sessionRepository;
    private final ArtifactRepository artifactRepository;
    private final FileVersionRepository fileVersionRepository;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ResourceAccessEvaluator accessEvaluator;
    private final StorageService storageService;
    private final FileService fileService;
    private final DomainEventPublisher eventPublisher;
    private final ArtifactMapper artifactMapper;
    private final ApplicationProperties properties;

    @Transactional
    public MultipartUploadResponse initiate(AuthenticatedUser user, UUID workspaceId,
                                            InitiateMultipartUploadRequest request) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.CREATE_CONTENT);
        if (request.sizeBytes() != null) {
            fileService.ensureWithinMaxSize(request.sizeBytes());
            fileService.ensureQuota(workspaceId, request.sizeBytes());
        }
        Folder parent = fileService.resolveParent(workspaceId, request.parentId());
        if (parent != null) {
            accessEvaluator.require(access, parent, AccessMode.WRITE, parent.resourceChain());
        }

        String storageKey = FileService.newStorageKey(workspaceId, request.filename());
        String uploadId = storageService.initiateMultipartUpload(storageKey, request.mimeType());
        Instant expiresAt = Instant.now().plus(SESSION_TTL);

        UploadSession session = sessionRepository.save(UploadSession.start(
                workspaceId, request.parentId(), user.id(), storageKey, uploadId,
                request.filename(), request.mimeType(), request.sizeBytes(),
                request.checksumSha256() == null ? null : request.checksumSha256().toLowerCase(),
                expiresAt));

        long partSize = properties.storage().multipartPartSize().toBytes();
        int partCount = request.expectedParts() != null
                ? request.expectedParts()
                : computePartCount(request.sizeBytes(), partSize);
        List<MultipartUploadResponse.PartUrl> urls = new ArrayList<>(partCount);
        for (int part = 1; part <= partCount; part++) {
            urls.add(new MultipartUploadResponse.PartUrl(part,
                    storageService.presignedPartUrl(storageKey, uploadId, part).toString()));
        }
        return new MultipartUploadResponse(session.getId(), urls, partSize, expiresAt);
    }

    @Transactional
    public ArtifactResponse complete(AuthenticatedUser user, UUID workspaceId, UUID sessionId,
                                     CompleteMultipartUploadRequest request) {
        UploadSession session = loadActiveSession(user, workspaceId, sessionId);

        storageService.completeMultipartUpload(session.getStorageKey(), session.getS3UploadId(),
                request.parts());

        // Verify the assembled object before it becomes reachable.
        Long actualSize = storageService.objectSize(session.getStorageKey());
        if (actualSize == null) {
            throw new ApiException(ErrorCode.UPLOAD_FAILED, HttpStatus.BAD_GATEWAY,
                    "Assembled object not found after completion");
        }
        if (session.getDeclaredSizeBytes() != null
                && !session.getDeclaredSizeBytes().equals(actualSize)) {
            abortAndFail(session, ErrorCode.UPLOAD_FAILED,
                    "Assembled size %d does not match the declared size %d"
                            .formatted(actualSize, session.getDeclaredSizeBytes()));
        }
        fileService.ensureWithinMaxSize(actualSize);
        fileService.ensureQuota(workspaceId, actualSize);

        String checksum = streamChecksumAndScan(session);
        if (session.getDeclaredChecksumSha256() != null
                && !session.getDeclaredChecksumSha256().equals(checksum)) {
            abortAndFail(session, ErrorCode.CHECKSUM_MISMATCH,
                    "Assembled object checksum does not match the declared SHA-256");
        }

        // Placement and label checks, then artifact materialization.
        WorkspaceAccessSnapshot access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.CREATE_CONTENT);
        Folder parent = fileService.resolveParent(workspaceId, session.getParentId());

        String mimeType = session.getDeclaredMimeType() != null ? session.getDeclaredMimeType()
                : FileTypeResolver.detectMimeType(InputStream.nullInputStream(),
                        session.getFilename(), null);
        ArtifactType type = FileTypeResolver.fromMimeType(mimeType);

        FileArtifact artifact = FileArtifact.create(type, workspaceId, parent,
                session.getFilename(), user.id(),
                mimeType, actualSize, session.getStorageKey(), checksum, session.getFilename());
        artifact = (FileArtifact) artifactRepository.save(artifact);
        fileVersionRepository.save(FileVersion.of(artifact.getId(), workspaceId, 1,
                session.getStorageKey(), mimeType, actualSize, checksum, user.id()));
        session.complete();

        eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(
                workspaceId, user.id(), artifact.getId(), type, artifact.getName(),
                session.getParentId()));
        eventPublisher.publish(FileEvents.FileUploaded.of(
                workspaceId, user.id(), artifact.getId(), type, mimeType, actualSize,
                session.getStorageKey()));
        return artifactMapper.toResponse(artifact);
    }

    @Transactional
    public void abort(AuthenticatedUser user, UUID workspaceId, UUID sessionId) {
        UploadSession session = loadActiveSession(user, workspaceId, sessionId);
        storageService.abortMultipartUploadQuietly(session.getStorageKey(), session.getS3UploadId());
        session.abort();
    }

    /** Aborts sessions whose TTL elapsed so incomplete parts do not leak storage. */
    @Scheduled(cron = "0 15 * * * *")
    @SchedulerLock(name = "upload-session-sweep", lockAtMostFor = "PT30M")
    @Transactional
    public void sweepExpiredSessions() {
        List<UploadSession> expired = sessionRepository
                .findAllByStatusAndExpiresAtBefore(UploadSession.Status.ACTIVE, Instant.now());
        for (UploadSession session : expired) {
            storageService.abortMultipartUploadQuietly(session.getStorageKey(),
                    session.getS3UploadId());
            session.abort();
        }
        if (!expired.isEmpty()) {
            log.info("Aborted {} expired multipart upload sessions", expired.size());
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private UploadSession loadActiveSession(AuthenticatedUser user, UUID workspaceId,
                                            UUID sessionId) {
        workspaceAuthorization.requirePermission(workspaceId, user,
                WorkspacePermission.CREATE_CONTENT);
        UploadSession session = sessionRepository.findByIdAndWorkspaceId(sessionId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session", sessionId));
        if (!session.getInitiatedBy().equals(user.id())) {
            throw new BadRequestException(ErrorCode.UPLOAD_FAILED,
                    "Only the user who initiated the upload can act on it");
        }
        if (!session.isActive()) {
            throw new BadRequestException(ErrorCode.UPLOAD_FAILED,
                    "Upload session is no longer active");
        }
        return session;
    }

    /** One streaming pass over the assembled object: SHA-256 + virus scan. */
    private String streamChecksumAndScan(UploadSession session) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream raw = storageService.getObject(session.getStorageKey());
                 DigestInputStream digesting = new DigestInputStream(raw, digest)) {
                fileService.scanStreamOrReject(digesting, session.getFilename());
                // Drain whatever the scanner did not consume so the digest covers everything.
                digesting.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.UPLOAD_FAILED, HttpStatus.BAD_GATEWAY,
                    "Failed to verify the assembled object", Map.of(), e);
        }
    }

    private void abortAndFail(UploadSession session, ErrorCode code, String message) {
        storageService.deleteObjectQuietly(session.getStorageKey());
        session.abort();
        throw new ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private int computePartCount(Long sizeBytes, long partSize) {
        if (sizeBytes == null) {
            throw new BadRequestException(ErrorCode.VALIDATION_FAILED,
                    "Either sizeBytes or expectedParts is required");
        }
        return (int) Math.max(1, (sizeBytes + partSize - 1) / partSize);
    }
}
