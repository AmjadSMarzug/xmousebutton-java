package io.github.amjadsmarzug.xmousebutton.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A keyboard shortcut: zero or more modifiers held while one key is tapped.
 *
 * @param modifiers virtual-key codes to hold down, in press order
 * @param key       virtual-key code of the main key
 */
public record KeyCombo(List<Integer> modifiers, int key) {

    public KeyCombo {
        modifiers = List.copyOf(modifiers);
    }

    /**
     * Parses strings such as {@code ctrl+shift+t}, {@code alt+left} or {@code volume_up}.
     *
     * @throws IllegalArgumentException when a token is unknown or the text is empty
     */
    public static KeyCombo parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty key combination");
        }
        String[] tokens = text.toLowerCase(Locale.ROOT).split("\\+", -1);
        List<Integer> mods = new ArrayList<>();
        for (int i = 0; i < tokens.length - 1; i++) {
            String token = tokens[i].trim();
            Integer mod = Keys.modifier(token);
            if (mod == null) {
                throw new IllegalArgumentException("'" + token + "' is not a modifier (use ctrl, shift, alt or win)");
            }
            mods.add(mod);
        }
        String keyName = tokens[tokens.length - 1].trim();
        Integer key = Keys.lookup(keyName);
        if (key == null) {
            throw new IllegalArgumentException("unknown key '" + keyName + "'");
        }
        return new KeyCombo(mods, key);
    }

    /** Writes the combo in config syntax, for example {@code ctrl+shift+t}. */
    public String toConfigString() {
        StringBuilder out = new StringBuilder();
        for (int mod : modifiers) {
            out.append(Keys.nameOf(mod)).append('+');
        }
        String key = Keys.nameOf(key());
        out.append(key != null ? key : "0x" + Integer.toHexString(key()));
        return out.toString();
    }
}
