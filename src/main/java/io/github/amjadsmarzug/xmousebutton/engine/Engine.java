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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * <p>Macros run on a worker thread so their waits never stall the mouse hook.
 *
 * <p>Pointer speed: while a profile sets {@code pointer_speed}, that speed is applied. A watch
 * started with {@link #startProfileWatch()} re-checks the foreground window every quarter second,
 * so the speed follows the window even when the mouse is not clicked.
 *
 * <p>Event handling is synchronized because hold timers and the profile watch run on other threads.
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
    private Integer appliedPointerSpeed;
    private boolean watching;
    private final ExecutorService macroWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "xmousebutton-macro");
        thread.setDaemon(true);
        return thread;
    });

    private static final long WATCH_INTERVAL_MS = 250;

    public Engine(Platform platform, Config config) {
        this(platform, config, Scheduler.system());
    }

    public Engine(Platform platform, Config config, Scheduler scheduler) {
        this.platform = platform;
        this.config = config;
        this.scheduler = scheduler;
    }

    /** Starts re-checking the foreground window so per-profile pointer speed follows focus. */
    public synchronized void startProfileWatch() {
        watching = true;
        watchTick();
    }

    private void watchTick() {
        if (!watching) {
            return;
        }
        applyPointerSpeed(config.resolve(platform.foregroundWindow()));
        scheduler.schedule(this::onWatchTimer, WATCH_INTERVAL_MS);
    }

    synchronized void onWatchTimer() {
        watchTick();
    }

    /** Stops the watch and restores the system pointer speed. */
    public synchronized void stop() {
        watching = false;
        if (appliedPointerSpeed != null) {
            platform.setPointerSpeed(null);
            appliedPointerSpeed = null;
        }
        macroWorker.shutdown();
    }

    private void applyPointerSpeed(Profile profile) {
        Integer wanted = profile.pointerSpeed();
        if (Objects.equals(wanted, appliedPointerSpeed)) {
            return;
        }
        try {
            platform.setPointerSpeed(wanted);
            appliedPointerSpeed = wanted;
        } catch (RuntimeException e) {
            System.err.println("could not change pointer speed: " + e.getMessage());
        }
    }

    /** Waits until queued macros have finished. Intended for tests. */
    void awaitMacros() throws InterruptedException, ExecutionException {
        macroWorker.submit(() -> { }).get();
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
            Profile profile = config.resolve(platform.foregroundWindow());
            applyPointerSpeed(profile);
            return execute(profile.lookup(activeLayer, button));
        }

        if (event.pressed()) {
            return press(button);
        }
        return release(button);
    }

    private boolean press(MouseButton button) {
        Profile profile = config.resolve(platform.foregroundWindow());
        applyPointerSpeed(profile);

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
            case Action.Type t -> {
                platform.typeText(t.text());
                yield true;
            }
            case Action.Macro m -> {
                macroWorker.execute(() -> runMacro(m.steps()));
                yield true;
            }
            case Action.Layer l -> true;
            case Action.Hold h -> true; // nested holds are rejected by the parser
            case Action.Wait w -> true; // waits only exist inside macros
        };
    }

    private void runMacro(List<Action> steps) {
        for (Action step : steps) {
            if (step instanceof Action.Wait wait) {
                try {
                    Thread.sleep(wait.millis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else {
                execute(step);
            }
        }
    }

    private void launch(Action.Run run) {
        try {
            platform.launch(run.command());
        } catch (IOException e) {
            System.err.println("could not start " + run.command().get(0) + ": " + e.getMessage());
        }
    }
}
