# Wurm Server 0.10.20 — Android graphics settings and frame delivery

## What the Thor's 0.10.19 test established

The user could move and interact in the world. The final client ran about 320 seconds
and produced 3,809 frames before opening Settings caused exit 42:
`NoClassDefFoundError: javafx/application/Platform`, from
`HeadsUpDisplay.addComponent` → `toggleComponent` → `MainMenu.buttonClicked`.
The last native draw returned, and both `EGL_CONTEXT_CLOSED` and `WINDOW_CLOSED`
were recorded. This failure is in desktop settings dispatch. It is not the preceding
invalid vertex pointer crash. The server later stopped normally with exit 0.

Performance remained poor. Later samples report roughly 11.6–13.2 published FPS
with a 15 FPS target, despite a higher internal swap rate. Readback cost around
4–7 ms and publication around 4–4.6 ms. The old viewer also discarded a complete
frame whenever a newer atomic file appeared during reading. The recording confirms
visible world interaction, choppiness and remaining rendering artifacts.

The previous shader compilation failures did not recur. Four early
`GL_INVALID_OPERATION` checks did occur and the client continued. Those errors,
the earlier Scudo shutdown abort and remaining rendering artifacts stay tracked.
One normal graphics shutdown does not establish that all native corruption is fixed.

## Settings correction

The inspected HUD has exactly two JavaFX `Platform.runLater` sites, for opening
and closing settings. A private runtime overlay redirects their owner to the
owned `SettingsDispatch` and changes the close callback's null `ActionEvent`
descriptor to `Object`. The original HUD SHA-256 is
`2d4cfa759ae6c725999f6c66d319b30f091a74f7f92419b81697b510bc25aba9`.
Reverse verification must reproduce that exact original class. No game class is
committed or bundled, and the imported client JAR is unchanged.

The owned settings adapter requests an Android dialog and lets the HUD remove its
empty desktop placeholder. The same dialog is available from **Client → Graphics
Settings** before startup and the game viewer's **Graphics Settings** button.
The dialog controls this inspected subset, not the entire desktop settings UI:

| Control | Default | When applied |
| --- | --- | --- |
| Graphics preset | Performance | Live from the game viewer; otherwise next startup |
| Render resolution | 800 × 450 | Next client startup |
| Frame target | 30 FPS | Live from the game viewer; otherwise next startup |

Performance selects low water detail, disables reflections, selects Short for
tree/structure/item-and-creature distance, and disables pretty trees, pretty weather
and sun glare. Field types and option labels are checked before mutation. The
Imported game settings choice restores the eight values captured after profile
loading in this client process. A failed setter triggers rollback. Readonly terrain
detail and other unqualified options are untouched. The new code stores its choices
in Android preferences; it does not rewrite game profile files.

## Frame delivery

The whole swap is paced at 30 or 15 FPS, including readback/publication in the
interval. Each swap publishes one frame; there is no separate unconditional
16 ms sleep or extra unpublished render loop. Missed deadlines do not cause
catch-up bursts. These are targets, not promised device rates.

Format 3 transports raw bottom-up RGBA bytes using bulk channel writes and atomic
rename. Android first checks that raw RGBA copies produce the correct red/blue
Bitmap pixels. It then copies directly into an opaque Bitmap and flips the image
only while drawing. A decoded ARGB fallback remains available. Pointer coordinates
and touch geometry stay top-down. Formats 1 and 2 remain readable for diagnostics.
This removes two per-pixel conversion loops from the normal path, but still uses
the interim GPU-readback/file/Bitmap pipeline; it is not direct GPU presentation.

The viewer accepts completed atomic reads in sequence, rejects previous-session
frames, polls more often, and refreshes visible diagnostic text only twice per
second. `UI_TIMING` now records actual Android display rate alongside producer
`FRAME_TIMING`; this distinguishes a slow renderer from a slow viewer.

## Validation and next milestone

Authored executable fixtures reproduce the missing-JavaFX callback failure and
exercise repeated patched open/close calls without JavaFX. Preset tests cover
restoration, ABI rejection before changes and rollback. Frame tests cover raw
channel/origin preservation, metadata, truncation and invalid writes; simulated
clock tests check pacing at both targets and after stalls. The exact private HUD
patch is verified against the owner's supplied client. The earlier checked native
pointer and shader fixes remain unchanged.

The next device milestone is repeatable graphics-settings use, visibly smoother
movement, and normal stop/restart with character persistence in the same preview.
Actual Thor frame rate and settings behavior in 0.10.20 still require device testing.

1. Keep older apps and backups. Stop the previous server normally; export its
   **working runtime ZIP** if retaining the latest world.
2. Install **0.10.20** (code 34, separate `smoothsettings` package). Import the
   working server ZIP and the same complete client ZIP; select **Adventure**.
3. Choose an unused name such as **Thorsmooth** with **Change Player Name**.
   Leave Graphics Settings at **Performance / 800 × 450 / 30 FPS** and Start Local Game.
4. Complete setup and move/look/interact for two to five minutes. Open the game's
   Settings menu, Save, and reopen it. It should show the Android dialog without
   ending the client. The viewer's Graphics Settings button opens it too.
5. Compare 15 and 30 FPS live; optionally compare Imported and Performance presets.
   Resolution requires stopping and restarting the client. Check colors, image
   orientation and touch/pointer alignment as well as smoothness.
6. Export **Client Report** and **Server Session Report**, with a short recording
   if possible. On a crash, export the client report **before Retry**. If stable,
   stop both normally and restart in this same app without reimporting to check
   character persistence.

The world export preserves old character data but does not migrate the separate
client login identity between preview packages. Keep older apps for their existing
characters and use an unused name in the new preview. No desktop launcher or
replacement game files are needed.
