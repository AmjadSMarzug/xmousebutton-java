package io.github.amjadsmarzug.xmousebutton.engine;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.platform.Platform;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Turns raw mouse events into actions.
 *
 * <p>The action chosen on press is remembered so the matching release is handled the same way,
 * even if the foreground window changed while the button was held. Not thread-safe except for
 * {@link #setConfig(Config)}, which may be called from the tray thread.
 */
public final class Engine {

    private final Platform platform;
    private volatile Config config;

    private final Map<MouseButton, Action> held = new EnumMap<>(MouseButton.class);
    private int activeLayer;
    private MouseButton layerOwner;

    public Engine(Platform platform, Config config) {
        this.platform = platform;
        this.config = config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Config config() {
        return config;
    }

    /** @return {@code true} if the original event must be swallowed */
    public synchronized boolean handle(ButtonEvent event) {
        MouseButton button = event.button();

        if (button.isWheel()) {
            if (!event.pressed()) {
                return false;
            }
            Action action = currentProfileLookup(button);
            return execute(action);
        }

        if (event.pressed()) {
            Action action = currentProfileLookup(button);
            held.put(button, action);
            if (action instanceof Action.Layer layer) {
                activeLayer = layer.layer();
                layerOwner = button;
                return true;
            }
            return execute(action);
        }

        Action action = held.remove(button);
        if (action == null) {
            return false;
        }
        if (action instanceof Action.Layer && button == layerOwner) {
            activeLayer = 0;
            layerOwner = null;
        }
        return !(action instanceof Action.Passthrough);
    }

    private Action currentProfileLookup(MouseButton button) {
        return config.resolve(platform.foregroundWindow()).lookup(activeLayer, button);
    }

    /** Runs the action immediately. Returns whether the triggering event is swallowed. */
    private boolean execute(Action action) {
        return switch (action) {
            case Action.Passthrough p -> false;
            case Action.Block b -> true;
            case Action.Key k -> {
                platform.tapKeys(k.combo());
                yield true;
            }
            case Action.Click c -> {
                platform.sendButton(c.button(), true);
                platform.sendButton(c.button(), false);
                yield true;
            }
            case Action.Wheel w -> {
                platform.sendWheel(w.direction(), w.notches());
                yield true;
            }
            case Action.Run r -> {
                launch(r);
                yield true;
            }
            case Action.Layer l -> true;
        };
    }

    private void launch(Action.Run run) {
        try {
            platform.launch(run.command());
        } catch (IOException e) {
            System.err.println("could not start " + run.command().get(0) + ": " + e.getMessage());
        }
    }
}
