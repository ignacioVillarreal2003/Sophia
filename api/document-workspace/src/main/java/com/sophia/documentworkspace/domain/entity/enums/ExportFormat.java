package com.sophia.documentworkspace.domain.entity.enums;

public enum ExportFormat {

    MARKDOWN("text/markdown; charset=utf-8", "md"),
    HTML("text/html; charset=utf-8", "html"),
    JSON("application/json", "json"),
    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
    TXT("text/plain; charset=utf-8", "txt"),
    CSV("text/csv; charset=utf-8", "csv"),
    PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    ZIP("application/zip", "zip");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
