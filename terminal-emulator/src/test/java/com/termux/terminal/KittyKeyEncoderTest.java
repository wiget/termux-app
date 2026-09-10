package com.termux.terminal;

import junit.framework.TestCase;

import static android.view.KeyEvent.*;
import static com.termux.terminal.KeyHandler.*;

public class KittyKeyEncoderTest extends TestCase {
    private String encode(int key, int mods, int flags, int type, int codePoint, int shifted, String text) {
        return getCode(new KittyKeyEvent(key, mods, codePoint, shifted, text, type), flags, false, false);
    }

    private String key(int key, int mods, int flags, int type) {
        return encode(key, mods, flags, type, 0, 0, null);
    }

    public void testZeroFlagsLegacyMatrix() {
        for (int key = 0; key <= KEYCODE_F24; key++) {
            for (int bits = 0; bits < 16; bits++) {
                int mods = ((bits & 1) != 0 ? KEYMOD_SHIFT : 0) | ((bits & 2) != 0 ? KEYMOD_ALT : 0)
                    | ((bits & 4) != 0 ? KEYMOD_CTRL : 0) | ((bits & 8) != 0 ? KEYMOD_NUM_LOCK : 0);
                for (int modes = 0; modes < 4; modes++) {
                    for (int type = KITTY_PRESS; type <= KITTY_REPEAT; type++) {
                        assertEquals(getCode(key, mods, (modes & 1) != 0, (modes & 2) != 0),
                            getCode(new KittyKeyEvent(key, mods, 'a', 'A', "a", type), 0, (modes & 1) != 0, (modes & 2) != 0));
                    }
                }
                assertEquals("", key(key, mods, 0, KITTY_RELEASE));
            }
        }
        // Golden modified-Enter expectations from upstream, without the local LF workaround.
        for (int mods : new int[]{0, KEYMOD_SHIFT, KEYMOD_CTRL, KEYMOD_SHIFT | KEYMOD_CTRL}) {
            assertEquals("\r", key(KEYCODE_ENTER, mods, 0, KITTY_PRESS));
            assertEquals("\033\r", key(KEYCODE_ENTER, mods | KEYMOD_ALT, 0, KITTY_PRESS));
        }
        assertEquals("\033OR", key(KEYCODE_F3, 0, 0, KITTY_PRESS));
        assertEquals("\033[Z", key(KEYCODE_TAB, KEYMOD_SHIFT, 0, KITTY_PRESS));
    }

    public void testDisambiguationAndRecovery() {
        assertEquals("\033[27u", key(KEYCODE_ESCAPE, 0, 1, KITTY_PRESS));
        assertEquals("\033[105;5u", encode(KEYCODE_I, KEYMOD_CTRL, 1, KITTY_PRESS, 'i', 0, null));
        assertEquals("\033[91;3u", encode(KEYCODE_LEFT_BRACKET, KEYMOD_ALT, 1, KITTY_PRESS, '[', 0, null));
        assertEquals("\033[97;8u", encode(KEYCODE_A, KEYMOD_SHIFT | KEYMOD_ALT | KEYMOD_CTRL, 1, KITTY_PRESS, 'a', 'A', null));
        assertEquals("\033[13;2u", key(KEYCODE_ENTER, KEYMOD_SHIFT, 1, KITTY_PRESS));
        assertEquals("\033[13;5u", key(KEYCODE_ENTER, KEYMOD_CTRL, 1, KITTY_PRESS));
        assertEquals("\033[9;6u", key(KEYCODE_TAB, KEYMOD_SHIFT | KEYMOD_CTRL, 1, KITTY_PRESS));
        for (int flags : new int[]{1, 2, 3, 7}) {
            assertEquals("\r", key(KEYCODE_ENTER, KEYMOD_CAPS_LOCK, flags, KITTY_REPEAT));
            assertEquals("\t", key(KEYCODE_TAB, 0, flags, KITTY_PRESS));
            assertEquals("\177", key(KEYCODE_DEL, 0, flags, KITTY_PRESS));
            assertEquals("", key(KEYCODE_ENTER, KEYMOD_CTRL, flags, KITTY_RELEASE));
            assertNull(encode(KEYCODE_A, KEYMOD_SHIFT | KEYMOD_CAPS_LOCK, flags, KITTY_REPEAT, 'a', 'A', "A"));
            assertEquals("", encode(KEYCODE_A, 0, flags, KITTY_RELEASE, 'a', 0, "a"));
        }
        assertEquals("\033[13u", key(KEYCODE_ENTER, 0, 8, KITTY_PRESS));
        assertEquals("\033[9u", key(KEYCODE_TAB, 0, 8, KITTY_PRESS));
        assertEquals("\033[127u", key(KEYCODE_DEL, 0, 8, KITTY_PRESS));
    }

    public void testSpace() {
        assertEquals("\033[32;5u", encode(KEYCODE_SPACE, KEYMOD_CTRL, 5, KITTY_PRESS, ' ', 0, null));
        assertEquals("", encode(KEYCODE_SPACE, KEYMOD_CTRL, 5, KITTY_RELEASE, ' ', 0, null));
        assertEquals("\033[32;5:3u", encode(KEYCODE_SPACE, KEYMOD_CTRL, 3, KITTY_RELEASE, ' ', 0, null));
        assertNull(encode(KEYCODE_SPACE, KEYMOD_SHIFT, 5, KITTY_PRESS, ' ', ' ', " "));
    }

    public void testEventsAndOptionalFields() {
        assertEquals("\033[97u", encode(KEYCODE_A, 0, 8, KITTY_REPEAT, 'a', 0, "a"));
        assertEquals("\033[97;1:2u", encode(KEYCODE_A, 0, 10, KITTY_REPEAT, 'a', 0, "a"));
        assertEquals("\033[97;1:3u", encode(KEYCODE_A, 0, 31, KITTY_RELEASE, 'a', 0, "a"));
        assertEquals("\033[97:65;2;65u", encode(KEYCODE_A, KEYMOD_SHIFT, 31, KITTY_PRESS, 'a', 'A', "A"));
        assertEquals("\033[97;;97u", encode(KEYCODE_A, 0, 24, KITTY_PRESS, 'a', 'A', "a"));
        assertEquals("\033[97;2u", encode(KEYCODE_A, KEYMOD_SHIFT, 8, KITTY_PRESS, 'a', 'A', "A"));
        assertEquals("\033[97;2:2;65u", encode(KEYCODE_A, KEYMOD_SHIFT, 26, KITTY_REPEAT, 'a', 'A', "A"));
        assertEquals("\033[101;;101:769u", encode(KEYCODE_E, 0, 24, KITTY_PRESS, 'e', 0, "e\u0301"));
        assertEquals("\033[128512;;128512u", encode(KEYCODE_UNKNOWN, 0, 24, KITTY_PRESS, 0x1f600, 0, "\ud83d\ude00"));
        assertNull(encode(KEYCODE_A, KEYMOD_SHIFT, 4, KITTY_PRESS, 'a', 'A', "A"));
        assertNull(encode(KEYCODE_A, 0, 16, KITTY_PRESS, 'a', 0, "a"));
    }

    public void testModifiers() {
        int[] bits = {KEYMOD_SHIFT, KEYMOD_ALT, KEYMOD_CTRL, KEYMOD_SUPER, KEYMOD_CAPS_LOCK, KEYMOD_NUM_LOCK};
        int[] values = {2, 3, 5, 9, 65, 129};
        for (int i = 0; i < bits.length; i++)
            assertEquals("\033[97;" + values[i] + "u", encode(KEYCODE_A, bits[i], 8, KITTY_PRESS, 'a', 0, null));
        assertEquals("\033[97;208u", encode(KEYCODE_A, KEYMOD_SHIFT | KEYMOD_ALT | KEYMOD_CTRL | KEYMOD_SUPER
            | KEYMOD_CAPS_LOCK | KEYMOD_NUM_LOCK, 8, KITTY_PRESS, 'a', 0, null));
    }

    public void testFunctionalKeys() {
        int[] keys = {KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT,
            KEYCODE_MOVE_HOME, KEYCODE_MOVE_END, KEYCODE_INSERT, KEYCODE_FORWARD_DEL, KEYCODE_PAGE_UP, KEYCODE_PAGE_DOWN};
        String[] forms = {"A", "B", "C", "D", "H", "F", "2~", "3~", "5~", "6~"};
        for (int i = 0; i < keys.length; i++) {
            assertEquals("\033[" + forms[i], key(keys[i], 0, 1, KITTY_PRESS));
            String number = i < 6 ? "1" : forms[i].substring(0, 1);
            char suffix = forms[i].charAt(forms[i].length() - 1);
            assertEquals("\033[" + number + ";5:3" + suffix, key(keys[i], KEYMOD_CTRL, 3, KITTY_RELEASE));
            assertEquals("\033[" + forms[i], getCode(new KittyKeyEvent(keys[i], 0, 0, 0, null, KITTY_PRESS), 8, true, true));
        }
        String[] functions = {"P", "Q", "13~", "S", "15~", "17~", "18~", "19~", "20~", "21~", "23~", "24~"};
        for (int i = 0; i < functions.length; i++) assertEquals("\033[" + functions[i], key(KEYCODE_F1 + i, 0, 1, KITTY_PRESS));
        for (int i = 0; i < 12; i++) assertEquals("\033[" + (57376 + i) + "u", key(KEYCODE_F13 + i, 0, 8, KITTY_PRESS));
    }

    public void testKeypadKeys() {
        assertEquals("\033[D", key(KEYCODE_NUMPAD_4, 0, 2, KITTY_PRESS));
        assertEquals("\033[1;1:3D", key(KEYCODE_NUMPAD_4, 0, 2, KITTY_RELEASE));
        assertEquals("4", key(KEYCODE_NUMPAD_4, KEYMOD_NUM_LOCK, 2, KITTY_PRESS));
        assertEquals("\r", key(KEYCODE_NUMPAD_ENTER, 0, 2, KITTY_PRESS));
        int[] navigation = {57425, 57424, 57420, 57422, 57417, 57427, 57418, 57423, 57419, 57421};
        for (int i = 0; i < 10; i++) {
            assertEquals("\033[" + (57399 + i) + ";129u", key(KEYCODE_NUMPAD_0 + i, KEYMOD_NUM_LOCK, 8, KITTY_PRESS));
            assertEquals(i == 5 ? "\033[E" : "\033[" + navigation[i] + "u", key(KEYCODE_NUMPAD_0 + i, 0, 1, KITTY_PRESS));
            assertNull(encode(KEYCODE_NUMPAD_0 + i, KEYMOD_NUM_LOCK, 1, KITTY_PRESS, '0' + i, 0, Integer.toString(i)));
        }
        int[] operators = {KEYCODE_NUMPAD_DOT, KEYCODE_NUMPAD_DIVIDE, KEYCODE_NUMPAD_MULTIPLY, KEYCODE_NUMPAD_SUBTRACT,
            KEYCODE_NUMPAD_ADD, KEYCODE_NUMPAD_ENTER, KEYCODE_NUMPAD_EQUALS, KEYCODE_NUMPAD_COMMA};
        for (int i = 0; i < operators.length; i++) assertEquals("\033[" + (57409 + i) + ";129u", key(operators[i], KEYMOD_NUM_LOCK, 8, KITTY_PRESS));
        assertEquals("\033[57426u", key(KEYCODE_NUMPAD_DOT, 0, 1, KITTY_PRESS));
    }

    public void testLocksMediaVolumeAndModifiers() {
        int[][] mappings = {{KEYCODE_CAPS_LOCK, 57358}, {KEYCODE_SCROLL_LOCK, 57359}, {KEYCODE_NUM_LOCK, 57360},
            {KEYCODE_SYSRQ, 57361}, {KEYCODE_BREAK, 57362}, {KEYCODE_MENU, 57363}, {KEYCODE_MEDIA_PLAY, 57428},
            {KEYCODE_MEDIA_PAUSE, 57429}, {KEYCODE_MEDIA_PLAY_PAUSE, 57430}, {KEYCODE_MEDIA_STOP, 57432},
            {KEYCODE_MEDIA_FAST_FORWARD, 57433}, {KEYCODE_MEDIA_REWIND, 57434}, {KEYCODE_MEDIA_NEXT, 57435},
            {KEYCODE_MEDIA_PREVIOUS, 57436}, {KEYCODE_MEDIA_RECORD, 57437}, {KEYCODE_VOLUME_DOWN, 57438},
            {KEYCODE_VOLUME_UP, 57439}, {KEYCODE_VOLUME_MUTE, 57440}, {KEYCODE_SHIFT_LEFT, 57441},
            {KEYCODE_CTRL_LEFT, 57442}, {KEYCODE_ALT_LEFT, 57443}, {KEYCODE_META_LEFT, 57444},
            {KEYCODE_SHIFT_RIGHT, 57447}, {KEYCODE_CTRL_RIGHT, 57448}, {KEYCODE_ALT_RIGHT, 57449}, {KEYCODE_META_RIGHT, 57450}};
        for (int[] mapping : mappings) assertEquals("\033[" + mapping[1] + "u", key(mapping[0], 0, 8, KITTY_PRESS));
        assertEquals("", key(KEYCODE_CTRL_LEFT, KEYMOD_CTRL, 3, KITTY_PRESS));
        assertEquals("\033[57442;5u", key(KEYCODE_CTRL_LEFT, KEYMOD_CTRL, 8, KITTY_PRESS));
        assertEquals("\033[57442;1:3u", key(KEYCODE_CTRL_LEFT, 0, 10, KITTY_RELEASE));
    }

    public void testInvalidAndUnavailableData() {
        assertNull(key(KEYCODE_UNKNOWN, 0, 31, KITTY_PRESS));
        for (int cp : new int[]{-1, 0xd800, 0xdfff, 0x110000}) assertNull(encode(KEYCODE_UNKNOWN, 0, 31, KITTY_PRESS, cp, 0, null));
        for (String text : new String[]{"\u001b", "\u007f", "\u0080", "x\ud800", "\udfff", "\n", ""}) {
            assertEquals("\033[97u", encode(KEYCODE_A, 0, 24, KITTY_PRESS, 'a', 0, text));
            assertNull(getKittyText(text, 24));
        }
        assertEquals("", key(KEYCODE_ENTER, 0, 31, 4));
        assertNull(encode(KEYCODE_A, 0, 32, KITTY_PRESS, 'a', 0, "a"));
    }

    public void testImeText() {
        assertEquals("\033[0;;229u", encode(KEYCODE_UNKNOWN, 0, 24, KITTY_PRESS, 0, 0, "\u00e5"));
        assertEquals("\033[0;;101:769:128512u", getKittyText("e\u0301\ud83d\ude00", 24));
        assertEquals("\033[0;;97u", getKittyText("a", 31));
        assertNull(getKittyText("abc", 0));
        assertNull(getKittyText("abc", 1));
        assertNull(getKittyText("abc", 8));
        assertNull(getKittyText("abc", 16));
    }
}
