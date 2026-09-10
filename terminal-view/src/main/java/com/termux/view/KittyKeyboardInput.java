package com.termux.view;

import android.view.KeyCharacterMap;

import com.termux.terminal.KeyHandler;

import java.util.HashMap;
import java.util.Map;

import static android.view.KeyEvent.*;
import static com.termux.terminal.KeyHandler.*;

/** Pure Android metadata conversion and press/release pairing, independently testable without a View. */
final class KittyKeyboardInput {
    private final Map<Long, Press> presses = new HashMap<>();

    private static final class Press {
        final KittyKeyEvent event;
        final boolean alternateScreen;

        Press(KittyKeyEvent event, boolean alternateScreen) {
            this.event = event;
            this.alternateScreen = alternateScreen;
        }
    }

    void clear() {
        presses.clear();
    }

    void forget(int device, int keyCode) {
        presses.remove(keyId(device, keyCode));
    }

    void pressed(int device, int physicalKeyCode, boolean alternateScreen, KittyKeyEvent event, int flags) {
        KittyKeyEvent release = new KittyKeyEvent(event.keyCode, event.keyMode, event.codePoint, event.shiftedCodePoint, event.text, KITTY_RELEASE);
        String code = KeyHandler.getCode(release, flags, false, false);
        if (code == null || code.isEmpty()) return;
        // Bound retention even for IMEs/devices that only send ACTION_DOWN.
        if (presses.size() >= 128) presses.clear();
        presses.put(keyId(device, physicalKeyCode), new Press(event, alternateScreen));
    }

    KittyKeyEvent released(int device, int keyCode, boolean alternateScreen, int keyMode) {
        Press press = presses.remove(keyId(device, keyCode));
        // Android downTime is the most recent down on the device, not a per-key identifier.
        // In a chord the modifier release may therefore have the other key's downTime.
        if (press == null || press.alternateScreen != alternateScreen) return null;
        KittyKeyEvent event = press.event;
        return new KittyKeyEvent(event.keyCode, keyMode, event.codePoint, event.shiftedCodePoint, null, KITTY_RELEASE);
    }

    private static long keyId(int device, int keyCode) {
        return ((long) device << 32) | (keyCode & 0xffffffffL);
    }

    static int eventType(int action, int repeatCount) {
        return action == ACTION_UP ? KITTY_RELEASE : repeatCount > 0 ? KITTY_REPEAT : KITTY_PRESS;
    }

    static int controlKey(int codePoint) {
        switch (codePoint) {
            case '\r': case '\n': return KEYCODE_ENTER;
            case '\t': return KEYCODE_TAB;
            case 8: case 127: return KEYCODE_DEL;
            case 27: return KEYCODE_ESCAPE;
            default: return KEYCODE_UNKNOWN;
        }
    }

    static int committedControlKeyMode(boolean ctrl, boolean alt, boolean shift) {
        return (ctrl ? KEYMOD_CTRL : 0) | (alt ? KEYMOD_ALT : 0) | (shift ? KEYMOD_SHIFT : 0);
    }

    static int textMetaState(int meta, boolean shift, boolean fn) {
        meta &= ~(META_CTRL_MASK | META_META_MASK);
        if ((meta & META_ALT_RIGHT_ON) == 0) meta &= ~META_ALT_MASK;
        if (shift) meta |= META_SHIFT_ON;
        if (fn) meta |= META_FUNCTION_ON;
        return meta;
    }

    static int unshiftedMetaState(int meta) {
        return meta & ~(META_SHIFT_MASK | META_CAPS_LOCK_ON);
    }

    static int keyMode(int meta, int keyCode, boolean down, boolean ctrl, boolean alt, boolean shift, boolean altGr) {
        // Android may report modifier state before the current modifier key's transition.
        int side = 0, mask = 0, aggregate = 0;
        switch (keyCode) {
            case KEYCODE_SHIFT_LEFT: side = META_SHIFT_LEFT_ON; mask = META_SHIFT_MASK; aggregate = META_SHIFT_ON; break;
            case KEYCODE_SHIFT_RIGHT: side = META_SHIFT_RIGHT_ON; mask = META_SHIFT_MASK; aggregate = META_SHIFT_ON; break;
            case KEYCODE_CTRL_LEFT: side = META_CTRL_LEFT_ON; mask = META_CTRL_MASK; aggregate = META_CTRL_ON; break;
            case KEYCODE_CTRL_RIGHT: side = META_CTRL_RIGHT_ON; mask = META_CTRL_MASK; aggregate = META_CTRL_ON; break;
            case KEYCODE_ALT_LEFT: side = META_ALT_LEFT_ON; mask = META_ALT_MASK; aggregate = META_ALT_ON; break;
            case KEYCODE_ALT_RIGHT: side = META_ALT_RIGHT_ON; mask = META_ALT_MASK; aggregate = META_ALT_ON; break;
            case KEYCODE_META_LEFT: side = META_META_LEFT_ON; mask = META_META_MASK; aggregate = META_META_ON; break;
            case KEYCODE_META_RIGHT: side = META_META_RIGHT_ON; mask = META_META_MASK; aggregate = META_META_ON; break;
        }
        if (side != 0) {
            meta = down ? meta | side : meta & ~side;
            meta &= ~aggregate;
            if ((meta & mask) != 0) meta |= aggregate;
        }
        int mode = 0;
        if (ctrl || (meta & META_CTRL_MASK) != 0) mode |= KEYMOD_CTRL;
        if (alt || (meta & (altGr ? META_ALT_LEFT_ON : META_ALT_MASK)) != 0) mode |= KEYMOD_ALT;
        if (shift || (meta & META_SHIFT_MASK) != 0) mode |= KEYMOD_SHIFT;
        if ((meta & META_META_MASK) != 0) mode |= KEYMOD_SUPER;
        if ((meta & META_CAPS_LOCK_ON) != 0) mode |= KEYMOD_CAPS_LOCK;
        if ((meta & META_NUM_LOCK_ON) != 0) mode |= KEYMOD_NUM_LOCK;
        return mode;
    }

    static int keyCodePoint(int unicode) {
        unicode &= KeyCharacterMap.COMBINING_ACCENT_MASK;
        return Character.isValidCodePoint(unicode) && (unicode < 0xd800 || unicode > 0xdfff) ? unicode : 0;
    }

    static String text(int unicode) {
        return unicode != 0 && (unicode & KeyCharacterMap.COMBINING_ACCENT) == 0 && keyCodePoint(unicode) != 0
            && !Character.isISOControl(unicode) ? new String(Character.toChars(unicode)) : null;
    }
}
