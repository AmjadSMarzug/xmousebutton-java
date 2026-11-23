package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;

import java.util.List;
import java.util.Map;

/** Turns profiles back into config text. The output parses to the same profiles. */
public final class ConfigWriter {

    private ConfigWriter() {
    }

    public static String write(List<Profile> profiles) {
        StringBuilder out = new StringBuilder();
        for (Profile profile : profiles) {
            out.append('[').append(profile.name()).append("]\n");
            if (!profile.enabled()) {
                out.append("enabled = false\n");
            }
            if (!profile.processGlobs().isEmpty()) {
                out.append("match.process = ").append(String.join(", ", profile.processGlobs())).append('\n');
            }
            if (!profile.titleGlobs().isEmpty()) {
                out.append("match.title = ").append(String.join(", ", profile.titleGlobs())).append('\n');
            }
            if (profile.pointerSpeed() != null) {
                out.append("pointer_speed = ").append(profile.pointerSpeed()).append('\n');
            }
            for (int layer = 0; layer <= Profile.MAX_LAYER; layer++) {
                String prefix = layer == 0 ? "" : "layer" + layer + ".";
                for (Map.Entry<MouseButton, Action> entry : profile.layerBindings(layer).entrySet()) {
                    out.append(prefix).append(entry.getKey().configName())
                            .append(" = ").append(ActionWriter.write(entry.getValue())).append('\n');
                }
            }
            for (Map.Entry<MouseButton, Map<MouseButton, Action>> byFirst : profile.chords().entrySet()) {
                for (Map.Entry<MouseButton, Action> chord : byFirst.getValue().entrySet()) {
                    out.append(byFirst.getKey().configName()).append('+').append(chord.getKey().configName())
                            .append(" = ").append(ActionWriter.write(chord.getValue())).append('\n');
                }
            }
            out.append('\n');
        }
        return out.toString();
    }
}
