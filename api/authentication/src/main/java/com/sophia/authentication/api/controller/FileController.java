package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.infrastructure.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@Tag(name = "Files", description = "Public download of stored files")
public class FileController {

    private static final Pattern SAFE_FILENAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$");

    private final StorageService storageService;

    @GetMapping("/files/{filename}")
    @Operation(summary = "Download a stored public file")
    public ResponseEntity<byte[]> download(@PathVariable String filename) {
        if (!SAFE_FILENAME.matcher(filename).matches()) {
            throw new ResourceNotFoundException("File", filename);
        }
        StorageService.StoredObject object = storageService.load(filename);
        MediaType mediaType = object.contentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(object.contentType());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(mediaType)
                .body(object.content());
    }
}
