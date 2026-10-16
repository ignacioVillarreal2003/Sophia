package com.sophia.documentworkspace.domain.util;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.List;
import java.util.regex.Pattern;

/** Markdown helpers shared by export, import, search indexing and the public view. */
public final class MarkdownUtil {

    private static final MutableDataSet OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, List.of(
                    TablesExtension.create(),
                    TaskListExtension.create(),
                    StrikethroughExtension.create(),
                    AutolinkExtension.create()))
            // Raw HTML in a note is escaped, never rendered, in exports and public views.
            .set(HtmlRenderer.ESCAPE_HTML, true);
    private static final Parser PARSER = Parser.builder(OPTIONS).build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder(OPTIONS).build();
    private static final FlexmarkHtmlConverter HTML_TO_MD = FlexmarkHtmlConverter.builder().build();

    private static final Pattern CODE_FENCE = Pattern.compile("(?m)^\\s*(```|~~~).*$");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|]*)(?:\\|([^\\]]*))?]]");
    private static final Pattern LINE_MARKUP = Pattern.compile("(?m)^\\s{0,3}(?:#{1,6}\\s+|>+\\s?|[-*+]\\s+\\[[ xX]]\\s+|[-*+]\\s+|\\d+[.)]\\s+|[-*_]{3,}\\s*$)");
    private static final Pattern INLINE_MARKUP = Pattern.compile("[*_~`|]+");

    private MarkdownUtil() {
    }

    public static Document parse(String markdown) {
        return PARSER.parse(markdown == null ? "" : markdown);
    }

    public static String markdownToHtml(String markdown) {
        return HTML_RENDERER.render(parse(markdown));
    }

    public static String htmlToMarkdown(String html) {
        return HTML_TO_MD.convert(html == null ? "" : html);
    }

    /** Text with the Markdown syntax removed, good enough for full-text indexing. */
    public static String plainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String text = CODE_FENCE.matcher(markdown).replaceAll("");
        text = IMAGE.matcher(text).replaceAll("$1");
        text = LINK.matcher(text).replaceAll("$1");
        text = WIKILINK.matcher(text).replaceAll(match -> match.group(2) != null ? match.group(2) : match.group(1));
        text = LINE_MARKUP.matcher(text).replaceAll("");
        return INLINE_MARKUP.matcher(text).replaceAll("");
    }
}
