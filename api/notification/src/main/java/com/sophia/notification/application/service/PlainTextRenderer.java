package com.sophia.notification.application.service;

import java.util.regex.Pattern;

public final class PlainTextRenderer {

    private static final Pattern ANCHOR =
            Pattern.compile("<a\\s[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BLOCK_END =
            Pattern.compile("</(p|div|h[1-6]|li|blockquote|tr)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_BREAK = Pattern.compile("<br\\s*/?>|<hr\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>", Pattern.DOTALL);
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\n{3,}");
    private static final Pattern TRAILING_SPACES = Pattern.compile("[ \t]+\n");

    private PlainTextRenderer() {
    }

    public static String fromHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = ANCHOR.matcher(html).replaceAll(match -> {
            String href = match.group(1);
            String label = TAG.matcher(match.group(2)).replaceAll("").trim();
            return java.util.regex.Matcher.quoteReplacement(
                    label.isEmpty() || label.equals(href) ? href : label + " (" + href + ")");
        });
        text = LINE_BREAK.matcher(text).replaceAll("\n");
        text = BLOCK_END.matcher(text).replaceAll("\n\n");
        text = TAG.matcher(text).replaceAll("");
        text = unescape(text);
        text = TRAILING_SPACES.matcher(text).replaceAll("\n");
        text = EXCESS_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    private static String unescape(String value) {
        return value
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }
}
