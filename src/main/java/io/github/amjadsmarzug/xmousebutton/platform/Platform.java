package io.github.amjadsmarzug.xmousebutton.platform;

import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.KeyCombo;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

/**
 * Operating-system boundary. Everything the engine needs from the OS goes through here,
 * which keeps the mapping logic testable without a desktop session.
 */
public interface Platform {

    /** Short name shown in logs and the tray tooltip. */
    String name();

    /** Whether this backend can observe and swallow global mouse input. */
    boolean supportsMouseHook();

    /**
     * Starts observing global mouse input. The handler returns {@code true} to swallow an event.
     * Events the backend itself injects must never reach the handler.
     */
    void startMouseHook(Predicate<ButtonEvent> handler) throws IOException;

    void stopMouseHook();

    /** Describes the window that currently has focus. */
    WindowInfo foregroundWindow();

    /** Presses the modifiers, taps the key, then releases everything. */
    void tapKeys(KeyCombo combo);

    /** Presses or releases a physical mouse button. */
    void sendButton(MouseButton button, boolean down);

    /** Scrolls the wheel. {@code direction} is one of the WHEEL_* values. */
    void sendWheel(MouseButton direction, int notches);

    /** Starts a program without waiting for it to exit. */
    void launch(List<String> command) throws IOException;
}
