package com.termux.terminal;

public class KittyKeyboardProtocolTest extends TerminalTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        withTerminalSized(20, 3);
    }

    private void flags(int expected) {
        assertEquals(expected, mTerminal.getKittyKeyboardFlags());
        assertEnteringStringGivesResponse("\033[?u", "\033[?" + expected + "u");
    }

    public void testQueryAndOperations() {
        flags(0);
        enterString("\033[=5u"); flags(5);
        enterString("\033[=10;2u"); flags(15);
        enterString("\033[=6;3u"); flags(9);
        enterString("\033[=16;1u"); flags(16);
        enterString("\033[=65537u"); flags(1);
        enterString("\033[=31;4u\033[=31;0u"); flags(1);
        enterString("\033[=u"); flags(0);
    }

    public void testStackAndDefaults() {
        enterString("\033[=4u\033[>1u\033[>3u\033[>7u"); flags(7);
        enterString("\033[<2u"); flags(1);
        enterString("\033[<u"); flags(4);
        enterString("\033[>u"); flags(0);
        enterString("\033[<0u"); flags(0);
        enterString("\033[<u"); flags(4);
        enterString("\033[<u"); flags(0);
        enterString("\033[>3u\033[<2147483647u"); flags(0);
    }

    public void testBoundedStackEvictsOldest() {
        enterString("\033[=16u");
        for (int i = 0; i < 33; i++) enterString("\033[>" + (i % 16) + "u");
        enterString("\033[<32u"); flags(0); // Oldest retained value, from first push.
        enterString("\033[<u"); flags(0);
    }

    public void testScreenIsolationAndReset() {
        for (String reset : new String[]{"\033c", "\033[!p"}) {
            enterString("\033[>1u\033[?1049h"); flags(0);
            enterString("\033[>8u\033[>31u\033[?1049l"); flags(1);
            enterString("\033[?1049h"); flags(31);
            enterString("\033[<u"); flags(8);
            enterString(reset); flags(0);
            enterString("\033[?1049l"); flags(0);
            enterString("\033[<u\033[?1049h\033[<u"); flags(0);
            enterString("\033[?1049l");
        }
    }

    public void testMalformedAndOversizedCommandsAreConsumed() {
        enterString("\033[=8u");
        for (String bad : new String[]{"=1:2u", "=1;2;3u", ">1;2u", "<1:2u", "?1u",
            "=999999999999999999999999u", ">999999999999999999999u", "=1$u", "=1?5u", "1=5u"}) {
            enterString("\033[" + bad); flags(8);
        }
        enterString("\033[=3\033[?u");
        assertEquals("\033[?8u", mOutput.getOutputAndClear());
        enterString("\033[=3\030\033[?u");
        assertEquals("\033[?8u", mOutput.getOutputAndClear());
        enterString("\033[>4;2m\033[=5x\033[<1x"); flags(8);
        assertEnteringStringGivesResponse("\033[>c", "\033[>41;320;0c");
        assertEnteringStringGivesResponse("\033[6n", "\033[1;1R");
        assertCursorAt(0, 0);
        enterString("ok"); assertLineStartsWith(0, 'o', 'k');
    }
}
