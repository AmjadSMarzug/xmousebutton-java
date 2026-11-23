package io.github.amjadsmarzug.xmousebutton.config;

import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.Config;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendedSyntaxTest {

    @Test
    void typeKeepsSpacesAndStripsQuotes() throws Exception {
        assertEquals("hello  world", ((Action.Type) ActionParser.parse("type \"hello  world\"")).text());
        assertEquals("plain text", ((Action.Type) ActionParser.parse("type plain text")).text());
    }

    @Test
    void macroSplitsStepsAndAllowsWait() throws Exception {
        Action.Macro macro = assertInstanceOf(Action.Macro.class,
                ActionParser.parse("macro key ctrl+a ; wait 25 ; key ctrl+c"));
        assertEquals(3, macro.steps().size());
        assertEquals(25, ((Action.Wait) macro.steps().get(1)).millis());
    }

    @Test
    void waitOutsideMacroIsRejected() {
        assertThrows(ConfigException.class, () -> ActionParser.parse("wait 100"));
    }

    @Test
    void macrosCannotNestOrContainEmptySteps() {
        assertThrows(ConfigException.class, () -> ActionParser.parse("macro macro key a"));
        assertThrows(ConfigException.class, () -> ActionParser.parse("macro key a ;  ; key b"));
        assertThrows(ConfigException.class, () -> ActionParser.parse("macro wait 999999"));
        assertThrows(ConfigException.class, () -> ActionParser.parse("macro hold 500 block"));
    }

    @Test
    void pointerSpeedIsParsedAndBounded() throws Exception {
        Config config = ConfigParser.parse("[games]\nmatch.process = a.exe\npointer_speed = 12\n");
        assertEquals(12, config.profiles().get(0).pointerSpeed());
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[x]\npointer_speed = 0\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[x]\npointer_speed = 21\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[x]\npointer_speed = fast\n"));
        assertThrows(ConfigException.class, () -> ConfigParser.parse("[x]\npointer_speed = 5\npointer_speed = 6\n"));
    }

    @Test
    void profileWithoutPointerSpeedUsesSystemDefault() throws Exception {
        Config config = ConfigParser.parse("[default]\nx1 = block\n");
        assertEquals(null, config.profiles().get(0).pointerSpeed());
    }

    @Test
    void chordsAndHoldsStillParseAlongsideNewSyntax() throws Exception {
        Config config = ConfigParser.parse("""
                [default]
                left = block
                right = block
                left+right = macro key ctrl+c ; wait 10 ; type done
                """);
        Action chord = config.profiles().get(0).chord(MouseButton.LEFT, MouseButton.RIGHT);
        assertInstanceOf(Action.Macro.class, chord);
    }
}
