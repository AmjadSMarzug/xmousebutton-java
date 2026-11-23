package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;

import java.util.List;
import java.util.stream.Collectors;

/** Writes actions in the same syntax that {@link ActionParser} reads. */
public final class ActionWriter {

    private ActionWriter() {
    }

    public static String write(Action action) {
        return switch (action) {
            case Action.Passthrough p -> "passthrough";
            case Action.Block b -> "block";
            case Action.Key k -> "key " + k.combo().toConfigString();
            case Action.Click c -> "click " + c.button().configName();
            case Action.Wheel w -> "wheel " + directionWord(w.direction()) + " " + w.notches();
            case Action.Run r -> "run " + quoteArgs(r.command());
            case Action.Layer l -> "layer " + l.layer();
            case Action.Type t -> "type \"" + t.text() + "\"";
            case Action.Wait w -> "wait " + w.millis();
            case Action.Macro m -> "macro " + m.steps().stream()
                    .map(ActionWriter::write)
                    .collect(Collectors.joining(" ; "));
            case Action.Hold h -> "hold " + h.millis() + " " + write(h.onHold()) + " | " + write(h.onTap());
        };
    }

    private static String directionWord(MouseButton direction) {
        return switch (direction) {
            case WHEEL_UP -> "up";
            case WHEEL_DOWN -> "down";
            case WHEEL_LEFT -> "left";
            case WHEEL_RIGHT -> "right";
            default -> throw new IllegalArgumentException("not a wheel direction: " + direction);
        };
    }

    private static String quoteArgs(List<String> command) {
        return command.stream()
                .map(arg -> arg.isEmpty() || arg.chars().anyMatch(Character::isWhitespace)
                        ? "\"" + arg.replace("\"", "\\\"") + "\""
                        : arg)
                .collect(Collectors.joining(" "));
    }
}
