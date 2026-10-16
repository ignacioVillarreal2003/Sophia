package com.sophia.documentworkspace.domain.util;

import com.vladsch.flexmark.ast.BlockQuote;
import com.vladsch.flexmark.ast.BulletList;
import com.vladsch.flexmark.ast.Code;
import com.vladsch.flexmark.ast.Emphasis;
import com.vladsch.flexmark.ast.FencedCodeBlock;
import com.vladsch.flexmark.ast.HardLineBreak;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.IndentedCodeBlock;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.ListItem;
import com.vladsch.flexmark.ast.OrderedList;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.SoftLineBreak;
import com.vladsch.flexmark.ast.StrongEmphasis;
import com.vladsch.flexmark.ast.ThematicBreak;
import com.vladsch.flexmark.ext.gfm.strikethrough.Strikethrough;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListItem;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.util.ast.Node;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Renders a Markdown document as a Word document (headings, lists, tasks, code, quotes, tables, inline styles). */
public final class MarkdownDocxWriter {

    private record Style(boolean bold, boolean italic, boolean strike, boolean code) {
        Style withBold() {
            return new Style(true, italic, strike, code);
        }

        Style withItalic() {
            return new Style(bold, true, strike, code);
        }

        Style withStrike() {
            return new Style(bold, italic, true, code);
        }

        Style withCode() {
            return new Style(bold, italic, strike, true);
        }
    }

    private MarkdownDocxWriter() {
    }

    public static byte[] write(String title, String markdown) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            if (title != null && !title.isBlank()) {
                XWPFRun run = document.createParagraph().createRun();
                run.setText(title);
                run.setBold(true);
                run.setFontSize(22);
            }
            for (Node node : MarkdownUtil.parse(markdown).getChildren()) {
                block(document, node, 0, null);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private static void block(XWPFDocument doc, Node node, int depth, String prefix) {
        if (node instanceof Heading heading) {
            XWPFParagraph paragraph = doc.createParagraph();
            int level = Math.min(heading.getLevel(), 6);
            inline(paragraph, heading, new Style(true, false, false, false), 24 - level * 2);
        } else if (node instanceof Paragraph) {
            XWPFParagraph paragraph = doc.createParagraph();
            if (prefix != null) {
                paragraph.setIndentationLeft(360 * (depth + 1));
                paragraph.createRun().setText(prefix);
            }
            inline(paragraph, node, new Style(false, false, false, false), 11);
        } else if (node instanceof BulletList || node instanceof OrderedList) {
            int number = 1;
            for (Node item : node.getChildren()) {
                String marker;
                if (item instanceof TaskListItem task) {
                    marker = task.isItemDoneMarker() ? "☑ " : "☐ ";
                } else if (node instanceof OrderedList) {
                    marker = number++ + ". ";
                } else {
                    marker = "• ";
                }
                listItem(doc, item, depth, marker);
            }
        } else if (node instanceof FencedCodeBlock || node instanceof IndentedCodeBlock) {
            for (String line : codeText(node).split("\n", -1)) {
                XWPFParagraph paragraph = doc.createParagraph();
                paragraph.setIndentationLeft(360);
                XWPFRun run = paragraph.createRun();
                run.setText(line);
                run.setFontFamily("Courier New");
                run.setFontSize(10);
            }
        } else if (node instanceof BlockQuote) {
            for (Node child : node.getChildren()) {
                XWPFParagraph paragraph = doc.createParagraph();
                paragraph.setIndentationLeft(720);
                paragraph.setBorderLeft(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
                inline(paragraph, child, new Style(false, true, false, false), 11);
            }
        } else if (node instanceof ThematicBreak) {
            doc.createParagraph().createRun().setText("―".repeat(30));
        } else if (node instanceof TableBlock table) {
            table(doc, table);
        } else {
            XWPFParagraph paragraph = doc.createParagraph();
            paragraph.createRun().setText(node.getChars().toString().strip());
        }
    }

    private static void listItem(XWPFDocument doc, Node item, int depth, String marker) {
        boolean first = true;
        for (Node child : item.getChildren()) {
            if (child instanceof BulletList || child instanceof OrderedList) {
                block(doc, child, depth + 1, null);
            } else if (child instanceof Paragraph && first) {
                XWPFParagraph paragraph = doc.createParagraph();
                paragraph.setIndentationLeft(360 * (depth + 1));
                paragraph.createRun().setText(marker);
                inline(paragraph, child, new Style(false, false, false, false), 11);
                first = false;
            } else {
                block(doc, child, depth, null);
            }
        }
        if (!(item instanceof ListItem) && !(item instanceof TaskListItem)) {
            return;
        }
    }

    private static void table(XWPFDocument doc, TableBlock block) {
        List<List<String>> rows = new ArrayList<>();
        collectRows(block, rows);
        if (rows.isEmpty()) {
            return;
        }
        int columns = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = doc.createTable(rows.size(), columns);
        for (int r = 0; r < rows.size(); r++) {
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < columns; c++) {
                String text = c < rows.get(r).size() ? rows.get(r).get(c) : "";
                XWPFRun run = row.getCell(c).getParagraphs().get(0).createRun();
                run.setText(text);
                run.setBold(r == 0);
            }
        }
    }

    private static void collectRows(Node node, List<List<String>> rows) {
        for (Node child : node.getChildren()) {
            if (child instanceof TableRow) {
                List<String> cells = new ArrayList<>();
                for (Node cell : child.getChildren()) {
                    if (cell instanceof TableCell) {
                        cells.add(cell.getChars().toString().strip());
                    }
                }
                if (!cells.isEmpty()) {
                    rows.add(cells);
                }
            } else {
                collectRows(child, rows);
            }
        }
    }

    private static String codeText(Node node) {
        if (node instanceof FencedCodeBlock fenced) {
            return fenced.getContentChars().toString().stripTrailing();
        }
        return ((IndentedCodeBlock) node).getContentChars().toString().stripTrailing();
    }

    private static void inline(XWPFParagraph paragraph, Node parent, Style style, int size) {
        for (Node child : parent.getChildren()) {
            if (child instanceof StrongEmphasis) {
                inline(paragraph, child, style.withBold(), size);
            } else if (child instanceof Emphasis) {
                inline(paragraph, child, style.withItalic(), size);
            } else if (child instanceof Strikethrough) {
                inline(paragraph, child, style.withStrike(), size);
            } else if (child instanceof Code code) {
                run(paragraph, code.getText().toString(), style.withCode(), size);
            } else if (child instanceof Link link) {
                XWPFRun run = run(paragraph, link.getText().toString(), style, size);
                run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
                run.setColor("1D4ED8");
            } else if (child instanceof SoftLineBreak) {
                run(paragraph, " ", style, size);
            } else if (child instanceof HardLineBreak) {
                paragraph.createRun().addBreak();
            } else if (child.hasChildren()) {
                inline(paragraph, child, style, size);
            } else {
                run(paragraph, child.getChars().toString(), style, size);
            }
        }
    }

    private static XWPFRun run(XWPFParagraph paragraph, String text, Style style, int size) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(style.bold());
        run.setItalic(style.italic());
        run.setStrikeThrough(style.strike());
        run.setFontSize(size);
        if (style.code()) {
            run.setFontFamily("Courier New");
        }
        return run;
    }
}
