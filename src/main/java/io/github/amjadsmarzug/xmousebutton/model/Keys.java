package io.github.amjadsmarzug.xmousebutton.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Name to Windows virtual-key code table. Names are lowercase. */
public final class Keys {

    private static final Map<String, Integer> MODIFIERS = new LinkedHashMap<>();
    private static final Map<String, Integer> KEYS = new LinkedHashMap<>();

    static {
        MODIFIERS.put("ctrl", 0x11);
        MODIFIERS.put("control", 0x11);
        MODIFIERS.put("shift", 0x10);
        MODIFIERS.put("alt", 0x12);
        MODIFIERS.put("win", 0x5B);
        MODIFIERS.put("meta", 0x5B);

        for (char c = 'a'; c <= 'z'; c++) {
            KEYS.put(String.valueOf(c), (int) Character.toUpperCase(c));
        }
        for (char c = '0'; c <= '9'; c++) {
            KEYS.put(String.valueOf(c), (int) c);
        }
        for (int f = 1; f <= 24; f++) {
            KEYS.put("f" + f, 0x6F + f);
        }
        KEYS.put("enter", 0x0D);
        KEYS.put("return", 0x0D);
        KEYS.put("tab", 0x09);
        KEYS.put("esc", 0x1B);
        KEYS.put("escape", 0x1B);
        KEYS.put("space", 0x20);
        KEYS.put("backspace", 0x08);
        KEYS.put("bksp", 0x08);
        KEYS.put("delete", 0x2E);
        KEYS.put("del", 0x2E);
        KEYS.put("insert", 0x2D);
        KEYS.put("ins", 0x2D);
        KEYS.put("home", 0x24);
        KEYS.put("end", 0x23);
        KEYS.put("pageup", 0x21);
        KEYS.put("pgup", 0x21);
        KEYS.put("pagedown", 0x22);
        KEYS.put("pgdn", 0x22);
        KEYS.put("left", 0x25);
        KEYS.put("up", 0x26);
        KEYS.put("right", 0x27);
        KEYS.put("down", 0x28);
        KEYS.put("printscreen", 0x2C);
        KEYS.put("capslock", 0x14);
        KEYS.put("plus", 0xBB);
        KEYS.put("minus", 0xBD);
        KEYS.put("comma", 0xBC);
        KEYS.put("period", 0xBE);
        KEYS.put("volume_mute", 0xAD);
        KEYS.put("mute", 0xAD);
        KEYS.put("volume_down", 0xAE);
        KEYS.put("volume_up", 0xAF);
        KEYS.put("media_next", 0xB0);
        KEYS.put("next_track", 0xB0);
        KEYS.put("media_prev", 0xB1);
        KEYS.put("prev_track", 0xB1);
        KEYS.put("media_stop", 0xB2);
        KEYS.put("media_play_pause", 0xB3);
        KEYS.put("play_pause", 0xB3);
        KEYS.put("browser_back", 0xA6);
        KEYS.put("browser_forward", 0xA7);
        KEYS.put("browser_refresh", 0xA8);
        KEYS.put("browser_home", 0xAC);
    }

    private Keys() {
    }

    /**
     * Canonical config name for a virtual-key code, or {@code null} if unknown. Where several
     * names share a code, the one listed first in this class is used.
     */
    public static String nameOf(int vk) {
        for (Map.Entry<String, Integer> e : MODIFIERS.entrySet()) {
            if (e.getValue() == vk) {
                return e.getKey();
            }
        }
        for (Map.Entry<String, Integer> e : KEYS.entrySet()) {
            if (e.getValue() == vk) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Virtual-key code for a modifier name, or {@code null} if it is not a modifier. */
    public static Integer modifier(String name) {
        return MODIFIERS.get(name);
    }

    /** Virtual-key code for any supported key name, or {@code null}. */
    public static Integer lookup(String name) {
        Integer mod = MODIFIERS.get(name);
        return mod != null ? mod : KEYS.get(name);
    }
}
