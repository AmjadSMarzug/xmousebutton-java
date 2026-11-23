package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.KeyCombo;
import io.github.amjadsmarzug.xmousebutton.model.Keys;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeyComboTest {

    @Test
    void parsesModifiersAndKey() {
        KeyCombo combo = KeyCombo.parse("Ctrl+Shift+T");
        assertEquals(List.of(0x11, 0x10), combo.modifiers());
        assertEquals(0x54, combo.key());
    }

    @Test
    void parsesNamedKeysAndFunctionKeys() {
        assertEquals(0x25, KeyCombo.parse("left").key());
        assertEquals(0x7B, KeyCombo.parse("f12").key());
        assertEquals(0xAF, KeyCombo.parse("volume_up").key());
        assertEquals(List.of(), KeyCombo.parse("enter").modifiers());
    }

    @Test
    void rejectsUnknownTokens() {
        assertThrows(IllegalArgumentException.class, () -> KeyCombo.parse("hyper+a"));
        assertThrows(IllegalArgumentException.class, () -> KeyCombo.parse("ctrl+banana"));
        assertThrows(IllegalArgumentException.class, () -> KeyCombo.parse("  "));
    }

    @Test
    void modifierLookupIsSeparateFromKeys() {
        assertEquals(0x12, Keys.modifier("alt"));
        assertEquals(null, Keys.modifier("f1"));
    }
}
