# Kitty keyboard protocol — manual test results

Copy this file for each path/configuration profile. Follow `docs/kitty-keyboard-manual-tests.md` from the tested source revision. All cases start as NOT RUN; the empty template is not test evidence.

## Run identification

| Field | Value |
| --- | --- |
| Campaign/run ID | |
| Report ID / retest number | |
| Runbook reference / revision | |
| Tester | |
| UTC start / end | |
| Path ID: L / S / M / SM | |
| Exact input path | |
| Configuration profile: clean / normal / other | |
| Required cases / scope exclusions | |
| Previous report / comparison report links | |

## Build and device

| Field | Value |
| --- | --- |
| Termux version / package variant / application ID | |
| APK filename / SHA-256 | |
| Source revision / dirty status / patch reference | |
| Build tool versions, if built locally | |
| Device manufacturer / model | |
| Android version / API level / build ID | |
| Physical keyboard manufacturer / model | |
| Keyboard transport: USB / Bluetooth / other | |
| Android physical keyboard layout name | |
| Initial Caps Lock / Num Lock state | |
| IME name / version / language | |
| Extra Keys layout and latched modifiers | |
| Relevant Termux shortcut/remapping settings | |
| Input evidence: physical / IME / injected / synthetic host | |

## Application and transport configuration

| Field | Value |
| --- | --- |
| Local shell / Fish version and binding mode | |
| Neovim version / executable (`vi` resolution if used) | |
| SSH client / server versions and server OS | |
| SSH command and intermediate hops (omit private authentication details) | |
| Outer TERM before tmux | |
| tmux client / running server versions | |
| tmux server name/socket / tested client identity | |
| tmux configuration file / hash or attached contents | |
| Effective extended-keys / extended-keys-format | |
| Matching terminal-features and terminal-overrides | |
| Compact-Escape mapping enabled? User-key slot? | |
| Pane TERM | |
| Other multiplexer name/version/configuration, if applicable | |
| f4/far2l version / terminal-mode invocation | |
| Other downstream application/version/tested bindings | |
| Application configuration files, hashes, or attached relevant mappings | |

Attach `environment.txt` and, for M/SM, `MUX-01-options.txt`. Explain any change to this environment during testing or start a new report.

## Case results

Allowed status: **PASS**, **FAIL**, **BLOCKED**, **N/A**, **NOT RUN**. For N/A/BLOCKED give a reason; for PASS/FAIL cite an observation or evidence artifact. Raw cases apply to L/S; use application/multiplexer cases for M/SM.

| Case ID | Test | Status | Observed result / subchecks | Evidence / issue / reason |
| --- | --- | --- | --- | --- |
| NEG-01 | Negotiation operations and soft reset | NOT RUN | | |
| NEG-02 | Full reset: both screens and stacks | NOT RUN | | |
| RAW-01 | Legacy mode, flags 0 | NOT RUN | | |
| RAW-02 | Escape, modified Enter, Ctrl+I/Tab, flags 1 | NOT RUN | | |
| RAW-03 | Modifier-only suppression/no crash, flags 1/3/5 | NOT RUN | | |
| RAW-04 | Repeat/release and recovery controls, flags 3 | NOT RUN | | |
| RAW-05 | Shifted alternate identity, flags 5 | NOT RUN | | |
| RAW-06 | All keys without text/events, flags 8 | NOT RUN | | |
| RAW-07 | Associated text, overlapping keys, releases, flags 31 | NOT RUN | | |
| RAW-08 | Keypad and lock state, flags 31 | NOT RUN | | |
| RAW-09 | Function/navigation/available system keys | NOT RUN | | |
| RAW-10 | Physical Ctrl+Space workaround, flags 0/5/3 | NOT RUN | | |
| IME-01 | Text/composition in flags 0/1 | NOT RUN | | |
| IME-02 | Text-only multi-code-point commit, flags 24 | NOT RUN | | |
| IME-03 | Text-only UTF-8 fallback, flags 8 | NOT RUN | | |
| IME-04 | Committed controls preserve Extra Keys modifiers, flags 24/31 | NOT RUN | | |
| FISH-01 | Automatic negotiation/key-reader recognition | NOT RUN | | |
| FISH-02 | Binding actions and suppressed modifiers | NOT RUN | | |
| FISH-03 | Child-process handoff, paste, restoration | NOT RUN | | |
| NVIM-01 | Escape preserves the inserted edit | NOT RUN | | |
| NVIM-02 | Modified Enter and Ctrl+I/Tab mappings | NOT RUN | | |
| NVIM-03 | Layout/composition, navigation, and exit | NOT RUN | | |
| APP-01 | f4/far2l functional check | NOT RUN | | |
| APP-02 | f4/far2l protocol detection evidence | NOT RUN | | |
| APP-03 | Normal configuration/downstream workflow | NOT RUN | | |
| MUX-01 | Effective options and terminal identities | NOT RUN | | |
| MUX-02 | Compact Escape compatibility and reload | NOT RUN | | |
| MUX-03 | Pane switching, exit, detach/reattach | NOT RUN | | |

### Detailed case record (duplicate as needed)

- **Case ID / attempt / UTC time:**
- **Status:**
- **Path / profile / application mode / negotiated or requested flags:**
- **Prerequisites and deviations from the runbook:**
- **Input route and ordered actions:**
  1.
  2.
  3.
- **Modifier sides, hold duration, release order, Caps Lock / Num Lock:**
- **Expected result:**
- **Actual result (including buffer content before/after where relevant):**
- **Raw bytes in hex / decoded keys / Unicode code points:**
- **IME callback grouping/normalization confirmed? How?**
- **Evidence files:**
- **Reproduction count (failures / attempts):**
- **Closest passing comparison path/profile:**
- **Classification: terminal / Android delivery / IME / application configuration / multiplexer / unknown:**
- **Issue reference, recovery action, and retest link:**

## Evidence inventory

Use distinct filenames for attempts. Annotate whether bytes came from the direct PTY or a multiplexer; mark screenshots, typed observations, Android callback logs, and host-side synthetic tests explicitly.

| Artifact | Case/attempt | Input path / evidence type | Description |
| --- | --- | --- | --- |
| environment.txt | Setup | | |
| | | | |

## Automated verification (optional)

Record commands actually executed. Counts must identify debug/release variants; do not count the same test method in both variants as two distinct methods.

| Exact command | Host/toolchain/revision | Exit status | Tests / failures / skipped by variant | Report or log |
| --- | --- | --- | --- | --- |
| | | | | |

## Summary

- **PASS count:**
- **FAIL count and case IDs:**
- **BLOCKED count and case IDs:**
- **N/A count and reasons:**
- **NOT RUN count and case IDs:**
- **Overall outcome: required scope passed / regression found / incomplete:**
- **Confirmed working paths, versions, layouts, IMEs, and profiles:**
- **Known limitations observed:**
- **Unresolved issues and next tests:**
- **Recovery/cleanup completed and test-only changes remaining:**
- **Tester confirmation/date:**
