# Wurm Server 0.10.21 — frame display and game UI recovery

## What the 0.10.20 report established

The Thor produced a median **30 FPS** across 75 timing samples, while the Android
viewer displayed a median **1 FPS** across 74 samples (0.8–1.2 FPS). The Performance
preset applied successfully, and Settings opened without the previous JavaFX crash.
The client remained connected until Stop was requested. The server subsequently
shut down normally with exit 0. Intermittent GL errors and visual artifacts remain.

The recording shows the world and Android launcher controls, but the Wurm HUD is
already absent at the beginning. It does not capture the transition that hid it.
The old report has input resets near the recorder interruption, but no HUD visibility
or Android lifecycle observations. The exact HUD-loss trigger is not established.

## One-frame-per-second correction

The viewer selected frames using `File.lastModified()` and file length. Android
13's implementation calculates that timestamp as `1000 * sb.st_mtime`, discarding
sub-second precision. Every constant-resolution frame within a second therefore
had the same apparent identity. Faster rendering could not overcome this viewer
gate. See [Android 13's UnixFileSystem implementation](https://android.googlesource.com/platform/libcore/+/refs/heads/android13-release/ojluni/src/main/native/UnixFileSystem_md.c).

The viewer now opens the current atomic frame and reads its sequence number.
Only a newer sequence triggers allocation and pixel transfer. Header and pixels
come from the same open file, so a concurrent atomic replacement cannot mix frames.
Previous-session reads and out-of-order completions remain rejected. A damaged
frame causes a bounded retry; a later frame can recover even with the same timestamp.

The regression publishes 30 different frames with identical modification times
and sizes. The old selection rule accepts one; the new reader accepts all 30.
Additional cases cover duplicates, old sequences, corruption/recovery and reset.
The GPU/readback/Bitmap path, 30 FPS target and existing graphics fixes remain.
Actual Android display rate after this correction still requires a Thor test.

## HUD recovery and recorder observations

Returning focus to the game viewer requests `setVisible(true)` on the inspected
game HUD, on its owning game thread. The viewer also has a **Restore Game UI**
button; scroll the top button row sideways if it is outside the visible area.
This operation is idempotent: repeating it cannot hide an already-visible HUD.
It preserves startup screens and does not recreate windows, reset the character,
send gameplay commands or alter login state.

This is recovery for a hidden HUD, not proof that the original trigger is fixed.
If the HUD's visibility flag remains true while its pixels disappear, the next
report will distinguish that rendering problem from a visibility toggle.
`VIEW_FOCUS`, `VIEW_PAUSED`, `VIEW_RESUMED`, `HUD_STATE` and `HUD_RESTORED` now record
the relevant transitions. Controller input is ignored while another Android
window owns focus; held inputs are still released on focus loss.

The low-resolution choice is now labeled and requested as **800 × 480**. Wurm
clamped the preceding 800 × 450 request to that actual size in the report.

## Thor test

1. Keep older apps and backups. Stop the old server normally and export its
   **working runtime ZIP** if preserving the latest world.
2. Install **0.10.21** (code 35, separate `framesequence` package). Import your
   working server ZIP and the same complete client ZIP. Select **Adventure** and
   an unused name such as **Thorframes**. Login identities do not yet migrate
   between separate previews; retain older apps for their existing characters.
3. Start with **Performance / 800 × 480 / 30 FPS**. Move, look and interact for
   a minute before recording. Note whether motion is substantially smoother.
4. Start and stop a short recording, then return to the game. If the HUD is still
   missing, press **Restore Game UI**. Note whether automatic or manual recovery
   restores it. Reopen Graphics Settings to confirm that route still works.
5. Export **Client Report** and **Server Session Report**, plus the recording.
   On a crash, export the client report before Retry. If stable, stop both normally
   and restart inside this same preview without reimporting to test persistence.

No desktop launcher or replacement game files are required. The next acceptance
gate is a display rate near the producer's rate and repeatable HUD recovery; a
30 FPS target is not a promise of sustained device performance.
