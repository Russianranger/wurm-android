# 0.10.41 — automatic stock-server preparation

Continue on `mod-launcher-test`. Main is unchanged. The user confirmed that
0.10.40 keyboard submission and complete backup export/restore work on the Thor.
The supplied untouched server ZIP now qualifies the missing stock preparation
recipe. Physical play/save/reentry from that stock ZIP remains the next check.

## Inputs and device evidence

The 81,233-byte support ZIP has SHA-256
`5704ad6062d029b1aa21627b28d708247569b3d2f6ad77d0bbefba4e9b2734df`.
Its header identifies `.keyboardprep`, exported 2026-09-13T12:27:22.637585Z.
The client entry exits 0; the server exits 0 after a requested normal stop,
75,661 ms after launch. Survival and LiveMap each reach loader-ready count 1;
the server records a successful player login. Eleven retained presentation
samples have median 30 FPS, including startup samples below that. No new fatal
ASan, OOM or fatal-signal marker was found. The shutdown exception stack is the
game's logged normal shutdown call, not a new crash. This short run is not a
soak test; keyboard and backup behavior are user-confirmed.

The untouched `WurmServerLauncher.zip` is 71,068,436 bytes, SHA-256
`1fe671b9437248ce0fb5796ea964464126f98fd351d71ccd68a221785335e941`.
It contains 711 files (726 ZIP entries), 230,917,305 uncompressed bytes.
Only these exact game pairs are accepted:

| File | Stock SHA-256 | Previously prepared SHA-256 |
| --- | --- | --- |
| server.jar | `ba5301b2e9b56dc9ab7eae9d8ac45188336835e37184e329c90128ea8ab01f64` | `9ea2761f210e05e7080777e988ddc0bd04e6fa5221813cdf141881cfb8ec8e06` |
| common.jar | `066fe846ac3ea3d1a85e070ed452c43e8e390cbfa112a9c0d7eaff3fbe531633` | Same |

The two server JARs have identical entry sets. Exactly four active classes
differ: ItemDbStrings, BodyDbStrings, CoinDbStrings and FrozenItemDbStrings.
CreaturePos, LoginHandler and all other entries are byte-identical. Proprietary
files, classes, worlds and disassembly stay outside the repository and APK.

## Preparation and compatibility

**Server → Setup & runtime → Prepare / import server ZIP** recognizes the
stock pair as `thor-stock-sqlite-1`; the older prepared recipe remains supported.
Inside unpublished staging, it moves the desktop `dist/` children into the
runtime root, rejecting all root/dist collisions before moving any entry.
Adventure, Creative, recipes and migrations retain their bytes. Launch uses the
game's existing `wurm.distRoot` property to point to this flattened root; older
exports with a `dist/` directory keep that directory as their resource root.
The existing wrapper-directory, ZIP path/duplicate/CRC/size, storage reserve,
atomic selection and original/working/checkpoint protections remain.

The app supplies both checksum-verified SQLite 3.53.2.1 artifacts and its
source-backed POC offline, then writes the recipe manifest and final inventory.
Game JARs are never rewritten. Unknown versions or changed dependencies fail
before activation. Import does not execute Wurm or open its databases.

Before each server start, preflight generates a disposable `server-items.jar`
alongside the existing position and login overlays. Four exact stock class
hashes are required. Two inspected SQL constants per class are redirected to
the **same UPDATE statements already returned by that class's personal-server
`setLastMaintainedOld` and `setDamageOld` methods**. Bindings remain timestamp/id
and damage/timestamp/id. Every method body, branch, stack map and debug attribute
is retained; all four output class hashes are pinned too.

This follows the existing personal-server and proven prepared-runtime behavior:
update existing rows, preserve row identity/other columns/children, and do not
recreate a missing item. It does not introduce an upsert or replacement-row
policy. SQLite [UPSERT](https://www.sqlite.org/lang_upsert.html) has different
insert behavior; it is deliberately not this recipe. FrozenItem's original
SQL spacing is retained exactly. Other SQL methods retain their original results.

An existing prepared JAR uses an empty item overlay and its four known active
class hashes; none of its item code changes. Before Wurm opens the world, the
launcher checks the selected item-class resources against the applicable pins.
The item overlay precedes server.jar with and without mods. Resource verification
does not load/freeze game classes before mod hooks. The pinned Javassist loader
can still modify the generated classes. Renderer/audio, ASan, GC, keyboard,
login checks, POC and SQLite versions remain unchanged.

A working export of a stock import still contains the original stock server.jar.
It is reusable by this launcher, which regenerates overlays at startup; it is
not a standalone patched desktop/Termux runtime.

## Validation

- Android API compilation and 35 focused Kotlin/JUnit tests: existing input,
  original/working storage, stock layout/content preservation and reimport,
  conflicting layout/unknown-pair rejection, dependency corruption/cancellation,
  import accounting, and mod/overlay/resource launch paths.
- Full local host suite: 174 tests passed with 16 unavailable fixture/platform
  skips. All six new item checks passed with the private stock/prepared inputs,
  exact SQLite JDBC and pinned Javassist.
- Actual SQL-holder methods were loaded from the supplied stock JAR and overlay;
  all zero-argument SQL methods match the original except the two intentionally
  redirected methods, which exactly match their personal-server counterparts.
  JDBC batches confirm 64-bit bindings, row/column preservation and rollback.
  Authored tests also enforce child retention, no DELETE, and missing-row no-op.
- All four original SQLite table schemas were copied into disposable in-memory
  databases: updates and rollback pass without opening a world for play.
- End-to-end import of the exact ZIP: Adventure and Creative discovered; every
  original file's content verified unchanged; 715 final files, approximately
  245.5 MB including generated dependencies/manifest. Host preflight passes all
  three overlays and Java/SQLite checks with every imported byte preserved.
  The actual game folder APIs recognize both worlds and all 581 recipes using
  the launch resource-root setting. Native Android startup still needs the Thor.
- [Release CI 34758094487](https://github.com/Russianranger/wurm-android/actions/runs/34758094487)
  passed all three Android build/unit/lint variants and required native/input
  and APK packaging gates. Initial host suite: 174 tests with 21 expected
  fixture/platform skips. Published APK checksum, package/version and v2
  signature independently verified; exact release identity is in HANDOFF.md.
  CI builds without proprietary inputs; private qualification fixtures are
  optional locally and do not upload the supplied files.

## Thor test

VersionCode 55, package `io.github.russianranger.wurmlauncher.stockprep`, installed
alongside 0.10.40 because preview signing still uses a per-run key. Keep the
working older app and its complete backup.

1. For the **new stock preparation test**, open the fresh 0.10.41 app and choose
   Server → Setup & runtime → Prepare / import server ZIP. Select the untouched
   WurmServerLauncher.zip exactly as supplied; no repacking/compiler is needed.
   Leave the previous app stopped while testing the same local port.
2. Choose Adventure or Creative and start the server. The report should contain
   `ITEM_PATCH_READY mode=stock-personal-updates`, four `ITEM_PATCH_ACTIVE` lines,
   `SERVER_PREFLIGHT_OK`, and normal server readiness. Check recipe/world loading.
3. Import the usual client ZIP under Client to test play against this fresh
   world. Log in, change inventory or move an item, save/stop normally, restart,
   and verify persistence. Then test your usual mods if desired.
4. Export a support bundle and send it back. The existing one-original-import
   rule remains: a complete old backup restore occupies that slot, so do the
   stock test before restoring a full backup into this fresh package.
5. To continue the previous world instead, restore the complete 0.10.40 backup
   through Backups & migration. This tests the retained prepared-runtime path,
   not new stock preparation. No separate client import is needed for that route.
