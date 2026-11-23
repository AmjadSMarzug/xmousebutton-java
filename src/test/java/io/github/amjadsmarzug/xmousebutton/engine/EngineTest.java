package io.github.amjadsmarzug.xmousebutton.engine;

import io.github.amjadsmarzug.xmousebutton.config.ConfigParser;
import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.github.amjadsmarzug.xmousebutton.model.MouseButton.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {

    private static final String CONFIG = """
            [default]
            x1 = key alt+left
            right = block

            [browser]
            match.process = chrome.exe
            x1 = key ctrl+shift+tab
            middle = layer 2
            layer2.left = key f12
            wheel_up = wheel down 2
            """;

    private RecordingPlatform platform;
    private Engine engine;

    @BeforeEach
    void setUp() throws Exception {
        platform = new RecordingPlatform();
        engine = new Engine(platform, ConfigParser.parse(CONFIG));
    }

    @Test
    void unboundButtonPassesThrough() {
        assertFalse(engine.handle(ButtonEvent.press(LEFT)));
        assertFalse(engine.handle(ButtonEvent.release(LEFT)));
        assertTrue(platform.calls.isEmpty());
    }

    @Test
    void keyActionFiresOnPressAndSwallowsRelease() {
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        assertTrue(engine.handle(ButtonEvent.release(X1)));
        assertEquals(java.util.List.of("keys KeyCombo[modifiers=[18], key=37]"), platform.calls);
    }

    @Test
    void blockedButtonIsSwallowedWithoutOutput() {
        assertTrue(engine.handle(ButtonEvent.press(RIGHT)));
        assertTrue(engine.handle(ButtonEvent.release(RIGHT)));
        assertTrue(platform.calls.isEmpty());
    }

    @Test
    void matchingWindowSelectsProfile() {
        platform.window = new WindowInfo("chrome.exe", "Home");
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        assertEquals(1, platform.calls.size());
        assertTrue(platform.calls.get(0).startsWith("keys KeyCombo[modifiers=[17, 16]"));
    }

    @Test
    void wheelFiresOnPressOnly() {
        platform.window = new WindowInfo("chrome.exe", "");
        assertTrue(engine.handle(ButtonEvent.press(WHEEL_UP)));
        assertEquals(java.util.List.of("wheel WHEEL_DOWN x2"), platform.calls);
    }

    @Test
    void layerHoldChangesMappingUntilReleased() {
        platform.window = new WindowInfo("chrome.exe", "");
        assertTrue(engine.handle(ButtonEvent.press(MIDDLE)));
        assertTrue(engine.handle(ButtonEvent.press(LEFT)));
        assertTrue(engine.handle(ButtonEvent.release(LEFT)));
        assertTrue(engine.handle(ButtonEvent.release(MIDDLE)));
        assertEquals(1, platform.calls.size());

        // After the layer button is released, left goes back to passthrough.
        assertFalse(engine.handle(ButtonEvent.press(LEFT)));
        assertFalse(engine.handle(ButtonEvent.release(LEFT)));
    }

    @Test
    void releaseUsesActionChosenOnPressEvenIfWindowChanged() {
        platform.window = new WindowInfo("chrome.exe", "");
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        platform.window = WindowInfo.UNKNOWN;
        // Release still swallows, because the press was handled by the browser profile.
        assertTrue(engine.handle(ButtonEvent.release(X1)));
    }

    @Test
    void runActionLaunchesCommand() throws Exception {
        Engine runEngine = new Engine(platform,
                ConfigParser.parse("[default]\nmiddle = run notepad.exe \"my file.txt\"\n"));
        assertTrue(runEngine.handle(ButtonEvent.press(MIDDLE)));
        assertEquals(java.util.List.of("launch notepad.exe my file.txt"), platform.calls);
    }
}
