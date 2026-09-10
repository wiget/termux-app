package com.termux.terminal;

import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;

import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_BREAK;
import static android.view.KeyEvent.KEYCODE_DEL;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_DOWN;
import static android.view.KeyEvent.KEYCODE_DPAD_LEFT;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_DPAD_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_ESCAPE;
import static android.view.KeyEvent.KEYCODE_F1;
import static android.view.KeyEvent.KEYCODE_F10;
import static android.view.KeyEvent.KEYCODE_F11;
import static android.view.KeyEvent.KEYCODE_F12;
import static android.view.KeyEvent.KEYCODE_F2;
import static android.view.KeyEvent.KEYCODE_F3;
import static android.view.KeyEvent.KEYCODE_F4;
import static android.view.KeyEvent.KEYCODE_F5;
import static android.view.KeyEvent.KEYCODE_F6;
import static android.view.KeyEvent.KEYCODE_F7;
import static android.view.KeyEvent.KEYCODE_F8;
import static android.view.KeyEvent.KEYCODE_F9;
import static android.view.KeyEvent.KEYCODE_FORWARD_DEL;
import static android.view.KeyEvent.KEYCODE_INSERT;
import static android.view.KeyEvent.KEYCODE_MOVE_END;
import static android.view.KeyEvent.KEYCODE_MOVE_HOME;
import static android.view.KeyEvent.KEYCODE_NUMPAD_0;
import static android.view.KeyEvent.KEYCODE_NUMPAD_1;
import static android.view.KeyEvent.KEYCODE_NUMPAD_2;
import static android.view.KeyEvent.KEYCODE_NUMPAD_3;
import static android.view.KeyEvent.KEYCODE_NUMPAD_4;
import static android.view.KeyEvent.KEYCODE_NUMPAD_5;
import static android.view.KeyEvent.KEYCODE_NUMPAD_6;
import static android.view.KeyEvent.KEYCODE_NUMPAD_7;
import static android.view.KeyEvent.KEYCODE_NUMPAD_8;
import static android.view.KeyEvent.KEYCODE_NUMPAD_9;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ADD;
import static android.view.KeyEvent.KEYCODE_NUMPAD_COMMA;
import static android.view.KeyEvent.KEYCODE_NUMPAD_DIVIDE;
import static android.view.KeyEvent.KEYCODE_NUMPAD_DOT;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_EQUALS;
import static android.view.KeyEvent.KEYCODE_NUMPAD_MULTIPLY;
import static android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT;
import static android.view.KeyEvent.KEYCODE_NUM_LOCK;
import static android.view.KeyEvent.KEYCODE_PAGE_DOWN;
import static android.view.KeyEvent.KEYCODE_PAGE_UP;
import static android.view.KeyEvent.KEYCODE_SPACE;
import static android.view.KeyEvent.KEYCODE_SYSRQ;
import static android.view.KeyEvent.KEYCODE_TAB;

public final class KeyHandler {

    public static final int KEYMOD_ALT = 0x80000000;
    public static final int KEYMOD_CTRL = 0x40000000;
    public static final int KEYMOD_SHIFT = 0x20000000;
    public static final int KEYMOD_NUM_LOCK = 0x10000000;
    public static final int KEYMOD_SUPER = 0x08000000;
    public static final int KEYMOD_CAPS_LOCK = 0x04000000;

    public static final int KITTY_DISAMBIGUATE = 1;
    public static final int KITTY_REPORT_EVENTS = 2;
    public static final int KITTY_REPORT_ALTERNATES = 4;
    public static final int KITTY_REPORT_ALL_KEYS = 8;
    public static final int KITTY_REPORT_TEXT = 16;
    public static final int KITTY_PRESS = 1;
    public static final int KITTY_REPEAT = 2;
    public static final int KITTY_RELEASE = 3;

    /** Layout information is optional (zero/null means unavailable). No base-layout key is fabricated. */
    public static final class KittyKeyEvent {
        public final int keyCode, keyMode, codePoint, shiftedCodePoint, eventType;
        public final String text;

        public KittyKeyEvent(int keyCode, int keyMode, int codePoint, int shiftedCodePoint, String text, int eventType) {
            this.keyCode = keyCode;
            this.keyMode = keyMode;
            this.codePoint = codePoint;
            this.shiftedCodePoint = shiftedCodePoint;
            this.text = text;
            this.eventType = eventType;
        }
    }

    /**
     * Encode a negotiated keyboard event. Null requests the existing text/dead-key path;
     * an empty string consumes an event without output (notably unrequested releases).
     * The four-argument API below remains the byte-for-byte legacy encoder.
     */
    public static String getCode(KittyKeyEvent event, int flags, boolean cursorApp, boolean keypadApplication) {
        flags &= 31;
        boolean release = event.eventType == KITTY_RELEASE;
        boolean all = (flags & KITTY_REPORT_ALL_KEYS) != 0;
        boolean events = (flags & KITTY_REPORT_EVENTS) != 0;
        boolean disambiguate = (flags & KITTY_DISAMBIGUATE) != 0 || all;
        if (event.eventType < KITTY_PRESS || event.eventType > KITTY_RELEASE) return "";
        if (release && !events) return "";
        if ((flags & (KITTY_DISAMBIGUATE | KITTY_REPORT_EVENTS | KITTY_REPORT_ALL_KEYS)) == 0) {
            return getCode(event.keyCode, event.keyMode & ~(KEYMOD_SUPER | KEYMOD_CAPS_LOCK), cursorApp, keypadApplication);
        }

        int mods = kittyModifiers(event.keyMode);
        int key = kittyFunctionalKey(event.keyCode, (event.keyMode & KEYMOD_NUM_LOCK) != 0);
        if (!disambiguate && key >= 57399 && key <= 57427) {
            // Event reporting alone does not request separate keypad identities.
            switch (key) {
                case 57414: key = 13; break;
                case 57417: key = 57350; break;
                case 57418: key = 57351; break;
                case 57419: key = 57352; break;
                case 57420: key = 57353; break;
                case 57421: key = 57354; break;
                case 57422: key = 57355; break;
                case 57423: key = 57356; break;
                case 57424: key = 57357; break;
                case 57425: key = 57348; break;
                case 57426: key = 57349; break;
                default:
                    return release ? "" : getCode(event.keyCode, event.keyMode & ~(KEYMOD_SUPER | KEYMOD_CAPS_LOCK), cursorApp, keypadApplication);
            }
        }
        boolean functional = key != 0;
        boolean recoveryKey = key == 13 || key == 9 || key == 127;
        if (!all && recoveryKey && release) return "";
        if (!all && recoveryKey && (mods & 63) == 0) {
            return key == 13 ? "\r" : key == 9 ? "\t" : "\177";
        }
        if (!all && key >= 57441 && key <= 57454) return "";

        // Printable keys (including keypad digits) remain text unless all-key mode is requested.
        boolean hasText = validKittyText(event.text);
        if (!all && hasText && (mods & 62) == 0) {
            return release ? "" : null;
        }
        if (!functional) {
            key = event.codePoint;
            if (!validCodePoint(key) || (key == 0 && !(all && (flags & KITTY_REPORT_TEXT) != 0 && hasText)))
                return release ? "" : null;
            if (!all && !disambiguate && (!events || event.eventType == KITTY_PRESS)) return release ? "" : null;
        }
        if (!all && key == 27 && !disambiguate && !release && (mods & 63) == 0) return "\033";

        char suffix = 'u';
        // Kitty's canonical functional encodings, including F3's non-CPR-conflicting 13~.
        switch (key) {
            case 57348: key = 2; suffix = '~'; break; // Insert
            case 57349: key = 3; suffix = '~'; break; // Delete
            case 57350: key = 1; suffix = 'D'; break;
            case 57351: key = 1; suffix = 'C'; break;
            case 57352: key = 1; suffix = 'A'; break;
            case 57353: key = 1; suffix = 'B'; break;
            case 57354: key = 5; suffix = '~'; break;
            case 57355: key = 6; suffix = '~'; break;
            case 57356: key = 1; suffix = 'H'; break;
            case 57357: key = 1; suffix = 'F'; break;
            case 57364: key = 1; suffix = 'P'; break;
            case 57365: key = 1; suffix = 'Q'; break;
            case 57366: key = 13; suffix = '~'; break;
            case 57367: key = 1; suffix = 'S'; break;
            case 57368: key = 15; suffix = '~'; break;
            case 57369: key = 17; suffix = '~'; break;
            case 57370: key = 18; suffix = '~'; break;
            case 57371: key = 19; suffix = '~'; break;
            case 57372: key = 20; suffix = '~'; break;
            case 57373: key = 21; suffix = '~'; break;
            case 57374: key = 23; suffix = '~'; break;
            case 57375: key = 24; suffix = '~'; break;
            case 57427: key = 1; suffix = 'E'; break;
        }
        int shifted = !functional && (flags & KITTY_REPORT_ALTERNATES) != 0 && (mods & 1) != 0
            && validCodePoint(event.shiftedCodePoint) ? event.shiftedCodePoint : 0;
        String text = all && (flags & KITTY_REPORT_TEXT) != 0 && !release && hasText ? event.text : null;
        return encodeKittySequence(key, shifted, mods, events ? event.eventType : KITTY_PRESS, text, suffix);
    }

    /** IME-only text has no key identity or event lifecycle. Null means use the committed-text fallback. */
    public static String getKittyText(CharSequence text, int flags) {
        if ((flags & (KITTY_REPORT_ALL_KEYS | KITTY_REPORT_TEXT)) != (KITTY_REPORT_ALL_KEYS | KITTY_REPORT_TEXT)
            || !validKittyText(text)) return null;
        return encodeKittySequence(0, 0, 0, KITTY_PRESS, text, 'u');
    }

    private static String encodeKittySequence(int key, int shifted, int mods, int type, CharSequence text, char suffix) {
        StringBuilder result = new StringBuilder("\033[");
        boolean second = mods != 0 || type != KITTY_PRESS;
        if (key != 1 || suffix == 'u' || shifted != 0 || second || text != null) result.append(key);
        if (shifted != 0) result.append(':').append(shifted);
        if (second || text != null) {
            result.append(';');
            if (second) result.append(mods + 1);
            if (type != KITTY_PRESS) result.append(':').append(type);
        }
        if (text != null) {
            for (int i = 0; i < text.length();) {
                int codePoint = Character.codePointAt(text, i);
                result.append(i == 0 ? ';' : ':').append(codePoint);
                i += Character.charCount(codePoint);
            }
        }
        return result.append(suffix).toString();
    }

    private static boolean validCodePoint(int value) {
        return Character.isValidCodePoint(value) && (value < 0xd800 || value > 0xdfff);
    }

    private static boolean validKittyText(CharSequence text) {
        if (text == null || text.length() == 0) return false;
        for (int i = 0; i < text.length();) {
            int codePoint = Character.codePointAt(text, i);
            if (!validCodePoint(codePoint) || Character.isISOControl(codePoint)) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }

    private static int kittyModifiers(int keyMode) {
        return ((keyMode & KEYMOD_SHIFT) != 0 ? 1 : 0) | ((keyMode & KEYMOD_ALT) != 0 ? 2 : 0)
            | ((keyMode & KEYMOD_CTRL) != 0 ? 4 : 0) | ((keyMode & KEYMOD_SUPER) != 0 ? 8 : 0)
            | ((keyMode & KEYMOD_CAPS_LOCK) != 0 ? 64 : 0) | ((keyMode & KEYMOD_NUM_LOCK) != 0 ? 128 : 0);
    }

    /** Android-deliverable keys from the Kitty functional-key table (retrieved 2026-09-09). */
    private static int kittyFunctionalKey(int keyCode, boolean numLock) {
        if (keyCode >= KEYCODE_F1 && keyCode <= KEYCODE_F12) return 57364 + keyCode - KEYCODE_F1;
        // Added in API 36; compile-time constants also work on older Android versions.
        if (keyCode >= KeyEvent.KEYCODE_F13 && keyCode <= KeyEvent.KEYCODE_F24) return 57376 + keyCode - KeyEvent.KEYCODE_F13;
        if (keyCode >= KEYCODE_NUMPAD_0 && keyCode <= KEYCODE_NUMPAD_9) {
            if (numLock) return 57399 + keyCode - KEYCODE_NUMPAD_0;
            switch (keyCode) {
                case KEYCODE_NUMPAD_0: return 57425; // KP_INSERT
                case KEYCODE_NUMPAD_1: return 57424; // KP_END
                case KEYCODE_NUMPAD_2: return 57420; // KP_DOWN
                case KEYCODE_NUMPAD_3: return 57422; // KP_PAGE_DOWN
                case KEYCODE_NUMPAD_4: return 57417; // KP_LEFT
                case KEYCODE_NUMPAD_5: return 57427; // KP_BEGIN
                case KEYCODE_NUMPAD_6: return 57418; // KP_RIGHT
                case KEYCODE_NUMPAD_7: return 57423; // KP_HOME
                case KEYCODE_NUMPAD_8: return 57419; // KP_UP
                case KEYCODE_NUMPAD_9: return 57421; // KP_PAGE_UP
            }
        }
        switch (keyCode) {
            case KEYCODE_ESCAPE: case KEYCODE_BACK: return 27;
            case KEYCODE_ENTER: case KEYCODE_DPAD_CENTER: return 13;
            case KEYCODE_TAB: return 9;
            case KEYCODE_DEL: return 127;
            case KEYCODE_INSERT: return 57348;
            case KEYCODE_FORWARD_DEL: return 57349;
            case KEYCODE_DPAD_LEFT: return 57350;
            case KEYCODE_DPAD_RIGHT: return 57351;
            case KEYCODE_DPAD_UP: return 57352;
            case KEYCODE_DPAD_DOWN: return 57353;
            case KEYCODE_PAGE_UP: return 57354;
            case KEYCODE_PAGE_DOWN: return 57355;
            case KEYCODE_MOVE_HOME: return 57356;
            case KEYCODE_MOVE_END: return 57357;
            case KeyEvent.KEYCODE_CAPS_LOCK: return 57358;
            case KeyEvent.KEYCODE_SCROLL_LOCK: return 57359;
            case KEYCODE_NUM_LOCK: return 57360;
            case KEYCODE_SYSRQ: return 57361;
            case KEYCODE_BREAK: return 57362;
            case KeyEvent.KEYCODE_MENU: return 57363;
            case KEYCODE_NUMPAD_DOT: return numLock ? 57409 : 57426;
            case KEYCODE_NUMPAD_DIVIDE: return 57410;
            case KEYCODE_NUMPAD_MULTIPLY: return 57411;
            case KEYCODE_NUMPAD_SUBTRACT: return 57412;
            case KEYCODE_NUMPAD_ADD: return 57413;
            case KEYCODE_NUMPAD_ENTER: return 57414;
            case KEYCODE_NUMPAD_EQUALS: return 57415;
            case KEYCODE_NUMPAD_COMMA: return 57416;
            case KeyEvent.KEYCODE_MEDIA_PLAY: return 57428;
            case KeyEvent.KEYCODE_MEDIA_PAUSE: return 57429;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: return 57430;
            case KeyEvent.KEYCODE_MEDIA_STOP: return 57432;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: return 57433;
            case KeyEvent.KEYCODE_MEDIA_REWIND: return 57434;
            case KeyEvent.KEYCODE_MEDIA_NEXT: return 57435;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: return 57436;
            case KeyEvent.KEYCODE_MEDIA_RECORD: return 57437;
            case KeyEvent.KEYCODE_VOLUME_DOWN: return 57438;
            case KeyEvent.KEYCODE_VOLUME_UP: return 57439;
            case KeyEvent.KEYCODE_VOLUME_MUTE: return 57440;
            case KeyEvent.KEYCODE_SHIFT_LEFT: return 57441;
            case KeyEvent.KEYCODE_CTRL_LEFT: return 57442;
            case KeyEvent.KEYCODE_ALT_LEFT: return 57443;
            case KeyEvent.KEYCODE_META_LEFT: return 57444;
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return 57447;
            case KeyEvent.KEYCODE_CTRL_RIGHT: return 57448;
            case KeyEvent.KEYCODE_ALT_RIGHT: return 57449;
            case KeyEvent.KEYCODE_META_RIGHT: return 57450;
            default: return 0;
        }
    }

    private static final Map<String, Integer> TERMCAP_TO_KEYCODE = new HashMap<>();

    static {
        // terminfo: http://pubs.opengroup.org/onlinepubs/7990989799/xcurses/terminfo.html
        // termcap: http://man7.org/linux/man-pages/man5/termcap.5.html
        TERMCAP_TO_KEYCODE.put("%i", KEYMOD_SHIFT | KEYCODE_DPAD_RIGHT);
        TERMCAP_TO_KEYCODE.put("#2", KEYMOD_SHIFT | KEYCODE_MOVE_HOME); // Shifted home
        TERMCAP_TO_KEYCODE.put("#4", KEYMOD_SHIFT | KEYCODE_DPAD_LEFT);
        TERMCAP_TO_KEYCODE.put("*7", KEYMOD_SHIFT | KEYCODE_MOVE_END); // Shifted end key

        TERMCAP_TO_KEYCODE.put("k1", KEYCODE_F1);
        TERMCAP_TO_KEYCODE.put("k2", KEYCODE_F2);
        TERMCAP_TO_KEYCODE.put("k3", KEYCODE_F3);
        TERMCAP_TO_KEYCODE.put("k4", KEYCODE_F4);
        TERMCAP_TO_KEYCODE.put("k5", KEYCODE_F5);
        TERMCAP_TO_KEYCODE.put("k6", KEYCODE_F6);
        TERMCAP_TO_KEYCODE.put("k7", KEYCODE_F7);
        TERMCAP_TO_KEYCODE.put("k8", KEYCODE_F8);
        TERMCAP_TO_KEYCODE.put("k9", KEYCODE_F9);
        TERMCAP_TO_KEYCODE.put("k;", KEYCODE_F10);
        TERMCAP_TO_KEYCODE.put("F1", KEYCODE_F11);
        TERMCAP_TO_KEYCODE.put("F2", KEYCODE_F12);
        TERMCAP_TO_KEYCODE.put("F3", KEYMOD_SHIFT | KEYCODE_F1);
        TERMCAP_TO_KEYCODE.put("F4", KEYMOD_SHIFT | KEYCODE_F2);
        TERMCAP_TO_KEYCODE.put("F5", KEYMOD_SHIFT | KEYCODE_F3);
        TERMCAP_TO_KEYCODE.put("F6", KEYMOD_SHIFT | KEYCODE_F4);
        TERMCAP_TO_KEYCODE.put("F7", KEYMOD_SHIFT | KEYCODE_F5);
        TERMCAP_TO_KEYCODE.put("F8", KEYMOD_SHIFT | KEYCODE_F6);
        TERMCAP_TO_KEYCODE.put("F9", KEYMOD_SHIFT | KEYCODE_F7);
        TERMCAP_TO_KEYCODE.put("FA", KEYMOD_SHIFT | KEYCODE_F8);
        TERMCAP_TO_KEYCODE.put("FB", KEYMOD_SHIFT | KEYCODE_F9);
        TERMCAP_TO_KEYCODE.put("FC", KEYMOD_SHIFT | KEYCODE_F10);
        TERMCAP_TO_KEYCODE.put("FD", KEYMOD_SHIFT | KEYCODE_F11);
        TERMCAP_TO_KEYCODE.put("FE", KEYMOD_SHIFT | KEYCODE_F12);

        TERMCAP_TO_KEYCODE.put("kb", KEYCODE_DEL); // backspace key

        TERMCAP_TO_KEYCODE.put("kd", KEYCODE_DPAD_DOWN); // terminfo=kcud1, down-arrow key
        TERMCAP_TO_KEYCODE.put("kh", KEYCODE_MOVE_HOME);
        TERMCAP_TO_KEYCODE.put("kl", KEYCODE_DPAD_LEFT);
        TERMCAP_TO_KEYCODE.put("kr", KEYCODE_DPAD_RIGHT);

        // K1=Upper left of keypad:
        // t_K1 <kHome> keypad home key
        // t_K3 <kPageUp> keypad page-up key
        // t_K4 <kEnd> keypad end key
        // t_K5 <kPageDown> keypad page-down key
        TERMCAP_TO_KEYCODE.put("K1", KEYCODE_MOVE_HOME);
        TERMCAP_TO_KEYCODE.put("K3", KEYCODE_PAGE_UP);
        TERMCAP_TO_KEYCODE.put("K4", KEYCODE_MOVE_END);
        TERMCAP_TO_KEYCODE.put("K5", KEYCODE_PAGE_DOWN);

        TERMCAP_TO_KEYCODE.put("ku", KEYCODE_DPAD_UP);

        TERMCAP_TO_KEYCODE.put("kB", KEYMOD_SHIFT | KEYCODE_TAB); // termcap=kB, terminfo=kcbt: Back-tab
        TERMCAP_TO_KEYCODE.put("kD", KEYCODE_FORWARD_DEL); // terminfo=kdch1, delete-character key
        TERMCAP_TO_KEYCODE.put("kDN", KEYMOD_SHIFT | KEYCODE_DPAD_DOWN); // non-standard shifted arrow down
        TERMCAP_TO_KEYCODE.put("kF", KEYMOD_SHIFT | KEYCODE_DPAD_DOWN); // terminfo=kind, scroll-forward key
        TERMCAP_TO_KEYCODE.put("kI", KEYCODE_INSERT);
        TERMCAP_TO_KEYCODE.put("kP", KEYCODE_PAGE_UP);
        TERMCAP_TO_KEYCODE.put("kN", KEYCODE_PAGE_DOWN);
        TERMCAP_TO_KEYCODE.put("kR", KEYMOD_SHIFT | KEYCODE_DPAD_UP); // terminfo=kri, scroll-backward key
        TERMCAP_TO_KEYCODE.put("kUP", KEYMOD_SHIFT | KEYCODE_DPAD_UP); // non-standard shifted up

        TERMCAP_TO_KEYCODE.put("@7", KEYCODE_MOVE_END);
        TERMCAP_TO_KEYCODE.put("@8", KEYCODE_NUMPAD_ENTER);
    }

    static String getCodeFromTermcap(String termcap, boolean cursorKeysApplication, boolean keypadApplication) {
        Integer keyCodeAndMod = TERMCAP_TO_KEYCODE.get(termcap);
        if (keyCodeAndMod == null) return null;
        int keyCode = keyCodeAndMod;
        int keyMod = 0;
        if ((keyCode & KEYMOD_SHIFT) != 0) {
            keyMod |= KEYMOD_SHIFT;
            keyCode &= ~KEYMOD_SHIFT;
        }
        if ((keyCode & KEYMOD_CTRL) != 0) {
            keyMod |= KEYMOD_CTRL;
            keyCode &= ~KEYMOD_CTRL;
        }
        if ((keyCode & KEYMOD_ALT) != 0) {
            keyMod |= KEYMOD_ALT;
            keyCode &= ~KEYMOD_ALT;
        }
        if ((keyCode & KEYMOD_NUM_LOCK) != 0) {
            keyMod |= KEYMOD_NUM_LOCK;
            keyCode &= ~KEYMOD_NUM_LOCK;
        }
        return getCode(keyCode, keyMod, cursorKeysApplication, keypadApplication);
    }

    public static String getCode(int keyCode, int keyMode, boolean cursorApp, boolean keypadApplication) {
        boolean numLockOn = (keyMode & KEYMOD_NUM_LOCK) != 0;
        keyMode &= ~KEYMOD_NUM_LOCK;
        switch (keyCode) {
            case KEYCODE_DPAD_CENTER:
                return "\015";

            case KEYCODE_DPAD_UP:
                return (keyMode == 0) ? (cursorApp ? "\033OA" : "\033[A") : transformForModifiers("\033[1", keyMode, 'A');
            case KEYCODE_DPAD_DOWN:
                return (keyMode == 0) ? (cursorApp ? "\033OB" : "\033[B") : transformForModifiers("\033[1", keyMode, 'B');
            case KEYCODE_DPAD_RIGHT:
                return (keyMode == 0) ? (cursorApp ? "\033OC" : "\033[C") : transformForModifiers("\033[1", keyMode, 'C');
            case KEYCODE_DPAD_LEFT:
                return (keyMode == 0) ? (cursorApp ? "\033OD" : "\033[D") : transformForModifiers("\033[1", keyMode, 'D');

            case KEYCODE_MOVE_HOME:
                // Note that KEYCODE_HOME is handled by the system and never delivered to applications.
                // On a Logitech k810 keyboard KEYCODE_MOVE_HOME is sent by FN+LeftArrow.
                return (keyMode == 0) ? (cursorApp ? "\033OH" : "\033[H") : transformForModifiers("\033[1", keyMode, 'H');
            case KEYCODE_MOVE_END:
                return (keyMode == 0) ? (cursorApp ? "\033OF" : "\033[F") : transformForModifiers("\033[1", keyMode, 'F');

            // An xterm can send function keys F1 to F4 in two modes: vt100 compatible or
            // not. Because Vim may not know what the xterm is sending, both types of keys
            // are recognized. The same happens for the <Home> and <End> keys.
            // normal vt100 ~
            // <F1> t_k1 <Esc>[11~ <xF1> <Esc>OP *<xF1>-xterm*
            // <F2> t_k2 <Esc>[12~ <xF2> <Esc>OQ *<xF2>-xterm*
            // <F3> t_k3 <Esc>[13~ <xF3> <Esc>OR *<xF3>-xterm*
            // <F4> t_k4 <Esc>[14~ <xF4> <Esc>OS *<xF4>-xterm*
            // <Home> t_kh <Esc>[7~ <xHome> <Esc>OH *<xHome>-xterm*
            // <End> t_@7 <Esc>[4~ <xEnd> <Esc>OF *<xEnd>-xterm*
            case KEYCODE_F1:
                return (keyMode == 0) ? "\033OP" : transformForModifiers("\033[1", keyMode, 'P');
            case KEYCODE_F2:
                return (keyMode == 0) ? "\033OQ" : transformForModifiers("\033[1", keyMode, 'Q');
            case KEYCODE_F3:
                return (keyMode == 0) ? "\033OR" : transformForModifiers("\033[1", keyMode, 'R');
            case KEYCODE_F4:
                return (keyMode == 0) ? "\033OS" : transformForModifiers("\033[1", keyMode, 'S');
            case KEYCODE_F5:
                return transformForModifiers("\033[15", keyMode, '~');
            case KEYCODE_F6:
                return transformForModifiers("\033[17", keyMode, '~');
            case KEYCODE_F7:
                return transformForModifiers("\033[18", keyMode, '~');
            case KEYCODE_F8:
                return transformForModifiers("\033[19", keyMode, '~');
            case KEYCODE_F9:
                return transformForModifiers("\033[20", keyMode, '~');
            case KEYCODE_F10:
                return transformForModifiers("\033[21", keyMode, '~');
            case KEYCODE_F11:
                return transformForModifiers("\033[23", keyMode, '~');
            case KEYCODE_F12:
                return transformForModifiers("\033[24", keyMode, '~');

            case KEYCODE_SYSRQ:
                return "\033[32~"; // Sys Request / Print
            // Is this Scroll lock? case Cancel: return "\033[33~";
            case KEYCODE_BREAK:
                return "\033[34~"; // Pause/Break

            case KEYCODE_ESCAPE:
            case KEYCODE_BACK:
                return "\033";

            case KEYCODE_INSERT:
                return transformForModifiers("\033[2", keyMode, '~');
            case KEYCODE_FORWARD_DEL:
                return transformForModifiers("\033[3", keyMode, '~');

            case KEYCODE_PAGE_UP:
                return transformForModifiers("\033[5", keyMode, '~');
            case KEYCODE_PAGE_DOWN:
                return transformForModifiers("\033[6", keyMode, '~');
            case KEYCODE_DEL:
                String prefix = ((keyMode & KEYMOD_ALT) == 0) ? "" : "\033";
                // Just do what xterm and gnome-terminal does:
                return prefix + (((keyMode & KEYMOD_CTRL) == 0) ? "\u007F" : "\u0008");
            case KEYCODE_NUM_LOCK:
                if (keypadApplication) {
                    return "\033OP";
                } else {
                    return null;
                }
            case KEYCODE_SPACE:
                // If ctrl is not down, return null so that it goes through normal input processing (which may e.g. cause a
                // combining accent to be written):
                return ((keyMode & KEYMOD_CTRL) == 0) ? null : "\0";
            case KEYCODE_TAB:
                // This is back-tab when shifted:
                return (keyMode & KEYMOD_SHIFT) == 0 ? "\011" : "\033[Z";
            case KEYCODE_ENTER:
                return ((keyMode & KEYMOD_ALT) == 0) ? "\r" : "\033\r";

            case KEYCODE_NUMPAD_ENTER:
                return keypadApplication ? transformForModifiers("\033O", keyMode, 'M') : "\n";
            case KEYCODE_NUMPAD_MULTIPLY:
                return keypadApplication ? transformForModifiers("\033O", keyMode, 'j') : "*";
            case KEYCODE_NUMPAD_ADD:
                return keypadApplication ? transformForModifiers("\033O", keyMode, 'k') : "+";
            case KEYCODE_NUMPAD_COMMA:
                return ",";
            case KEYCODE_NUMPAD_DOT:
                if (numLockOn) {
                    return keypadApplication ? "\033On" : ".";
                } else {
                    // DELETE
                    return transformForModifiers("\033[3", keyMode, '~');
                }
            case KEYCODE_NUMPAD_SUBTRACT:
                return keypadApplication ? transformForModifiers("\033O", keyMode, 'm') : "-";
            case KEYCODE_NUMPAD_DIVIDE:
                return keypadApplication ? transformForModifiers("\033O", keyMode, 'o') : "/";
            case KEYCODE_NUMPAD_0:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 'p') : "0";
                } else {
                    // INSERT
                    return transformForModifiers("\033[2", keyMode, '~');
                }
            case KEYCODE_NUMPAD_1:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 'q') : "1";
                } else {
                    // END
                    return (keyMode == 0) ? (cursorApp ? "\033OF" : "\033[F") : transformForModifiers("\033[1", keyMode, 'F');
                }
            case KEYCODE_NUMPAD_2:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 'r') : "2";
                } else {
                    // DOWN
                    return (keyMode == 0) ? (cursorApp ? "\033OB" : "\033[B") : transformForModifiers("\033[1", keyMode, 'B');
                }
            case KEYCODE_NUMPAD_3:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 's') : "3";
                } else {
                    // PGDN
                    return "\033[6~";
                }
            case KEYCODE_NUMPAD_4:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 't') : "4";
                } else {
                    // LEFT
                    return (keyMode == 0) ? (cursorApp ? "\033OD" : "\033[D") : transformForModifiers("\033[1", keyMode, 'D');
                }
            case KEYCODE_NUMPAD_5:
                return keypadApplication ? transformForModifiers("\033O", keyMode, 'u') : "5";
            case KEYCODE_NUMPAD_6:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 'v') : "6";
                } else {
                    // RIGHT
                    return (keyMode == 0) ? (cursorApp ? "\033OC" : "\033[C") : transformForModifiers("\033[1", keyMode, 'C');
                }
            case KEYCODE_NUMPAD_7:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 'w') : "7";
                } else {
                    // HOME
                    return (keyMode == 0) ? (cursorApp ? "\033OH" : "\033[H") : transformForModifiers("\033[1", keyMode, 'H');
                }
            case KEYCODE_NUMPAD_8:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 'x') : "8";
                } else {
                    // UP
                    return (keyMode == 0) ? (cursorApp ? "\033OA" : "\033[A") : transformForModifiers("\033[1", keyMode, 'A');
                }
            case KEYCODE_NUMPAD_9:
                if (numLockOn) {
                    return keypadApplication ? transformForModifiers("\033O", keyMode, 'y') : "9";
                } else {
                    // PGUP
                    return "\033[5~";
                }
            case KEYCODE_NUMPAD_EQUALS:
                return keypadApplication ? transformForModifiers("\033O", keyMode, 'X') : "=";
        }

        return null;
    }

    private static String transformForModifiers(String start, int keymod, char lastChar) {
        int modifier;
        switch (keymod) {
            case KEYMOD_SHIFT:
                modifier = 2;
                break;
            case KEYMOD_ALT:
                modifier = 3;
                break;
            case (KEYMOD_SHIFT | KEYMOD_ALT):
                modifier = 4;
                break;
            case KEYMOD_CTRL:
                modifier = 5;
                break;
            case KEYMOD_SHIFT | KEYMOD_CTRL:
                modifier = 6;
                break;
            case KEYMOD_ALT | KEYMOD_CTRL:
                modifier = 7;
                break;
            case KEYMOD_SHIFT | KEYMOD_ALT | KEYMOD_CTRL:
                modifier = 8;
                break;
            default:
                return start + lastChar;
        }
        return start + (";" + modifier) + lastChar;
    }
}
