package com.sophia.authentication.infrastructure.service;

import com.sophia.authentication.domain.exception.BadRequestException;
import com.sophia.authentication.domain.exception.ResourceNotFoundException;
import com.sophia.authentication.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock private S3Client s3Client;

    private StorageService storageService;

    private static final byte[] PNG_BYTES = pngBytes();
    private static final byte[] JPEG_BYTES = jpegBytes();
    private static final byte[] WEBP_BYTES = webpBytes();

    private static byte[] pngBytes() {
        byte[] bytes = new byte[32];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }

    private static byte[] jpegBytes() {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        return bytes;
    }

    private static byte[] webpBytes() {
        byte[] bytes = new byte[32];
        byte[] riff = {'R', 'I', 'F', 'F', 0x20, 0x00, 0x00, 0x00, 'W', 'E', 'B', 'P'};
        System.arraycopy(riff, 0, bytes, 0, riff.length);
        return bytes;
    }

    @BeforeEach
    void setUp() {
        storageService = new StorageService(s3Client, TestFixtures.appProperties());
    }

    @Test
    @DisplayName("stores a real PNG in the bucket and names it after the sniffed type")
    void storesPng() {
        String filename = storageService.storeAvatar(
                new MockMultipartFile("file", "avatar.png", "image/png", PNG_BYTES));

        assertThat(filename).endsWith(".png");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("sophia-avatars-test");
        assertThat(request.getValue().key()).isEqualTo(filename);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("rejects files whose content does not match any allowed image signature")
    void rejectsSpoofedContent() {
        MockMultipartFile spoofed = new MockMultipartFile("file", "avatar.png", "image/png",
                "<?php echo 'not an image'; ?>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.storeAvatar(spoofed))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("rejects declared content types outside the allow list")
    void rejectsUnsupportedDeclaredType() {
        MockMultipartFile svg = new MockMultipartFile("file", "avatar.svg", "image/svg+xml",
                "<svg/>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.storeAvatar(svg))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("rejects empty uploads")
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "avatar.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.storeAvatar(empty))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("loads a stored object with its content type")
    void loadsStoredObject() {
        GetObjectResponse response = GetObjectResponse.builder().contentType("image/png").build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(response, PNG_BYTES));

        StorageService.StoredObject object = storageService.load("avatar.png");

        assertThat(object.content()).isEqualTo(PNG_BYTES);
        assertThat(object.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("maps a missing object to a 404")
    void missingObjectIsNotFound() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> storageService.load("missing.png"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deletes are best-effort and never throw")
    void deleteIsQuiet() {
        doThrow(S3Exception.builder().message("down").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatCode(() -> storageService.delete("avatar.png")).doesNotThrowAnyException();
        assertThatCode(() -> storageService.delete(" ")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("detects jpeg, png and webp signatures")
    void detectsSignatures() {
        assertThat(StorageService.detectImageType(JPEG_BYTES)).isEqualTo("image/jpeg");
        assertThat(StorageService.detectImageType(PNG_BYTES)).isEqualTo("image/png");
        assertThat(StorageService.detectImageType(WEBP_BYTES)).isEqualTo("image/webp");
        assertThat(StorageService.detectImageType(Arrays.copyOf(PNG_BYTES, 4))).isNull();
        assertThat(StorageService.detectImageType("plain text file content".getBytes(StandardCharsets.UTF_8)))
                .isNull();
    }
}
