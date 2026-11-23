package io.github.amjadsmarzug.xmousebutton.engine;

import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.KeyCombo;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;
import io.github.amjadsmarzug.xmousebutton.platform.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Test double that records every output call and reports a settable foreground window. */
final class RecordingPlatform implements Platform {

    final List<String> calls = new ArrayList<>();
    WindowInfo window = WindowInfo.UNKNOWN;

    @Override
    public String name() {
        return "recording";
    }

    @Override
    public boolean supportsMouseHook() {
        return true;
    }

    @Override
    public void startMouseHook(Predicate<ButtonEvent> handler) {
    }

    @Override
    public void stopMouseHook() {
    }

    @Override
    public WindowInfo foregroundWindow() {
        return window;
    }

    @Override
    public void tapKeys(KeyCombo combo) {
        calls.add("keys " + combo);
    }

    @Override
    public void sendButton(MouseButton button, boolean down) {
        calls.add("button " + button + (down ? " down" : " up"));
    }

    @Override
    public void sendWheel(MouseButton direction, int notches) {
        calls.add("wheel " + direction + " x" + notches);
    }

    @Override
    public void launch(List<String> command) {
        calls.add("launch " + String.join(" ", command));
    }
}
