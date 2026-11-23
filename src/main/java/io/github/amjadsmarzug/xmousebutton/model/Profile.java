package io.github.amjadsmarzug.xmousebutton.model;

import io.github.amjadsmarzug.xmousebutton.util.Glob;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A named set of button bindings that applies to certain windows.
 * Layer 0 is the base mapping; layers 1 to 10 are used while a layer button is held.
 */
public final class Profile {

    public static final String FALLBACK_NAME = "default";
    public static final int MAX_LAYER = 10;

    private final String name;
    private final List<Pattern> processPatterns;
    private final List<Pattern> titlePatterns;
    private final Map<MouseButton, Action>[] layers;
    private final Map<MouseButton, Map<MouseButton, Action>> chords;
    private final Integer pointerSpeed;

    @SuppressWarnings("unchecked")
    public Profile(String name, List<String> processGlobs, List<String> titleGlobs,
                   Map<Integer, Map<MouseButton, Action>> bindings,
                   Map<MouseButton, Map<MouseButton, Action>> chords,
                   Integer pointerSpeed) {
        this.name = name;
        this.chords = Map.copyOf(chords);
        this.pointerSpeed = pointerSpeed;
        this.processPatterns = processGlobs.stream().map(Glob::compile).toList();
        this.titlePatterns = titleGlobs.stream().map(Glob::compile).toList();
        this.layers = new Map[MAX_LAYER + 1];
        for (int i = 0; i <= MAX_LAYER; i++) {
            Map<MouseButton, Action> map = bindings.get(i);
            layers[i] = map == null ? new EnumMap<>(MouseButton.class) : new EnumMap<>(map);
        }
    }

    public String name() {
        return name;
    }

    public boolean isFallback() {
        return FALLBACK_NAME.equalsIgnoreCase(name);
    }

    /** A profile with no match rules never matches a window on its own. */
    public boolean matches(WindowInfo window) {
        if (processPatterns.isEmpty() && titlePatterns.isEmpty()) {
            return false;
        }
        boolean processOk = processPatterns.isEmpty()
                || processPatterns.stream().anyMatch(p -> p.matcher(window.processName()).matches());
        boolean titleOk = titlePatterns.isEmpty()
                || titlePatterns.stream().anyMatch(p -> p.matcher(window.title()).matches());
        return processOk && titleOk;
    }

    /**
     * Finds the action for a button on a layer. A layer falls back to the base mapping
     * when it has no entry for that button.
     */
    public Action lookup(int layer, MouseButton button) {
        Action action = layers[layer].get(button);
        if (action == null && layer != 0) {
            action = layers[0].get(button);
        }
        return action != null ? action : Action.Passthrough.INSTANCE;
    }

    /**
     * Action for pressing {@code second} while {@code first} is held, or {@code null} if no chord
     * is defined for that pair.
     */
    public Action chord(MouseButton first, MouseButton second) {
        Map<MouseButton, Action> byFirst = chords.get(first);
        return byFirst == null ? null : byFirst.get(second);
    }

    /** Pointer speed (1 to 20) to use while this profile is active, or {@code null} for the system setting. */
    public Integer pointerSpeed() {
        return pointerSpeed;
    }

    public static Profile empty(String name) {
        return new Profile(name, List.of(), List.of(), Map.of(), Map.of(), null);
    }
}
