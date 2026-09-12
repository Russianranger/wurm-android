# 0.10.35 — world settings and in-game bindings

The user reports 0.10.34 stable on the AYN Thor. This build adds settings without
changing the graphics compatibility patches, audio, memory collectors, heap sizes,
server launch/stop behavior or the three-tab layout.

## Server gameplay settings

Open **Server → World gameplay settings** with the server stopped. Values come
from the selected world's `wurm.ini` DB_HOST and its SQLite `SERVERS` local row.
Nothing is replaced by assumed defaults. Worlds pointing to the same database
share these values; the editor displays that database and server ID.

| Control | Accepted values and meaning |
| --- | --- |
| Skill gain multiplier | 0.01 or higher; higher increases gain |
| Action speed multiplier | 0.01 or higher; higher shortens standard actions |
| Starting characteristics, Mind Logic, Body Control, fighting, other skills | Each 1–100; new characters only |
| Field growth interval | At least 0.01 hours; lower is faster; stored in milliseconds |
| Tree spread odds | Nonnegative integer; 1 in N chance; zero disables spread |
| Breeding speed modifier | Positive integer; app minimum 1 avoids zero-divisor behavior |
| Maximum creatures | Nonnegative integer; app minimum 0; higher populations cost resources |
| Aggressive creature percentage | 0 or higher, matching the server editor's lower bound |
| Minimum mining hits | Positive integer; app minimum 1 |
| Settlement upkeep, free settlement founding, random spawning | On / off |

The inspected server editor supplies no explicit maximum for the multipliers,
field interval or percentage. The app does not label an invented maximum as a
game rule. It rejects NaN/infinity, numeric overflow and fractional integers.
Integer/long/float storage limits still apply. The three additional app minimums
are labeled in the form. Unchanged imported values, including values outside
these editing bounds, are preserved. Existing characters' skills are not reset.

Save affects the next server start. Settings are unavailable during an active
server/storage operation or unresolved recovery. The workspace's existing native
lock remains authoritative. An absent/ambiguous local row or missing configured
database blocks edits; there is no guessed fallback path or new database creation.
Only changed allowlisted columns in the unique local row are updated, in one
SQLite transaction. Reopening rejects stale values or a changed working copy.

Before a write, SQLite `VACUUM INTO` creates a consistent login-database backup,
including committed WAL contents, under `android-settings-backups/<id>.db` in the
working runtime. The file is flushed and atomically published before the update.
Backups travel with an exported working ZIP. A backup error aborts the save;
a failed SQL update rolls back. These are database backups, not complete world
checkpoints; the original import and normal before-start checkpoint still exist.

## In-game gear menu

- **Game keybindings** reads Wurm's actual action/category and key catalogs at
  runtime. The inspected client has 351 built-in actions and 93 nonempty base-key
  choices. Existing custom bound actions also appear. Unbound actions are included.
- Pick a category and action. Edit its comma-separated keys, or use the key picker
  and ALT/CTRL/SHIFT checkboxes. Blank unbinds that action. Save one action at a time;
  up to eight keys are supported. Clear a conflicting binding before reassigning
  its key. The live game's key translator also detects legacy aliases and different
  modifier order, so a key is not silently stolen from another action.
- The editor validates the active profile, file revision and game-thread ownership.
  It uses the existing headless store's atomic save and first-save `.android-backup`.
  It preserves comments and unrelated bindings. Files containing other console
  commands are displayed but not rewritten. External file changes fail visibly.
- Save reloads Wurm's console bindings and refreshes the action-bar/menu labels
  on the game thread. An acknowledgment confirms completion. If live refresh fails
  after a successful save, the message explicitly says to restart the client.
  A timeout means unconfirmed: use Refresh to inspect the actual state.
- **Controller mappings** opens the existing handheld editor while the game keeps
  running. Save and return; the viewer releases held input and reloads the profile
  on resume. Close the gear panel to play with the new mapping. This remains distinct
  from assigning a keyboard key to a Wurm action.
- **1280 × 720** is now the default when the resolution preference is absent or
  invalid. Explicit 800 × 480 or 960 × 540 choices are preserved. Resolution changes
  still need a client restart; fullscreen is independent and already defaults on.

## Evidence and tests

Private inspection used the owner's unchanged server JAR SHA-256
`9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06` and client JAR
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
Relevant APIs: ServerPropertySheet/CustomPropertyItem bounds, ServerEntry save
columns, ServerDirInfo selected INI, SqliteConnectionFactory DB_HOST paths;
PlayerKeybind/PlayerKeybindCategory/KeybindButtons metadata, active PlayerProfile
binding file, WurmConsole translator and executeKeybinds, HUD updateBinds and
SelectBar.updateActions. Proprietary JARs, disassembly and assets stay private.
The real client catalogs initialize on the host without JavaFX.

New tests use real host SQLite behind a narrow database adapter for reopen,
neighbor/column preservation, exact unedited milliseconds, stale snapshots,
transaction rollback and WAL-inclusive backup. Typed bound tests cover fractions,
nonfinite values and overflow. Keybinding protocol fixtures cover live apply,
unbinding, backup/reload, duplicate and alias conflicts, stale revisions, external
edits, custom-command preservation, loading/wrong-thread refusal and live-refresh
failure. Android API compilation, host suite, Android unit/lint/build and APK
identity checks are release gates. Host tests do not establish physical gameplay.

## Next Thor test

1. Keep 0.10.34 installed. Stop the old server normally and export its **working**
   runtime ZIP. Install 0.10.35 (`.worldcontrols`) alongside it; import that ZIP and
   the existing client ZIP. The separate package preserves the old app's data.
2. Before starting, open World gameplay settings. Record the skill multiplier,
   change it to a modest value such as 2, save, reopen and confirm. Start local play.
   Existing skills should remain; actions should use the configured rules. Starting
   skill settings only affect newly created characters.
3. Confirm the new default frame size is 1280 × 720. Open gear → Game keybindings;
   bind an unused key to an action, save and test it. Try a duplicate and verify it
   reports the conflict. Restart the client and confirm persistence.
4. Open gear → Controller mappings; map a spare button to that keyboard key, save,
   return and close the gear panel. Confirm it works without a client restart and
   no movement/button remains held. Test cancel/back as well as Save.
5. Logout/reenter, then stop both processes normally. Send Client and Server reports
   from Diagnostics, noting any editor message or unexpected settings behavior.
