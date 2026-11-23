package io.github.amjadsmarzug.xmousebutton.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandLineTest {

    @Test
    void splitsOnWhitespaceAndKeepsQuotedPaths() {
        assertEquals(List.of("C:\\Program Files\\app.exe", "--flag", "x y"),
                CommandLine.split("\"C:\\Program Files\\app.exe\" --flag \"x y\""));
    }

    @Test
    void emptyInputGivesNoTokens() {
        assertEquals(List.of(), CommandLine.split("   "));
    }
}
