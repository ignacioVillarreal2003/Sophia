package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.BreadcrumbEntry;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.TemplateArtifact;
import com.sophia.documentworkspace.domain.entity.enums.ArtifactType;
import com.sophia.documentworkspace.support.TestIds;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every concrete {@link Artifact} subtype must have a {@code @SubclassMapping}
 * in {@link ArtifactMapper}, or {@code toResponse} throws at runtime for that
 * type (the mapper is configured with {@code RUNTIME_EXCEPTION} exhaustiveness).
 * One test per subtype guards against a new or renamed type silently losing
 * its mapping.
 */
class ArtifactMapperTest {

    private final ArtifactMapper mapper = Mappers.getMapper(ArtifactMapper.class);
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Test
    void mapsAFolder() {
        Folder folder = TestIds.withRandomId(Folder.create(
                workspaceId, null, "Projects", ownerId, "All projects", "#336699"));

        ArtifactResponse response = mapper.toResponse(folder);

        assertThat(response.id()).isEqualTo(folder.getId());
        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        assertThat(response.name()).isEqualTo("Projects");
        assertThat(response.type()).isEqualTo(ArtifactType.FOLDER);
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.description()).isEqualTo("All projects");
        assertThat(response.color()).isEqualTo("#336699");
    }

    @Test
    void mapsADocument() {
        DocumentArtifact document = TestIds.withRandomId(DocumentArtifact.create(
                ArtifactType.MARKDOWN_DOCUMENT, workspaceId, null, "Runbook", ownerId,
                "Operations notes", "# Runbook"));

        ArtifactResponse response = mapper.toResponse(document);

        assertThat(response.type()).isEqualTo(ArtifactType.MARKDOWN_DOCUMENT);
        assertThat(response.description()).isEqualTo("Operations notes");
        assertThat(response.contentVersion()).isZero();
    }

    @Test
    void mapsAFile() {
        FileArtifact file = TestIds.withRandomId(FileArtifact.create(
                ArtifactType.PDF, workspaceId, null, "report.pdf", ownerId,
                "application/pdf", 2048L, "storage-key-1", "abc123", "report.pdf"));

        ArtifactResponse response = mapper.toResponse(file);

        assertThat(response.type()).isEqualTo(ArtifactType.PDF);
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        assertThat(response.sizeBytes()).isEqualTo(2048L);
        assertThat(response.originalFilename()).isEqualTo("report.pdf");
    }

    @Test
    void mapsATemplate() {
        TemplateArtifact template = TestIds.withRandomId(TemplateArtifact.create(
                workspaceId, null, "Meeting notes", ownerId,
                ArtifactType.MARKDOWN_DOCUMENT, "Weekly sync template", "# Agenda"));

        ArtifactResponse response = mapper.toResponse(template);

        assertThat(response.type()).isEqualTo(ArtifactType.TEMPLATE);
        assertThat(response.name()).isEqualTo("Meeting notes");
    }

    @Test
    void mapsToABreadcrumbEntry() {
        Folder folder = TestIds.withRandomId(Folder.create(
                workspaceId, null, "Projects", ownerId, null, null));

        BreadcrumbEntry entry = mapper.toBreadcrumbEntry(folder);

        assertThat(entry.id()).isEqualTo(folder.getId());
        assertThat(entry.name()).isEqualTo("Projects");
        assertThat(entry.type()).isEqualTo(ArtifactType.FOLDER);
    }
}
