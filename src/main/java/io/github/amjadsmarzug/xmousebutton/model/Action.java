package io.github.amjadsmarzug.xmousebutton.model;

import java.util.List;

/** What a mouse input should do. Sealed so the engine can switch over every case. */
public sealed interface Action {

    /** Let the original input reach Windows untouched. */
    record Passthrough() implements Action {
        public static final Passthrough INSTANCE = new Passthrough();
    }

    /** Swallow the input and do nothing. */
    record Block() implements Action {
        public static final Block INSTANCE = new Block();
    }

    /** Tap a keyboard shortcut. */
    record Key(KeyCombo combo) implements Action {
    }

    /** Click a physical mouse button (down and up). */
    record Click(MouseButton button) implements Action {
    }

    /** Scroll the wheel by a number of notches. {@code direction} is one of the WHEEL_* values. */
    record Wheel(MouseButton direction, int notches) implements Action {
    }

    /** Start a program with the given argument vector. Does not wait for it to exit. */
    record Run(List<String> command) implements Action {
        public Run {
            command = List.copyOf(command);
        }
    }

    /** While this button is held, use the mappings of layer {@code layer} (1 to 10). */
    record Layer(int layer) implements Action {
    }

    /**
     * Long-press binding. If the button is held for {@code millis} ms, {@code onHold} runs
     * while it is still down. If it is released earlier, {@code onTap} runs instead.
     */
    record Hold(long millis, Action onHold, Action onTap) implements Action {
    }

    /** Types text one character at a time, using Unicode input so any character works. */
    record Type(String text) implements Action {
    }

    /** Pause between macro steps. Only valid inside a macro. */
    record Wait(long millis) implements Action {
    }

    /** Runs steps one after another on a worker thread, so a long macro never blocks the mouse hook. */
    record Macro(List<Action> steps) implements Action {
        public Macro {
            steps = List.copyOf(steps);
        }
    }
}
