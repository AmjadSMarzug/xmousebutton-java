package io.github.amjadsmarzug.xmousebutton.ui;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Edits a profile's name, enable state, window match rules and pointer speed. */
final class ProfileDialog {

    /** The edited profile, or {@code null} from {@link #edit} when the user cancels. */
    record Result(ProfileDraft draft) {
    }

    private ProfileDialog() {
    }

    static Result edit(JFrame owner, ProfileDraft original, List<ProfileDraft> others) {
        JTextField name = new JTextField(original.name, 24);
        JCheckBox enabled = new JCheckBox("Enabled", original.enabled);
        JTextField process = new JTextField(String.join(", ", original.processGlobs), 24);
        JTextField title = new JTextField(String.join(", ", original.titleGlobs), 24);
        SpinnerNumberModel speedModel = new SpinnerNumberModel(
                original.pointerSpeed == null ? 0 : original.pointerSpeed, 0, 20, 1);
        JSpinner speed = new JSpinner(speedModel);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        addRow(panel, c, 0, "Description:", name);
        c.gridx = 1;
        c.gridy = 1;
        panel.add(enabled, c);
        addRow(panel, c, 2, "Process (e.g. chrome.exe, *.exe):", process);
        addRow(panel, c, 3, "Window caption (e.g. *YouTube*):", title);
        addRow(panel, c, 4, "Pointer speed (0 = system default, 1 to 20):", speed);
        JLabel hint = new JLabel("A profile with no process or caption never matches on its own.");
        hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        panel.add(hint, c);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(owner, panel,
                    original.name.equals("new profile") ? "Add profile" : "Edit profile",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            String newName = name.getText().strip();
            String problem = validateName(newName, original, others);
            if (problem != null) {
                JOptionPane.showMessageDialog(owner, problem, "Check the profile", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            ProfileDraft draft = original.copyWithName(newName);
            draft.enabled = enabled.isSelected();
            draft.processGlobs = splitList(process.getText());
            draft.titleGlobs = splitList(title.getText());
            int value = (Integer) speed.getValue();
            draft.pointerSpeed = value == 0 ? null : value;
            return new Result(draft);
        }
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(field, c);
    }

    static String validateName(String name, ProfileDraft original, List<ProfileDraft> others) {
        if (name.isEmpty()) {
            return "The profile needs a description.";
        }
        if (name.contains("]") || name.contains("[") || name.contains("\n")) {
            return "Brackets and line breaks are not allowed in the description.";
        }
        for (ProfileDraft other : others) {
            if (other != original && other.name.equalsIgnoreCase(name)) {
                return "Another profile is already called \"" + name + "\".";
            }
        }
        return null;
    }

    static List<String> splitList(String text) {
        return Arrays.stream(text.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
