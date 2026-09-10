package com.termux.view;

import junit.framework.TestCase;

import static android.view.KeyEvent.*;
import static com.termux.terminal.KeyHandler.*;

public class KittyKeyboardInputTest extends TestCase {
    public void testModifierBitsAndLocks() {
        int meta = META_SHIFT_ON | META_ALT_LEFT_ON | META_CTRL_ON | META_META_ON | META_CAPS_LOCK_ON | META_NUM_LOCK_ON;
        assertEquals(KEYMOD_SHIFT | KEYMOD_ALT | KEYMOD_CTRL | KEYMOD_SUPER | KEYMOD_CAPS_LOCK | KEYMOD_NUM_LOCK,
            KittyKeyboardInput.keyMode(meta, KEYCODE_A, true, false, false, false, false));
        assertEquals(KEYMOD_CTRL | KEYMOD_ALT | KEYMOD_SHIFT,
            KittyKeyboardInput.keyMode(0, KEYCODE_A, true, true, true, true, false));
    }

    public void testModifierTransitions() {
        assertEquals(KEYMOD_CTRL, KittyKeyboardInput.keyMode(0, KEYCODE_CTRL_LEFT, true, false, false, false, false));
        assertEquals(0, KittyKeyboardInput.keyMode(META_CTRL_LEFT_ON | META_CTRL_ON, KEYCODE_CTRL_LEFT, false, false, false, false, false));
        assertEquals(KEYMOD_CTRL, KittyKeyboardInput.keyMode(META_CTRL_LEFT_ON | META_CTRL_RIGHT_ON | META_CTRL_ON,
            KEYCODE_CTRL_LEFT, false, false, false, false, false));
        assertEquals(KEYMOD_SUPER, KittyKeyboardInput.keyMode(0, KEYCODE_META_RIGHT, true, false, false, false, false));
        assertEquals(0, KittyKeyboardInput.keyMode(META_META_ON | META_META_RIGHT_ON, KEYCODE_META_RIGHT, false, false, false, false, false));
    }

    public void testAltGrAndLayoutMetadata() {
        int meta = META_ALT_ON | META_ALT_RIGHT_ON | META_CTRL_ON | META_META_ON | META_CAPS_LOCK_ON | META_SHIFT_ON;
        int textMeta = KittyKeyboardInput.textMetaState(meta, true, false);
        assertEquals(META_ALT_ON | META_ALT_RIGHT_ON | META_CAPS_LOCK_ON | META_SHIFT_ON, textMeta);
        assertEquals(META_ALT_ON | META_ALT_RIGHT_ON, KittyKeyboardInput.unshiftedMetaState(textMeta));
        assertEquals(0, KittyKeyboardInput.keyMode(META_ALT_ON | META_ALT_RIGHT_ON, KEYCODE_E, true, false, false, false, true));
        assertEquals(KEYMOD_ALT, KittyKeyboardInput.keyMode(META_ALT_ON | META_ALT_RIGHT_ON | META_ALT_LEFT_ON,
            KEYCODE_E, true, false, false, false, true));
        assertEquals(0, KittyKeyboardInput.textMetaState(META_ALT_ON | META_ALT_LEFT_ON, false, false));
        assertEquals(META_FUNCTION_ON | META_SHIFT_ON, KittyKeyboardInput.textMetaState(0, true, true));
    }

    public void testUnicodeAndDeadKeys() {
        assertEquals(0x00b4, KittyKeyboardInput.keyCodePoint(0x800000b4));
        assertNull(KittyKeyboardInput.text(0x800000b4));
        assertEquals("\u00e9", KittyKeyboardInput.text(0x00e9));
        assertEquals("\ud83d\ude00", KittyKeyboardInput.text(0x1f600));
        for (int codePoint : new int[]{0, 13, 127, 0x9f, 0xd800, 0xdfff, 0x110000, -1})
            assertNull(KittyKeyboardInput.text(codePoint));
        assertEquals(KEYCODE_ENTER, KittyKeyboardInput.controlKey('\n'));
        assertEquals(KEYCODE_ENTER, KittyKeyboardInput.controlKey('\r'));
        assertEquals(KEYCODE_TAB, KittyKeyboardInput.controlKey('\t'));
        assertEquals(KEYCODE_UNKNOWN, KittyKeyboardInput.controlKey('a'));
    }

    public void testCommittedControlKeysPreserveCapturedModifiers() {
        for (int flags : new int[]{24, 31}) {
            assertEquals("\033[13;2u", committedControlCode('\n', false, false, true, flags));
            assertEquals("\033[9;2u", committedControlCode('\t', false, false, true, flags));
            assertEquals("\033[13;6u", committedControlCode('\r', true, false, true, flags));
            assertEquals("\033[9;4u", committedControlCode('\t', false, true, true, flags));
        }
    }

    public void testRepeatAndReleaseTypes() {
        assertEquals(KITTY_PRESS, KittyKeyboardInput.eventType(ACTION_DOWN, 0));
        assertEquals(KITTY_REPEAT, KittyKeyboardInput.eventType(ACTION_DOWN, 7));
        assertEquals(KITTY_RELEASE, KittyKeyboardInput.eventType(ACTION_UP, 7));
    }

    public void testReleasePairingPreservesIdentityAndUsesCurrentModifiers() {
        KittyKeyboardInput input = new KittyKeyboardInput();
        KittyKeyEvent press = new KittyKeyEvent(KEYCODE_I, KEYMOD_CTRL, 'i', 0, null, KITTY_PRESS);
        input.pressed(2, KEYCODE_I, false, press, 3);
        KittyKeyEvent release = input.released(2, KEYCODE_I, false, 0);
        assertNotNull(release);
        assertEquals('i', release.codePoint);
        assertNull(release.text);
        assertEquals("\033[105;1:3u", getCode(release, 3, false, false));
        assertNull(input.released(2, KEYCODE_I, false, 0));
    }

    public void testNoUnmatchedOrUnrequestedRelease() {
        KittyKeyboardInput input = new KittyKeyboardInput();
        KittyKeyEvent press = new KittyKeyEvent(KEYCODE_A, 0, 'a', 0, "a", KITTY_PRESS);
        assertNull(input.released(1, KEYCODE_A, false, 0)); // Client-consumed shortcut.
        for (int flags : new int[]{0, 1, 3, 8}) {
            input.pressed(1, KEYCODE_A, false, press, flags);
            assertNull(input.released(1, KEYCODE_A, false, 0));
        }
        input.pressed(1, KEYCODE_A, false, press, 10);
        input.forget(1, KEYCODE_A);
        assertNull(input.released(1, KEYCODE_A, false, 0));
    }

    public void testReleaseIsolationAndLifecycle() {
        KittyKeyboardInput input = new KittyKeyboardInput();
        KittyKeyEvent press = new KittyKeyEvent(KEYCODE_A, 0, 'a', 0, "a", KITTY_PRESS);
        input.pressed(1, KEYCODE_A, false, press, 10);
        input.pressed(2, KEYCODE_A, false, press, 10);
        assertNull(input.released(3, KEYCODE_A, false, 0));
        assertNotNull(input.released(2, KEYCODE_A, false, 0));
        assertNotNull(input.released(1, KEYCODE_A, false, 0));
        input.pressed(1, KEYCODE_A, false, press, 10);
        assertNull(input.released(1, KEYCODE_A, true, 0)); // Different screen.
        input.pressed(1, KEYCODE_A, false, press, 10);
        input.clear(); // Session switch / focus loss.
        assertNull(input.released(1, KEYCODE_A, false, 0));
    }

    public void testFallbackKeyRetainsPhysicalPairing() {
        KittyKeyboardInput input = new KittyKeyboardInput();
        KittyKeyEvent functionMapped = new KittyKeyEvent(KEYCODE_UNKNOWN, 0, '1', 0, "1", KITTY_PRESS);
        input.pressed(-1, KEYCODE_DPAD_UP, false, functionMapped, 10);
        KittyKeyEvent release = input.released(-1, KEYCODE_DPAD_UP, false, 0);
        assertEquals("\033[49;1:3u", getCode(release, 10, false, false));
    }

    public void testOverlappingKeysAndModifierRelease() {
        KittyKeyboardInput input = new KittyKeyboardInput();
        input.pressed(1, KEYCODE_SHIFT_LEFT, false, new KittyKeyEvent(KEYCODE_SHIFT_LEFT, KEYMOD_SHIFT, 0, 0, null, KITTY_PRESS), 31);
        input.pressed(1, KEYCODE_A, false, new KittyKeyEvent(KEYCODE_A, KEYMOD_SHIFT, 'a', 'A', "A", KITTY_PRESS), 31);
        input.pressed(1, KEYCODE_B, false, new KittyKeyEvent(KEYCODE_B, KEYMOD_SHIFT, 'b', 'B', "B", KITTY_PRESS), 31);
        assertEquals("\033[97:65;2:3u", getCode(input.released(1, KEYCODE_A, false, KEYMOD_SHIFT), 31, false, false));
        assertEquals("\033[98:66;2:3u", getCode(input.released(1, KEYCODE_B, false, KEYMOD_SHIFT), 31, false, false));
        assertEquals("\033[57441;1:3u", getCode(input.released(1, KEYCODE_SHIFT_LEFT, false, 0), 31, false, false));
    }

    private static String committedControlCode(int codePoint, boolean ctrl, boolean alt, boolean shift, int flags) {
        int keyCode = KittyKeyboardInput.controlKey(codePoint);
        int keyMode = KittyKeyboardInput.committedControlKeyMode(ctrl, alt, shift);
        return getCode(new KittyKeyEvent(keyCode, keyMode, 0, 0, null, KITTY_PRESS), flags, false, false);
    }
}
