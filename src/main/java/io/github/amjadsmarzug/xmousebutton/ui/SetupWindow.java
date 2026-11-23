package io.github.amjadsmarzug.xmousebutton.ui;

import io.github.amjadsmarzug.xmousebutton.config.ActionParser;
import io.github.amjadsmarzug.xmousebutton.config.ActionWriter;
import io.github.amjadsmarzug.xmousebutton.config.ConfigException;
import io.github.amjadsmarzug.xmousebutton.config.ConfigParser;
import io.github.amjadsmarzug.xmousebutton.config.ConfigWriter;
import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EnumMap;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Profile setup window. Profiles are listed on the left with an enable checkbox; the right side
 * shows one tab per layer with a dropdown and an edit button for every mouse input.
 * Changes are kept in memory until Save or Apply.
 */
public final class SetupWindow {

    private static final MouseButton[] ROWS = {
            MouseButton.LEFT, MouseButton.RIGHT, MouseButton.MIDDLE, MouseButton.X1, MouseButton.X2,
            MouseButton.WHEEL_UP, MouseButton.WHEEL_DOWN, MouseButton.WHEEL_LEFT, MouseButton.WHEEL_RIGHT};
    private static final String[] ROW_LABELS = {
            "Left Button", "Right Button", "Middle Button", "Mouse Button 4", "Mouse Button 5",
            "Wheel Up", "Wheel Down", "Tilt Wheel Left", "Tilt Wheel Right"};
    private static final String EDIT_LABEL = "Edit...";
    private static final String VERSION = "0.1.0";

    /** What a dropdown entry means. Each kind has a default action, and the gear edits its text. */
    enum Kind {
        NO_CHANGE("** No Change (Don't intercept) **", "passthrough"),
        DISABLE("Disable", "block"),
        SIMULATED_KEYS("Simulated Keys", "key ctrl+c"),
        CLICK("Click Button", "click left"),
        MOUSE_WHEEL("Mouse Wheel", "wheel up 1"),
        RUN_PROGRAM("Run Program", "run notepad.exe"),
        TYPE_TEXT("Type Text", "type \"text\""),
        MACRO("Macro", "macro key ctrl+c ; wait 50 ; key ctrl+v"),
        LAYER_HOLD("Layer Hold", "layer 1"),
        LONG_PRESS("Long Press / Tap", "hold 500 block | block");

        final String label;
        final String defaultText;

        Kind(String label, String defaultText) {
            this.label = label;
            this.defaultText = defaultText;
        }

        static Kind of(Action action) {
            return switch (action) {
                case Action.Passthrough p -> NO_CHANGE;
                case Action.Block b -> DISABLE;
                case Action.Key k -> SIMULATED_KEYS;
                case Action.Click c -> CLICK;
                case Action.Wheel w -> MOUSE_WHEEL;
                case Action.Run r -> RUN_PROGRAM;
                case Action.Type t -> TYPE_TEXT;
                case Action.Macro m -> MACRO;
                case Action.Layer l -> LAYER_HOLD;
                case Action.Hold h -> LONG_PRESS;
                case Action.Wait w -> DISABLE; // only exists inside macros
            };
        }

        /** Wheel inputs cannot be clicked, held as layers, or long-pressed. */
        boolean allowedOn(MouseButton button) {
            if (!button.isWheel()) {
                return true;
            }
            return this != CLICK && this != LAYER_HOLD && this != LONG_PRESS;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private Path file;
    private final Runnable onApply;
    private final JFrame frame = new JFrame();
    private final List<ProfileDraft> drafts = new ArrayList<>();
    private final DefaultListModel<ProfileDraft> profileModel = new DefaultListModel<>();
    private final JList<ProfileDraft> profileList = new JList<>(profileModel);
    private final JTabbedPane layerTabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.WRAP_TAB_LAYOUT);
    private final JComboBox<Kind>[][] kindBoxes = newKindBoxes();
    private final JButton[][] gearButtons = new JButton[Profile.MAX_LAYER + 1][ROWS.length];
    private final DefaultListModel<String> chordModel = new DefaultListModel<>();
    private final JList<String> chordList = new JList<>(chordModel);
    private final JLabel infoName = new JLabel();
    private final JLabel infoProcess = new JLabel();
    private final JLabel infoTitle = new JLabel();
    private final JLabel infoSpeed = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JButton saveButton = new JButton("Save Profile");
    private final JButton applyButton = new JButton("Apply");
    private boolean updating;
    private boolean loadFailed;
    private boolean dirty;
    private boolean unapplied;

    private SetupWindow(Path file, Runnable onApply) {
        this.file = file;
        this.onApply = onApply;
    }

    /** Opens the setup window on the Swing thread. {@code onApply} runs after the file is saved with Apply. */
    public static void open(Path file, Runnable onApply) {
        SwingUtilities.invokeLater(() -> new SetupWindow(file, onApply).show());
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<Kind>[][] newKindBoxes() {
        return (JComboBox<Kind>[][]) new JComboBox[Profile.MAX_LAYER + 1][ROWS.length];
    }

    private void show() {
        frame.setTitle("XMouseButton setup - " + file.getFileName());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeWindow();
            }
        });
        frame.setIconImage(Tray.createIcon());
        frame.setContentPane(buildContent());
        loadFile(file);
        frame.pack();
        frame.setMinimumSize(new Dimension(900, 560));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        refreshAll();
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildBottomBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildCenter() {
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.add(new JLabel("Application / Window Profiles:"), BorderLayout.NORTH);
        profileList.setCellRenderer(new ProfileRenderer());
        profileList.setVisibleRowCount(18);
        profileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshAll();
            }
        });
        profileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = profileList.locationToIndex(e.getPoint());
                if (index >= 0 && e.getX() < 26) {
                    ProfileDraft draft = profileModel.get(index);
                    draft.enabled = !draft.enabled;
                    changed();
                }
            }
        });
        left.add(new JScrollPane(profileList), BorderLayout.CENTER);

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftButtons.add(button("Add", e -> addProfile()));
        leftButtons.add(button("Copy", e -> copyProfile()));
        leftButtons.add(button("Edit", e -> editProfile()));
        leftButtons.add(button("Remove", e -> removeProfile()));
        leftButtons.add(button("Up", e -> moveProfile(-1)));
        leftButtons.add(button("Down", e -> moveProfile(1)));
        left.add(leftButtons, BorderLayout.SOUTH);

        for (int layer = 0; layer <= Profile.MAX_LAYER; layer++) {
            layerTabs.addTab(layer == 0 ? "Default" : "Layer " + layer, buildLayerPanel(layer));
        }
        layerTabs.addTab("Chords", buildChordPanel());

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(layerTabs, BorderLayout.CENTER);
        right.add(buildInfoPanel(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(300);
        split.setResizeWeight(0.25);
        return asPanel(split);
    }

    private static JPanel asPanel(Component component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLayerPanel(int layer) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        for (int row = 0; row < ROWS.length; row++) {
            MouseButton button = ROWS[row];
            c.gridy = row;
            c.gridx = 0;
            panel.add(new JLabel(ROW_LABELS[row] + ":"), c);

            JComboBox<Kind> box = new JComboBox<>();
            box.setPreferredSize(new Dimension(340, 26));
            final int l = layer;
            box.addActionListener(e -> {
                if (!updating && box.getSelectedItem() instanceof Kind kind) {
                    chooseKind(l, button, kind);
                }
            });
            kindBoxes[layer][row] = box;
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            panel.add(box, c);

            JButton gear = new JButton(EDIT_LABEL);
            gear.setToolTipText("Edit the action text for this input");
            gear.addActionListener(e -> editAction(l, button));
            gearButtons[layer][row] = gear;
            c.gridx = 2;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            panel.add(gear, c);
        }
        // Keeps the rows at the top when the tab is taller than the list.
        c.gridy = ROWS.length;
        c.gridx = 0;
        c.weighty = 1;
        panel.add(new JLabel(" "), c);
        return panel;
    }

    private JPanel buildChordPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(new JLabel("Press the second button while the first is held to run the action:"),
                BorderLayout.NORTH);
        panel.add(new JScrollPane(chordList), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.add(button("Add chord", e -> addChord()));
        buttons.add(button("Edit chord", e -> editChord()));
        buttons.add(button("Remove chord", e -> removeChord()));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        TitledBorder border = BorderFactory.createTitledBorder("Profile Information");
        panel.setBorder(border);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 6, 2, 6);
        c.anchor = GridBagConstraints.WEST;
        String[] names = {"Description:", "Process:", "Window Caption:", "Pointer speed:"};
        JLabel[] values = {infoName, infoProcess, infoTitle, infoSpeed};
        for (int i = 0; i < names.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            panel.add(new JLabel(names[i]), c);
            c.gridx = 1;
            c.weightx = 0;
            panel.add(values[i], c);
        }
        c.gridy = names.length;
        c.gridx = 0;
        panel.add(button("Edit profile...", e -> editProfile()), c);
        // Push the value column to the left edge instead of centring the grid.
        c.gridx = 2;
        c.gridy = 0;
        c.weightx = 1;
        panel.add(new JLabel(" "), c);
        return panel;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        saveButton.addActionListener(e -> save(false));
        applyButton.addActionListener(e -> save(true));
        left.add(saveButton);
        left.add(button("Load Profile", e -> loadFromChooser()));
        left.add(applyButton);
        status.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(button("About", e -> showAbout()));
        right.add(button("Close", e -> closeWindow()));
        bar.add(left, BorderLayout.WEST);
        bar.add(status, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private static JButton button(String text, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.addActionListener(action);
        return b;
    }

    // ---- loading and saving ---------------------------------------------------------------

    private void loadFile(Path path) {
        drafts.clear();
        loadFailed = false;
        file = path;
        try {
            if (Files.exists(path)) {
                Config config = ConfigParser.load(path);
                for (Profile profile : config.profiles()) {
                    drafts.add(ProfileDraft.from(profile));
                }
            }
            status.setText(Files.exists(path) ? "Loaded " + path : "New file: " + path);
        } catch (ConfigException e) {
            loadFailed = true;
            status.setText("Could not load " + path + ". Fix the file or load another one.");
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Config error", JOptionPane.ERROR_MESSAGE);
        }
        dirty = false;
        unapplied = false;
        reloadProfileList();
    }

    private void loadFromChooser() {
        if (dirty && JOptionPane.showConfirmDialog(frame, "Discard unsaved changes?", "Unsaved changes",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        JFileChooser chooser = new JFileChooser(file.toAbsolutePath().getParent().toFile());
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            loadFile(chooser.getSelectedFile().toPath());
            frame.setTitle("XMouseButton setup - " + file.getFileName());
        }
    }

    /** Saves the file. With {@code apply}, the running engine is also reloaded. */
    private void save(boolean apply) {
        if (loadFailed) {
            JOptionPane.showMessageDialog(frame, "The file could not be loaded, so it will not be overwritten.",
                    "Not saved", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<Profile> profiles = drafts.stream().map(ProfileDraft::toProfile).toList();
        String text = ConfigWriter.write(profiles);
        try {
            ConfigParser.parse(text);
        } catch (ConfigException e) {
            JOptionPane.showMessageDialog(frame, "Not saved: " + e.getMessage(), "Invalid config",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not save: " + e.getMessage(), "Save failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        dirty = false;
        if (apply) {
            unapplied = false;
            if (onApply != null) {
                onApply.run();
            }
            status.setText("Saved and applied to the running program.");
        } else {
            status.setText("Saved " + file.getFileName() + ". Click Apply to reload the running program.");
        }
        refreshButtons();
    }

    private void closeWindow() {
        if (!dirty || JOptionPane.showConfirmDialog(frame, "Close and discard unsaved changes?",
                "Unsaved changes", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            frame.dispose();
        }
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(frame,
                "XMouseButton " + VERSION + "\n\n"
                        + "A clean-room Java remapper for mouse buttons and the scroll wheel,\n"
                        + "with per-application profiles.\n\n"
                        + "Released under the MIT license.",
                "About", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---- profile list ----------------------------------------------------------------------

    private void reloadProfileList() {
        int selected = profileList.getSelectedIndex();
        profileModel.clear();
        for (ProfileDraft draft : drafts) {
            profileModel.addElement(draft);
        }
        if (!drafts.isEmpty()) {
            profileList.setSelectedIndex(Math.max(0, Math.min(selected, drafts.size() - 1)));
        }
        refreshAll();
    }

    private ProfileDraft selected() {
        return profileList.getSelectedValue();
    }

    private void changed() {
        dirty = true;
        unapplied = true;
        profileList.repaint();
        refreshAll();
    }

    private void addProfile() {
        ProfileDraft draft = new ProfileDraft("new profile");
        ProfileDialog.Result result = ProfileDialog.edit(frame, draft, drafts);
        if (result != null) {
            drafts.add(result.draft());
            profileModel.addElement(result.draft());
            profileList.setSelectedIndex(drafts.size() - 1);
            changed();
        }
    }

    private void copyProfile() {
        ProfileDraft source = selected();
        if (source == null) {
            return;
        }
        ProfileDraft copy = source.copyWithName(source.name + " copy");
        int index = drafts.indexOf(source) + 1;
        drafts.add(index, copy);
        reloadProfileList();
        profileList.setSelectedIndex(index);
        changed();
    }

    private void editProfile() {
        ProfileDraft draft = selected();
        if (draft == null) {
            return;
        }
        ProfileDialog.Result result = ProfileDialog.edit(frame, draft, drafts);
        if (result != null) {
            int index = drafts.indexOf(draft);
            drafts.set(index, result.draft());
            reloadProfileList();
            profileList.setSelectedIndex(index);
            changed();
        }
    }

    private void removeProfile() {
        ProfileDraft draft = selected();
        if (draft == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(frame, "Remove profile \"" + draft.name + "\"?", "Remove profile",
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            drafts.remove(draft);
            reloadProfileList();
            changed();
        }
    }

    private void moveProfile(int direction) {
        ProfileDraft draft = selected();
        if (draft == null) {
            return;
        }
        int from = drafts.indexOf(draft);
        int to = from + direction;
        if (to < 0 || to >= drafts.size()) {
            return;
        }
        drafts.remove(from);
        drafts.add(to, draft);
        reloadProfileList();
        profileList.setSelectedIndex(to);
        changed();
    }

    // ---- actions ---------------------------------------------------------------------------

    private void chooseKind(int layer, MouseButton button, Kind kind) {
        ProfileDraft draft = selected();
        if (draft == null) {
            return;
        }
        Action current = draft.binding(layer, button);
        if (Kind.of(current) == kind) {
            return;
        }
        draft.setBinding(layer, button, defaultAction(kind, button));
        changed();
    }

    private static Action defaultAction(Kind kind, MouseButton button) {
        String text = switch (kind) {
            case CLICK -> "click " + (button.isPhysicalButton() ? button.configName() : "left");
            case MOUSE_WHEEL -> "wheel " + wheelWord(button) + " 1";
            default -> kind.defaultText;
        };
        return parseOrThrow(text);
    }

    private static String wheelWord(MouseButton button) {
        return switch (button) {
            case WHEEL_DOWN -> "down";
            case WHEEL_LEFT -> "left";
            case WHEEL_RIGHT -> "right";
            default -> "up";
        };
    }

    private static Action parseOrThrow(String text) {
        try {
            return ActionParser.parse(text);
        } catch (ConfigException e) {
            throw new IllegalStateException("built-in default is invalid: " + text, e);
        }
    }

    private void editAction(int layer, MouseButton button) {
        ProfileDraft draft = selected();
        if (draft == null) {
            return;
        }
        Action current = draft.binding(layer, button);
        String text = JOptionPane.showInputDialog(frame,
                "Action for " + button.configName() + " (see Help in the config reference):\n"
                        + "e.g. key ctrl+c, click middle, wheel down 3, run notepad.exe, "
                        + "hold 500 key f5 | key f6",
                ActionWriter.write(current));
        if (text == null) {
            return;
        }
        try {
            Action action = ActionParser.parse(text);
            draft.setBinding(layer, button, action);
            changed();
        } catch (ConfigException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "Invalid action", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- chords ----------------------------------------------------------------------------

    private void addChord() {
        ProfileDraft draft = selected();
        if (draft != null) {
            ChordDialog.Result result = ChordDialog.edit(frame, null, null, null);
            if (result != null) {
                draft.chords.computeIfAbsent(result.first(), k -> new EnumMap<>(MouseButton.class))
                        .put(result.second(), result.action());
                changed();
            }
        }
    }

    private void editChord() {
        ProfileDraft draft = selected();
        int index = chordList.getSelectedIndex();
        if (draft == null || index < 0) {
            return;
        }
        ChordDialog.Pair pair = chordAt(draft, index);
        Action current = draft.chords.get(pair.first()).get(pair.second());
        ChordDialog.Result result = ChordDialog.edit(frame, pair.first(), pair.second(), current);
        if (result != null) {
            draft.chords.get(pair.first()).remove(pair.second());
            draft.chords.computeIfAbsent(result.first(), k -> new EnumMap<>(MouseButton.class))
                    .put(result.second(), result.action());
            changed();
        }
    }

    private void removeChord() {
        ProfileDraft draft = selected();
        int index = chordList.getSelectedIndex();
        if (draft == null || index < 0) {
            return;
        }
        ChordDialog.Pair pair = chordAt(draft, index);
        draft.chords.get(pair.first()).remove(pair.second());
        changed();
    }

    private static ChordDialog.Pair chordAt(ProfileDraft draft, int index) {
        List<ChordDialog.Pair> pairs = chordPairs(draft);
        return pairs.get(index);
    }

    private static List<ChordDialog.Pair> chordPairs(ProfileDraft draft) {
        List<ChordDialog.Pair> pairs = new ArrayList<>();
        draft.chords.forEach((first, byFirst) ->
                byFirst.keySet().forEach(second -> pairs.add(new ChordDialog.Pair(first, second))));
        return pairs;
    }

    // ---- refresh ---------------------------------------------------------------------------

    private void refreshAll() {
        ProfileDraft draft = selected();
        updating = true;
        try {
            refreshInfo(draft);
            refreshLayers(draft);
            refreshChords(draft);
        } finally {
            updating = false;
        }
        refreshButtons();
    }

    private void refreshInfo(ProfileDraft draft) {
        if (draft == null) {
            infoName.setText("");
            infoProcess.setText("");
            infoTitle.setText("");
            infoSpeed.setText("");
            return;
        }
        infoName.setText(draft.name + (draft.enabled ? "" : " (disabled)"));
        infoProcess.setText(draft.processGlobs.isEmpty() ? "All" : String.join(", ", draft.processGlobs));
        infoTitle.setText(draft.titleGlobs.isEmpty() ? "All" : String.join(", ", draft.titleGlobs));
        infoSpeed.setText(draft.pointerSpeed == null ? "System default" : draft.pointerSpeed.toString());
    }

    private void refreshLayers(ProfileDraft draft) {
        for (int layer = 0; layer <= Profile.MAX_LAYER; layer++) {
            for (int row = 0; row < ROWS.length; row++) {
                MouseButton button = ROWS[row];
                JComboBox<Kind> box = kindBoxes[layer][row];
                Action action = draft == null ? Action.Passthrough.INSTANCE : draft.binding(layer, button);
                Kind current = Kind.of(action);
                DefaultComboBoxModel<Kind> model = new DefaultComboBoxModel<>();
                for (Kind kind : Kind.values()) {
                    if (kind.allowedOn(button) || kind == current) {
                        model.addElement(kind);
                    }
                }
                box.setModel(model);
                box.setSelectedItem(current);
                box.setEnabled(draft != null);
                gearButtons[layer][row].setEnabled(draft != null);
            }
        }
    }

    private void refreshChords(ProfileDraft draft) {
        chordModel.clear();
        if (draft != null) {
            for (ChordDialog.Pair pair : chordPairs(draft)) {
                Action action = draft.chords.get(pair.first()).get(pair.second());
                chordModel.addElement(pair.first().configName() + " + " + pair.second().configName()
                        + "  =  " + ActionWriter.write(action));
            }
        }
    }

    private void refreshButtons() {
        saveButton.setEnabled(!loadFailed);
        applyButton.setEnabled(!loadFailed && unapplied);
    }

    /** Renders each profile with a checkbox for its enabled state. */
    private static final class ProfileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            ProfileDraft draft = (ProfileDraft) value;
            JCheckBox box = new JCheckBox(draft.name, draft.enabled);
            box.setOpaque(true);
            box.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            box.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return box;
        }
    }
}
