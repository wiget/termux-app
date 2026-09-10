# Kitty keyboard protocol: manual test runbook

## Purpose

Use this runbook to verify keyboard behavior on an Android device and record reproducible results. The [protocol reference](kitty-keyboard-protocol.md) defines supported behavior and limitations. Record observations in a copy of the [results template](kitty-keyboard-test-results-template.md); do not fill the distributed template with results from a particular device.

Test both raw protocol output and application behavior. A key reader recognizing a key does not prove that an editor preserves its buffer, and working editor bindings do not prove support for every progressive-enhancement flag.

## 1. Define a test run

Use one report per input path and configuration profile. Reuse the campaign/run ID when comparing paths; start a new report when the APK, keyboard/layout, IME, application versions, or relevant configuration changes.

| Path ID | Input path | Raw negotiation tests | Application tests |
| --- | --- | --- | --- |
| L | Android keyboard → Termux → local application | Yes | Yes |
| S | Android keyboard → Termux → SSH → remote application | Yes | Yes |
| M | Android keyboard → Termux → local tmux → application | Not a full Kitty conformance target | Yes |
| SM | Android keyboard → Termux → SSH → remote tmux → application | Not a full Kitty conformance target | Yes |

Run L before S, then M/SM as applicable. For M/SM, complete MUX-01 before the application cases. For a failure, repeat the same application and keys on the nearest path without a multiplexer. Distinguish a clean application profile from the normal user profile in separate reports.

### Result values

- **PASS:** all stated expectations for the case were observed; cite evidence.
- **FAIL:** at least one expectation was violated; record the exact input and actual result.
- **BLOCKED:** a prerequisite prevented execution or verification, such as a missing application or unobservable IME callback.
- **N/A:** the case does not apply to this path/hardware; give a reason.
- **NOT RUN:** no result has been collected.

Do not count BLOCKED, N/A, or NOT RUN as passing. A known multiplexer limitation can explain a FAIL without making it a Termux failure. If a case includes several subchecks, retain their individual observations and fail the case when any applicable required subcheck fails.

## 2. Prepare the environment and evidence directory

Prerequisites:

- An identified Termux APK and its source revision, including whether the source was modified when built.
- A physical USB or Bluetooth keyboard. Record its model and Android layout name.
- Bash and coreutils (`stty`, `timeout`, `dd`, `od`) for the raw recorder.
- Fish 4.x for the named-key binding examples and a Neovim release supporting extended keyboard input.
- SSH/tmux and any additional applications required by the selected path.
- Copies of `kitty-keyboard-check.sh` and the results template on the host where the commands will run. The local tmux reproducer additionally needs Python 3 and a POSIX host.

Use a disposable terminal session and an empty editor buffer. The recorder deliberately resets keyboard state and switches screens. It must run in the terminal being tested, not an unrelated `adb shell`, and its standard output must remain connected to that terminal.

Run the setup commands in **Bash**. From Fish, `bash --noprofile --norc` starts a suitable temporary shell. Replace `DOCS` with the directory containing the test tools; change `PATH_ID` for each path:

```bash
DOCS=/path/to/termux-app/docs
RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)
PATH_ID=L
export RESULTS="$HOME/kitty-keyboard-results/$RUN_ID/$PATH_ID"
mkdir -p "$RESULTS"
cp "$DOCS/kitty-keyboard-test-results-template.md" "$RESULTS/results.md"
```

For comparisons on another host, use the same chosen `RUN_ID`. Keep each retest in a new report or evidence filename instead of overwriting a failure.

Collect available metadata in `environment.txt` and complete the report's device/configuration fields:

```bash
{
    date -u
    uname -a
    printf 'TERM=%s\n' "$TERM"
    bash --version
    fish --version
    nvim --version
    ssh -V
    tmux -V
} > "$RESULTS/environment.txt" 2>&1
```

Record unavailable tools as such. Obtain the APK hash and source revision on the build host (`sha256sum APK`, `git rev-parse HEAD`, and source dirty status). For SSH, record the client and server versions, server OS, and connection command with private authentication details omitted. Record both the terminal layout and the IME language, which can differ.

Unless a case specifies otherwise, turn Caps Lock and Num Lock off and clear latched Extra Keys modifiers. Record every change of lock state or layout during a case. ASCII key-identity examples assume a layout that produces the specified unshifted characters.

## 3. Raw protocol checks (L and S)

### Using the recorder

For each raw case, choose a separate log and the requested flags:

```bash
bash "$DOCS/kitty-keyboard-check.sh" "$RESULTS/RAW-02-flags1.log" 1 60
```

The recorder first checks negotiation, then displays **“Press keys now.”** Only enter the case's keys after that message. Leave about a second between unrelated actions; release all keys before the recording interval ends. Record the order, modifier side, and lock state in the case detail. The raw bytes appear as hex between `RECORD` and `END RECORD` in the log.

The timer ends the recording. Ctrl+C is input data in raw mode and is not a reliable way to stop it. Wait for completion and check that the shell is usable afterward. If the application exits abnormally and the terminal remains unusable, use Termux's terminal Reset action or a fresh session and record the recovery action.

`CSI` below means bytes `1b 5b`. For example, `CSI 13;5u` is `1b 5b 31 33 3b 35 75`. Optional default fields can have equivalent representations; compare their meanings as well as the raw bytes. The examples below show the current encoder's form with locks off unless stated otherwise.

Do not use this recorder as a full negotiation test inside tmux: it requires flag-stack support that tmux's extended-key translation does not provide. Use section 6 for M/SM.

### Negotiation and reset

#### NEG-01 — Query, state operations, and soft reset

```bash
bash "$DOCS/kitty-keyboard-check.sh" "$RESULTS/NEG-01.log"
```

Do not type during the check. **Expected:** the script completes with no FAIL or timeout. Its assertions cover query, replace/set/clear, unknown-bit masking, an invalid operation mode, nested push/multi-pop/underflow, independent main/alternate state, DECSTR soft reset, and malformed/oversized input. Save the complete log and the command's exit status.

This script does not test every stack boundary or malformed sequence; those have dedicated emulator unit tests.

#### NEG-02 — Full reset clears both screen states and stacks

In the disposable Bash session, run this block. It pushes state on both screens, sends RIS (`ESC c`), queries each screen, and attempts to restore pre-reset stack entries:

```bash
(
    saved=$(stty -g) || exit 1
    trap 'printf "\033[=0u\033[?1049l\033[=0u"; stty "$saved"' EXIT
    stty raw -echo
    printf '\033[>1u\033[?1049h\033[>31u\033c'
    printf '\033[?u\033[<u\033[?u\033[?1049l\033[?u\033[<u\033[?u'
    timeout --foreground 3 dd bs=128 status=none | od -An -v -tx1 > "$RESULTS/NEG-02.log"
)
```

**Expected:** four replies, each `CSI ? 0 u` (`1b 5b 3f 30 75`), and normal shell input after the block. If no complete replies arrive, record a failure/timeout; an empty log is not a pass.

### Encoding and lifecycle cases

For each row, run the recorder using that case ID and flags. Repeat multi-mode cases with separate logs.

| ID | Flags | Procedure | Expected result |
| --- | --- | --- | --- |
| RAW-01 | 0 | Press `a`, Shift+A, Enter, Shift+Enter, Ctrl+Enter, Tab, Ctrl+I, Backspace, Escape. Release each before the next. | `a`, `A`, CR for all three Enter presses, HT for Tab and Ctrl+I, DEL for Backspace, and one ESC byte. No release bytes. This is the legacy-mode baseline. |
| RAW-02 | 1 | Press Escape, Enter, Shift+Enter, Ctrl+Enter, Tab, Ctrl+I. | Escape `CSI 27u`; Enter CR; Shift+Enter `CSI 13;2u`; Ctrl+Enter `CSI 13;5u`; Tab HT; Ctrl+I `CSI 105;5u`. No release bytes. |
| RAW-03 | 1, 3, 5 | In each mode press/release left and right Ctrl, Alt, and Shift individually; hold one modifier long enough to repeat. Press Ctrl+Alt together with no other key, then release in both orders. | No output for the modifier-only events and no crash, focus loss attributable to a crash, or session termination. Record Android-reserved actions separately. |
| RAW-04 | 3 | Tap Up, hold Up for two seconds, release. Then tap `a`, Enter, Tab, and Backspace. | Up press `CSI A`, repeat `CSI 1;1:2A`, release `CSI 1;1:3A`. Plain `a` is UTF-8. Unmodified Enter/Tab/Backspace retain CR/HT/DEL and do not generate release events. |
| RAW-05 | 5 | Press Ctrl+Shift+A; release A while the modifiers remain held, then release the modifiers. | `CSI 97:65;6u` for the press: unshifted `a`, shifted alternate `A`, Ctrl+Shift. No base-layout subfield, associated text, or release events. |
| RAW-06 | 8 | Tap `a`, Enter, Tab, Backspace, and left Shift. Hold `a`, then release it. | Text/control keys are encoded: `CSI 97u`, `CSI 13u`, `CSI 9u`, `CSI 127u`. Shift press `CSI 57441;2u`. Repeats have press syntax; no release or associated-text fields. |
| RAW-07 | 31 | Tap `a`; type Shift+A; test Shift+Enter and Ctrl+Enter. Repeat with the modifier released before the letter/Enter key. Overlap A and B presses, releasing them separately. | `a` press `CSI 97;;97u`; Shift+A press `CSI 97:65;2;65u`. Eligible repeats/releases carry `:2`/`:3`; releases have no associated text. Every delivered key has the matching release, including the modifier. Released key identity remains correct when another key was pressed in between; modifier bits reflect the release-time state. |
| RAW-08 | 31 | With Num Lock on, tap keypad 0–9, operators, decimal, and keypad Enter. Turn Num Lock off and repeat keypad navigation keys. Toggle Caps Lock and type `a`/Shift+A. | Dedicated keypad identities and the observed lock bits are preserved. For example, Num Lock on keypad 1 is `CSI 57400;129;49u` when Android supplies text `1`; keypad Enter is `CSI 57414;129u`. Num Lock off keypad 1 becomes KP_END (`57424`), not the digit. Releases must be present; record exact lock transitions and text. |
| RAW-09 | 1, then 31 | Test F1–F12, arrows, Home/End, Insert/Delete, Page Up/Down; test higher function, lock, media and volume keys if available/delivered. | Correct functional identities; F3 uses `CSI 13~`. Navigation/Enter keys do not insert literal escape fragments. Record each tested key. Android/client-consumed media/system keys are N/A for terminal encoding, with the interception reason; do not claim them as delivered. |
| RAW-10 | 0, 5, 3 | Enable `ctrl-space-workaround`, reload settings, and use a physical keyboard to press Ctrl+Space, plain Space, and Shift+Space in each mode. | Ctrl+Space is NUL with flags 0, `CSI 32;5u` with flags 5, and a `CSI 32;5u` press followed by `CSI 32;5:3u` release with flags 3. Plain Space and Shift+Space are one SP byte in all three modes. |

For RAW-07 with locks off, left Ctrl press is `CSI 57442;5u` and its final release is `CSI 57442;1:3u`. If Ctrl is released before a letter, the letter's release must still identify that letter. Repeat testing with both modifier sides held when supported.

## 4. Software keyboard and composition (raw cases on L/S)

Record IME name/version, language, input route, and expected Unicode text. Use the IME's text entry/composition rather than Termux's clipboard paste action when testing `commitText()`; clipboard paste has a separate terminal path.

| ID | Flags | Procedure | Expected result |
| --- | --- | --- | --- |
| IME-01 | 0, 1 | Enter an ASCII word, a composed/non-ASCII word, a space, and newline with the software keyboard. Complete and cancel a composition. | Usable text entry and the established Enter/control behavior; no duplicate committed text, stranded composition, or invented repeat/release events. |
| IME-02 | 24 | Commit a multi-code-point sample, such as `é😀`, using an IME known to commit it as a single text operation. Also test a supplementary character separately. | A text-only commit uses key code 0 and preserves all committed code points in order, with no release/repeat. If the exact commit is U+0065 U+0301 U+1F600, expect `CSI 0;;101:769:128512u`. |
| IME-03 | 8 | Repeat the same text-only input without associated-text reporting enabled. | The UTF-8 fallback remains usable; text is not dropped and no physical key identity/repeat/release is invented. |
| IME-04 | 24, 31 | With an IME that commits control characters through `commitText()`, activate the Extra Keys Shift modifier and commit newline and Tab. Repeat newline with Ctrl+Shift and Tab with Alt+Shift. Use separate logs for each flags value. | Shift+Enter is `CSI 13;2u`; Shift+Tab is `CSI 9;2u`; Ctrl+Shift+Enter is `CSI 13;6u`; Alt+Shift+Tab is `CSI 9;4u`. The latched modifiers are consumed once, and no repeat/release event is invented. |

For IME-02, a visually identical string does not establish the callback's contents. An IME may normalize `e` + combining acute into `é`, or send separate commits. Record what the IME actually committed when logging/instrumentation is available. If a single multi-code-point commit cannot be established, mark that subcheck BLOCKED, and separately record the observed end-user behavior. Do not infer `commitText()` behavior from a clipboard paste or ADB key injection.

Test physical AltGr and a dead-key layout as part of NVIM-03, recording the physical keys and layout. These are separate input routes from text-only IME commits.

## 5. Application tests (all selected paths)

Keep application output attached to the tested terminal. Do not redirect or pipe the interactive key reader's output to a log: doing so may prevent negotiation from reaching the terminal. Copy its reported results afterward or attach a screenshot/video. The raw recorder already writes its evidence separately.

### Fish

#### FISH-01 — Automatic negotiation and key recognition

Start `fish --no-config`. Run:

```fish
fish_key_reader --continuous --verbose
```

Press Enter, Shift+Enter, Ctrl+Enter, Tab, Ctrl+I, Escape, Ctrl+[, arrows with Ctrl/Shift, and the available function keys. **Expected:** distinct named keys for the ambiguous pairs, notably `enter` / `shift-enter` / `ctrl-enter` and `tab` / `ctrl-i`. Ordinary characters and AltGr input remain correct. Save both the decoded names and byte sequences shown by the reader. Exit with Ctrl+C twice.

On M/SM this verifies tmux's extended-key translation, not an assertion that Fish negotiated full Kitty support with tmux. Record protocol diagnostics separately if available.

#### FISH-02 — Bindings and suppressed modifiers

In the temporary clean Fish shell, select predictable bindings and add visible actions:

```fish
fish_default_key_bindings
bind shift-enter 'commandline -i "[SHIFT-ENTER]"; commandline -f repaint'
bind ctrl-enter 'commandline -i "[CTRL-ENTER]"; commandline -f repaint'
bind ctrl-i 'commandline -i "[CTRL-I]"; commandline -f repaint'
```

Press each bound combination once. **Expected:** one corresponding marker per press, no duplicate on release. Plain Tab still completes; plain Enter still executes a valid command. Clear the marker-filled line with Ctrl+C rather than executing it. Repeat the modifier-only sequence from RAW-03: no crash or inserted modifier text is allowed.

#### FISH-03 — Child-process handoff, paste, and restoration

1. Run `sleep 30`, then Ctrl+C. Expect the process to stop and a usable prompt to return.
2. Paste a controlled multiline sample containing a non-ASCII character. Expect a single copy of the text; do not execute the sample merely to test paste.
3. Run and exit `nvim --clean`, then repeat the bindings from FISH-02 and completion/history editing.
4. Exit the temporary Fish shell and verify normal input in its parent shell.

**Expected:** no stuck protocol state, missing modifiers, duplicate text, or visible control-sequence fragments. Record the actual paste route (Termux action, IME button, or desktop clipboard).

### Neovim

#### NVIM-01 — Escape preserves the edit

1. Start `nvim --clean` in an empty buffer.
2. Press `i` and physically type `KEEP_THIS_EDIT`.
3. Press Escape **once** and release it.
4. Observe mode and buffer before pressing any other key.

**Expected:** Normal mode, with `KEEP_THIS_EDIT` unchanged. Undo, partial deletion, a literal `27u`, or remaining in Insert mode is a failure. Record this result even if a key reader previously recognized Escape correctly. Test this on L/S and M/SM; tmux's compact-Escape compatibility is covered in MUX-02.

#### NVIM-02 — Distinct modified-key actions

In clean Neovim, enter:

```vim
:inoremap <S-CR> [SHIFT-ENTER]
:inoremap <C-CR> [CTRL-ENTER]
:inoremap <Tab> [TAB]
:inoremap <C-I> [CTRL-I]
```

Enter Insert mode and press each mapped key once, then plain Enter. **Expected:** the four distinct markers and a normal newline. Release must not insert a second marker. Press Escape once: all text must remain. Repeat modifier-only presses from RAW-03 in both Normal and Insert modes, including overlapping press/release order: no crash is allowed.

If clean mode passes and the normal configuration fails, collect the relevant mappings with `:verbose imap <S-CR>`, `:verbose imap <C-CR>`, `:verbose imap <Tab>`, and `:verbose imap <C-I>` in the failing profile.

#### NVIM-03 — Layout, editing, and exit

In an empty scratch buffer:

1. Type a layout-specific sample, for example `zażółć gęślą jaźń` on a Polish layout, plus shifted variants. Record the exact sample.
2. On a dead-key layout, test both a supported composition and a non-composing following key; record each physical key and expected result.
3. Enter/paste a supplementary character and a combining-character sample using separately identified routes.
4. Hold an arrow, release it, then test Home/End, Page Up/Down, Backspace/Delete, and keypad digits/Enter in both Num Lock states.
5. Leave Insert mode and confirm edits remain; exit with `:q!` and verify shell editing and modified-key recognition again.

**Expected:** correct text, no duplicate commits, navigation that stops on release, no unexpected buffer changes on Escape, and a usable parent shell. Existing Termux shortcuts such as Shift+Page Up/Down scrolling retain precedence; record their action rather than expecting an editor binding for an intercepted combination.

### Other applications

#### APP-01 — f4 or far2l functional check

Run an installed version of f4 or far2l in its documented terminal mode in the disposable results directory. Record executable, version, complete invocation, and application configuration. Navigate entries, open and close a viewer, enter/cancel a dialog, and exercise a documented modified-key binding and available function keys. Use only scratch content.

**Expected:** the application's documented actions, normal text/AltGr entry where applicable, and Escape cancelling/closing the current operation without an unrelated action. On exit, the shell remains usable. Do not assume Shift+Enter has the same action in every application.

#### APP-02 — f4/far2l protocol detection evidence

Record the application's capability/debug output or a terminal-output trace showing its negotiation and received response. **Expected:** evidence of negotiated Kitty support on L/S, or the documented supported extended-key mechanism on M/SM. Navigation alone is insufficient evidence of protocol detection. If the installed version provides no practical observation method, mark this case BLOCKED and retain APP-01 as a separate functional result.

#### APP-03 — Normal configuration/downstream workflow

Repeat the important bindings and Escape checks with the normal Fish/Neovim configuration and any downstream application under test. Record each binding's intended action before testing. **Expected:** exactly that action once per press, no duplicate submission/newline, and no unintended undo. Start a separate report if the configuration profile differs from the clean run.

## 6. tmux tests (M and SM)

### MUX-01 — Configuration and terminal identities

Apply configuration on the host running tmux. Follow the [tmux compatibility instructions](kitty-keyboard-protocol.md#tmux) and record the exact file and effective options. For an isolated test, save the configuration as `tmux-test.conf` and start it from outside another multiplexer:

```bash
tmux -L kitty-manual-test -f /path/to/tmux-test.conf new-session -s keyboard-test
```

Use a previously unused server name, or verify that an existing test server has loaded the intended file; `-f` does not reload an already running server. Do not stop unrelated servers to run these tests.

Inside the test server, capture:

```bash
{
    tmux -V
    tmux display-message -p 'server=#{version} client=#{client_name} outer=#{client_termname}'
    tmux show-options -s extended-keys
    tmux show-options -s extended-keys-format
    tmux show-options -s terminal-features
    tmux show-options -s terminal-overrides
    tmux show-options -s user-keys
    tmux show-options -g default-terminal
    printf 'pane TERM=%s\n' "$TERM"
} > "$RESULTS/MUX-01-options.txt" 2>&1
```

**Expected for the reference configuration:** `extended-keys on`, `extended-keys-format csi-u`, an `Eneks`/`Dseks` override matching the actual outer terminal, and `TERM=tmux-256color` inside panes. For Termux's usual outer identity, the client reports `xterm-256color`. With several clients attached, identify which client supplies the test input. Restart the tested applications after terminal capability changes.

### MUX-02 — Compact Escape compatibility

Record whether the [compact-Escape mapping](kitty-keyboard-protocol.md#tmux) is enabled and which user-key slot it uses. Run NVIM-01 and NVIM-02 in a disposable pane. **Expected with the mapping:** Escape ends Insert mode without undoing text, and Shift+Enter/Ctrl+Enter remain distinct.

If comparing an affected tmux version without the mapping, use a separate disposable test server/report. Record the observed failure as FAIL with a multiplexer-compatibility classification; do not reuse the same result as a pass after applying the workaround. Reload the mapping, repeat the cases, and link the retest evidence. Check that no unrelated tmux root-table binding consumes the tested keys.

The optional host-side reproducer does not require Android:

```bash
python3 "$DOCS/kitty-tmux-escape-check.py" > "$RESULTS/MUX-02-local-reproducer.txt" 2>&1
```

It compares compact/explicit-modifier Escape through real local tmux and Neovim, including mapping startup/live reload and modified-Enter bindings. Label its results as synthetic host-side evidence, not physical-device verification.

### MUX-03 — Panes, application exit, and reattachment

1. Use two panes: one Fish and one clean Neovim. Switch between them and repeat FISH-02/NVIM-02, followed by NVIM-01.
2. Exit Neovim and check the shell in that pane.
3. Detach the test client, then reattach from the same input path using its test server name, for example `tmux -L kitty-manual-test attach-session -t keyboard-test`.
4. Verify MUX-01's terminal identities again; repeat modified Enter and Escape.

**Expected:** no loss of modified keys, state leaking between panes, accidental undo, or stale mode after reattachment. Applications inside tmux are not required to receive all Kitty release/text fields: this case verifies the configured extended-key translation.

## 7. Record failures and close the run

For every FAIL/BLOCKED case, complete the template's detailed record: exact ordered keys, modifier release order, lock state, application mode, expected/actual text or bytes, evidence filename, and the closest comparison path that passes. For a crash, distinguish an Android app crash from a child-process exit or an Android-reserved shortcut; attach the relevant crash trace when available.

After each recorder, confirm `END RECORD` when recording was requested and check shell usability. A script printing negotiation PASS lines does not automatically pass the physical-key cases. Copy any displayed results before closing the disposable session. Exit temporary application shells so their test-only bindings are removed; note remaining test sessions/configuration and any cleanup performed.

Record exact automated task names, exit status, and per-variant counts if automated verification accompanies the manual run:

```bash
./gradlew :terminal-emulator:test
./gradlew :terminal-view:assembleDebug
./gradlew :app:assembleDebug
./gradlew test
```

Complete the report with totals for each status, unresolved case IDs, and links to comparison reports. Claim success only for the paths, versions, layouts, IMEs, and configuration profiles actually tested.
