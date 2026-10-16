package com.sophia.documentworkspace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sophia.documentworkspace.support.AbstractIntegrationTest;
import com.sophia.documentworkspace.support.ApiClient;
import com.sophia.documentworkspace.support.TestJwtFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * File storage, download URLs, content versioning, export formats and the
 * import pipeline (markdown + zip archives) against real MinIO.
 */
class FileAndExportIT extends AbstractIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private UUID userId;
    private ApiClient api;
    private String workspaceId;
    private String jwt;

    @BeforeAll
    void setUpWorkspace() {
        userId = UUID.randomUUID();
        jwt = TestJwtFactory.token(userId, "files-" + userId + "@x.com");
        api = ApiClient.as(baseUrl(), userId, "files-" + userId + "@x.com");
        workspaceId = api.post("/api/v1/workspaces", Map.of("name", "Files WS"))
                .data().path("id").asText();
    }

    private String base() {
        return "/api/v1/workspaces/" + workspaceId;
    }

    /** Multipart uploads need a real HTTP multipart client. */
    private JsonNode uploadMultipart(String uri, String filename, byte[] content,
                                     String contentType) throws Exception {
        RestTemplate rest = new RestTemplate();
        rest.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(contentType));
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        }, fileHeaders);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", filePart);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(jwt);

        ResponseEntity<String> response = rest.postForEntity(URI.create(baseUrl() + uri),
                new HttpEntity<>(form, headers), String.class);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(response.getStatusCode().value())
                .withFailMessage("upload failed: %s", body)
                .isIn(200, 201);
        return body.path("data");
    }

    @Test
    void uploadDownloadAndVersionLifecycle() throws Exception {
        JsonNode uploaded = uploadMultipart(base() + "/files", "notes.txt",
                "first version".getBytes(StandardCharsets.UTF_8), "text/plain");
        String artifactId = uploaded.path("id").asText();
        assertThat(uploaded.path("type").asText()).isEqualTo("TXT");
        assertThat(uploaded.path("sizeBytes").asLong()).isEqualTo(13);

        // Presigned download URL serves the exact bytes from MinIO.
        String downloadUrl = api.get(base() + "/files/" + artifactId + "/download")
                .data().path("url").asText();
        String fetched = new RestTemplate().getForObject(URI.create(downloadUrl), String.class);
        assertThat(fetched).isEqualTo("first version");

        // New content version.
        JsonNode v2Response = JSON.readTree(JSON.writeValueAsString(Map.of()));
        RestTemplate rest = new RestTemplate();
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_PLAIN);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(
                new ByteArrayResource("second version!".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return "notes.txt";
                    }
                }, fileHeaders);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", filePart);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(jwt);
        rest.put(URI.create(baseUrl() + base() + "/files/" + artifactId + "/content"),
                new HttpEntity<>(form, headers));

        JsonNode versions = api.get(base() + "/files/" + artifactId + "/versions").data();
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).path("seq").asLong()).isEqualTo(2);
        assertThat(versions.get(0).path("current").asBoolean()).isTrue();

        // Restoring version 1 swaps the artifact's content pointer back.
        String v1Id = versions.get(1).path("id").asText();
        JsonNode restored = api.post(base() + "/files/" + artifactId + "/versions/" + v1Id
                + "/restore", null).data();
        assertThat(restored.path("sizeBytes").asLong()).isEqualTo(13);
    }

    @Test
    void previewsAreGeneratedForImagesAndDocuments() throws Exception {
        // PNG → thumbnail.
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                800, 600, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.createGraphics().fillRect(0, 0, 800, 600);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        String imageId = uploadMultipart(base() + "/files", "pic.png",
                png.toByteArray(), "image/png").path("id").asText();

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(api.get(base() + "/files/" + imageId
                        + "/preview?kind=thumbnail").status()).isEqualTo(200));

        // PDF → thumbnail + text preview.
        ByteArrayOutputStream pdfBytes = new ByteArrayOutputStream();
        try (org.apache.pdfbox.pdmodel.PDDocument pdf =
                     new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage();
            pdf.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream stream =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) {
                stream.beginText();
                stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                        org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("pdf preview content");
                stream.endText();
            }
            pdf.save(pdfBytes);
        }
        String pdfId = uploadMultipart(base() + "/files", "doc.pdf",
                pdfBytes.toByteArray(), "application/pdf").path("id").asText();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(api.get(base() + "/files/" + pdfId
                        + "/preview?kind=preview").status()).isEqualTo(200));

        // DOCX → text preview.
        ByteArrayOutputStream docxBytes = new ByteArrayOutputStream();
        try (org.apache.poi.xwpf.usermodel.XWPFDocument docx =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument()) {
            docx.createParagraph().createRun().setText("docx preview content");
            docx.write(docxBytes);
        }
        String docxId = uploadMultipart(base() + "/files", "word.docx", docxBytes.toByteArray(),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .path("id").asText();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(api.get(base() + "/files/" + docxId
                        + "/preview").status()).isEqualTo(200));

        // XLSX → text preview.
        ByteArrayOutputStream xlsxBytes = new ByteArrayOutputStream();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            workbook.createSheet("data").createRow(0).createCell(0).setCellValue("cell");
            workbook.write(xlsxBytes);
        }
        String xlsxId = uploadMultipart(base() + "/files", "sheet.xlsx", xlsxBytes.toByteArray(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .path("id").asText();
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(api.get(base() + "/files/" + xlsxId
                        + "/preview").status()).isEqualTo(200));
    }

    @Test
    void zipImportRecreatesTheFolderTree() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("vault/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("vault/note.md"));
            zip.write("# Vault note".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("vault/data.bin"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }

        JsonNode result = uploadMultipart(base() + "/import", "vault.zip",
                buffer.toByteArray(), "application/zip");
        assertThat(result.path("createdArtifactIds")).hasSizeGreaterThanOrEqualTo(2);

        // The root now contains a "vault" folder with a document and a file.
        JsonNode rootChildren = api.get(base() + "/artifacts").data().path("content");
        String vaultId = rootChildren.findParents("name").stream()
                .filter(n -> n.path("name").asText().equals("vault"))
                .findFirst().orElseThrow().path("id").asText();
        JsonNode vaultChildren = api.get(base() + "/artifacts?parentId=" + vaultId)
                .data().path("content");
        assertThat(vaultChildren.findValuesAsText("name"))
                .containsExactlyInAnyOrder("note", "data.bin");
    }
}
