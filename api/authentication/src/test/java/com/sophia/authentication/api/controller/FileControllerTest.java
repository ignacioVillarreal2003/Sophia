package com.sophia.authentication.api.controller;

import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.infrastructure.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock private StorageService storageService;

    @InjectMocks
    private FileController fileController;

    @Test
    @DisplayName("streams a stored file with its content type and cache headers")
    void downloadsStoredFile() {
        byte[] content = {1, 2, 3};
        when(storageService.load("avatar.png"))
                .thenReturn(new StorageService.StoredObject(content, "image/png"));

        ResponseEntity<byte[]> response = fileController.download("avatar.png");

        assertThat(response.getBody()).isEqualTo(content);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=3600");
    }

    @Test
    @DisplayName("defaults to octet-stream when the stored content type is missing")
    void defaultsToOctetStream() {
        when(storageService.load("blob.bin"))
                .thenReturn(new StorageService.StoredObject(new byte[]{1}, null));

        ResponseEntity<byte[]> response = fileController.download("blob.bin");

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    @DisplayName("rejects unsafe filenames without touching storage")
    void rejectsUnsafeFilename() {
        assertThatThrownBy(() -> fileController.download("../secrets"))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(storageService);
    }
}
