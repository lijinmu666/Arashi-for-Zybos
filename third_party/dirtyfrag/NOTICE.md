# DirtyFrag VR integration

Source: https://github.com/qq2070006042-create/yuehong-privilege-assistant
Pinned revision: d88dab3debaa9af6337536cde7166ef9648d12e4
Upstream DirtyFrag VR: https://github.com/DONGDERS/DirtyFalg-vr
Lineage: https://github.com/diabl0w/DFRoot
Version: v1.16-vr

The native sources and eight original KMI modules are vendored in
`app/src/main/jni/dfroot/`. The original modules are retained byte-for-byte.
At build time `tools/relocate_dirtyfrag.py` replaces the original modules'
legacy UMH shell command with a marker-only command of the same storage size.
The original command exports dmesg/lsmod to shared storage and tries to execute
`df.root`'s bootstrap. Arashi instead executes its own bootstrap once from the
assembly worker after insmod returns, matching the upstream assembly fallback.
The source module's UMH command is changed to the same marker-only behavior.
Every ELF offset and section size remains unchanged. The unused df.root string
literals in the upstream binaries are retained; VR removal is disabled.
Building modules from source requires the corresponding KMI kernel tree:
`make -C app/src/main/jni/dfroot/lkm KDIR=/path/to/kernel/build`.

The Java runner/reporter are adapted from Yuehong's integration (MPL-2.0).
Changes: Arashi package and payload resolution, scoped IPsec resources,
process timeout/cleanup, and explicit KernelSU handoff verification.
The ZYBOS entry currently permits arm64, primary user, 5.10.x kernels only.
This is a preflight filter, not a guarantee of exploit compatibility.
No automatic boot-time activation is configured. The confirmation page exposes
module disabling and post-handoff soft reboot as explicit, default-off checkboxes.
Each run snapshots the selected values before invoking the native bootstrap.
The optional upstream vivo VR probe removal is not enabled for ZYBOS.
A successful handoff does not automatically grant Arashi a KernelSU su rule.
The user continues in the explicitly selected official KernelSU manager.

License/attribution status retained from upstream NOTICE.md:
- Yuehong integration modifications: MPL-2.0 (included here).
- DFRoot identifies its license as GPL-2.0; `lkm/dfroot.c` declares GPL
  (GPL-2.0 text included here).
- `include.inc` retains its Odzhan BSD-style license notice.
- DirtyFrag VR has no clear repository-wide license covering every file;
  its remaining files retain upstream notices and are not relicensed as MPL.

Copyright remains with the respective upstream authors and contributors.
