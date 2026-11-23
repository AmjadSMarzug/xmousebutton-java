package io.github.amjadsmarzug.xmousebutton.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text helpers for editors. These do not validate the config; use {@link ConfigParser} for that. */
public final class ConfigText {

    /** A profile header found in the text. {@code line} is zero-based. */
    public record Section(String name, int line) {
    }

    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^\\]]+)]\\s*$");

    private ConfigText() {
    }

    public static List<Section> sections(String text) {
        List<Section> out = new ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = SECTION.matcher(lines[i]);
            if (m.matches()) {
                out.add(new Section(m.group(1).strip(), i));
            }
        }
        return out;
    }

    /** Character offset at which the given zero-based line starts. */
    public static int offsetOfLine(String text, int line) {
        int offset = 0;
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < line && i < lines.length; i++) {
            offset += lines[i].length() + lineBreakLength(text, offset + lines[i].length());
        }
        return offset;
    }

    private static int lineBreakLength(String text, int index) {
        if (index >= text.length()) {
            return 0;
        }
        if (text.startsWith("\r\n", index)) {
            return 2;
        }
        return 1;
    }
}
