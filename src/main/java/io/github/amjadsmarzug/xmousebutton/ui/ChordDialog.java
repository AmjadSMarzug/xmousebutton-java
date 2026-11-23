package io.github.amjadsmarzug.xmousebutton.ui;

import io.github.amjadsmarzug.xmousebutton.config.ActionParser;
import io.github.amjadsmarzug.xmousebutton.config.ActionWriter;
import io.github.amjadsmarzug.xmousebutton.config.ConfigException;
import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Picks two physical buttons and the action a chord runs. */
final class ChordDialog {

    record Pair(MouseButton first, MouseButton second) {
    }

    record Result(MouseButton first, MouseButton second, Action action) {
    }

    private static final MouseButton[] PHYSICAL = {
            MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE, MouseButton.X1, MouseButton.X2};

    private ChordDialog() {
    }

    /** Pass nulls to start with defaults. Returns {@code null} on cancel. */
    static Result edit(JFrame owner, MouseButton first, MouseButton second, Action action) {
        JComboBox<MouseButton> firstBox = new JComboBox<>(PHYSICAL);
        JComboBox<MouseButton> secondBox = new JComboBox<>(PHYSICAL);
        firstBox.setSelectedItem(first == null ? MouseButton.LEFT : first);
        secondBox.setSelectedItem(second == null ? MouseButton.RIGHT : second);
        JTextField actionText = new JTextField(
                action == null ? "key ctrl+c" : ActionWriter.write(action), 24);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("First button (held):"), c);
        c.gridx = 1;
        panel.add(firstBox, c);
        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Second button (pressed):"), c);
        c.gridx = 1;
        panel.add(secondBox, c);
        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Action:"), c);
        c.gridx = 1;
        panel.add(actionText, c);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(owner, panel, "Chord",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            MouseButton a = (MouseButton) firstBox.getSelectedItem();
            MouseButton b = (MouseButton) secondBox.getSelectedItem();
            if (a == b) {
                JOptionPane.showMessageDialog(owner, "A chord needs two different buttons.",
                        "Check the chord", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            try {
                Action parsed = ActionParser.parse(actionText.getText());
                if (parsed instanceof Action.Hold || parsed instanceof Action.Layer) {
                    throw new ConfigException("a chord cannot be hold or layer");
                }
                return new Result(a, b, parsed);
            } catch (ConfigException e) {
                JOptionPane.showMessageDialog(owner, e.getMessage(), "Invalid action", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
