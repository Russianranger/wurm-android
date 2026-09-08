# Thor 0.6.0 world/configuration observation PASS — 2026-09-08

The user confirmed closing/reopening the app with the world report intact.
The supplied world and session reports confirm a completed observation inside
the ordinary-UID server process, TCP readiness and requested normal exit 0.
No new APK or repeat storage baseline is needed to accept this result.

## Evidence identity

| Field | Recorded value |
| --- | --- |
| App/device | 0.6.0 managed preview; Android 13 / API 33 |
| UID/eUID | 10199 |
| Launch ID | `371c42de-b929-4152-b2ff-730a405d3597` |
| Working copy | `work-0924d255-2ee7-4432-b46a-382d6500ec93` |
| Selected/recognized GameFolder | `Adventure` |
| Heap / expected TCP | 4096 MiB / 3724 |
| Report created / last updated (UTC) | `19:07:42.284330` / `19:08:53.232958` |
| Child snapshot (UTC) | `19:08:07.380787082` through `19:08:07.428348957` |
| Preflight / server PID | 11996 / 12099 |

Supplied attachment identities distinguish these files from earlier reports
with the same filenames. Raw reports and game files are not committed.

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `wurm-world-report.txt` | 4615 | `ad9a970831df06f7b481c3243272e32c4b0689e0b5fa5ec607220c613f37d2d8` |
| `wurm-server-report.txt` | 26166 | `4ce0879328d0114fd1616e1f36ab65329ab23b317f48acefbc7797bf5daed9d2` |

## Confirmed paths and listeners

- POC selected and recognized `Adventure`, forced personal-server mode and
  requested offline startup. The POC returned and loopback TCP 3724 was reachable.
- Five map files were actually open: `Adventure/flags.map`, `map_cave.map`,
  `resources.map`, `rock_layer.map` and `top_layer.map`.
- Wurm/Flyway logged nine database opens under `localhost/sqlite`: creatures,
  deities, economy, items, login, logs, players, templates and zones.
- At snapshot time, eight of those main database files were still open, each
  with `-wal` and `-shm` sidecars; eight SHM files were also memory-mapped.
  `wurmlogs.db` was logged at startup but was not among the observed open FDs.
  A transient snapshot does not establish that this database is unused.
- FD enumeration completed with zero skipped entries. Both proc TCP tables were
  readable. Two listeners matched the Java child's own socket inodes:
  `[::]:3724` and `[::]:48020`, recorded in the `tcp6` table. No matching listeners
  were recorded in the `tcp` table. The separate IPv4 loopback connection to
  `127.0.0.1:3724` succeeded.
- The Steam shim received game port 3724 and query port 27016. This remains
  synthetic Steam configuration evidence. The probe inspects TCP only and does
  not qualify UDP/query networking. The purpose of listener 48020 is unknown;
  do not assign it a role based only on its port number.

The observed IPv6 wildcard binding and successful local IPv4 connection establish
more than an assumed game port. They do not establish reachability from another
device, client login or gameplay. No network/configuration change is justified
solely by the presence of the additional listener.

The earlier guide expected proc TCP access might be denied on this Android
device. **It succeeded in this Thor server child.** Keep denial handling for
other devices, but do not describe proc inspection as blocked in this result.

## Configuration evidence and persistence scope

Root `wurm.ini` was absent. `Adventure/wurm.ini` and `localhost/wurm.ini` each
had 2174 bytes and the same SHA-256:
`e57c5084f6174b5b9893b1db5963fc435986d4d043509bdc911e6429bd380ddd`.
Both exposed the literals `DB_PORT=1` and `DB_HOST=localhost`. These equal files
do not distinguish which candidate Wurm read. Do not interpret DB_PORT as the
game's listening port or change it to 3724. The effective database location is
independently established by JDBC logs and actual open files.

The live WAL/SHM observations establish that those sidecars were present and
open during this server run. They do not establish interrupted-WAL recovery or
the stopped audit's behavior on a surviving WAL. The earlier 0.5.0 stopped checks
reported `wal=false`; retain that distinction.

The final lifecycle in the saved world report is:

```text
Child exited 0; requested=true; normalStop=true; forced=false; startupCancelled=false
```

The session independently records `PREFLIGHT_PASS`, `TCP_READY`,
`SHUTDOWN_REQUESTED` and
`SERVER_EXIT=0; stopRequested=true; force=false; startupCancelled=false`.
Its fresh-app status is `Stopped — No server process owned by this app`, which
is consistent with reopening after shutdown. The user's confirmation supplies
the app close/reopen observation; there is one exported world report, not a
separate before/after report pair.

The [0.5.0 changed-file persistence PASS](THOR_STORAGE_PASS.md) remains accepted.
This 0.6.0 result additionally qualifies report persistence, live path observation
and child listener inspection. It does not yet qualify a specific gameplay save
or the Wurm item insert/update SQL paths.

## Next physical gate

Keep the working 0.6.0 installation. Establish which existing compatible Wurm
client and second device are available before giving platform-specific commands.
With that device on the same local network, test TCP reachability to the Thor's
LAN address on 3724, then attempt a supported client connection. Treat network
reachability, authentication/login and entering the world as separate results.

Once a client/admin route works, make one identifiable persistent gameplay change,
Stop normally, start the same working copy and verify that change in Wurm.
Include an item insert/update path to exercise the preserved SQLite fix. Export
session evidence for failures before considering configuration changes. Android
client execution remains its own development task; it is not enabled by this test.

This evidence update changes only this file, `README.md`,
`IMPLEMENTATION_PLAN.md` and `WORLD_CONFIGURATION_TEST.md`. The tested APK, POC,
JRE, SQL patches and game files remain unchanged.
