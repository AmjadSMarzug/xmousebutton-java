package io.github.amjadsmarzug.xmousebutton.model;

/**
 * Describes the window under the mouse focus.
 *
 * @param processName executable file name, for example {@code chrome.exe}; may be empty
 * @param title       window title; may be empty
 */
public record WindowInfo(String processName, String title) {

    public static final WindowInfo UNKNOWN = new WindowInfo("", "");
}
