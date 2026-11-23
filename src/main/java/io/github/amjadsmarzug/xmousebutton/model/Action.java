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
}
