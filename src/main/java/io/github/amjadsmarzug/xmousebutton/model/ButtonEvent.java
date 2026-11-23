package io.github.amjadsmarzug.xmousebutton.model;

/** A single raw mouse input as reported by the platform backend. */
public record ButtonEvent(MouseButton button, boolean pressed) {

    public static ButtonEvent press(MouseButton button) {
        return new ButtonEvent(button, true);
    }

    public static ButtonEvent release(MouseButton button) {
        return new ButtonEvent(button, false);
    }
}
