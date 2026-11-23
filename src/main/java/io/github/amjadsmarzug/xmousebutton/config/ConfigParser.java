package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the plain-text configuration format.
 *
 * <pre>
 * # comment
 * [default]
 * x1 = key alt+left
 *
 * [browser]
 * match.process = chrome.exe, msedge.exe, firefox.exe
 * match.title = *YouTube*
 * x1 = key ctrl+shift+tab
 * middle = layer 2
 * layer2.left = key ctrl+t
 * </pre>
 */
public final class ConfigParser {

    private static final Pattern SECTION = Pattern.compile("^\\[([^\\]]+)]$");
    private static final Pattern LAYER_KEY = Pattern.compile("^layer(\\d+)\\.(.+)$");

    private ConfigParser() {
    }

    public static Config load(Path file) throws ConfigException {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ConfigException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public static Config parse(String text) throws ConfigException {
        Map<String, ProfileBuilder> builders = new LinkedHashMap<>();
        ProfileBuilder current = null;
        String[] lines = text.split("\\R", -1);

        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            try {
                Matcher section = SECTION.matcher(line);
                if (section.matches()) {
                    String name = section.group(1).strip();
                    if (name.isEmpty()) {
                        throw new ConfigException("empty section name");
                    }
                    if (builders.containsKey(name.toLowerCase(Locale.ROOT))) {
                        throw new ConfigException("duplicate section [" + name + "]");
                    }
                    current = new ProfileBuilder(name);
                    builders.put(name.toLowerCase(Locale.ROOT), current);
                    continue;
                }
                if (current == null) {
                    throw new ConfigException("binding before any [section]");
                }
                int eq = line.indexOf('=');
                if (eq < 0) {
                    throw new ConfigException("expected 'key = value'");
                }
                String key = line.substring(0, eq).strip().toLowerCase(Locale.ROOT);
                String value = line.substring(eq + 1).strip();
                current.apply(key, value);
            } catch (ConfigException e) {
                throw new ConfigException("line " + lineNo + ": " + e.getMessage(), e);
            }
        }

        List<Profile> profiles = new ArrayList<>();
        for (ProfileBuilder builder : builders.values()) {
            profiles.add(builder.build());
        }
        return new Config(profiles);
    }

    private static final class ProfileBuilder {
        private final String name;
        private final List<String> processGlobs = new ArrayList<>();
        private final List<String> titleGlobs = new ArrayList<>();
        private final Map<Integer, Map<MouseButton, Action>> layers = new LinkedHashMap<>();
        private final Map<MouseButton, Map<MouseButton, Action>> chords = new EnumMap<>(MouseButton.class);

        ProfileBuilder(String name) {
            this.name = name;
        }

        void apply(String key, String value) throws ConfigException {
            switch (key) {
                case "match.process" -> processGlobs.addAll(splitList(value));
                case "match.title" -> titleGlobs.addAll(splitList(value));
                default -> applyBinding(key, value);
            }
        }

        private void applyBinding(String key, String value) throws ConfigException {
            if (key.contains("+")) {
                applyChord(key, value);
                return;
            }
            int layer = 0;
            String buttonName = key;
            Matcher layerMatcher = LAYER_KEY.matcher(key);
            if (layerMatcher.matches()) {
                layer = Integer.parseInt(layerMatcher.group(1));
                buttonName = layerMatcher.group(2);
                if (layer < 1 || layer > Profile.MAX_LAYER) {
                    throw new ConfigException("layer must be from 1 to " + Profile.MAX_LAYER);
                }
            }
            MouseButton button = MouseButton.fromConfigName(buttonName).orElse(null);
            if (button == null) {
                throw new ConfigException("unknown button '" + buttonName + "'");
            }
            Action action = ActionParser.parse(value);
            if (button.isWheel() && action instanceof Action.Hold) {
                throw new ConfigException("hold does not work on wheel buttons");
            }
            Map<MouseButton, Action> layerMap = layers.computeIfAbsent(layer, l -> new EnumMap<>(MouseButton.class));
            if (layerMap.containsKey(button)) {
                throw new ConfigException("'" + key + "' is bound twice in [" + name + "]");
            }
            layerMap.put(button, action);
        }

        /** {@code first+second = action}: fires when {@code second} is pressed while {@code first} is held. */
        private void applyChord(String key, String value) throws ConfigException {
            String[] names = key.split("\\+");
            if (names.length != 2) {
                throw new ConfigException("a chord takes exactly two buttons, like left+right");
            }
            MouseButton first = MouseButton.fromConfigName(names[0].strip()).orElse(null);
            MouseButton second = MouseButton.fromConfigName(names[1].strip()).orElse(null);
            if (first == null || second == null) {
                throw new ConfigException("unknown button in chord '" + key + "'");
            }
            if (!first.isPhysicalButton() || !second.isPhysicalButton() || first == second) {
                throw new ConfigException("chords need two different physical buttons");
            }
            Action action = ActionParser.parse(value);
            if (action instanceof Action.Hold || action instanceof Action.Layer) {
                throw new ConfigException("a chord cannot be hold or layer");
            }
            Map<MouseButton, Action> byFirst = chords.computeIfAbsent(first, f -> new EnumMap<>(MouseButton.class));
            if (byFirst.containsKey(second)) {
                throw new ConfigException("chord '" + key + "' is defined twice in [" + name + "]");
            }
            byFirst.put(second, action);
        }

        Profile build() {
            return new Profile(name, processGlobs, titleGlobs, layers, chords);
        }

        private static List<String> splitList(String value) {
            List<String> items = new ArrayList<>();
            for (String part : value.split(",")) {
                String item = part.strip();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
            return items;
        }
    }
}
