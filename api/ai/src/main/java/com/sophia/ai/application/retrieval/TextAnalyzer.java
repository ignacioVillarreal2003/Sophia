package com.sophia.ai.application.retrieval;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns text into search terms and documents into passages. Indexing and querying go through the same normalisation
 * (lower case, no accents, a light trim of plural and verb endings in Spanish and English), which matters more than
 * how clever the stemming is: "Fotosíntesis" and "fotosintesis" meet, "redes" finds "red", "networks" finds "network".
 */
public final class TextAnalyzer {

    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.*)$");

    private static final Set<String> STOP_WORDS = Set.of(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "y", "o", "u", "e", "en", "con",
            "por", "para", "que", "es", "son", "se", "su", "sus", "lo", "como", "mas", "pero", "si", "no", "me", "mi",
            "te", "tu", "este", "esta", "esto", "ese", "esa", "eso", "hay", "fue", "ser", "sobre", "entre", "cual",
            "cuales", "cuando", "donde", "quien", "explica", "explicame", "dime", "decime", "decir",
            "the", "a", "an", "of", "and", "or", "in", "on", "to", "for", "with", "is", "are", "was", "were", "be",
            "it", "its", "this", "that", "these", "those", "as", "at", "by", "from", "what", "which", "who", "how",
            "why", "when", "where", "do", "does", "did", "my", "tell", "explain", "about");

    private TextAnalyzer() {
    }

    /** Lower-cased, accent-free words in the text, trimmed to a rough stem. */
    public static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        String plain = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        for (String word : NON_ALNUM.split(plain)) {
            if (word.length() >= 2 && word.length() <= 40) {
                out.add(stem(word));
            }
        }
        return out;
    }

    /** The searchable form of a passage: its terms, space separated. */
    public static String indexable(String text) {
        return String.join(" ", terms(text));
    }

    /**
     * Terms of a question worth searching for: stop words are dropped (an index keeps them, a question should not
     * match on them), duplicates are removed, and the list is capped so a pasted paragraph is still one cheap query.
     */
    public static List<String> queryTerms(String question) {
        Set<String> out = new LinkedHashSet<>();
        if (question == null) {
            return List.of();
        }
        String plain = DIACRITICS.matcher(Normalizer.normalize(question, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT);
        for (String word : NON_ALNUM.split(plain)) {
            if (word.length() < 2 || word.length() > 40 || STOP_WORDS.contains(word)) {
                continue;
            }
            out.add(stem(word));
            if (out.size() >= 24) {
                break;
            }
        }
        return new ArrayList<>(out);
    }

    static String stem(String word) {
        int n = word.length();
        if (n <= 4) {
            return word;
        }
        for (String suffix : new String[]{"aciones", "uciones", "amiento", "imiento", "ations", "ation", "acion",
                "ucion", "mente", "ingly", "ities", "iendo", "ando", "ments", "ment", "ness", "ings", "ing", "ers"}) {
            if (word.endsWith(suffix) && n - suffix.length() >= 3) {
                return word.substring(0, n - suffix.length());
            }
        }
        if (word.endsWith("ies") && n > 4) {
            return word.substring(0, n - 3) + "y";
        }
        if (word.endsWith("es") && n > 4) {
            return word.substring(0, n - 2);
        }
        if (word.endsWith("s") && !word.endsWith("ss") && n > 4) {
            return word.substring(0, n - 1);
        }
        if (word.endsWith("ed") && n > 5) {
            return word.substring(0, n - 2);
        }
        return word;
    }

    /**
     * Splits a document into passages of about {@code target} characters, cutting at paragraph boundaries. Each
     * passage is prefixed with the heading it sits under, so "the second step" still says what it is a step of. A
     * paragraph longer than the target is cut at sentence ends (or, failing that, anywhere).
     */
    public static List<String> chunks(String text, int target) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        int hard = target + target / 2;
        String heading = "";
        StringBuilder current = new StringBuilder();
        for (String block : text.replace("\r\n", "\n").split("\n\\s*\n")) {
            String paragraph = block.strip();
            if (paragraph.isEmpty()) {
                continue;
            }
            String firstLine = paragraph.lines().findFirst().orElse("");
            var match = HEADING.matcher(firstLine);
            if (match.matches()) {
                flush(out, current, heading);
                heading = match.group(1).strip();
            }
            for (String piece : split(paragraph, hard)) {
                if (current.length() > 0 && current.length() + piece.length() + 2 > target) {
                    flush(out, current, heading);
                }
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(piece);
            }
        }
        flush(out, current, heading);
        return out;
    }

    private static void flush(List<String> out, StringBuilder current, String heading) {
        if (current.length() == 0) {
            return;
        }
        String body = current.toString();
        boolean startsWithHeading = !heading.isEmpty() && body.lines().findFirst().orElse("").contains(heading);
        out.add(heading.isEmpty() || startsWithHeading ? body : "[" + heading + "]\n" + body);
        current.setLength(0);
    }

    private static List<String> split(String paragraph, int max) {
        if (paragraph.length() <= max) {
            return List.of(paragraph);
        }
        List<String> pieces = new ArrayList<>();
        String rest = paragraph;
        while (rest.length() > max) {
            // End of the piece, exclusive: just after a sentence end, else at a space, else anywhere.
            int end = rest.lastIndexOf(". ", max - 1) + 1;
            if (end < max / 2) {
                end = rest.lastIndexOf(' ', max - 1) + 1;
            }
            if (end < max / 2) {
                end = max;
            }
            pieces.add(rest.substring(0, end).strip());
            rest = rest.substring(end).strip();
        }
        if (!rest.isEmpty()) {
            pieces.add(rest);
        }
        return pieces;
    }
}
