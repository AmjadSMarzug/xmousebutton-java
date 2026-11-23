package io.github.amjadsmarzug.xmousebutton.engine;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.ButtonEvent;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;
import io.github.amjadsmarzug.xmousebutton.platform.Platform;

import java.io.IOException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns raw mouse events into actions.
 *
 * <p>The action chosen on press is remembered so the matching release is handled the same way,
 * even if the foreground window changed while the button was held.
 *
 * <p>Chords: pressing a second button while a first one is held runs the chord action. Both
 * releases are swallowed. The first button's own binding still runs on its press, so bind it to
 * {@code block}, {@code hold} or a layer if it should stay silent.
 *
 * <p>Hold: a {@code hold} binding runs its long-press action from a timer while the button is
 * still down. A release before the timer fires runs the tap action instead.
 *
 * <p>Event handling is synchronized because hold timers fire on another thread.
 */
public final class Engine {

    private final Platform platform;
    private final Scheduler scheduler;
    private volatile Config config;

    private final Map<MouseButton, Action> held = new EnumMap<>(MouseButton.class);
    /** Cancel handles for hold timers that have not fired yet. */
    private final Map<MouseButton, Runnable> pendingHolds = new EnumMap<>(MouseButton.class);
    /** First buttons whose release must be swallowed because a chord used them. */
    private final Set<MouseButton> chordFirsts = EnumSet.noneOf(MouseButton.class);
    private int activeLayer;
    private MouseButton layerOwner;

    public Engine(Platform platform, Config config) {
        this(platform, config, Scheduler.system());
    }

    public Engine(Platform platform, Config config, Scheduler scheduler) {
        this.platform = platform;
        this.config = config;
        this.scheduler = scheduler;
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
            Action action = config.resolve(platform.foregroundWindow()).lookup(activeLayer, button);
            return execute(action);
        }

        if (event.pressed()) {
            return press(button);
        }
        return release(button);
    }

    private boolean press(MouseButton button) {
        Profile profile = config.resolve(platform.foregroundWindow());

        if (tryChord(button, profile)) {
            return true;
        }

        Action action = profile.lookup(activeLayer, button);
        held.put(button, action);

        if (action instanceof Action.Layer layer) {
            activeLayer = layer.layer();
            layerOwner = button;
            return true;
        }
        if (action instanceof Action.Hold hold) {
            pendingHolds.put(button, scheduler.schedule(() -> onHoldTimer(button), hold.millis()));
            return true;
        }
        return execute(action);
    }

    private boolean tryChord(MouseButton second, Profile profile) {
        for (MouseButton first : List.copyOf(held.keySet())) {
            if (!first.isPhysicalButton()) {
                continue;
            }
            Action chord = profile.chord(first, second);
            if (chord == null) {
                continue;
            }
            cancelPendingHold(first);
            chordFirsts.add(first);
            held.put(second, Action.Block.INSTANCE);
            execute(chord);
            return true;
        }
        return false;
    }

    private boolean release(MouseButton button) {
        if (chordFirsts.remove(button)) {
            cancelPendingHold(button);
            held.remove(button);
            return true;
        }

        Action action = held.remove(button);
        if (action == null) {
            return false;
        }
        if (action instanceof Action.Hold hold) {
            Runnable cancel = pendingHolds.remove(button);
            if (cancel != null) {
                // Released before the timer fired: this was a tap.
                cancel.run();
                execute(hold.onTap());
            }
            return true;
        }
        if (action instanceof Action.Layer && button == layerOwner) {
            activeLayer = 0;
            layerOwner = null;
        }
        return !(action instanceof Action.Passthrough);
    }

    /** Runs on the timer thread once a hold has lasted long enough. */
    synchronized void onHoldTimer(MouseButton button) {
        if (pendingHolds.remove(button) == null) {
            return; // already released, or cancelled by a chord
        }
        if (held.get(button) instanceof Action.Hold hold) {
            execute(hold.onHold());
        }
    }

    private void cancelPendingHold(MouseButton button) {
        Runnable cancel = pendingHolds.remove(button);
        if (cancel != null) {
            cancel.run();
        }
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
            case Action.Hold h -> true; // nested holds are rejected by the parser
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
