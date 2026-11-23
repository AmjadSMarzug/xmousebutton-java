package io.github.amjadsmarzug.xmousebutton.config;

import java.util.ArrayList;
import java.util.List;

/** Splits a command line into arguments. Supports double quotes and backslash escapes inside quotes. */
public final class CommandLine {

    private CommandLine() {
    }

    public static List<String> split(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                hasToken = true;
            } else if (c == '\\' && inQuotes && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                current.append('"');
                i++;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (hasToken) {
                    out.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            out.add(current.toString());
        }
        return out;
    }
}
