package io.github.amjadsmarzug.xmousebutton.util;

import java.util.regex.Pattern;

/** Case-insensitive wildcard matching where {@code *} matches any run and {@code ?} matches one character. */
public final class Glob {

    private Glob() {
    }

    public static Pattern compile(String glob) {
        StringBuilder regex = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    public static boolean matches(String glob, String text) {
        return compile(glob).matcher(text).matches();
    }
}
