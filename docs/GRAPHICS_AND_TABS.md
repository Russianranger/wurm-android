# 0.10.34 graphics audit and launcher tabs

## Latest device evidence

Reviewed the 0.10.32 client report exported as `wurm-client-report(20260911-152658).txt`, the accompanying server report, and `Wurm Server_2026-09-11 10_20_44.mp4`. The filenames are later than some report contents; use embedded UTC timestamps and PIDs. Client reports include server history, and retained observations duplicate console entries; do not count those as additional events.

| Session | Observed result |
| --- | --- |
| Client PID 7722, entry 13:58:35 to 14:00:33 UTC | Entry exit 0; game loop reached |
| Client PID 11179, entry 15:18:14 to 15:20:04 UTC | Entry exit 0; game loop reached |
| Client PID 11705, entry 15:20:10 to 15:20:54 UTC | Entry exit 0; reconnect and game loop reached |
| Server PID 7490, exit 14:00:45 UTC | Requested shutdown, exit 0; approximately 142 seconds |
| Server PID 10869, exit 15:21:06 UTC | Requested shutdown, exit 0; approximately 186 seconds |

The user confirms repeated logout/login and app quit/reentry work, with object pop-in resolved. No fatal ASan report, SIGSEGV, OOM or GLES readback error appears in the client portions. Across 51 frame-timing samples the median presented rate is 29.8 FPS, ranging from 4.8 during loading to 30.2. Short intervals can slightly exceed the nominal 30 FPS target.

Recoverable startup GL errors remain: 16 unique origin records across three PIDs and ten pre-readback `IllegalStateException` reports in the first session, 13:58:46–13:59:03. Later attempts each have two recorded origin events during startup. These are not evidence of a crash. `glBindFramebuffer:263` is an error collection point that can report an earlier pending driver error, not conclusive attribution to the bind itself.

Initial heap growth causes full collections of roughly 23–248 ms. Each exit has two explicit collections, approximately 328–498 ms; their timestamps coincide with teardown. Client PSS rises from initial loading to 1462–1667 MiB in the available 30-second samples. Android PSS is about 103–154 MiB, server up to 881 MiB; no swap is recorded. These short runs do not establish a leak or replace the preceding longer-run GC investigation. Heap sizes and collectors are unchanged.

Server warnings remain duplicate templates (eye and guard-tower IDs 384, 430, 528, 638) and the login-server TimeSync notice. At 13:58:49 an INFO intrateleport record includes an Exception stack after a large position discrepancy during initial loading; it recovers and the server continues. Two other Exception stacks accompany explicitly requested shutdowns. They must not be presented as unhandled server failures. Monitor the position correction if the user experiences rubber-banding.

## Beam flicker and depth precision

Frames from the video show parts of cross-beams breaking up with distance while the building geometry remains visible. This is consistent with depth competition between nearby surfaces; the video alone does not prove the cause. It differs from the earlier unsupported occlusion-query problem that hid entire nearby objects.

Source inspection found three relevant paths:

1. The owned EGL window requested a minimum 16-bit depth buffer and did not report the actual selected precision. It now prefers 24 bits, falls back to 16 only when no matching 24-bit config is offered, verifies the chosen depth, and records `[graphics-depth] EGL_CONFIG preferred=24 selected=... fallback16=...`.
2. Wurm's FBO requests unsized `GL_DEPTH_COMPONENT` renderbuffers. Pinned GL4ES mapped those to 16 bits. Unsized requests now use 24 bits when GL4ES reports support; the existing fallback remains.
3. Unsized depth textures requested with unsigned-byte input selected unsigned-short storage. They now choose unsigned-int storage when depth24 is supported. Explicitly sized/input-type choices and existing conversion policies are otherwise retained.

The patch is downstream, guarded by hashes of the pinned original GL4ES files. It does not change Wurm geometry, camera planes or occlusion policy. EGL config selection and the actual extracted format-conversion decisions have host regressions covering supported depth24, depth16 fallback, explicit formats and failures. Hardware allocation/rendering and reduction of flicker still require the next Thor test; exact coplanar geometry or texture aliasing may remain even with more precision.

## Graphics options inventory

Audited public option fields, constructor labels/ranges, mutability groups and setters from the privately supplied client. Inspection identities:

- `Options.class`: SHA-256 `c8e240acafd430ca80dd0d9c5be9df37a3ccf5aa49e574f2a44adbd8653f6a14`
- `renderer/backend/FBO.class`: SHA-256 `1461cb33bc67df90353f974aceb3449494776fbdf2d8fead50ef8b081995932d`

No proprietary classes or disassembly are committed. Runtime enum-label checks reject a changed ABI before any setting is applied. Preferences remain in the Android app; the imported profile is not rewritten by this adapter. Settings marked restart are applied before launching the engine and are skipped by live requests. Legacy 17- and 41-field commands still work; omitted new fields inherit the captured base values. All 47 settings default to inherited values, preserving the established preset unless changed by the user.

| Group | Exposed controls | Application |
| --- | --- | --- |
| Water | Water detail; reflections; reflection texture quality | Live |
| Distance | Tree; building; item/creature distances; model detail distance; enable model LOD | Live |
| Small objects | Contribution culling on/off; threshold 0–200 | Live; independent of disabled occlusion queries |
| World | Detailed trees; weather particles; sun glare; cave detail; tile transitions | Live |
| Particles and animation | Opaque/transparent particles; own-character animation; GPU skinning | Live |
| Lighting | Shadow level/resolution; bloom; vignette; FXAA; limit/max dynamic lights | Live; renderer capability dependent |
| Offscreen quality | Offscreen texture quality; supersampling; high-resolution binoculars | Live; potentially large performance/memory cost |
| Texture loading | Anisotropic filtering; max texture quality; player texture size; terrain texture size; texture scaling filter | Restart; avoids mixed old/new loaded textures |
| Terrain and shading | Terrain detail; normal maps; max shader lights; ground decoration density; sky detail; distant terrain | Restart |
| Texture format | Compression and S3TC compression | Restart; the client still disables requests if the required extensions are missing |
| Brightness | Game brightness −100% to +100% | Live; maps to the inspected postprocess brightness range −1 to +1 |
| Animation loading | Model loading thread count; model animation detail | Restart |
| View | Horizontal field of view 60–110; game font smoothing | Restart |
| Owned viewer | 800×480, 960×540, 1280×720 render resolution | Restart |
| Owned viewer | 15/30 FPS; fullscreen; gear expand/collapse; opacity | Live |

The audit distinguishes live-change categories from actual consumers: ground decorations, sky detail, distant terrain and compression are marked unready by the original live console but are read by renderer/loading code. They are therefore exposed at startup. Brightness is read by PostProcessRenderer and is exposed live; it is not a desktop display-gamma control.

The first 17 controls remain in their original protocol positions; 30 additional controls follow. Frame target/resolution and fullscreen/opacity are separate from the 47 client options.

### Explicit exclusions shown by the menu

The menu includes **Compatibility and unavailable options**. These controls are not silently missing:

| Options | Reason |
| --- | --- |
| Occlusion queries | Forced off by the working visibility correction; do not re-enable through preferences |
| Desktop MSAA samples / VSync | No complete connection to the owned pbuffer/frame-file viewer; use FXAA/frame target |
| Fog-coordinate source | No consumer found in the inspected client; no functional control exposed |
| Modern/deferred renderer and GL extension switches (GLSL, VBO, FBO, multidraw, NPOT, automatic mipmaps, depth clamping) | GLHelper requires desktop OpenGL 3.3 for deferred shading; this compatibility runtime advertises GL 2.1. Low-level capabilities stay managed |
| Fast-yield/clock workarounds, OS thread priority, native/background FPS limit and timers | Runtime scheduling controls, not alternate graphics quality controls; owned frame pacing remains authoritative |
| Phobia models, camera bob/third-person, selection outlines, GUI skin/transparency/font sizing, screenshot format | Gameplay/accessibility/UI preferences; not included in this graphics pass |
| Debug rendering switches, forced season, residency and instrumentation flags | Diagnostic/developer behavior, not supported gameplay graphics controls |

This is a coverage audit of the inspected client version, not a promise that every desktop feature is supported by GL4ES. There is no arbitrary option-name console in the graphics menu.

## Three tabs and lifecycle

`ManagedActivity` now hosts three persistent, individually scrolling pages beneath a fixed tab bar. Selected tab and scroll offsets are saved across Activity recreation. `ClientActivity` remains a compatibility redirect. Notification and in-game navigation target the host with clear-top/single-top flags, preventing duplicate launcher stacks.

| Tab | Contents |
| --- | --- |
| Server | Import; world; settings; start/stop/restart/force stop; export working runtime/checkpoint; restore with existing confirmations |
| Client | Import; player name; graphics; controller bindings; Start Local Game/Start Client; Return to Game; Stop Client |
| Diagnostics | Native startup/JVM memory/controller/window/graphics tests; stop test; both session exports; world/configuration/storage reports; storage baseline/check; client/server output |

The game gear retains gameplay controls and links to Client and Diagnostics. Inline debug output moves to Diagnostics. Only the visible launcher page refreshes; basic pages do not concatenate or redraw client/server logs. All diagnostic tests that take the client process are disabled during active client work. Foreground-service ownership and shutdown behavior remain independent of tab changes.

## Validation and next device test

Focused host tests cover live/startup separation, ranges, invalid commands, full rollback, enum ABI, old commands, config fallback and pinned depth conversions. The 41-option intermediate passed the full 154-test host suite and Android build/unit/lint gates; the final 47-option revision repeats the focused settings checks and all CI gates before release. See the maintained handoff for final release verification.

1. Keep 0.10.32 and backups. Install the separate `.graphicstabs` APK, import the stopped working server export and client ZIP, and select the same world/player.
2. Set 1280×720 and your previous distances. Start local play. Repeat the video’s approach/retreat at the same building. Keep other settings unchanged for this first comparison.
3. From the gear, open Diagnostics, then each tab, then Client → Return to Game. Verify the game/server keep running, tab labels remain visible and the player-name dialog does not pull the page scroll.
4. Test one live change, such as weather particles. Set a different anisotropic filtering value: it must wait for a client restart. Restart and check both retained settings and appearance.
5. Exercise logout/reconnect and normal stop. Export both reports from Diagnostics. The new depth record will show whether the device selected 24-bit precision. If beams still flicker, send a comparable clip; do not assume increasing draw distance will fix it.

Remaining work after this test: confirm depth allocations and texture appearance on Adreno; assess any GC stutter in a longer run; consider a separately tested frame-buffer reuse optimization. No blanket GC disabling or collector change is part of this release.
