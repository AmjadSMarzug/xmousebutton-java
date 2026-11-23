package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigParserTest {

    @Test
    void parsesProfilesInOrder() throws Exception {
        Config config = ConfigParser.parse("""
                [default]
                x1 = passthrough
                [browser]
                match.process = chrome.exe, msedge.exe
                match.title = *YouTube*
                x2 = block
                """);
        assertEquals(2, config.profiles().size());
        assertEquals("browser", config.profiles().get(1).name());
    }

    @Test
    void matchRulesRequireProcessAndTitleTogether() throws Exception {
        Config config = ConfigParser.parse("""
                [video]
                match.process = chrome.exe
                match.title = *YouTube*
                x1 = block
                """);
        Profile video = config.profiles().get(0);
        assertTrue(video.matches(new WindowInfo("chrome.exe", "Cat - YouTube")));
        assertEquals(false, video.matches(new WindowInfo("chrome.exe", "Docs")));
        assertEquals(false, video.matches(new WindowInfo("firefox.exe", "Cat - YouTube")));
    }

    @Test
    void resolveFallsBackToDefault() throws Exception {
        Config config = ConfigParser.parse("""
                [default]
                x1 = key alt+left
                [browser]
                match.process = chrome.exe
                x1 = key ctrl+shift+tab
                """);
        Profile hit = config.resolve(new WindowInfo("CHROME.EXE", ""));
        assertEquals("browser", hit.name());
        assertEquals("default", config.resolve(new WindowInfo("notepad.exe", "")).name());
    }

    @Test
    void layerBindingsFallBackToBase() throws Exception {
        Config config = ConfigParser.parse("""
                [default]
                left = key ctrl+c
                middle = layer 1
                layer1.right = key ctrl+v
                """);
        Profile base = config.profiles().get(0);
        assertInstanceOf(Action.Key.class, base.lookup(1, MouseButton.LEFT));
        assertInstanceOf(Action.Key.class, base.lookup(1, MouseButton.RIGHT));
        assertInstanceOf(Action.Passthrough.class, base.lookup(0, MouseButton.RIGHT));
    }

    @Test
    void duplicateBindingIsRejectedWithLineNumber() {
        ConfigException e = assertThrows(ConfigException.class, () -> ConfigParser.parse("""
                [default]
                x1 = block
                x1 = passthrough
                """));
        assertTrue(e.getMessage().startsWith("line 3:"), e.getMessage());
    }

    @Test
    void unknownActionIsRejected() {
        ConfigException e = assertThrows(ConfigException.class,
                () -> ConfigParser.parse("[default]\nx1 = explode\n"));
        assertTrue(e.getMessage().contains("unknown action"), e.getMessage());
    }

    @Test
    void bindingBeforeSectionIsRejected() {
        assertThrows(ConfigException.class, () -> ConfigParser.parse("x1 = block\n"));
    }

    @Test
    void layerOutOfRangeIsRejected() {
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nlayer11.x1 = block\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nx1 = layer 0\n"));
    }

    @Test
    void wheelNotchesAreBounded() throws Exception {
        Config config = ConfigParser.parse("[default]\nwheel_up = wheel up 5\n");
        Action.Wheel wheel = assertInstanceOf(Action.Wheel.class,
                config.profiles().get(0).lookup(0, MouseButton.WHEEL_UP));
        assertEquals(5, wheel.notches());
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nwheel_up = wheel up 99\n"));
    }

    @Test
    void bundledDefaultConfigIsValid() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/default-config.conf")) {
            assertTrue(in != null, "default config missing from resources");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(ConfigParser.parse(text).profiles().size() >= 2);
        }
    }
}
