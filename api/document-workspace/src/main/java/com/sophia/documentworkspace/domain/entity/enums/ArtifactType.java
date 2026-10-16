package com.sophia.documentworkspace.domain.entity.enums;

/**
 * Every concrete artifact type of the workspace. Types are grouped into
 * {@link Kind}s that correspond to the JPA subclasses of {@code Artifact}:
 * one class can host several types (e.g. every uploaded file is a
 * {@code FileArtifact} whose type narrows the format, and every text-content
 * document is a {@code DocumentArtifact}).
 *
 * <ul>
 *   <li>MARKDOWN_DOCUMENT: a Markdown note (Notion/Obsidian style)</li>
 *   <li>CHECKLIST: a Markdown document made of task-list items</li>
 *   <li>SPREADSHEET: a workbook (sheets, cells, formulas, formats) stored as JSON</li>
 * </ul>
 */
public enum ArtifactType {

    FOLDER(Kind.FOLDER),

    MARKDOWN_DOCUMENT(Kind.DOCUMENT),
    CHECKLIST(Kind.DOCUMENT),
    SPREADSHEET(Kind.DOCUMENT),

    PDF(Kind.FILE),
    DOCX(Kind.FILE),
    XLSX(Kind.FILE),
    PPTX(Kind.FILE),
    TXT(Kind.FILE),
    IMAGE(Kind.FILE),
    VIDEO(Kind.FILE),
    AUDIO(Kind.FILE),
    ZIP(Kind.FILE),
    BINARY_FILE(Kind.FILE),

    TEMPLATE(Kind.TEMPLATE);

    /** JPA subclass family an artifact type belongs to. */
    public enum Kind {
        FOLDER,
        DOCUMENT,
        FILE,
        TEMPLATE
    }

    private final Kind kind;

    ArtifactType(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isContainer() {
        return this == FOLDER;
    }
}
