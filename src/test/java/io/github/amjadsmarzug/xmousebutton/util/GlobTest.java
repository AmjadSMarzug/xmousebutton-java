package io.github.amjadsmarzug.xmousebutton.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobTest {

    @Test
    void starAndQuestionMarkWork() {
        assertTrue(Glob.matches("*YouTube*", "Video - YouTube - Chrome"));
        assertTrue(Glob.matches("chrome.???", "chrome.exe"));
        assertFalse(Glob.matches("chrome.?", "chrome.exe"));
    }

    @Test
    void matchingIsCaseInsensitiveAndDotsAreLiteral() {
        assertTrue(Glob.matches("CHROME.EXE", "chrome.exe"));
        assertFalse(Glob.matches("chrome.exe", "chromeXexe"));
    }
}
