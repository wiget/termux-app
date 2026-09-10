# Kitty keyboard protocol

Termux supports application-negotiated keyboard reporting using the [Kitty keyboard protocol](https://sw.kovidgoyal.net/kitty/keyboard-protocol/). Applications enable the required progressive enhancements with terminal control sequences; no Termux preference is required.

With no enhancements enabled, keyboard input retains its legacy behavior, including cursor/keypad application modes, Ctrl transformations, Alt-prefixed text, and application shortcuts.

For verification procedures and recordable results, see the [manual test runbook](kitty-keyboard-manual-tests.md) and [results template](kitty-keyboard-test-results-template.md).

## Negotiation and state

`CSI` denotes the bytes `ESC [` (`1b 5b` in hexadecimal). Spaces in sequence notation are for readability and are not transmitted.

| Command | Effect |
| --- | --- |
| `CSI ? u` | Query active flags; reply `CSI ? flags u` |
| `CSI = flags ; 1 u` | Replace active flags; omitted mode defaults to 1 |
| `CSI = flags ; 2 u` | Set the specified bits, preserving other bits |
| `CSI = flags ; 3 u` | Clear the specified bits, preserving other bits |
| `CSI > flags u` | Save current flags and activate the supplied flags; omitted flags mean 0 |
| `CSI < count u` | Restore saved flags; omitted count means 1 and count 0 is a no-op |

Only the five defined enhancement bits are retained. Invalid operation modes and malformed commands leave state unchanged. Decimal arguments exceeding a signed 32-bit integer are consumed and ignored rather than wrapped. Valid large pop counts underflow safely to zero.

The main and alternate screens each maintain independent active flags and a 32-entry stack. Stack overflow evicts the oldest saved entry; underflow resets flags to zero. Screen switching preserves both states. RIS (`ESC c`), DECSTR (`CSI ! p`), and the terminal Reset action clear flags and stacks for both screens.

## Progressive enhancements

| Flag | Value | Behavior |
| --- | ---: | --- |
| Disambiguate escape codes | 1 | Encode Escape, ambiguous modified keys, and non-text keypad keys unambiguously |
| Report event types | 2 | Report repeat and release events for eligible keys |
| Report alternate keys | 4 | Include the shifted key when Shift is active and Android supplies it |
| Report all keys | 8 | Encode text-producing and modifier keys as escape sequences |
| Report associated text | 16 | Include generated text when flag 8 is also active |

Flags 4 and 16 alone do not force text into escape sequences. Associated-text reporting without flag 8 has no defined protocol behavior; Termux does not enable all-key reporting implicitly.

Without flag 8, unmodified Enter, Tab, and Backspace (including lock-only modifiers) retain CR, HT, and DEL output so recovery commands remain usable. Their releases are suppressed. Modified presses use negotiated encoding.

## Event encoding

The general form is:

```text
CSI unicode-key-code:alternate-key-codes ; modifiers:event-type ; text-as-codepoints u
```

Optional fields are omitted when possible. The unshifted key is the primary identity. A shifted alternate is included only when Shift is active and the value is available. The base-layout alternate is omitted because Android does not reliably expose the required PC-101 layout identity.

Event types are press (1), repeat (2), and release (3). Press type 1 is omitted; requested repeats and releases use `:2` and `:3`. Associated text is never attached to a release, excludes C0/C1 controls, and consists of valid Unicode scalar values separated by colons. A supplementary character is represented by one code point, not two surrogate values.

Modifiers use `1 + bitmask`:

| Reported modifier | Bit |
| --- | ---: |
| Shift | 1 |
| Alt | 2 |
| Ctrl | 4 |
| Android Meta / Kitty Super | 8 |
| Caps Lock | 64 |
| Num Lock | 128 |

Android does not generally expose separate Kitty Hyper or Meta modifiers. Termux does not synthesize them. Modifier-key events reflect the state after the current transition, including the case where the opposite-side modifier remains held.

Functional keys use the protocol's specified trailers: arrows use `CSI A/B/C/D`, editing keys use their letter or `~` forms, and F3 uses `CSI 13 ~` to avoid the cursor-position-report conflict with `CSI R`.

Examples with lock modifiers off:

| Input / flags | Encoded bytes |
| --- | --- |
| Escape / 1 | `\x1b[27u` |
| Shift+Enter / 1 | `\x1b[13;2u` |
| Ctrl+Enter / 1 | `\x1b[13;5u` |
| Ctrl+I / 1 | `\x1b[105;5u` |
| Unmodified Enter, Tab, Backspace / 1 | `\r`, `\t`, `\x7f` |
| `a` / 31 | `\x1b[97;;97u` |
| Shift+A / 31 | `\x1b[97:65;2;65u` |
| Up repeat / 3 | `\x1b[1;1:2A` |
| Left Shift release / 31 | `\x1b[57441;1:3u` |
| Text-only IME commit U+0065 U+0301 U+1F600 / 24 | `\x1b[0;;101:769:128512u` |

`CSI 27 u` and `CSI 27 ; 1 u` are equivalent representations of an unmodified Escape press: the omitted modifier defaults to 1. Termux uses the compact form.

## Android input

### Functional keys and delivery

Mappings cover Escape/Back, Enter/DPAD Center, Tab, Backspace, arrows, editing keys, F1–F12, locks, SysRq/Print Screen, Pause/Break, Menu, and keypad digits/operators. F13–F24 use the key constants exposed by Android API 36; delivery depends on the platform and keyboard.

With Num Lock off, keypad digits and decimal have dedicated keypad navigation, Insert, Delete, and Begin identities. Event reporting alone does not implicitly request keypad disambiguation.

Media play/pause/play-pause/stop/next/previous/rewind/fast-forward/record and volume up/down/mute are mapped when delivered. Android or the application client can intercept these events before terminal dispatch. The microphone-mute key is not treated as speaker mute.

Application shortcuts retain precedence, including Termux's Shift+Page Up/Down scrollback action. System-reserved keys are delegated to Android. Key-code mappings do not guarantee that an ordinary Android application can receive every listed key.

### Layouts and event lifecycle

Unshifted identity is looked up without Shift/Caps Lock and without terminal-consumed Ctrl/Alt/Super. Right Alt remains in the character-map lookup for AltGr composition; a consumed AltGr modifier is excluded from the resulting text event. Dead-key composition is processed before generating associated text. Android KCM function mappings take precedence when they produce characters.

Releases are paired by device and physical key code. Android's `KeyEvent.getDownTime()` is device-wide and is not a reliable per-key identifier when keys overlap. Pending events are bounded, isolated between devices, and cleared on session changes and loss of window focus. Client-consumed, system-delegated, and canceled events do not produce terminal releases.

### Software keyboards and IME commits

Text-only IME commits provide resulting text without a physical key identity or press/repeat/release lifecycle. Termux does not invent repeat or release events for `commitText()`.

- In legacy and disambiguation-only modes, normal committed text and existing IME control-character conventions are retained.
- With flags 8 and 16 together, printable commits are encoded with key code 0. Multi-code-point text, combining characters, and surrogate pairs remain together at the commit boundary. Control characters and application shortcuts create separate boundaries. Newline/Enter, Tab, Backspace, and Escape commits use their semantic control-key encodings in this mode.
- With flag 8 but without flag 16, text-only commits fall back to UTF-8 so a software keyboard remains usable when there is no physical identity to report.

Composing-text handling and clearing of the editable buffer are retained; a commit does not generate an additional synthetic key event. Timing-based de-duplication is not used because identical key events and commits can represent legitimate separate input. IMEs that independently issue both callbacks for the same text require device-specific verification. Termux's clipboard paste action is a separate input path and does not establish IME commit behavior.

## Library interfaces

- `TerminalEmulator` owns negotiation and per-screen state. `getKittyKeyboardFlags()` exposes the active flags; stack mutation remains in the parser.
- `KeyHandler.getCode(int, int, boolean, boolean)` remains the legacy encoder. The `KittyKeyEvent` overload accepts extracted key identity, modifiers, text, and event type for negotiated encoding. A null result requests fallback processing; an empty result consumes the event without output.
- `TerminalView` handles Android metadata, composition, shortcut precedence, and event dispatch. `KittyKeyboardInput` contains independently testable metadata conversion and release pairing.
- `TerminalOutput.write(String)` treats null and empty strings as no-ops, so suppressed keyboard events do not reach the byte queue as zero-length writes.

Callers that deliberately write bytes through `TerminalSession`, including client-defined remappings, remain responsible for those bytes. The legacy encoder API does not implicitly negotiate keyboard modes.

## Application configuration

Modern Fish and Neovim negotiate supported keyboard protocols automatically. No application binding is required merely to activate negotiation; bindings determine which actions the decoded keys perform. Use clean profiles when testing, then repeat with the normal configuration.

Keep the terminal identity accurate: Termux normally uses `TERM=xterm-256color`, and applications in tmux normally see `TERM=tmux-256color`. Changing the terminal name to `xterm-kitty` is not a protocol activation mechanism.

### tmux

tmux's extended-key translation is not full Kitty keyboard protocol support. Its normal `extkeys` capabilities enable xterm's modifyOtherKeys mode, which Termux does not implement. A tmux server used with Termux clients can instead enable Kitty disambiguation on the outer terminal and translate modified presses for its panes.

For clients whose actual outer terminal identity is `xterm-256color`, use:

```tmux
set -g default-terminal tmux-256color
set -s extended-keys on
set -s extended-keys-format csi-u
set -as terminal-features 'xterm-256color:extkeys'
set -as terminal-overrides ',xterm-256color:Eneks=\E[=1u:Dseks=\E[=0u'

# Compatibility with parsers that require an explicit CSI-u modifier field.
set -s user-keys[0] "\e[27u"
bind-key -n User0 send-keys Escape
```

The terminal overrides are specific to Termux's capabilities. Scope them to the intended clients or use a dedicated test server when other terminals share the same `TERM` name. Choose an unused `user-keys` index and corresponding `UserN` binding if slot 0 is already allocated.

`Eneks` and `Dseks` assign flags 1 and 0, respectively. Assignments are idempotent when tmux refreshes capabilities repeatedly. Enabling all five flags toward tmux is not equivalent to enabling its supported extended-key translation.

The Escape mapping addresses [tmux 3.6's extended-key parser](https://github.com/tmux/tmux/blob/3.6/tty-keys.c), which requires `CSI key ; modifier u` and does not recognize the valid compact `CSI 27 u`. Without the mapping, the sequence may be interpreted as multiple keys, including an undo command after an editor leaves Insert mode. The mapping translates the entire sequence to one Escape key. It does not implement the rest of the Kitty protocol inside tmux.

Reload configuration with `tmux source-file ~/.tmux.conf` in the affected server. Terminal capability changes require detaching and reattaching the client; restarting applications ensures they negotiate against the current environment. Check effective settings, not just the configuration file:

```sh
tmux show-options -s extended-keys
tmux show-options -s extended-keys-format
tmux show-options -s terminal-overrides
tmux display-message -p 'server=#{version} outer=#{client_termname}'
```

Function/keypad translation and advanced event fields depend on the multiplexer version. Verify the required application bindings on the selected version; do not infer full flag-stack, release, alternate-key, or associated-text support from working modified Enter.

## Verification tools

Run the configured unit tests and builds:

```sh
./gradlew :terminal-emulator:test
./gradlew :terminal-view:assembleDebug
./gradlew :app:assembleDebug
./gradlew test
```

Emulator tests cover negotiation, stack bounds/isolation/reset, malformed input, legacy regression output, encoding, Unicode, and queue-backed output. View tests cover pure Android metadata conversion and event pairing; they do not replace physical Android dispatch and IME testing. Record exact tasks and per-variant counts with the tested revision.

The [manual test runbook](kitty-keyboard-manual-tests.md) provides device, SSH, application, and multiplexer cases, with a [results template](kitty-keyboard-test-results-template.md) for evidence.

`kitty-keyboard-check.sh` performs negotiation assertions and optionally records raw input as hex. Run it in a disposable direct terminal/SSH session with Bash and coreutils, keeping standard output attached to the tested terminal. It changes screen and keyboard state, uses raw/no-echo input, and restores terminal settings and legacy keyboard flags on normal exit. It is not a full-protocol test for a multiplexer that lacks Kitty negotiation support.

`kitty-tmux-escape-check.py` exercises compact Escape through real local tmux and clean Neovim in disposable PTYs. It compares compact and explicit-modifier Escape, mapping startup/live reload, and modified-Enter actions. It requires Python 3, tmux, and Neovim on a POSIX host. Its synthetic terminal peer does not access an Android device or existing tmux sessions and is not a complete terminal emulator.

## References

- [Kitty keyboard protocol specification](https://sw.kovidgoyal.net/kitty/keyboard-protocol/)
- [Kitty reference encoder](https://github.com/kovidgoyal/kitty/blob/master/kitty/key_encoding.c)
- [Android KeyEvent API](https://developer.android.com/reference/android/view/KeyEvent)
- [Fish key reader](https://fishshell.com/docs/current/cmds/fish_key_reader.html)
- [Neovim terminal input](https://neovim.io/doc/user/tui/#tui-input)
- [tmux 3.7b terminal feature definitions](https://github.com/tmux/tmux/blob/3.7b/tty-features.c)
