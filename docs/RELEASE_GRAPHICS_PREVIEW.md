# Wurm Server 0.10.18 — shader and frame performance fix

## 0.10.17 device result

The Thor completed character setup using the new controls and rendered buildings,
terrain, trees and actors. The last retained frame recorded 445 applied input events.
The client then exited 139 (SIGSEGV) after approximately 119 seconds. Its native
stack enters Adreno glDrawElements from GL4ES fpe_glDrawElements, draw_renderlist,
and glDrawArrays. The server report remained Running and TCP-reachable.

The retained client log contains 1,955 occurrences of the seven-compilation-errors
summary. These include repeated output for the same failures, not 1,955 distinct
shaders. No evidence establishes Java heap exhaustion as the crash cause.

## Reproduced shader defect

GL4ES wraps Wurm's shaders to add fixed-function alpha testing. The pinned custom
fragment wrapper inserted its global declarations on the line before main.
When that line is #endif, both _gl4es_FragColor and _gl4es_AlphaRef end up inside an
optional block. Disabling that block removes the declarations, causing exactly
the undeclared-identifier failures in the report. GL4ES retries its failed
customization on later draws.

A checked source patch inserts declarations at the main declaration and recomputes
the alpha-uniform insertion point after earlier changes. Alpha testing is retained.
A small authored shader reproduces failure with the original native wrapper and
compiles with the fixed wrapper on a real host GLES compiler. Tests cover enabled
and disabled conditionals and LF/CRLF. No proprietary shader is committed.

## Performance changes and diagnostics

The diagnostic viewer previously presented at most five frames per second,
regardless of Wurm's internal FPS. This release targets 15 presented FPS, checks for
new images every 33 ms, transfers rows/pixels in bulk and reuses the Android bitmap.
Transient frame publication retains atomic rename but no longer forces flash
storage after each frame. World/database/checkpoint durability code is unchanged.

A local 960x540 frame-publication benchmark measured about 6.09 ms before and
3.04 ms afterward. This measures host frame encoding/publication only; it is not a
Thor performance claim. FRAME_TIMING reports internal swap rate, presented rate,
GPU readback time and publication time every five seconds. GPU readback and file
transfer are still an interim display path.

The Adreno crash is **not yet proven resolved**. A fixed 1 KiB shared breadcrumb
records the actual GL4ES draw parameters, active attributes and buffer bindings
immediately before the driver call, then marks a successful return. It uses no
per-draw syscalls or log output. After client exit, Client Report includes NATIVE_DRAW
even if shader output displaced earlier logs. Tests verify abrupt-exit retention,
completed calls, invalid records and unavailable diagnostic storage.

The build checks whole source hashes and ships original source archives, the
patch script and authored diagnostic header. Touch/controller controls, server
bootstrap and separate server lifetime are retained. The optional window test now
understands pointer frame metadata, and the original eight-button protocol is retained.

## Thor test — preserve the old world and use a test character

The working-runtime export contains the world and its characters. The synthetic
local login identity lives separately in each app's client user-home directory.
This release does not migrate that identity. Importing the world alone does not
let a new app log back in as the old character. Keep 0.10.17 and its data.

1. In **0.10.17**, use **Stop Server**, wait until stopped, then **Export working
   runtime ZIP**. Keep the ZIP and old app as your character/world backup.
2. Install **0.10.18** (code 32, separate shaderperf package). Import the working
   server ZIP and your same complete client ZIP; select **Adventure** and a new
   test-character name such as **Thorperf**. The imported world retains Thor's
   data, but Thorperf uses this new app's local identity.
3. Start Local Game and complete setup for Thorperf. If using your original prepared
   server ZIP instead, an unused name such as Thor can be created afresh.
4. Move and look around for two minutes, including the area where it crashed.
   Note perceived smoothness and missing/incorrect graphics; take a screenshot.
5. If the client crashes, export **Client Report before Retry**. Stop Server
   normally, then export **Server Session Report**. Send both and the screenshot.
6. If stable, stop Client and Server normally and export both reports anyway.
   Restart in this same app without reimporting to check Thorperf's persistence.

A future client identity export/import is needed for straightforward character
continuity between separate preview packages. No password checks or character
records are changed by this release.
