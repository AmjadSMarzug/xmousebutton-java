package io.github.amjadsmarzug.xmousebutton;

import io.github.amjadsmarzug.xmousebutton.config.ConfigException;
import io.github.amjadsmarzug.xmousebutton.config.ConfigParser;
import io.github.amjadsmarzug.xmousebutton.engine.Engine;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.platform.Platform;
import io.github.amjadsmarzug.xmousebutton.platform.PlatformFactory;
import io.github.amjadsmarzug.xmousebutton.ui.ConfigEditor;
import io.github.amjadsmarzug.xmousebutton.ui.Tray;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

/** Command-line entry point. */
public final class Main {

    static final String VERSION = "0.1.0";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Path configPath = null;
        boolean checkOnly = false;
        boolean editorOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> {
                    if (i + 1 >= args.length) {
                        usage("--config needs a path");
                        return;
                    }
                    configPath = Paths.get(args[++i]);
                }
                case "--check" -> checkOnly = true;
                case "--editor" -> editorOnly = true;
                case "--version" -> {
                    System.out.println("xmousebutton " + VERSION);
                    return;
                }
                case "--help", "-h" -> {
                    usage(null);
                    return;
                }
                default -> {
                    usage("unknown option " + args[i]);
                    System.exit(2);
                }
            }
        }

        if (configPath == null) {
            configPath = defaultConfigPath();
        }
        ensureConfigExists(configPath);

        if (editorOnly) {
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println("the config editor needs a desktop session");
                System.exit(3);
                return;
            }
            ConfigEditor.open(configPath, null);
            return;
        }

        Config config;
        try {
            config = ConfigParser.load(configPath);
        } catch (ConfigException e) {
            System.err.println("config error in " + configPath + ": " + e.getMessage());
            System.exit(1);
            return;
        }

        if (checkOnly) {
            System.out.println("OK: " + config.profiles().size() + " profile(s) in " + configPath);
            return;
        }

        Platform platform = PlatformFactory.detect();
        if (!platform.supportsMouseHook()) {
            System.err.println("Mouse remapping is not available on " + platform.name() + " yet.");
            System.err.println("Use --check to validate a config file on this system.");
            System.exit(3);
            return;
        }

        Engine engine = new Engine(platform, config);
        platform.startMouseHook(engine::handle);
        engine.startProfileWatch();

        CountDownLatch done = new CountDownLatch(1);
        Path finalConfigPath = configPath;
        Tray tray = new Tray(
                () -> reload(finalConfigPath, engine),
                () -> ConfigEditor.open(finalConfigPath, () -> reload(finalConfigPath, engine)),
                done::countDown);
        boolean trayShown = tray.install("XMouseButton: " + finalConfigPath.getFileName());
        if (!trayShown) {
            System.out.println("Running without a tray icon. Press Ctrl+C to stop.");
        }
        System.out.println("xmousebutton " + VERSION + " running with " + config.profiles().size()
                + " profile(s) from " + configPath);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            engine.stop();
            platform.stopMouseHook();
            tray.remove();
        }));
        done.await();
        engine.stop();
        platform.stopMouseHook();
        tray.remove();
        System.exit(0);
    }

    private static void reload(Path configPath, Engine engine) {
        try {
            engine.setConfig(ConfigParser.load(configPath));
            System.out.println("config reloaded from " + configPath);
        } catch (ConfigException e) {
            System.err.println("reload failed, keeping previous config: " + e.getMessage());
        }
    }

    static Path defaultConfigPath() {
        return Paths.get(System.getProperty("user.home"), ".xmousebutton", "config.conf");
    }

    private static void ensureConfigExists(Path path) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (InputStream in = Main.class.getResourceAsStream("/default-config.conf")) {
            if (in == null) {
                throw new IOException("bundled default config is missing");
            }
            Files.copy(in, path);
        }
        System.out.println("created default config at " + path);
    }

    private static void usage(String problem) {
        if (problem != null) {
            System.err.println(problem);
        }
        System.out.println("""
                Usage: xmousebutton [--config FILE] [--check] [--editor] [--version]

                  --config FILE   read bindings from FILE
                                  (default: ~/.xmousebutton/config.conf)
                  --check         parse the config, report errors and exit
                  --editor        open the graphical config editor
                  --version       print the version
                """);
    }
}
