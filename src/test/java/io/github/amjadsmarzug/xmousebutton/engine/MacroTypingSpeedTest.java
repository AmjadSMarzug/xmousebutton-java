package io.github.amjadsmarzug.xmousebutton.engine;

import io.github.amjadsmarzug.xmousebutton.config.ConfigParser;
import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.amjadsmarzug.xmousebutton.model.MouseButton.MIDDLE;
import static io.github.amjadsmarzug.xmousebutton.model.MouseButton.X1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacroTypingSpeedTest {

    /** Keeps timer tasks so a test can run the profile watch by hand. */
    private static final class CapturingScheduler implements Scheduler {
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public Runnable schedule(Runnable task, long delayMillis) {
            tasks.add(task);
            return () -> { };
        }
    }

    private static final String CONFIG = """
            [default]
            x1 = type "hi there"
            middle = macro key ctrl+a ; wait 5 ; key ctrl+c

            [games]
            match.process = game.exe
            pointer_speed = 15
            """;

    @Test
    void typeActionSendsLiteralTextIncludingSpaces() throws Exception {
        RecordingPlatform platform = new RecordingPlatform();
        Engine engine = new Engine(platform, ConfigParser.parse(CONFIG), new CapturingScheduler());
        assertTrue(engine.handle(ButtonEvent.press(X1)));
        assertEquals(List.of("type hi there"), platform.calls);
    }

    @Test
    void macroRunsStepsInOrderOnWorkerThread() throws Exception {
        RecordingPlatform platform = new RecordingPlatform();
        Engine engine = new Engine(platform, ConfigParser.parse(CONFIG), new CapturingScheduler());
        assertTrue(engine.handle(ButtonEvent.press(MIDDLE)));
        engine.awaitMacros();
        assertEquals(List.of("keys KeyCombo[modifiers=[17], key=65]",
                "keys KeyCombo[modifiers=[17], key=67]"), platform.calls);
    }

    @Test
    void pointerSpeedFollowsActiveProfileAndRestoresDefault() throws Exception {
        RecordingPlatform platform = new RecordingPlatform();
        CapturingScheduler scheduler = new CapturingScheduler();
        Engine engine = new Engine(platform, ConfigParser.parse(CONFIG), scheduler);

        platform.window = new WindowInfo("game.exe", "");
        engine.startProfileWatch();
        assertEquals(List.of("speed 15"), platform.calls);

        // Focus moves to another app: the watch restores the system speed.
        platform.window = new WindowInfo("notepad.exe", "");
        scheduler.tasks.get(scheduler.tasks.size() - 1).run();
        assertEquals(List.of("speed 15", "speed null"), platform.calls);

        // Nothing changed, so nothing is re-applied.
        scheduler.tasks.get(scheduler.tasks.size() - 1).run();
        assertEquals(2, platform.calls.size());

        engine.stop();
        assertEquals("speed null", platform.calls.get(platform.calls.size() - 1));
    }

    @Test
    void stopBeforeAnySpeedChangeDoesNotTouchSystemSpeed() throws Exception {
        RecordingPlatform platform = new RecordingPlatform();
        Engine engine = new Engine(platform, ConfigParser.parse(CONFIG), new CapturingScheduler());
        engine.stop();
        assertTrue(platform.calls.isEmpty());
    }
}
