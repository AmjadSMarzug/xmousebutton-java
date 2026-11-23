package io.github.amjadsmarzug.xmousebutton.ui;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mutable copy of a profile that the setup window edits. Turned back into a {@link Profile} to save. */
final class ProfileDraft {

    String name;
    boolean enabled = true;
    List<String> processGlobs = new ArrayList<>();
    List<String> titleGlobs = new ArrayList<>();
    Integer pointerSpeed;
    /** Layer 0 is the default layer; 1 to 10 are the numbered layers. */
    final Map<Integer, EnumMap<MouseButton, Action>> layers = new HashMap<>();
    final Map<MouseButton, EnumMap<MouseButton, Action>> chords = new EnumMap<>(MouseButton.class);

    ProfileDraft(String name) {
        this.name = name;
        for (int layer = 0; layer <= Profile.MAX_LAYER; layer++) {
            layers.put(layer, new EnumMap<>(MouseButton.class));
        }
    }

    static ProfileDraft from(Profile profile) {
        ProfileDraft draft = new ProfileDraft(profile.name());
        draft.enabled = profile.enabled();
        draft.processGlobs = new ArrayList<>(profile.processGlobs());
        draft.titleGlobs = new ArrayList<>(profile.titleGlobs());
        draft.pointerSpeed = profile.pointerSpeed();
        for (int layer = 0; layer <= Profile.MAX_LAYER; layer++) {
            draft.layers.get(layer).putAll(profile.layerBindings(layer));
        }
        profile.chords().forEach((first, byFirst) -> {
            EnumMap<MouseButton, Action> copy = new EnumMap<>(MouseButton.class);
            copy.putAll(byFirst);
            draft.chords.put(first, copy);
        });
        return draft;
    }

    Profile toProfile() {
        Map<Integer, Map<MouseButton, Action>> bindings = new HashMap<>();
        for (Map.Entry<Integer, EnumMap<MouseButton, Action>> entry : layers.entrySet()) {
            bindings.put(entry.getKey(), entry.getValue());
        }
        Map<MouseButton, Map<MouseButton, Action>> chordCopy = new HashMap<>();
        chords.forEach((first, byFirst) -> chordCopy.put(first, byFirst));
        return new Profile(name, enabled, processGlobs, titleGlobs, bindings, chordCopy, pointerSpeed);
    }

    Action binding(int layer, MouseButton button) {
        return layers.get(layer).getOrDefault(button, Action.Passthrough.INSTANCE);
    }

    /** Passthrough means "no binding", so it is stored as an absent entry. */
    void setBinding(int layer, MouseButton button, Action action) {
        if (action instanceof Action.Passthrough) {
            layers.get(layer).remove(button);
        } else {
            layers.get(layer).put(button, action);
        }
    }

    ProfileDraft copyWithName(String newName) {
        ProfileDraft copy = from(toProfile());
        copy.name = newName;
        return copy;
    }

    @Override
    public String toString() {
        return name;
    }
}
