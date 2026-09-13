# 0.10.39 — launcher flow, complete backups and keyboard input

Implementation is on **mod-launcher-test**. Keep main unchanged. This is the
first production-polish device test, not a declaration that release
qualification is complete. The native renderer/audio/ASan configuration, server
POC, SQLite patches, heap and collector choices are retained.

## Everyday controls

- Tabs remain Server, Client, Mods, Diagnostics. The launcher remembers the
  selected tab and opens Client initially. Navigation does not stop services.
- Client shows the character/world and one contextual **Play / Resume game**
  action. Play checks required imports and starts the selected local server
  when needed. Setup, runtime import and connection to an already-running local
  server are secondary actions. No remote-server picker is implied.
- Server shows its world and **Start / Save & stop** first. World/server
  settings, setup, server exports/recovery and advanced stop/restart actions
  are grouped. Port remains explicitly a connection check, not an editor for
  the server's actual listening port.
- Mods has a Server/Client selector. Switches describe the next launch;
  observed READY markers are shown separately while the affected runtime is
  active. File/dependency checks and manifests are now in Diagnostics.
- Diagnostics offers a single ZIP support bundle, separate reports, expandable
  tests/checks and optional warning/error filtering. The combined ZIP includes
  client and server reports separately without embedding another server report
  in the client report. Reports still contain timestamped history.
- Graphics settings retain all 47 options with category/search and visible
  group reset. Save reports the live acknowledgment, failure or timeout;
  restart-only choices remain explicitly labeled. Acknowledgment confirms the
  existing client callback, not visual correctness of every setting.

## Gear, keyboard and mouse

The compact gear menu contains Resume, Graphics, Show/Hide keyboard, Controls,
Display & overlay, Back to launcher, session actions and Support. The gear can
be placed on the left or right. Opacity affects the panel background while
text remains readable; holding the gear restores 85%. 1280x720 remains the
default render resolution. The game view keeps the screen awake while visible.

1. Tap/select the intended text field or chat input in Wurm.
2. Open the gear and choose **Show keyboard**. The controls panel closes and an
   Android text composer opens. This uses a normal Android editor so composing,
   selection, correction and clipboard paste are handled locally by the IME.
3. **Insert** queues the composed text into the selected game field. **Enter**
   inserts a nonempty draft, then presses the game's Enter key. Backspace edits
   the draft, or sends a game backspace if the draft is empty.
4. Use **Hide**, Android Back, or gear → **Hide keyboard** to dismiss it. Hiding
   does not submit the draft. The composer accepts up to 240 UTF-16 units per
   insertion and retains the draft if enqueue fails. Text is not logged.

This composer is deliberate: it does not pretend to know Wurm's current text
selection or implement bidirectional IME composition inside a desktop widget.
Confirm the game field has focus before inserting. Engine font/character
support still determines which characters display. Device IME behavior and
actual Wurm text consumption need the checklist below.

With the text bar closed, hardware keyboards send key state, characters,
modifiers and repeat into LWJGL. Android Back remains navigation. Hardware
mouse hover/position, primary/secondary/middle clicks and vertical wheel are
routed through the existing normalized frame geometry. Menu/focus transitions
and device removal release held input. These are new input paths; broad
keyboard-layout/IME/device qualification is not claimed by host tests.

## Full backup and restore

Open **Backups & migration** from Server or Client. Save/stop both runtimes and
wait for active file operations to finish. Export includes:

- the selected original server import and current working world;
- the before-start checkpoint and recovery-required marker, if present;
- the selected client import, its PlayerFiles/game bindings and mod files;
- persistent `managed-client/user` data;
- both enabled/disabled mod stores, manifests, configuration and mod-owned data;
- the controller profile and typed server/client/launcher preferences.

Generated JVM/session/frame caches, obsolete import generations, locks and logs
are excluded. Use Diagnostics for logs. This is a full-file backup, so it can
be large. Restore needs enough internal storage for the existing installation
and incoming backup together; the app checks available space while staging.

The archive has a versioned manifest and per-file SHA-256/size checks. Restore
stages files, validates all entries and preference types, then replaces both
sets of files under exclusive app/native ownership. A journal preserves the
previous roots/preferences until the commit point. Startup recovers an
interrupted transaction before permitting runtime launches. Completed/recovered
journals are renamed out of the recovery path before cleanup, so partial
cleanup cannot turn a committed restore into a rollback. If recovery cannot
finish, launch remains blocked and Backups exposes Retry recovery.

Restoring replaces all backed-up components; if a component is absent in the
backup, it is removed from the restored installation. Export the current state
first when needed. Cancel is checked during archive work and validation;
publication/recovery completes its short transaction. An incomplete export
must not be treated as a usable backup. After a successful restore, returning
to the launcher recreates its screens so old world selections cannot overwrite
the restored preferences.

## Moving from 0.10.38

0.10.39 uses the separate package
`io.github.russianranger.wurmlauncher.launcherpreview`, versionCode 53. Keep
0.10.38 installed. Its CI signing key was not persisted, so this release cannot
silently update its private files or retrofit an exporter into that APK.

1. In 0.10.38, normally stop client and server; export the **working server
   runtime ZIP**. This includes the current world and server mods.
2. Install 0.10.39 alongside it. Server → Setup & runtime → Import server ZIP.
3. Client → Setup & runtime → Import client ZIP. Use the same player name to
   rejoin the character stored in the migrated server world.
4. Import the client loader/Live Map again and reproduce client-only preferences
   as needed. The old server export does not contain those client files.
5. Verify play, save/reentry, then create a **complete backup** in the new app.
   Future compatible builds can restore this bundle directly.

No uninstall of the old app is needed for this test. A future persisted signing
identity is still required for seamless APK upgrades; backup portability is not
the same as Android's package-signature compatibility.

## Signing foundation

Gradle accepts `WURM_SIGNING_STORE`, `WURM_SIGNING_PASSWORD`,
`WURM_SIGNING_ALIAS` and `WURM_SIGNING_KEY_PASSWORD` for managed builds. If the
store is supplied, all values must be present. Without it, the managed preview
retains explicitly debug signing. No private key is created or committed by
this implementation. CI persistent-secret provisioning is not completed here.
Before a stable distribution, the owner must persist a signing key privately,
configure the release environment and keep the chosen application ID. Verify
an actual update preserves data. Never publish private signing material in
release artifacts or the source repository.

## Validation and next physical test

Automated coverage includes complete backup round-trip of both sides, world and
mod/config retention, rejected damaged/truncated/oversized archives, failure
at every publication boundary, restore into an empty installation and
maintenance/runtime exclusion. Input tests exercise text/character/repeat
delivery and reset at the LWJGL queue boundary; CI also checks the actual pinned
LWJGL implementation. Android compilation/lint/build and the existing runtime
suite remain required release gates. See HANDOFF.md for completed run results.

On the Thor:

1. Import as above; check tab selection, Play/Resume, settings search/save and
   both mod lists. Move between launcher and game without stopping either side.
2. Select game chat, invoke the keyboard from the gear, compose/Insert, Enter,
   correct with Backspace, hide/reopen without submitting an unsent draft.
   Verify typing never moves the character. Try a game rename/search dialog.
3. If available, test keyboard movement/release, shifted letters, modifiers,
   mouse right-click/wheel and controller transitions. Unplug a held keyboard
   key/controller and confirm movement stops. Exercise screen recording and
   normal background/return.
4. Change a world value, player binding, controller mapping, graphics choice
   and mod toggle; normally stop both and export a complete backup. Change
   those again, restore, and verify the backed-up choices/world return. Keep
   the exported ZIP and older app until verified.
5. Stop normally and export the support bundle from Diagnostics. Report actual
   IME behavior, input issues, backup size/time and whether restoration preserved
   each tested item. Native performance tuning and automatic stock-server ZIP
   preparation remain later milestones.

API references: [Android keyboard visibility](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)
and [Android app signing](https://developer.android.com/studio/publish/app-signing).
