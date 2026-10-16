package com.sophia.documentworkspace.application.service;

import com.sophia.documentworkspace.domain.entity.Artifact;
import com.sophia.documentworkspace.domain.entity.FileArtifact;
import com.sophia.documentworkspace.domain.event.FileEvents;
import com.sophia.documentworkspace.infrastructure.repository.ArtifactRepository;
import com.sophia.documentworkspace.infrastructure.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Asynchronous preview/thumbnail generation, triggered after the upload
 * transaction commits. Artifacts get:
 * <ul>
 *   <li>images → 512px JPEG thumbnail</li>
 *   <li>PDF → first-page JPEG thumbnail + extracted-text preview</li>
 *   <li>DOCX/XLSX/PPTX → extracted-text preview (POI)</li>
 *   <li>plain text / markdown → truncated text preview</li>
 * </ul>
 * Generated assets live next to the content ({@code previews/} prefix) and
 * their keys are stored in the artifact metadata
 * ({@code thumbnailStorageKey}, {@code previewStorageKey}). Failures are
 * logged, never propagated: previews are best-effort by design. Videos are
 * intentionally skipped (frame extraction needs an ffmpeg sidecar; the hook
 * point is this class).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewService {

    private static final int THUMBNAIL_SIZE = 512;
    private static final int PREVIEW_TEXT_LIMIT = 20_000;

    private final ArtifactRepository artifactRepository;
    private final StorageService storageService;

    // The listeners carry the transaction themselves: the internal call to
    // generateForArtifact is a self-invocation, so an annotation there would be
    // silently bypassed and the metadata update would never be flushed.

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFileUploaded(FileEvents.FileUploaded event) {
        generateForArtifact(event.artifactId());
    }

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFileVersionAdded(FileEvents.FileVersionAdded event) {
        generateForArtifact(event.artifactId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateForArtifact(UUID artifactId) {
        Artifact artifact = artifactRepository.findById(artifactId).orElse(null);
        if (!(artifact instanceof FileArtifact file) || file.getStorageKey() == null) {
            return;
        }
        try {
            Map<String, Object> metadata = new HashMap<>();
            boolean generated = switch (file.getType()) {
                case IMAGE -> generateImageThumbnail(file, metadata);
                case PDF -> generatePdfPreview(file, metadata);
                case DOCX -> generateTextPreview(file, metadata, this::extractDocxText);
                case XLSX -> generateTextPreview(file, metadata, this::extractXlsxText);
                case PPTX -> generateTextPreview(file, metadata, this::extractPptxText);
                case TXT -> generateTextPreview(file, metadata, this::extractPlainText);
                default -> false;
            };
            if (generated) {
                file.attachPreview((String) metadata.get("previewText"),
                        (String) metadata.get("thumbnailStorageKey"), (String) metadata.get("previewStorageKey"));
            }
        } catch (Exception e) {
            log.warn("Preview generation failed for artifact {} ({}): {}",
                    artifactId, file.getType(), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Generators
    // ------------------------------------------------------------------

    private boolean generateImageThumbnail(FileArtifact file, Map<String, Object> metadata)
            throws Exception {
        try (InputStream content = storageService.getObject(file.getStorageKey())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Thumbnails.of(content)
                    .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    .keepAspectRatio(true)
                    .outputFormat("jpg")
                    .toOutputStream(out);
            String key = previewKey(file, "thumbnail.jpg");
            storageService.putObject(key, out.toByteArray(), "image/jpeg");
            metadata.put("thumbnailStorageKey", key);
        }
        return true;
    }

    private boolean generatePdfPreview(FileArtifact file, Map<String, Object> metadata)
            throws Exception {
        byte[] bytes;
        try (InputStream content = storageService.getObject(file.getStorageKey())) {
            bytes = content.readAllBytes();
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > 0) {
                BufferedImage firstPage = new PDFRenderer(document).renderImageWithDPI(0, 96);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                BufferedImage scaled = Thumbnails.of(firstPage)
                        .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                        .keepAspectRatio(true)
                        .asBufferedImage();
                ImageIO.write(scaled, "jpg", out);
                String thumbKey = previewKey(file, "thumbnail.jpg");
                storageService.putObject(thumbKey, out.toByteArray(), "image/jpeg");
                metadata.put("thumbnailStorageKey", thumbKey);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(Math.min(document.getNumberOfPages(), 20));
            storeTextPreview(file, metadata, stripper.getText(document));
        }
        return true;
    }

    private interface TextExtractor {
        String extract(InputStream content) throws Exception;
    }

    private boolean generateTextPreview(FileArtifact file, Map<String, Object> metadata,
                                        TextExtractor extractor) throws Exception {
        try (InputStream content = storageService.getObject(file.getStorageKey())) {
            storeTextPreview(file, metadata, extractor.extract(content));
        }
        return true;
    }

    private void storeTextPreview(FileArtifact file, Map<String, Object> metadata, String text) {
        String truncated = text == null ? ""
                : text.substring(0, Math.min(text.length(), PREVIEW_TEXT_LIMIT));
        String key = previewKey(file, "preview.txt");
        storageService.putObject(key, truncated.getBytes(StandardCharsets.UTF_8),
                "text/plain; charset=utf-8");
        metadata.put("previewStorageKey", key);
        metadata.put("previewText", truncated);
    }

    private String extractDocxText(InputStream content) throws Exception {
        try (XWPFDocument document = new XWPFDocument(content)) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
                if (text.length() > PREVIEW_TEXT_LIMIT) {
                    break;
                }
            }
            return text.toString();
        }
    }

    private String extractXlsxText(InputStream content) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(content)) {
            StringBuilder text = new StringBuilder();
            org.apache.poi.ss.usermodel.DataFormatter formatter =
                    new org.apache.poi.ss.usermodel.DataFormatter();
            for (int i = 0; i < workbook.getNumberOfSheets() && text.length() < PREVIEW_TEXT_LIMIT; i++) {
                var sheet = workbook.getSheetAt(i);
                text.append("# ").append(sheet.getSheetName()).append('\n');
                for (var row : sheet) {
                    for (var cell : row) {
                        text.append(formatter.formatCellValue(cell)).append('\t');
                    }
                    text.append('\n');
                    if (text.length() > PREVIEW_TEXT_LIMIT) {
                        break;
                    }
                }
            }
            return text.toString();
        }
    }

    private String extractPptxText(InputStream content) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(content)) {
            StringBuilder text = new StringBuilder();
            slideShow.getSlides().forEach(slide -> slide.getShapes().forEach(shape -> {
                if (shape instanceof org.apache.poi.xslf.usermodel.XSLFTextShape textShape
                        && text.length() < PREVIEW_TEXT_LIMIT) {
                    text.append(textShape.getText()).append('\n');
                }
            }));
            return text.toString();
        }
    }

    private String extractPlainText(InputStream content) throws Exception {
        byte[] bytes = content.readNBytes(PREVIEW_TEXT_LIMIT);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String previewKey(FileArtifact file, String assetName) {
        return "ws/%s/previews/%s/%s".formatted(
                file.getWorkspaceId(), file.getId(), assetName);
    }
}
