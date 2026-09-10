# Wurm Server 0.10.16 — existing runtime import fix

0.10.15 changed the packaged bootstrap to enable personal-server character creation,
but retained an importer check requiring any archived bootstrap to match it exactly.
Existing prepared runtime ZIPs containing the older bootstrap were therefore rejected
before server startup. This is an importer regression; the report does not test the
0.10.15 character-creation fix.

## Change

Import accepts the current bootstrap unchanged, injects it when absent, and upgrades
only the exact authored bootstrap shipped through 0.10.14:
`0fe4039a1a06afae93099b6eaf140e04fe7e0b1f1145323116468f8f78a884fe`.
The replacement is the verified packaged 0.10.15 bootstrap:
`82a39c9797a394b036785ad366e5c1a6ed0de935ab1f3b82e1fcc80f5181dfa4`.

Replacement happens only in private staging, before publication. The source ZIP,
game JARs, world databases and configuration bytes are preserved. Manifest size and
hash reflect the replacement. Other bootstrap hashes remain rejected. An unsuccessful
import preserves the previously committed runtime. A successful legacy migration logs
`POC_UPGRADED`. No desktop launcher or archive editing is required.

## Validation and remaining gate

The regression was reproduced with the actual historical authored bootstrap.
All 12 importer JUnit tests pass after the fix. They cover root and wrapped archives,
preserved game/world/configuration bytes, manifest totals and reopened state, limits,
unknown/altered bootstrap rejection, and rollback. CI must also build all APK variants,
run Android unit tests/lint and verify packaged artifacts before publication.

The personal-server change from 0.10.15 is retained. On-device import, first-time
character creation, world entry and persistence still require testing. The earlier
EGL/Scudo exit issue is unresolved.

## Thor test

1. Install **0.10.16** (code 30, separate importupgrade package). Stop older servers
   and clients; retain their data and your original ZIPs.
2. Import the same prepared server ZIP. A legacy bootstrap should log
   **POC_UPGRADED** and import should complete. If it fails, export **Session Report**
   and send it before attempting server/client startup.
3. Select **Adventure**, import the same complete client ZIP and use player **Thor**.
4. Use this version's **Start Local Game**. Complete in-game character setup if shown.
5. If the world appears, test movement/look/clicks briefly and take a screenshot.
6. Stop Client and export **Client Report**. Stop Server, wait, and export
   **Server Session Report**. Send both reports and the screenshot.
7. After successful creation and clean shutdown, start again without reimporting
   and check that the character and location persist.
