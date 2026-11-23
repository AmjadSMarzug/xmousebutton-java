package io.github.amjadsmarzug.xmousebutton.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Every input the mapper can see. Wheel directions behave like buttons that only fire on press. */
public enum MouseButton {
    LEFT("left", false),
    RIGHT("right", false),
    MIDDLE("middle", false),
    X1("x1", false),
    X2("x2", false),
    WHEEL_UP("wheel_up", true),
    WHEEL_DOWN("wheel_down", true),
    WHEEL_LEFT("wheel_left", true),
    WHEEL_RIGHT("wheel_right", true);

    private final String configName;
    private final boolean wheel;

    MouseButton(String configName, boolean wheel) {
        this.configName = configName;
        this.wheel = wheel;
    }

    public String configName() {
        return configName;
    }

    /** Wheel inputs have no release event. */
    public boolean isWheel() {
        return wheel;
    }

    /** True for the five physical buttons that can be pressed and released. */
    public boolean isPhysicalButton() {
        return !wheel;
    }

    public static Optional<MouseButton> fromConfigName(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(b -> b.configName.equals(key)).findFirst();
    }
}
