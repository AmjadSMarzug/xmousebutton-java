package io.github.amjadsmarzug.xmousebutton.engine;

import io.github.amjadsmarzug.xmousebutton.config.ConfigException;
import io.github.amjadsmarzug.xmousebutton.config.ConfigParser;
import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.amjadsmarzug.xmousebutton.model.MouseButton.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChordAndHoldTest {

    /** Holds timer tasks so a test decides when (or whether) they fire. */
    private static final class ManualScheduler implements Scheduler {
        final List<Runnable> tasks = new ArrayList<>();
        final List<Boolean> cancelled = new ArrayList<>();

        @Override
        public Runnable schedule(Runnable task, long delayMillis) {
            int index = tasks.size();
            tasks.add(task);
            cancelled.add(false);
            return () -> cancelled.set(index, true);
        }

        void fire(int index) {
            if (!cancelled.get(index)) {
                tasks.get(index).run();
            }
        }
    }

    private static final String CONFIG = """
            [default]
            left = block
            x1 = hold 500 key ctrl+shift+esc | key f5
            x2 = block
            right = block
            left+right = key ctrl+c
            x1+x2 = key ctrl+v
            """;

    private RecordingPlatform platform;
    private ManualScheduler scheduler;
    private Engine engine;

    @BeforeEach
    void setUp() throws Exception {
        platform = new RecordingPlatform();
        scheduler = new ManualScheduler();
        engine = new Engine(platform, ConfigParser.parse(CONFIG), scheduler);
    }

    @Test
    void chordFiresOnSecondPressAndSwallowsBothReleases() {
        assertTrue(engine.handle(ButtonEvent.press(LEFT)));
        assertTrue(engine.handle(ButtonEvent.press(RIGHT)));
        assertTrue(engine.handle(ButtonEvent.release(RIGHT)));
        assertTrue(engine.handle(ButtonEvent.release(LEFT)));
        assertEquals(List.of("keys KeyCombo[modifiers=[17], key=67]"), platform.calls);
    }

    @Test
    void chordIsOrderSensitive() {
        // right+left is not defined, so the right press is handled as a normal block.
        assertTrue(engine.handle(ButtonEvent.press(RIGHT)));
        assertTrue(engine.handle(ButtonEvent.press(LEFT)));
        assertTrue(platform.calls.isEmpty());
    }

    @Test
    void chordWithPassthroughFirstButtonStillSwallowsItsRelease() throws Exception {
        Engine plain = new Engine(platform, ConfigParser.parse("""
                [default]
                middle = passthrough
                x2 = block
                middle+x2 = key ctrl+z
                """), scheduler);
        assertFalse(plain.handle(ButtonEvent.press(MIDDLE)));
        assertTrue(plain.handle(ButtonEvent.press(X2)));
        assertTrue(plain.handle(ButtonEvent.release(X2)));
        assertTrue(plain.handle(ButtonEvent.release(MIDDLE)));
    }

    @Test
    void shortPressRunsTapAction() {
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        assertEquals(1, scheduler.tasks.size());
        assertTrue(engine.handle(ButtonEvent.release(X1)));
        assertEquals(List.of("keys KeyCombo[modifiers=[], key=116]"), platform.calls);
    }

    @Test
    void longPressRunsHoldActionWhileDownAndNoTapOnRelease() {
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        scheduler.fire(0);
        assertEquals(List.of("keys KeyCombo[modifiers=[17, 16], key=27]"), platform.calls);

        assertTrue(engine.handle(ButtonEvent.release(X1)));
        assertEquals(1, platform.calls.size(), "release after a hold must not also tap");
    }

    @Test
    void holdTimerCancelledByChordDoesNotFire() {
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        assertTrue(engine.handle(ButtonEvent.press(X2)));
        scheduler.fire(0);
        assertTrue(platform.calls.stream().noneMatch(c -> c.contains("key=27")));
        assertTrue(engine.handle(ButtonEvent.release(X1)));
        assertTrue(engine.handle(ButtonEvent.release(X2)));
        assertEquals(List.of("keys KeyCombo[modifiers=[17], key=86]"), platform.calls);
    }

    @Test
    void holdTimerFiringAfterReleaseIsIgnored() {
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        assertTrue(engine.handle(ButtonEvent.release(X1)));
        // A late timer callback must not run the hold action after the tap already ran.
        scheduler.tasks.get(0).run();
        assertEquals(List.of("keys KeyCombo[modifiers=[], key=116]"), platform.calls);
    }

    @Test
    void holdAndChordParsingRejectsBadInput() {
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nx1 = hold 50 block\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nx1 = hold 500\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nx1 = hold 500 hold 500 block\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nwheel_up = hold 500 block\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nleft+left = block\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nwheel_up+left = block\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nleft+right+middle = block\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nleft+right = layer 1\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[default]\nleft+right = block\nleft+right = block\n"));
    }

    @Test
    void chordDefinedInProfileOnlyAppliesToThatWindow() throws Exception {
        platform.window = new WindowInfo("notepad.exe", "");
        Engine scoped = new Engine(platform, ConfigParser.parse("""
                [default]
                left = block
                right = block
                [editor]
                match.process = notepad.exe
                left+right = key ctrl+s
                """), scheduler);
        // Unbound in this profile, so the left press passes through to Windows as usual.
        assertFalse(scoped.handle(ButtonEvent.press(LEFT)));
        assertTrue(scoped.handle(ButtonEvent.press(RIGHT)));
        assertEquals(List.of("keys KeyCombo[modifiers=[17], key=83]"), platform.calls);
    }
}
