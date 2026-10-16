package com.sophia.documentworkspace.domain.util;

import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeResolverTest {

    @Test
    void mapsKnownMimeTypesToArtifactTypes() {
        assertThat(FileTypeResolver.fromMimeType("application/pdf")).isEqualTo(ArtifactType.PDF);
        assertThat(FileTypeResolver.fromMimeType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .isEqualTo(ArtifactType.DOCX);
        assertThat(FileTypeResolver.fromMimeType("image/png")).isEqualTo(ArtifactType.IMAGE);
        assertThat(FileTypeResolver.fromMimeType("video/mp4")).isEqualTo(ArtifactType.VIDEO);
        assertThat(FileTypeResolver.fromMimeType("audio/mpeg")).isEqualTo(ArtifactType.AUDIO);
        assertThat(FileTypeResolver.fromMimeType("application/zip")).isEqualTo(ArtifactType.ZIP);
        assertThat(FileTypeResolver.fromMimeType("text/plain")).isEqualTo(ArtifactType.TXT);
        assertThat(FileTypeResolver.fromMimeType("application/x-unknown"))
                .isEqualTo(ArtifactType.BINARY_FILE);
        assertThat(FileTypeResolver.fromMimeType(null)).isEqualTo(ArtifactType.BINARY_FILE);
    }

    @Test
    void declaredTypeWinsUnlessGeneric() {
        String detected = FileTypeResolver.detectMimeType(
                new ByteArrayInputStream("hello".getBytes()), "note.txt", "text/markdown");
        assertThat(detected).isEqualTo("text/markdown");
    }

    @Test
    void fallsBackToDetectionForGenericDeclaredType() {
        String detected = FileTypeResolver.detectMimeType(
                new ByteArrayInputStream("plain".getBytes()), "note.txt",
                "application/octet-stream");
        assertThat(detected).isEqualTo("text/plain");
    }
}
