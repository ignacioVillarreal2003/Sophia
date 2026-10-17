package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.application.mapper.ArtifactMapper;
import com.sophia.documentworkspace.application.security.ArtifactAccessService;
import com.sophia.documentworkspace.application.security.AuthenticatedUser;
import com.sophia.documentworkspace.application.security.ResourceAccessEvaluator;
import com.sophia.documentworkspace.application.security.WorkspaceAccessSnapshot;
import com.sophia.documentworkspace.application.security.WorkspaceAuthorizationService;
import com.sophia.documentworkspace.domain.config.ApplicationProperties;
import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.DownloadUrlResponse;
import com.sophia.documentworkspace.domain.dto.response.FileVersionResponse;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.FileVersion;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.Workspace;
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
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import com.sophia.documentworkspace.infrastructure.service.VirusScanner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileService {

    private final ArtifactRepository artifactRepository;
    private final FileVersionRepository fileVersionRepository;
    private final com.sophia.documentworkspace.infrastructure.repository.WorkspaceRepository workspaceRepository;
    private final com.sophia.documentworkspace.infrastructure.repository.WorkspaceLimitsRepository limitsRepository;
    private final WorkspaceAuthorizationService workspaceAuthorization;
    private final ArtifactAccessService artifactAccess;
    private final ResourceAccessEvaluator accessEvaluator;
    private final StorageService storageService;
    private final StorageQuotaService quotaService;
    private final VirusScanner virusScanner;
    private final DomainEventPublisher eventPublisher;
    private final ArtifactMapper artifactMapper;
    private final ApplicationProperties properties;

    @Transactional
    public ArtifactResponse upload(AuthenticatedUser user, UUID workspaceId, UUID parentId,
                                   MultipartFile file) {
        WorkspaceAccessSnapshot access = workspaceAuthorization.requirePermission(
                workspaceId, user, WorkspacePermission.CREATE_CONTENT);
        if (file.isEmpty()) {
            throw new BadRequestException(ErrorCode.UPLOAD_FAILED, "Uploaded file is empty");
        }
        ensureWithinMaxSize(file.getSize());
        ensureQuota(workspaceId, file.getSize());

        Folder parent = resolveParent(workspaceId, parentId);
        if (parent != null) {
            accessEvaluator.require(access, parent, AccessMode.WRITE, parent.resourceChain());
        }

        byte[] content = readBytes(file);
        scanOrReject(content, file.getOriginalFilename());

        String checksum = sha256Hex(content);
        String mimeType = FileTypeResolver.detectMimeType(
                new ByteArrayInputStream(content), file.getOriginalFilename(), file.getContentType());
        ArtifactType type = FileTypeResolver.fromMimeType(mimeType);
        String storageKey = newStorageKey(workspaceId, file.getOriginalFilename());

        storageService.putObject(storageKey, content, mimeType);

        FileArtifact artifact = FileArtifact.create(type, workspaceId, parent,
                displayName(file.getOriginalFilename()), user.id(),
                mimeType, file.getSize(), storageKey, checksum, file.getOriginalFilename());
        artifact = (FileArtifact) artifactRepository.save(artifact);
        fileVersionRepository.save(FileVersion.of(artifact.getId(), workspaceId, 1,
                storageKey, mimeType, file.getSize(), checksum, user.id()));

        eventPublisher.publish(ArtifactEvents.ArtifactCreated.of(
                workspaceId, user.id(), artifact.getId(), type, artifact.getName(), parentId));
        eventPublisher.publish(FileEvents.FileUploaded.of(
                workspaceId, user.id(), artifact.getId(), type, mimeType, file.getSize(), storageKey));
        return artifactMapper.toResponse(artifact);
    }

    @Transactional
    public ArtifactResponse uploadNewVersion(AuthenticatedUser user, UUID workspaceId,
                                             UUID artifactId, MultipartFile file) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.WRITE);
        FileArtifact artifact = asFile(result.artifact());
        if (file.isEmpty()) {
            throw new BadRequestException(ErrorCode.UPLOAD_FAILED, "Uploaded file is empty");
        }
        ensureWithinMaxSize(file.getSize());
        long delta = file.getSize() - (artifact.getSizeBytes() == null ? 0 : artifact.getSizeBytes());
        if (delta > 0) {
            ensureQuota(workspaceId, delta);
        }

        byte[] content = readBytes(file);
        scanOrReject(content, file.getOriginalFilename());

        String checksum = sha256Hex(content);
        String mimeType = FileTypeResolver.detectMimeType(
                new ByteArrayInputStream(content), file.getOriginalFilename(), file.getContentType());
        String storageKey = newStorageKey(workspaceId, file.getOriginalFilename());
        storageService.putObject(storageKey, content, mimeType);

        long nextSeq = fileVersionRepository.findFirstByArtifactIdOrderBySeqDesc(artifactId)
                .map(v -> v.getSeq() + 1)
                .orElse(1L);
        fileVersionRepository.save(FileVersion.of(artifactId, workspaceId, nextSeq,
                storageKey, mimeType, file.getSize(), checksum, user.id()));
        artifact.attachContent(storageKey, mimeType, file.getSize(), checksum);

        eventPublisher.publish(FileEvents.FileVersionAdded.of(
                workspaceId, user.id(), artifactId, nextSeq, mimeType, file.getSize(), storageKey));
        return artifactMapper.toResponse(artifact);
    }

    @Transactional(readOnly = true)
    /** Metadata and an open stream of the current content, for authenticated inline viewing and download. */
    public record FileContent(String filename, String contentType, Long size, java.io.InputStream stream) {
    }

    @Transactional(readOnly = true)
    public FileContent openContent(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        FileArtifact artifact = asFile(result.artifact());
        if (artifact.getStorageKey() == null) {
            throw new ResourceNotFoundException("File content", artifactId);
        }
        String name = artifact.getOriginalFilename() == null ? artifact.getName() : artifact.getOriginalFilename();
        return new FileContent(name,
                artifact.getMimeType() == null ? "application/octet-stream" : artifact.getMimeType(),
                artifact.getSizeBytes(), storageService.getObject(artifact.getStorageKey()));
    }

    public DownloadUrlResponse downloadUrl(AuthenticatedUser user, UUID workspaceId, UUID artifactId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        FileArtifact artifact = asFile(result.artifact());
        if (artifact.getStorageKey() == null) {
            throw new ResourceNotFoundException("File content", artifactId);
        }
        var url = storageService.presignedDownloadUrl(
                artifact.getStorageKey(), artifact.getOriginalFilename());
        return new DownloadUrlResponse(url.toString(),
                Instant.now().plus(properties.storage().presignedUrlTtl()));
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse previewUrl(AuthenticatedUser user, UUID workspaceId, UUID artifactId,
                                          String kind) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        FileArtifact file = asFile(result.artifact());
        Object key = "thumbnail".equals(kind) ? file.getThumbnailStorageKey() : file.getPreviewStorageKey();
        if (key == null) {
            throw new ApiException(ErrorCode.PREVIEW_UNAVAILABLE, HttpStatus.NOT_FOUND,
                    "No %s has been generated for this artifact".formatted(kind));
        }
        var url = storageService.presignedDownloadUrl(String.valueOf(key), null);
        return new DownloadUrlResponse(url.toString(),
                Instant.now().plus(properties.storage().presignedUrlTtl()));
    }

    @Transactional(readOnly = true)
    public java.util.List<FileVersionResponse> listVersions(AuthenticatedUser user, UUID workspaceId,
                                                            UUID artifactId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.READ);
        FileArtifact artifact = asFile(result.artifact());
        return fileVersionRepository.findAllByArtifactIdOrderBySeqDesc(artifactId).stream()
                .map(version -> toVersionResponse(version, artifact))
                .toList();
    }

    @Transactional
    public ArtifactResponse restoreVersion(AuthenticatedUser user, UUID workspaceId,
                                           UUID artifactId, UUID versionId) {
        var result = artifactAccess.require(user, workspaceId, artifactId, AccessMode.WRITE);
        FileArtifact artifact = asFile(result.artifact());
        FileVersion version = fileVersionRepository.findByIdAndArtifactId(versionId, artifactId)
                .orElseThrow(() -> new ResourceNotFoundException("File version", versionId));

        artifact.attachContent(version.getStorageKey(), version.getMimeType(),
                version.getSizeBytes(), version.getChecksumSha256());
        eventPublisher.publish(FileEvents.FileVersionRestored.of(
                workspaceId, user.id(), artifactId, version.getSeq()));
        return artifactMapper.toResponse(artifact);
    }

    void ensureQuota(UUID workspaceId, long additionalBytes) {
        Long quotaMb = limitsRepository.findById(workspaceId)
                .map(com.sophia.documentworkspace.domain.entity.WorkspaceLimits::getStorageQuotaMb)
                .orElse(null);
        if (quotaMb == null) {
            return;
        }
        long used = artifactRepository.sumFileSizes(workspaceId);
        if (used + additionalBytes > quotaMb * 1024L * 1024L) {
            throw new ApiException(ErrorCode.QUOTA_EXCEEDED, HttpStatus.CONFLICT,
                    "This upload would exceed the workspace storage quota",
                    Map.of("quotaMb", quotaMb, "usedBytes", used));
        }
        quotaService.evaluate(workspaceId, additionalBytes);
    }

    void ensureWithinMaxSize(long sizeBytes) {
        long max = properties.uploads().maxFileSize().toBytes();
        if (sizeBytes > max) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds the maximum allowed size of %d bytes".formatted(max));
        }
    }

    void scanOrReject(byte[] content, String filename) {
        VirusScanner.ScanResult scan = virusScanner.scan(new ByteArrayInputStream(content), filename);
        if (scan.infected()) {
            throw new ApiException(ErrorCode.VIRUS_DETECTED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Upload rejected by malware scanning",
                    Map.of("signature", String.valueOf(scan.signature())));
        }
    }

    void scanStreamOrReject(InputStream content, String filename) {
        VirusScanner.ScanResult scan = virusScanner.scan(content, filename);
        if (scan.infected()) {
            throw new ApiException(ErrorCode.VIRUS_DETECTED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Upload rejected by malware scanning",
                    Map.of("signature", String.valueOf(scan.signature())));
        }
    }

    Folder resolveParent(UUID workspaceId, UUID parentId) {
        if (parentId == null) {
            return null;
        }
        Artifact parent = artifactRepository
                .findByIdAndWorkspaceIdAndDeletedAtIsNull(parentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent folder", parentId));
        if (!(parent instanceof Folder folder)) {
            throw new BadRequestException(ErrorCode.INVALID_HIERARCHY,
                    "The parent artifact is not a folder");
        }
        return folder;
    }

    static String newStorageKey(UUID workspaceId, String filename) {
        String safeName = filename == null ? "file"
                : filename.replaceAll("[^A-Za-z0-9._-]", "_");
        return "ws/%s/%s/%s".formatted(workspaceId, UUID.randomUUID(), safeName);
    }

    static String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String displayName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "Untitled file";
        }
        int slash = Math.max(originalFilename.lastIndexOf('/'), originalFilename.lastIndexOf('\\'));
        return slash >= 0 ? originalFilename.substring(slash + 1) : originalFilename;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.UPLOAD_FAILED, HttpStatus.BAD_REQUEST,
                    "Failed to read the uploaded file", Map.of(), e);
        }
    }

    private FileArtifact asFile(Artifact artifact) {
        if (!(artifact instanceof FileArtifact file)) {
            throw new BadRequestException(ErrorCode.UNSUPPORTED_ARTIFACT_TYPE,
                    "Artifact %s is not a file".formatted(artifact.getId()));
        }
        return file;
    }

    private FileVersionResponse toVersionResponse(FileVersion version, FileArtifact artifact) {
        boolean current = version.getStorageKey().equals(artifact.getStorageKey());
        return new FileVersionResponse(version.getId(), version.getArtifactId(), version.getSeq(),
                version.getMimeType(), version.getSizeBytes(), version.getChecksumSha256(),
                version.getUploadedBy(), current, version.getCreatedAt());
    }
}
