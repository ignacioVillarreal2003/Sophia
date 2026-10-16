package com.sophia.documentworkspace.domain.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadsheetCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String WORKBOOK = """
            {"version":1,"sheets":[{"id":"s1","name":"Budget","rows":100,"cols":26,
             "cells":{
               "A1":{"v":"Item","s":{"b":true,"bg":"#ffeeaa"}},
               "B1":{"v":"Cost","s":{"b":true,"al":"right"}},
               "A2":{"v":"Rent"},"B2":{"v":1200,"s":{"nf":"#,##0.00"}},
               "A3":{"v":"Food, drinks"},"B3":{"v":300.5},
               "B4":{"v":1500.5,"f":"=SUM(B2:B3)"}},
             "colWidths":{"0":160},"frozenRows":1,"merges":["D1:E1"]}]}""";

    @Test
    void xlsxRoundTripKeepsValuesFormulasAndStyles() throws Exception {
        byte[] xlsx = SpreadsheetCodec.toXlsx(WORKBOOK);
        JsonNode back = MAPPER.readTree(SpreadsheetCodec.fromXlsx(xlsx));

        JsonNode sheet = back.path("sheets").get(0);
        assertThat(sheet.path("name").asText()).isEqualTo("Budget");
        JsonNode cells = sheet.path("cells");
        assertThat(cells.path("A2").path("v").asText()).isEqualTo("Rent");
        assertThat(cells.path("B2").path("v").asDouble()).isEqualTo(1200.0);
        assertThat(cells.path("B2").path("s").path("nf").asText()).isEqualTo("#,##0.00");
        assertThat(cells.path("A1").path("s").path("b").asBoolean()).isTrue();
        assertThat(cells.path("A1").path("s").path("bg").asText()).isEqualTo("#ffeeaa");
        assertThat(cells.path("B4").path("f").asText()).isEqualTo("=SUM(B2:B3)");
        assertThat(cells.path("B4").path("v").asDouble()).isEqualTo(1500.5);
        assertThat(sheet.path("frozenRows").asInt()).isEqualTo(1);
    }

    @Test
    void csvExportQuotesAndImportParsesIt() throws Exception {
        String csv = SpreadsheetCodec.toCsv(WORKBOOK, 0);
        assertThat(csv).contains("Item,Cost").contains("\"Food, drinks\",300.5");

        JsonNode back = MAPPER.readTree(SpreadsheetCodec.fromCsv(csv, "Imported"));
        JsonNode cells = back.path("sheets").get(0).path("cells");
        assertThat(cells.path("A3").path("v").asText()).isEqualTo("Food, drinks");
        assertThat(cells.path("B2").path("v").asDouble()).isEqualTo(1200.0);
    }

    @Test
    void csvDetectsSemicolonsAndHandlesQuotedNewlines() throws Exception {
        JsonNode back = MAPPER.readTree(SpreadsheetCodec.fromCsv("a;b\n\"x\ny\";3\n", "S"));
        JsonNode cells = back.path("sheets").get(0).path("cells");
        assertThat(cells.path("B1").path("v").asText()).isEqualTo("b");
        assertThat(cells.path("A2").path("v").asText()).isEqualTo("x\ny");
        assertThat(cells.path("B2").path("v").asDouble()).isEqualTo(3.0);
    }

    @Test
    void emptyWorkbookHasOneSheet() throws Exception {
        assertThat(MAPPER.readTree(SpreadsheetCodec.emptyWorkbook()).path("sheets")).hasSize(1);
    }

    @Test
    void searchTextCollectsSheetNamesAndValues() {
        assertThat(SpreadsheetCodec.searchText(WORKBOOK)).contains("Budget").contains("Rent").contains("1200");
    }
}
