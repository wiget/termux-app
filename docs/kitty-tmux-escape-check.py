#!/usr/bin/env python3
"""Reproduce Kitty Escape handling through a disposable local tmux and Neovim.

Requires Python 3, tmux, and Neovim with --listen/--remote-expr support on a
POSIX host. No Android device, user configuration, or existing tmux server is
used. The PTY peer sends recorded key bytes and answers basic terminal queries;
it is not a complete terminal emulator.
"""
import fcntl
import json
import os
from pathlib import Path
import pty
import select
import shutil
import struct
import subprocess
import tempfile
import termios
import time


def check(escape, workaround=False, live_reload=False):
    with tempfile.TemporaryDirectory(prefix="kitty-escape-") as directory:
        directory = Path(directory)
        tmux_socket = str(directory / "tmux.sock")
        nvim_socket = str(directory / "nvim.sock")
        config = directory / "tmux.conf"
        mapping = """set -s user-keys[0] "\\e[27u"
bind-key -n User0 send-keys Escape
"""
        config.write_text("""set -g default-terminal tmux-256color
set -s extended-keys on
set -s extended-keys-format csi-u
set -as terminal-features 'xterm-256color:extkeys'
set -as terminal-overrides ',xterm-256color:Eneks=\\E[=1u:Dseks=\\E[=0u'
""" + (mapping if workaround and not live_reload else ""))
        environment = dict(os.environ, TERM="xterm-256color")
        environment.pop("TMUX", None)
        tmux_executable = shutil.which("tmux")
        nvim = shutil.which("nvim")
        if tmux_executable is None or nvim is None:
            raise RuntimeError("Both tmux and nvim must be available on PATH")
        tmux = [tmux_executable, "-S", tmux_socket]
        pid, master = pty.fork()
        if pid == 0:
            os.execvpe(tmux[0], tmux + ["-f", str(config), "new-session", "-s", "escape-test",
                                      nvim, "--clean", "--listen", nvim_socket], environment)
        fcntl.ioctl(master, termios.TIOCSWINSZ, struct.pack("HHHH", 24, 80, 384, 640))

        def pump(seconds):
            end = time.monotonic() + seconds
            while time.monotonic() < end:
                if not select.select([master], [], [], min(0.05, max(0, end - time.monotonic())))[0]:
                    continue
                try:
                    data = os.read(master, 65536)
                except OSError:
                    return
                if not data:
                    return
                # Only answer the basic terminal queries used by the tmux client.
                for query, response in [
                    (b"\x1b[c", b"\x1b[?64;1;2c"),
                    (b"\x1b[>c", b"\x1b[>41;320;0c"),
                    (b"\x1b[>q", b"\x1bP>|Termux test peer\x1b\\"),
                    (b"\x1b[18t", b"\x1b[8;24;80t"),
                    (b"\x1b[14t", b"\x1b[4;384;640t"),
                    (b"\x1b]10;?\x1b\\", b"\x1b]10;rgb:ffff/ffff/ffff\x1b\\"),
                    (b"\x1b]11;?\x1b\\", b"\x1b]11;rgb:0000/0000/0000\x1b\\"),
                ]:
                    for _ in range(data.count(query)):
                        os.write(master, response)

        def evaluate(expression):
            result = subprocess.run([nvim, "--server", nvim_socket, "--remote-expr",
                                     expression],
                                    capture_output=True, text=True, timeout=5, check=True)
            return result.stdout.strip()

        def state():
            return json.loads(evaluate("json_encode([mode(), getline(1, '$')])"))

        try:
            deadline = time.monotonic() + 10
            while not Path(nvim_socket).exists():
                if time.monotonic() > deadline:
                    raise RuntimeError("Neovim did not start")
                pump(0.1)
            pump(1)
            if live_reload:
                mapping_file = directory / "escape.conf"
                mapping_file.write_text(mapping)
                subprocess.run(tmux + ["source-file", str(mapping_file)], check=True, timeout=5)
                pump(0.1)
            os.write(master, b"iKEEP_THIS_EDIT")
            pump(0.3)
            before = state()
            assert before == ["i", ["KEEP_THIS_EDIT"]], before
            os.write(master, escape)
            pump(0.5)
            after = state()
            print(json.dumps({"escape": escape.decode("ascii"), "workaround": workaround,
                              "live_reload": live_reload, "before": before, "after": after}))
            if workaround:
                # Check that the Escape-only mapping preserves modified Enter forwarding.
                evaluate('execute("inoremap <S-CR> [SHIFT]")')
                evaluate('execute("inoremap <C-CR> [CTRL]")')
                os.write(master, b"A\x1b[13;2u\x1b[13;5u")
                pump(0.3)
                assert state() == ["i", ["KEEP_THIS_EDIT[SHIFT][CTRL]"]], state()
                os.write(master, escape)
                pump(0.3)
                assert state() == ["n", ["KEEP_THIS_EDIT[SHIFT][CTRL]"]], state()
                print("PASS: Shift+Enter and Ctrl+Enter remain distinct; Escape preserves the edits")
            return after
        finally:
            subprocess.run(tmux + ["kill-server"], capture_output=True, timeout=5)
            os.close(master)
            os.waitpid(pid, 0)


if __name__ == "__main__":
    print(subprocess.check_output(["tmux", "-V"], text=True).strip())
    check(b"\x1b[27u")
    assert check(b"\x1b[27;1u") == ["n", ["KEEP_THIS_EDIT"]]
    assert check(b"\x1b[27u", workaround=True) == ["n", ["KEEP_THIS_EDIT"]]
    assert check(b"\x1b[27u", workaround=True, live_reload=True) == ["n", ["KEEP_THIS_EDIT"]]
