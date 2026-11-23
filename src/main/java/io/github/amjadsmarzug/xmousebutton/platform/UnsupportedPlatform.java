package io.github.amjadsmarzug.xmousebutton.platform;

import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.KeyCombo;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.WindowInfo;

import java.util.List;
import java.util.function.Predicate;

/**
 * Used on systems where no mouse backend exists yet. The configuration can still be
 * validated with {@code --check}, but nothing is remapped.
 */
public final class UnsupportedPlatform implements Platform {

    private final String osName;

    public UnsupportedPlatform(String osName) {
        this.osName = osName;
    }

    @Override
    public String name() {
        return "unsupported (" + osName + ")";
    }

    @Override
    public boolean supportsMouseHook() {
        return false;
    }

    @Override
    public void startMouseHook(Predicate<ButtonEvent> handler) {
        throw new UnsupportedOperationException("no mouse backend for " + osName + " yet");
    }

    @Override
    public void stopMouseHook() {
    }

    @Override
    public WindowInfo foregroundWindow() {
        return WindowInfo.UNKNOWN;
    }

    @Override
    public void tapKeys(KeyCombo combo) {
        throw new UnsupportedOperationException("no input backend for " + osName + " yet");
    }

    @Override
    public void sendButton(MouseButton button, boolean down) {
        throw new UnsupportedOperationException("no input backend for " + osName + " yet");
    }

    @Override
    public void sendWheel(MouseButton direction, int notches) {
        throw new UnsupportedOperationException("no input backend for " + osName + " yet");
    }

    @Override
    public void launch(List<String> command) {
        throw new UnsupportedOperationException("no process backend for " + osName + " yet");
    }

    @Override
    public void typeText(String text) {
        throw new UnsupportedOperationException("no input backend for " + osName + " yet");
    }

    @Override
    public void setPointerSpeed(Integer speed) {
        // No pointer-speed backend here, so per-profile speeds are ignored.
    }
}
