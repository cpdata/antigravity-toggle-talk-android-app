# Action Report: Reverting PRoot Bypass in run_antigravity.sh

This report documents the changes applied to `run_antigravity.sh` to revert the direct glibc linker bypass and instead run the Antigravity CLI via `proot --kill-on-exit` with standard bindings.

> [!NOTE]
> This document is kept unstaged and uncommitted per user instructions.

## 1. Background & Rationale
To prevent Antigravity from hanging in headless/widget contexts (where orphaned/daemonized guest child processes keep `proot` active indefinitely), a PRoot bypass was previously implemented. However, this bypass had severe side-effects:
- It lacked virtual filesystem mount points like `/usr/bin/env` or `/bin/sh` in the process namespace.
- Consequently, helper utilities (e.g. `task-cli`, shell wrappers, test runners) executing scripts with standard shebangs failed with `bad interpreter: No such file or directory`.

By using `proot` with the `--kill-on-exit` flag:
1. `proot` immediately terminates any active child processes in its namespace as soon as the guest binary (`agy.va39`) exits, resolving the headless widget hang.
2. Standard directory bindings (`/etc/resolv.conf`, `/usr/bin/env`, `/bin/sh`, `/bin/bash`) are preserved, ensuring script shebangs resolve correctly.

## 2. Summary of Changes in `run_antigravity.sh`
- Removed `export GODEBUG=netdns=go` since standard DNS virtualization is handled by `proot` mounting `/etc/resolv.conf`.
- Defined `PROOT_BIN="/data/data/com.termux/files/usr/bin/proot"`.
- Wrapped the guest executable calls in `env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit` and mounted:
  - `/data/data/com.termux/files/usr/etc/resolv.conf:/etc/resolv.conf` (DNS)
  - `/data/data/com.termux/files/usr/bin/env:/usr/bin/env` (Shebang helper)
  - `/data/data/com.termux/files/usr/bin/sh:/bin/sh` (Shell)
  - `/data/data/com.termux/files/usr/bin/bash:/bin/bash` (Bash)

This ensures standard behavior for the guest executable and any invoked commands.
