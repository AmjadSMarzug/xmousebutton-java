package io.github.amjadsmarzug.xmousebutton.ui;

import io.github.amjadsmarzug.xmousebutton.config.ConfigException;
import io.github.amjadsmarzug.xmousebutton.config.ConfigParser;
import io.github.amjadsmarzug.xmousebutton.config.ConfigText;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A small editor for the config file. It validates as you type, lists profiles for quick
 * navigation, and refuses to save a file that would not load.
 */
public final class ConfigEditor {

    private static final String NEW_PROFILE = """

            [new-profile]
            match.process = app.exe
            x1 = passthrough
            """;

    private static final String HELP = """
            Buttons: left, right, middle, x1, x2, wheel_up, wheel_down, wheel_left, wheel_right

            passthrough | block
            key ctrl+shift+t          tap a shortcut
            click middle              click a physical button
            wheel up 3                scroll (up, down, left, right)
            run notepad.exe           start a program
            layer 2                   hold to use layer2.* bindings
            hold 500 <action> | <tap> long press, otherwise tap
            type hello world          type text
            macro key ctrl+a ; wait 50 ; key ctrl+c
            left+right = <action>     chord (both physical buttons)

            Profile rules:  match.process = chrome.exe, *.exe
                            match.title   = *YouTube*
                            pointer_speed = 10   (1 to 20, applies while the profile is active)
            """;

    private final Path file;
    private final Runnable onSaved;
    private final JFrame frame;
    private final JTextArea editor = new JTextArea(30, 80);
    private final DefaultListModel<String> profileModel = new DefaultListModel<>();
    private final JList<String> profileList = new JList<>(profileModel);
    private final JLabel status = new JLabel(" ");
    private List<ConfigText.Section> sections = List.of();
    private boolean dirty;

    private ConfigEditor(Path file, Runnable onSaved) {
        this.file = file;
        this.onSaved = onSaved;
        this.frame = new JFrame("XMouseButton config - " + file.getFileName());
    }

    /** Opens the editor on the Swing thread. {@code onSaved} runs after a successful save. */
    public static void open(Path file, Runnable onSaved) {
        SwingUtilities.invokeLater(() -> new ConfigEditor(file, onSaved).show());
    }

    private void show() {
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editor.setTabSize(4);
        try {
            editor.setText(Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "");
        } catch (IOException e) {
            status.setText("Could not read " + file + ": " + e.getMessage());
        }
        dirty = false;
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        profileList.addListSelectionListener((ListSelectionEvent e) -> {
            int index = profileList.getSelectedIndex();
            if (!e.getValueIsAdjusting() && index >= 0 && index < sections.size()) {
                int offset = ConfigText.offsetOfLine(editor.getText(), sections.get(index).line());
                editor.requestFocusInWindow();
                editor.setCaretPosition(offset);
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(button("Save", e -> save()));
        buttons.add(button("Open...", e -> openFile()));
        buttons.add(button("Add profile", e -> addProfile()));
        buttons.add(button("Help", e -> JOptionPane.showMessageDialog(frame, HELP, "Config syntax",
                JOptionPane.PLAIN_MESSAGE)));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(profileList), new JScrollPane(editor));
        split.setDividerLocation(180);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(buttons, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);

        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (!dirty || confirmDiscard()) {
                    frame.dispose();
                }
            }
        });
        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        changed();
        dirty = false; // loading the file is not an edit
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    private void changed() {
        String text = editor.getText();
        dirty = true;
        sections = ConfigText.sections(text);
        profileModel.clear();
        for (ConfigText.Section section : sections) {
            profileModel.addElement(section.name());
        }
        try {
            ConfigParser.parse(text);
            status.setForeground(new Color(0x1B, 0x7F, 0x3B));
            status.setText("Config is valid. " + sections.size() + " profile(s).");
        } catch (ConfigException e) {
            status.setForeground(new Color(0xB0, 0x20, 0x20));
            status.setText("Error: " + e.getMessage());
        }
    }

    private void save() {
        try {
            ConfigParser.parse(editor.getText());
        } catch (ConfigException e) {
            JOptionPane.showMessageDialog(frame, "Not saved. " + e.getMessage(), "Invalid config",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, editor.getText(), StandardCharsets.UTF_8);
            dirty = false;
            status.setText("Saved " + file + ". Use Reload config in the tray to apply it.");
            if (onSaved != null) {
                onSaved.run();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not save: " + e.getMessage(), "Save failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFile() {
        if (dirty && !confirmDiscard()) {
            return;
        }
        JFileChooser chooser = new JFileChooser(file.getParent() == null ? null : file.getParent().toFile());
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            editor.setText(Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8));
            dirty = false;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not open: " + e.getMessage(), "Open failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addProfile() {
        String text = editor.getText();
        editor.setText(text.stripTrailing() + "\n" + NEW_PROFILE);
        editor.setCaretPosition(editor.getText().length());
    }

    private boolean confirmDiscard() {
        int choice = JOptionPane.showConfirmDialog(frame, "Discard unsaved changes?", "Unsaved changes",
                JOptionPane.YES_NO_OPTION);
        return choice == JOptionPane.YES_OPTION;
    }
}
