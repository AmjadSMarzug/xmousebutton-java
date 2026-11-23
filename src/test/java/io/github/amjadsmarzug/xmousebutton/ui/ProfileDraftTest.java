package io.github.amjadsmarzug.xmousebutton.ui;

import io.github.amjadsmarzug.xmousebutton.config.ActionParser;
import io.github.amjadsmarzug.xmousebutton.config.ConfigParser;
import io.github.amjadsmarzug.xmousebutton.config.ConfigWriter;
import io.github.amjadsmarzug.xmousebutton.model.Action;
import io.github.amjadsmarzug.xmousebutton.model.MouseButton;
import io.github.amjadsmarzug.xmousebutton.model.Profile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileDraftTest {

    @Test
    void draftRoundTripsThroughProfile() throws Exception {
        String text = "[browser]\nmatch.process = chrome.exe\nx1 = key alt+left\nlayer3.left = block\n";
        Profile original = ConfigParser.parse(text).profiles().get(0);
        Profile back = ProfileDraft.from(original).toProfile();
        assertEquals(ConfigWriter.write(List.of(original)), ConfigWriter.write(List.of(back)));
    }

    @Test
    void settingPassthroughRemovesTheBinding() throws Exception {
        ProfileDraft draft = ProfileDraft.from(ConfigParser.parse("[p]\nx1 = block\n").profiles().get(0));
        draft.setBinding(0, MouseButton.X1, Action.Passthrough.INSTANCE);
        assertTrue(draft.layers.get(0).isEmpty());
        assertTrue(draft.toProfile().layerBindings(0).isEmpty());
    }

    @Test
    void copyKeepsSettingsUnderNewName() throws Exception {
        ProfileDraft draft = ProfileDraft.from(
                ConfigParser.parse("[p]\nmatch.title = *Notes*\npointer_speed = 7\nx2 = block\n").profiles().get(0));
        ProfileDraft copy = draft.copyWithName("p copy");
        assertEquals("p copy", copy.name);
        assertEquals(7, copy.pointerSpeed);
        assertEquals(List.of("*Notes*"), copy.titleGlobs);
        assertEquals(Action.Block.INSTANCE, copy.binding(0, MouseButton.X2));
    }

    @Test
    void chordsAreCopiedAndEditable() throws Exception {
        ProfileDraft draft = ProfileDraft.from(
                ConfigParser.parse("[p]\nleft = block\nleft+right = key ctrl+c\n").profiles().get(0));
        Action chord = draft.chords.get(MouseButton.LEFT).get(MouseButton.RIGHT);
        assertEquals(ActionParser.parse("key ctrl+c"), chord);
        draft.chords.get(MouseButton.LEFT).put(MouseButton.RIGHT, ActionParser.parse("block"));
        assertEquals(Action.Block.INSTANCE, draft.toProfile().chord(MouseButton.LEFT, MouseButton.RIGHT));
    }

    @Test
    void nameRulesAreEnforced() {
        ProfileDraft existing = new ProfileDraft("Browser");
        ProfileDraft other = new ProfileDraft("Editor");
        assertNotNull(ProfileDialog.validateName("", existing, List.of(existing)));
        assertNotNull(ProfileDialog.validateName("bad]name", existing, List.of(existing)));
        assertNotNull(ProfileDialog.validateName("editor", existing, List.of(existing, other)));
        assertNull(ProfileDialog.validateName("Browser", existing, List.of(existing, other)));
    }

    @Test
    void splitListIgnoresBlankEntries() {
        assertEquals(List.of("a.exe", "b.exe"), ProfileDialog.splitList(" a.exe, ,b.exe ,"));
    }

    @Test
    void disabledDraftIsWrittenWithEnabledFalse() {
        ProfileDraft draft = new ProfileDraft("off");
        draft.enabled = false;
        Profile profile = draft.toProfile();
        assertFalse(profile.enabled());
        assertTrue(ConfigWriter.write(List.of(profile)).contains("enabled = false"));
    }
}
