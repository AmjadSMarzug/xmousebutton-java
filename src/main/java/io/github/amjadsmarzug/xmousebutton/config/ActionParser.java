package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.KeyCombo;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;

import java.util.List;
import java.util.Locale;

/**
 * Parses the right-hand side of a binding line.
 * <pre>
 * passthrough | block
 * key ctrl+shift+t
 * click middle
 * wheel up [notches]
 * run "C:\Program Files\App\app.exe" --flag
 * layer 2
 * </pre>
 */
public final class ActionParser {

    private ActionParser() {
    }

    public static Action parse(String text) throws ConfigException {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new ConfigException("missing action");
        }
        String[] head = trimmed.split("\\s+", 2);
        String verb = head[0].toLowerCase(Locale.ROOT);
        String arg = head.length > 1 ? head[1].trim() : "";

        return switch (verb) {
            case "passthrough", "pass" -> noArg(verb, arg, Action.Passthrough.INSTANCE);
            case "block", "none" -> noArg(verb, arg, Action.Block.INSTANCE);
            case "key" -> {
                requireArg(verb, arg);
                try {
                    yield new Action.Key(KeyCombo.parse(arg));
                } catch (IllegalArgumentException e) {
                    throw new ConfigException(e.getMessage(), e);
                }
            }
            case "click" -> {
                requireArg(verb, arg);
                MouseButton button = MouseButton.fromConfigName(arg)
                        .filter(MouseButton::isPhysicalButton)
                        .orElseThrow(() -> new ConfigException("click needs left, right, middle, x1 or x2"));
                yield new Action.Click(button);
            }
            case "wheel" -> parseWheel(arg);
            case "run" -> {
                requireArg(verb, arg);
                List<String> command = CommandLine.split(arg);
                if (command.isEmpty()) {
                    throw new ConfigException("run needs a program to start");
                }
                yield new Action.Run(command);
            }
            case "hold" -> parseHold(arg);
            case "layer" -> {
                requireArg(verb, arg);
                int layer;
                try {
                    layer = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new ConfigException("layer needs a number from 1 to " + Profile.MAX_LAYER);
                }
                if (layer < 1 || layer > Profile.MAX_LAYER) {
                    throw new ConfigException("layer must be from 1 to " + Profile.MAX_LAYER);
                }
                yield new Action.Layer(layer);
            }
            default -> throw new ConfigException("unknown action '" + verb + "'");
        };
    }

    private static Action parseWheel(String arg) throws ConfigException {
        requireArg("wheel", arg);
        String[] parts = arg.split("\\s+");
        MouseButton direction = switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "up" -> MouseButton.WHEEL_UP;
            case "down" -> MouseButton.WHEEL_DOWN;
            case "left" -> MouseButton.WHEEL_LEFT;
            case "right" -> MouseButton.WHEEL_RIGHT;
            default -> throw new ConfigException("wheel needs up, down, left or right");
        };
        int notches = 1;
        if (parts.length > 1) {
            try {
                notches = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new ConfigException("wheel notches must be a number");
            }
            if (notches < 1 || notches > 20) {
                throw new ConfigException("wheel notches must be from 1 to 20");
            }
        }
        return new Action.Wheel(direction, notches);
    }

    /** {@code hold <ms> <action> [| <tap action>]}. Without a tap action, a short press does nothing. */
    private static Action parseHold(String arg) throws ConfigException {
        requireArg("hold", arg);
        String[] parts = arg.split("\\s+", 2);
        long millis;
        try {
            millis = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new ConfigException("hold needs a time in milliseconds first");
        }
        if (millis < 100 || millis > 10_000) {
            throw new ConfigException("hold time must be from 100 to 10000 ms");
        }
        if (parts.length < 2 || parts[1].isBlank()) {
            throw new ConfigException("hold needs an action after the time");
        }
        String body = parts[1];
        String tapText = "block";
        int bar = body.indexOf('|');
        if (bar >= 0) {
            tapText = body.substring(bar + 1);
            body = body.substring(0, bar);
        }
        Action onHold = parse(body);
        Action onTap = parse(tapText);
        requireSimple(onHold, "hold");
        requireSimple(onTap, "hold tap");
        return new Action.Hold(millis, onHold, onTap);
    }

    private static void requireSimple(Action action, String context) throws ConfigException {
        if (action instanceof Action.Hold || action instanceof Action.Layer) {
            throw new ConfigException(context + " cannot be hold or layer");
        }
    }

    private static Action noArg(String verb, String arg, Action action) throws ConfigException {
        if (!arg.isEmpty()) {
            throw new ConfigException("'" + verb + "' does not take an argument");
        }
        return action;
    }

    private static void requireArg(String verb, String arg) throws ConfigException {
        if (arg.isEmpty()) {
            throw new ConfigException("'" + verb + "' needs an argument");
        }
    }
}
