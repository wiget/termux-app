package com.termux.terminal;

import junit.framework.TestCase;

import java.nio.charset.StandardCharsets;

import static android.view.KeyEvent.*;
import static com.termux.terminal.KeyHandler.*;

public class TerminalOutputTest extends TestCase {
    /** Use the same queue as a live session, which rejects zero-length writes. */
    private static final class QueueTerminalOutput extends TerminalTestCase.MockTerminalOutput {
        final ByteQueue queue = new ByteQueue(1024);

        @Override
        public void write(byte[] data, int offset, int count) {
            queue.write(data, offset, count);
        }
    }

    public void testEmptyAndNullOutputAreNoOps() {
        QueueTerminalOutput output = new QueueTerminalOutput();
        output.write((String) null);
        output.write("");
        assertEquals(0, output.queue.read(new byte[32], false));
    }

    public void testSuppressedKittyEventsDoNotReachSessionQueue() {
        QueueTerminalOutput output = new QueueTerminalOutput();
        int[] modifiers = {KEYCODE_SHIFT_LEFT, KEYCODE_SHIFT_RIGHT, KEYCODE_CTRL_LEFT, KEYCODE_CTRL_RIGHT,
            KEYCODE_ALT_LEFT, KEYCODE_ALT_RIGHT, KEYCODE_META_LEFT, KEYCODE_META_RIGHT};
        for (int flags : new int[]{1, 3, 5}) { // Disambiguation, Neovim, and Fish modes.
            for (int key : modifiers) {
                for (int type = KITTY_PRESS; type <= KITTY_RELEASE; type++) {
                    String code = getCode(new KittyKeyEvent(key, 0, 0, 0, null, type), flags, false, false);
                    assertEquals("", code);
                    output.write(code);
                }
            }
            output.write(getCode(new KittyKeyEvent(KEYCODE_ENTER, 0, 0, 0, null, KITTY_RELEASE), flags, false, false));
        }
        assertEquals(0, output.queue.read(new byte[32], false));
    }

    public void testNonemptyOutputPreservesUtf8NulAndKittyEvents() {
        QueueTerminalOutput output = new QueueTerminalOutput();
        String text = "\0\r\t\u0142\ud83d\ude00";
        String press = getCode(new KittyKeyEvent(KEYCODE_ENTER, KEYMOD_CTRL, 0, 0, null, KITTY_PRESS), 3, false, false);
        assertEquals("\033[13;5u", press);
        output.write(text);
        output.write("");
        output.write(press);
        byte[] actual = new byte[1024];
        int count = output.queue.read(actual, false);
        assertEquals(text + press, new String(actual, 0, count, StandardCharsets.UTF_8));
    }
}
