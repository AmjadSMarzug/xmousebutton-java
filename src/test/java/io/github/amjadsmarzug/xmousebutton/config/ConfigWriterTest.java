package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.model.Keys;
import io.github.amjadsmarzug.xmousebutton.model.KeyCombo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWriterTest {

    private static final String SAMPLE = """
            [default]
            x1 = key alt+left
            right = hold 500 key ctrl+shift+esc | block
            left+right = macro key ctrl+c ; wait 20 ; type "done here"

            [browser]
            match.process = chrome.exe, msedge.exe
            match.title = *YouTube*
            enabled = false
            pointer_speed = 12
            middle = layer 2
            layer2.left = run "C:\\Program Files\\My App\\app.exe" --flag "two words"
            wheel_up = wheel down 3
            """;

    @Test
    void writingThenParsingGivesTheSameText() throws Exception {
        Config first = ConfigParser.parse(SAMPLE);
        String written = ConfigWriter.write(first.profiles());
        String again = ConfigWriter.write(ConfigParser.parse(written).profiles());
        assertEquals(written, again);
    }

    @Test
    void writtenTextKeepsProfileSettings() throws Exception {
        String written = ConfigWriter.write(ConfigParser.parse(SAMPLE).profiles());
        Config back = ConfigParser.parse(written);
        var browser = back.profiles().get(1);
        assertEquals("browser", browser.name());
        assertFalse(browser.enabled());
        assertEquals(12, browser.pointerSpeed());
        assertEquals(List.of("chrome.exe", "msedge.exe"), browser.processGlobs());
        assertEquals(List.of("*YouTube*"), browser.titleGlobs());
        assertTrue(written.contains("enabled = false"));
    }

    @Test
    void disabledProfileNeverMatches() throws Exception {
        Config config = ConfigParser.parse("[browser]\nmatch.process = chrome.exe\nenabled = no\nx1 = block\n");
        assertEquals("default", config.resolve(new io.github.amjadsmarzug.xmousebutton.model.WindowInfo(
                "chrome.exe", "")).name());
    }

    @Test
    void enabledValuesAreValidated() {
        org.junit.jupiter.api.Assertions.assertThrows(ConfigException.class,
                () -> ConfigParser.parse("[x]\nenabled = maybe\n"));
    }

    @Test
    void everyActionKindSurvivesWriteAndParse() throws Exception {
        String[] texts = {
                "passthrough", "block", "key ctrl+shift+f5", "key volume_up", "click x2", "wheel left 4",
                "run notepad.exe", "run \"my file.txt\" plain", "layer 10", "type \"hi there\"",
                "macro key a ; wait 0 ; click middle", "hold 1000 key f5 | block",
        };
        for (String text : texts) {
            Action parsed = ActionParser.parse(text);
            Action again = ActionParser.parse(ActionWriter.write(parsed));
            assertEquals(parsed, again, "round trip failed for: " + text);
        }
    }

    @Test
    void keyNamesAreCanonical() {
        assertEquals("ctrl", Keys.nameOf(0x11));
        assertEquals("delete", Keys.nameOf(0x2E));
        assertEquals("enter", Keys.nameOf(0x0D));
        assertEquals("volume_mute", Keys.nameOf(0xAD));
        assertEquals("ctrl+shift+t", KeyCombo.parse("ctrl+shift+t").toConfigString());
    }
}
