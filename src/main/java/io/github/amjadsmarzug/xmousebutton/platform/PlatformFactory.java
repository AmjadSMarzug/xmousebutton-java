package io.github.amjadsmarzug.xmousebutton.platform;

import io.github.amjadsmarzug.xmousebutton.platform.windows.WindowsPlatform;

import java.util.Locale;

/** Picks the backend for the current operating system. */
public final class PlatformFactory {

    private PlatformFactory() {
    }

    public static Platform detect() {
        String os = System.getProperty("os.name", "unknown");
        if (os.toLowerCase(Locale.ROOT).startsWith("windows")) {
            return new WindowsPlatform();
        }
        return new UnsupportedPlatform(os);
    }
}
