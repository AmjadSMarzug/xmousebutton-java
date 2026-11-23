package io.github.amjadsmarzug.xmousebutton.ui;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Optional system tray icon with reload and exit actions. Does nothing when the desktop has no tray. */
public final class Tray {

    private final Runnable onReload;
    private final Runnable onEdit;
    private final Runnable onExit;
    private TrayIcon icon;

    public Tray(Runnable onReload, Runnable onEdit, Runnable onExit) {
        this.onReload = onReload;
        this.onEdit = onEdit;
        this.onExit = onExit;
    }

    /** @return {@code true} if the icon was installed */
    public boolean install(String tooltip) {
        if (!SystemTray.isSupported()) {
            return false;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem reload = new MenuItem("Reload config");
        reload.addActionListener(e -> onReload.run());
        MenuItem exit = new MenuItem("Exit");
        exit.addActionListener(e -> onExit.run());
        MenuItem edit = new MenuItem("Profile setup...");
        edit.addActionListener(e -> onEdit.run());
        menu.add(reload);
        menu.add(edit);
        menu.addSeparator();
        menu.add(exit);

        icon = new TrayIcon(createIcon(), tooltip, menu);
        icon.setImageAutoSize(true);
        try {
            SystemTray.getSystemTray().add(icon);
            return true;
        } catch (AWTException e) {
            icon = null;
            return false;
        }
    }

    public void remove() {
        if (icon != null) {
            SystemTray.getSystemTray().remove(icon);
            icon = null;
        }
    }

    /** The app logo from resources, or a plain drawn fallback if the resource is missing. */
    static Image createIcon() {
        try (InputStream in = Tray.class.getResourceAsStream("/icons/xmousebutton-64.png")) {
            if (in != null) {
                Image logo = ImageIO.read(in);
                if (logo != null) {
                    return logo;
                }
            }
        } catch (IOException ignored) {
            // fall through to the drawn icon
        }
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x2A, 0x7F, 0xDB));
        g.fillOval(2, 2, size - 4, size - 4);
        g.setColor(Color.WHITE);
        g.fillOval(12, 8, 8, 16);
        g.dispose();
        return image;
    }
}
