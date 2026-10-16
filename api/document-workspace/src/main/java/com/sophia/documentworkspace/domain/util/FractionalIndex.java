package com.sophia.documentworkspace.domain.util;

/**
 * Lexicographic fractional ordering keys (LexoRank-style, base-62). A new key
 * can always be generated strictly between two existing keys without
 * renumbering siblings, which is what makes concurrent inserts cheap.
 * Generated keys never end with the minimum digit, so a "between" always
 * exists for any pair this class produced. Clients normally generate keys
 * themselves; the server uses this for imports, duplication and
 * system-generated blocks.
 */
public final class FractionalIndex {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private FractionalIndex() {
    }

    /**
     * Key strictly between {@code before} and {@code after}; either side may be
     * null (open end). Treats keys as fractions in [0, 1) over base-62 digits.
     */
    public static String between(String before, String after) {
        String low = before == null ? "" : before;
        String high = after; // null high means +infinity
        if (high != null && !high.isEmpty() && low.compareTo(high) >= 0) {
            throw new IllegalArgumentException(
                    "before (%s) must sort strictly below after (%s)".formatted(before, after));
        }

        StringBuilder key = new StringBuilder();
        int i = 0;
        while (true) {
            int lowDigit = i < low.length() ? digit(low.charAt(i)) : 0;
            int highDigit = high == null ? BASE
                    : (i < high.length() ? digit(high.charAt(i)) : 0);

            if (highDigit - lowDigit > 1) {
                key.append(ALPHABET.charAt((lowDigit + highDigit) / 2));
                return key.toString();
            }
            if (highDigit - lowDigit == 1) {
                // No room at this digit: pin the low digit; from here the upper
                // bound becomes +infinity relative to the low key's remainder.
                key.append(ALPHABET.charAt(lowDigit));
                high = null;
                i++;
                continue;
            }
            // Digits equal: copy and advance.
            key.append(ALPHABET.charAt(lowDigit));
            i++;
        }
    }

    /** First key for an empty sibling list. */
    public static String first() {
        return String.valueOf(ALPHABET.charAt(BASE / 2));
    }

    /** Key sorting after every existing key when {@code lastKey} is the current maximum. */
    public static String after(String lastKey) {
        return between(lastKey, null);
    }

    /** Key sorting before every existing key when {@code firstKey} is the current minimum. */
    public static String before(String firstKey) {
        return between(null, firstKey);
    }

    private static int digit(char c) {
        int index = ALPHABET.indexOf(c);
        if (index < 0) {
            throw new IllegalArgumentException("Invalid sort key character: " + c);
        }
        return index;
    }
}
