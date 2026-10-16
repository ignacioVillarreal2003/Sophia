package com.sophia.documentworkspace.domain.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Converts between the workbook JSON stored in a SPREADSHEET document and the formats people exchange
 * (XLSX and CSV).
 *
 * <p>Workbook JSON: {@code {"version":1,"sheets":[{"id","name","rows","cols","cells":{"A1":{"v","f","s"}},
 * "colWidths":{"0":120},"rowHeights":{"0":24},"frozenRows":0,"frozenCols":0,"merges":["A1:B2"]}]}}.
 * A cell has a value {@code v} (number, string or boolean), an optional formula {@code f} (with a leading
 * "="), and an optional style {@code s} ({@code b i u st} flags, {@code al} alignment, {@code c} text color,
 * {@code bg} background, {@code nf} Excel number format, {@code wrap}).
 */
public final class SpreadsheetCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ROWS = 100_000;
    private static final int MAX_COLS = 702;

    private SpreadsheetCodec() {
    }

    public static String emptyWorkbook() {
        ObjectNode workbook = MAPPER.createObjectNode();
        workbook.put("version", 1);
        ArrayNode sheets = workbook.putArray("sheets");
        sheets.add(emptySheet("Sheet1"));
        return workbook.toString();
    }

    public static ObjectNode emptySheet(String name) {
        ObjectNode sheet = MAPPER.createObjectNode();
        sheet.put("id", UUID.randomUUID().toString().substring(0, 8));
        sheet.put("name", name);
        sheet.put("rows", 100);
        sheet.put("cols", 26);
        sheet.putObject("cells");
        return sheet;
    }

    /** All text and values of a workbook, one cell per line, for full-text indexing. */
    public static String searchText(String workbookJson) {
        if (workbookJson == null || workbookJson.isBlank()) {
            return "";
        }
        try {
            StringBuilder text = new StringBuilder();
            for (JsonNode sheet : MAPPER.readTree(workbookJson).path("sheets")) {
                text.append(sheet.path("name").asText("")).append(System.lineSeparator());
                for (JsonNode cell : sheet.path("cells")) {
                    JsonNode value = cell.path("v");
                    if (!value.isMissingNode() && !value.isNull() && !value.asText("").isBlank()) {
                        text.append(value.asText()).append(System.lineSeparator());
                    }
                }
            }
            return text.toString();
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------

    public static byte[] toXlsx(String workbookJson) throws IOException {
        JsonNode root = MAPPER.readTree(workbookJson == null || workbookJson.isBlank() ? emptyWorkbook() : workbookJson);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Map<String, CellStyle> styles = new HashMap<>();
            int index = 1;
            for (JsonNode sheetNode : root.path("sheets")) {
                String name = safeSheetName(sheetNode.path("name").asText("Sheet" + index), index);
                Sheet sheet = workbook.createSheet(name);
                writeSheet(workbook, sheet, sheetNode, styles);
                index++;
            }
            if (workbook.getNumberOfSheets() == 0) {
                workbook.createSheet("Sheet1");
            }
            try {
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                evaluator.evaluateAll();
            } catch (RuntimeException ignored) {
                // Unsupported functions keep their formula; Excel recalculates them when opened.
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static void writeSheet(XSSFWorkbook workbook, Sheet sheet, JsonNode sheetNode,
                                   Map<String, CellStyle> styles) {
        Iterator<Map.Entry<String, JsonNode>> cells = sheetNode.path("cells").fields();
        while (cells.hasNext()) {
            var entry = cells.next();
            CellReference ref;
            try {
                ref = new CellReference(entry.getKey());
            } catch (RuntimeException e) {
                continue;
            }
            JsonNode cellNode = entry.getValue();
            Row row = sheet.getRow(ref.getRow());
            if (row == null) {
                row = sheet.createRow(ref.getRow());
            }
            Cell cell = row.createCell(ref.getCol());
            String formula = cellNode.path("f").asText("");
            JsonNode value = cellNode.path("v");
            if (formula.startsWith("=") && formula.length() > 1) {
                try {
                    cell.setCellFormula(formula.substring(1));
                } catch (RuntimeException e) {
                    cell.setCellValue(value.isMissingNode() ? "" : value.asText(""));
                }
            } else if (value.isNumber()) {
                cell.setCellValue(value.asDouble());
            } else if (value.isBoolean()) {
                cell.setCellValue(value.asBoolean());
            } else if (!value.isMissingNode() && !value.isNull()) {
                cell.setCellValue(value.asText());
            }
            JsonNode style = cellNode.path("s");
            if (style.isObject() && !style.isEmpty()) {
                cell.setCellStyle(styleFor(workbook, style, styles));
            }
        }
        JsonNode widths = sheetNode.path("colWidths");
        widths.fields().forEachRemaining(e -> {
            try {
                sheet.setColumnWidth(Integer.parseInt(e.getKey()), (int) Math.min(255 * 256, Math.max(0, (e.getValue().asDouble() - 5) / 7 * 256)));
            } catch (NumberFormatException ignored) {
                // Not a column index.
            }
        });
        sheetNode.path("rowHeights").fields().forEachRemaining(e -> {
            try {
                int r = Integer.parseInt(e.getKey());
                Row row = sheet.getRow(r) == null ? sheet.createRow(r) : sheet.getRow(r);
                row.setHeightInPoints((float) (e.getValue().asDouble() * 0.75));
            } catch (NumberFormatException ignored) {
                // Not a row index.
            }
        });
        for (JsonNode merge : sheetNode.path("merges")) {
            try {
                sheet.addMergedRegion(CellRangeAddress.valueOf(merge.asText()));
            } catch (RuntimeException ignored) {
                // Skip overlapping or malformed merges.
            }
        }
        int frozenRows = sheetNode.path("frozenRows").asInt(0);
        int frozenCols = sheetNode.path("frozenCols").asInt(0);
        if (frozenRows > 0 || frozenCols > 0) {
            sheet.createFreezePane(frozenCols, frozenRows);
        }
    }

    private static CellStyle styleFor(XSSFWorkbook workbook, JsonNode s, Map<String, CellStyle> cache) {
        String key = s.toString();
        CellStyle cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        XSSFCellStyle style = workbook.createCellStyle();
        XSSFFont font = workbook.createFont();
        font.setBold(s.path("b").asBoolean(false));
        font.setItalic(s.path("i").asBoolean(false));
        font.setUnderline(s.path("u").asBoolean(false) ? org.apache.poi.ss.usermodel.Font.U_SINGLE : org.apache.poi.ss.usermodel.Font.U_NONE);
        font.setStrikeout(s.path("st").asBoolean(false));
        if (s.hasNonNull("fs")) {
            font.setFontHeightInPoints((short) Math.max(6, Math.min(72, s.path("fs").asInt(11))));
        }
        XSSFColor color = color(s.path("c").asText(null));
        if (color != null) {
            font.setColor(color);
        }
        style.setFont(font);
        XSSFColor background = color(s.path("bg").asText(null));
        if (background != null) {
            style.setFillForegroundColor(background);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        switch (s.path("al").asText("")) {
            case "center" -> style.setAlignment(HorizontalAlignment.CENTER);
            case "right" -> style.setAlignment(HorizontalAlignment.RIGHT);
            case "left" -> style.setAlignment(HorizontalAlignment.LEFT);
            default -> {
            }
        }
        style.setWrapText(s.path("wrap").asBoolean(false));
        if (s.path("bd").asBoolean(false)) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
        String format = s.path("nf").asText("");
        if (!format.isBlank() && !"General".equalsIgnoreCase(format)) {
            DataFormat dataFormat = workbook.createDataFormat();
            style.setDataFormat(dataFormat.getFormat(format));
        }
        cache.put(key, style);
        return style;
    }

    private static XSSFColor color(String hex) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        byte[] rgb = {
                (byte) Integer.parseInt(hex.substring(1, 3), 16),
                (byte) Integer.parseInt(hex.substring(3, 5), 16),
                (byte) Integer.parseInt(hex.substring(5, 7), 16)};
        return new XSSFColor(rgb, null);
    }

    private static String safeSheetName(String name, int index) {
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", "_").strip();
        if (cleaned.isEmpty()) {
            cleaned = "Sheet" + index;
        }
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /** CSV of one sheet using the cached values, quoting like RFC 4180. */
    public static String toCsv(String workbookJson, int sheetIndex) throws IOException {
        JsonNode root = MAPPER.readTree(workbookJson == null || workbookJson.isBlank() ? emptyWorkbook() : workbookJson);
        JsonNode sheet = root.path("sheets").path(Math.max(0, sheetIndex));
        int maxRow = -1;
        int maxCol = -1;
        Map<String, JsonNode> cells = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = sheet.path("cells").fields();
        while (it.hasNext()) {
            var entry = it.next();
            try {
                CellReference ref = new CellReference(entry.getKey());
                cells.put(entry.getKey(), entry.getValue());
                maxRow = Math.max(maxRow, ref.getRow());
                maxCol = Math.max(maxCol, ref.getCol());
            } catch (RuntimeException ignored) {
                // Skip malformed keys.
            }
        }
        StringBuilder csv = new StringBuilder();
        for (int r = 0; r <= maxRow; r++) {
            for (int c = 0; c <= maxCol; c++) {
                if (c > 0) {
                    csv.append(',');
                }
                JsonNode cell = cells.get(new CellReference(r, c).formatAsString());
                csv.append(csvEscape(cell == null ? "" : display(cell.path("v"))));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private static String display(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        if (value.isBoolean()) {
            return value.asBoolean() ? "TRUE" : "FALSE";
        }
        return value.isNumber() && value.asDouble() == Math.rint(value.asDouble()) && Math.abs(value.asDouble()) < 1e15
                ? String.valueOf(value.asLong())
                : value.asText("");
    }

    private static String csvEscape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------

    public static String fromXlsx(byte[] bytes) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("version", 1);
            ArrayNode sheets = root.putArray("sheets");
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheets.add(readSheet(workbook.getSheetAt(i)));
            }
            if (sheets.isEmpty()) {
                sheets.add(emptySheet("Sheet1"));
            }
            return root.toString();
        }
    }

    private static ObjectNode readSheet(Sheet sheet) {
        ObjectNode node = emptySheet(sheet.getSheetName());
        ObjectNode cells = (ObjectNode) node.get("cells");
        int maxRow = 0;
        int maxCol = 0;
        for (Row row : sheet) {
            if (row.getRowNum() >= MAX_ROWS) {
                break;
            }
            for (Cell cell : row) {
                if (cell.getColumnIndex() >= MAX_COLS) {
                    continue;
                }
                ObjectNode cellNode = readCell(cell);
                if (cellNode == null) {
                    continue;
                }
                cells.set(new CellReference(cell.getRowIndex(), cell.getColumnIndex()).formatAsString(), cellNode);
                maxRow = Math.max(maxRow, cell.getRowIndex() + 1);
                maxCol = Math.max(maxCol, cell.getColumnIndex() + 1);
            }
            if (row.getHeight() != sheet.getDefaultRowHeight() && row.getHeightInPoints() > 0) {
                ((ObjectNode) node.with("rowHeights")).put(String.valueOf(row.getRowNum()),
                        Math.round(row.getHeightInPoints() / 0.75));
            }
        }
        for (int c = 0; c < Math.min(maxCol + 5, MAX_COLS); c++) {
            int width = sheet.getColumnWidth(c);
            if (width != sheet.getDefaultColumnWidth() * 256) {
                node.with("colWidths").put(String.valueOf(c), Math.round(width / 256.0 * 7 + 5));
            }
        }
        ArrayNode merges = node.putArray("merges");
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            merges.add(region.formatAsString());
        }
        if (sheet.getPaneInformation() != null && sheet.getPaneInformation().isFreezePane()) {
            node.put("frozenRows", sheet.getPaneInformation().getHorizontalSplitPosition());
            node.put("frozenCols", sheet.getPaneInformation().getVerticalSplitPosition());
        }
        node.put("rows", Math.max(100, maxRow + 20));
        node.put("cols", Math.max(26, maxCol + 3));
        return node;
    }

    private static ObjectNode readCell(Cell cell) {
        ObjectNode node = MAPPER.createObjectNode();
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            node.put("f", "=" + cell.getCellFormula());
            type = cell.getCachedFormulaResultType();
        }
        switch (type) {
            case NUMERIC -> node.put("v", cell.getNumericCellValue());
            case STRING -> node.put("v", cell.getStringCellValue());
            case BOOLEAN -> node.put("v", cell.getBooleanCellValue());
            case ERROR -> node.put("v", "#ERROR!");
            default -> {
                if (!node.has("f")) {
                    return cell.getCellStyle().getFillForegroundColor() != 0 ? withStyle(node, cell) : null;
                }
            }
        }
        return withStyle(node, cell);
    }

    private static ObjectNode withStyle(ObjectNode node, Cell cell) {
        CellStyle cellStyle = cell.getCellStyle();
        if (!(cellStyle instanceof XSSFCellStyle style)) {
            return node;
        }
        ObjectNode s = MAPPER.createObjectNode();
        XSSFFont font = style.getFont();
        if (font.getBold()) {
            s.put("b", true);
        }
        if (font.getItalic()) {
            s.put("i", true);
        }
        if (font.getUnderline() != org.apache.poi.ss.usermodel.Font.U_NONE) {
            s.put("u", true);
        }
        if (font.getStrikeout()) {
            s.put("st", true);
        }
        if (font.getFontHeightInPoints() != 11 && font.getFontHeightInPoints() > 0) {
            s.put("fs", (int) font.getFontHeightInPoints());
        }
        String textColor = hex(font.getXSSFColor());
        if (textColor != null && !"#000000".equals(textColor)) {
            s.put("c", textColor);
        }
        if (style.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
            String background = hex(style.getFillForegroundXSSFColor());
            if (background != null) {
                s.put("bg", background);
            }
        }
        switch (style.getAlignment()) {
            case CENTER -> s.put("al", "center");
            case RIGHT -> s.put("al", "right");
            case LEFT -> s.put("al", "left");
            default -> {
            }
        }
        if (style.getWrapText()) {
            s.put("wrap", true);
        }
        if (style.getBorderBottom() != BorderStyle.NONE || style.getBorderTop() != BorderStyle.NONE) {
            s.put("bd", true);
        }
        String format = style.getDataFormatString();
        if (format != null && !"General".equalsIgnoreCase(format)) {
            s.put("nf", format);
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                s.put("date", true);
            }
        }
        if (!s.isEmpty()) {
            node.set("s", s);
        }
        return node;
    }

    private static String hex(XSSFColor color) {
        if (color == null || color.getRGB() == null) {
            return null;
        }
        byte[] rgb = color.getRGB();
        return "#%02x%02x%02x".formatted(rgb[0] & 0xff, rgb[1] & 0xff, rgb[2] & 0xff);
    }

    public static String fromCsv(String csv, String sheetName) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 1);
        ObjectNode sheet = emptySheet(sheetName);
        ObjectNode cells = (ObjectNode) sheet.get("cells");
        int row = 0;
        int col = 0;
        int maxCol = 0;
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean fieldStarted = false;
        String text = csv == null ? "" : csv.replace("\r\n", "\n").replace('\r', '\n');
        char delimiter = detectDelimiter(text);
        for (int i = 0; i <= text.length(); i++) {
            boolean end = i == text.length();
            char ch = end ? '\n' : text.charAt(i);
            if (quoted) {
                if (ch == '"' && !end) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else if (!end) {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"' && field.isEmpty() && !end) {
                quoted = true;
                fieldStarted = true;
            } else if (ch == delimiter && !end) {
                putCsvCell(cells, row, col++, field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (ch == '\n') {
                if (fieldStarted || !field.isEmpty() || col > 0) {
                    putCsvCell(cells, row, col, field.toString());
                    maxCol = Math.max(maxCol, col + 1);
                    row++;
                }
                col = 0;
                field.setLength(0);
                fieldStarted = false;
                if (row >= MAX_ROWS) {
                    break;
                }
            } else {
                field.append(ch);
            }
        }
        sheet.put("rows", Math.max(100, row + 20));
        sheet.put("cols", Math.max(26, maxCol + 3));
        root.putArray("sheets").add(sheet);
        return root.toString();
    }

    private static char detectDelimiter(String text) {
        String firstLine = text.contains("\n") ? text.substring(0, text.indexOf('\n')) : text;
        long commas = firstLine.chars().filter(c -> c == ',').count();
        long semicolons = firstLine.chars().filter(c -> c == ';').count();
        long tabs = firstLine.chars().filter(c -> c == '\t').count();
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return semicolons > commas ? ';' : ',';
    }

    private static void putCsvCell(ObjectNode cells, int row, int col, String raw) {
        if (raw.isEmpty() || col >= MAX_COLS) {
            return;
        }
        ObjectNode cell = MAPPER.createObjectNode();
        String trimmed = raw.strip();
        if (trimmed.matches("-?\\d+(\\.\\d+)?") && trimmed.length() < 16) {
            cell.put("v", Double.parseDouble(trimmed));
        } else if ("TRUE".equalsIgnoreCase(trimmed) || "FALSE".equalsIgnoreCase(trimmed)) {
            cell.put("v", Boolean.parseBoolean(trimmed));
        } else {
            cell.put("v", raw);
        }
        cells.set(new CellReference(row, col).formatAsString(), cell);
    }
}
