package com.sophia.documentworkspace.application.mapper;

import com.sophia.documentworkspace.domain.dto.response.ArtifactResponse;
import com.sophia.documentworkspace.domain.dto.response.BreadcrumbEntry;
import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.DocumentArtifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.entity.Folder;
import com.sophia.documentworkspace.domain.entity.TemplateArtifact;
import org.mapstruct.Mapper;
import org.mapstruct.SubclassExhaustiveStrategy;
import org.mapstruct.SubclassMapping;

@Mapper(subclassExhaustiveStrategy = SubclassExhaustiveStrategy.RUNTIME_EXCEPTION)
public interface ArtifactMapper {

    @SubclassMapping(source = Folder.class, target = ArtifactResponse.class)
    @SubclassMapping(source = DocumentArtifact.class, target = ArtifactResponse.class)
    @SubclassMapping(source = FileArtifact.class, target = ArtifactResponse.class)
    @SubclassMapping(source = TemplateArtifact.class, target = ArtifactResponse.class)
    ArtifactResponse toResponse(Artifact artifact);

    BreadcrumbEntry toBreadcrumbEntry(Artifact artifact);
}
