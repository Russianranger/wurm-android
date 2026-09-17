# Wurm Android handoff

Updated: 2026-09-17. Keep this file current when investigating, changing, or releasing the app. Start here when continuing in a new chat; then read the linked release/review documents and current source. Do not rely on a previous chat being available.

## Latest release — 0.10.51 inventory and frame delivery

User authorized resolving the support7 findings. Version 0.10.51/code 65 uses
separate `.inventoryframes` package, branch `mod-launcher-test`, immutable tag
`v0.10.51-inventory-frames`. Main and old releases remain preserved. See
[CLIENT_INVENTORY_FRAME_FIX.md](CLIENT_INVENTORY_FRAME_FIX.md).

Device follow-up: support8 now verifies the inventory improvement on the Thor.
See [CLIENT_INVENTORY_FRAME_DEVICE_REVIEW_20260917.md](CLIENT_INVENTORY_FRAME_DEVICE_REVIEW_20260917.md).
Observed inventory allocation is 4.44/4.64 KiB per call versus roughly 40-45 KiB
in support7. Common initial 210-second spans show 39.33 FPS delivery off / 45.30 on;
game-thread publication falls from 2.954 to 0.043 ms with negligible buffer wait.
Inventory activity differs, so the observed 15.2% FPS gain is not an isolated
feature-effect estimate. The on recording ends with normal client exit after
about 3m41s, without a five-minute END marker. Keep both improvements; next target
is the remaining 14.17 ms client work plus 7.75 ms readback (~22 ms serial frame).
All four client attempts and the server exit normally. No new fatal/frame errors;
short captures do not establish or exclude a leak. Two startup Epic mission
backup-map warnings are recorded separately; storage audit was not run. No repeat
of the unchanged diagnostic build is needed before the next investigation.

- Inventory: one exact-hash/reversible invocation patch in the tree-list panel.
  One reusable matcher per worker, exact regex semantics, input cleared after
  each check. Focused 100,000-call host allocation: 89,600,000 -> 5,600,000 bytes
  (93.75% reduction for this check only). Locale/edge/random/concurrent/retention
  tests and exact private class reversal/JVM verification pass. Existing text
  buffer reuse is unchanged; support8 subsequently verifies lower per-render
  inventory allocation on device, with workload limits explained in its review.
- Frame path: optional background publication, enabled by default in Diagnostics.
  Two direct pixel buffers, one worker, FIFO ownership and backpressure. Same
  GPU readback and atomic V3 pixels/metadata; close/resize drain before reuse.
  Extra pixel capacity at 720p is 3.52 MiB. Focused ownership, blocked-writer,
  error, exact-pixel/metadata, reader lifetime and both window mode tests pass.
- New timing fields separate queue/buffer wait from worker publication. Async
  writerMs overlaps game work and must not be added to its serial stage budget.
  Existing presentedFps is captures/submissions; UI_TIMING is delivered FPS.
- Device procedure: brief 30 FPS visual/input check, then matched five-minute
  60 FPS recordings with background delivery off and on. Same inventory contents,
  camera and open/closed sequence; keep text reuse/profiling on and periodic
  cleanup skipping off. No forced GC, heap limit or GPU policy changes.
- Full local host suite passes: 215 tests, 37 expected unavailable fixture/platform
  skips, no failures. Includes exact private inventory/GUI/text overlays and all
  text-reuse/profiling combinations. Android CI and downloaded-APK verification
  are complete. The first device comparison is reviewed above; rendering/readback
  investigation remains next.

Released from commit `fd00614b7ef746fc11934a95e92b2c04b0425751`, tree
`f3f80fe5a7a810727be4de16725fd2584c00ec00`; immutable tag points to that
commit. Main independently remains `2e41fb091ee75a76b9116e934abecff90bc735d9`.

- [Download 0.10.51](https://github.com/Russianranger/wurm-android/releases/download/v0.10.51-inventory-frames/Wurm-Server.apk),
  69,352,606 bytes, SHA-256
  `8f5ac3aab5bb4c540d71e470124d881048f6899ca69c459ccc66b79ddc652c65`.
  Published 2026-09-16T09:44:38Z as a prerelease with 63 assets. APK and new test
  guide match the downloaded SHA256SUMS and GitHub asset digests; guide also
  matches tagged source, SHA-256
  `d004f7dcdc2623eba2eb22ea20510211747cd057adf3109304b30f7214ee0f1e`.
- [CI 35080246140](https://github.com/Russianranger/wurm-android/actions/runs/35080246140)
  passes: build `104742246579`, publisher `104744997367`. CI host suite passes
  215 tests with 34 expected private/platform skips. All three Android variants
  build, unit test and lint successfully; native graphics/input, packaging,
  source and signature checks pass. Expected host EGL skip remains. CI matcher
  allocation is 91,072,520 -> 6,167,848 bytes (about 93.2% for the isolated check).
- Downloaded manifest confirms `0.10.51-managed-preview`, code 65, package
  `io.github.russianranger.wurmlauncher.inventoryframes`. Managed verifier and
  independent APK v2 verification pass. Certificate SHA-256
  `2902aff0f1456fd3f2852707b52231d35b9ff26b81d795292c82a855ded3f8d4`
  matches CI; prior apps/signatures/data remain preserved by the separate package.
- All seven focused tests pass against downloaded runtime bytecode: exact private
  inventory/GUI patch verification, matcher semantics/allocation/retention,
  asynchronous publisher ownership/backpressure/atomicity/errors, both window
  publication modes and existing raw publisher behavior. Shipped matcher result
  is 89,600,000 -> 5,600,000 heap bytes per 100,000 calls; GUI wrapper measured zero
  heap bytes in both active and idle loops. No device FPS or leak-fix claim.
- Versus 0.10.50, seven of nine JVM JARs have identical members. Changed members
  are only ClientGraphicsPatch, ClientGuiPatch, new inventory matcher/patch,
  WindowBackend and new FramePublisher classes. All 194 JRE-data ZIP members,
  fantasy WebP images and medieval font are identical. ZIP container metadata
  differs; comparing extracted members confirms unchanged runtime content.
  Of 40 native libraries, 39 match byte-for-byte; GL4ES differs only in its
  20-byte GNU build ID and five ASCII build-time digits. No native rendering
  implementation changed. This final maintenance commit changes handoff only.

## Previous release — 0.10.50 GUI/frame attribution

User resumed performance work: remaining GUI allocations, then 60 FPS rendering/
readback slowdown. Exact support6 and private client were recovered and hashes
match the previous review. The user's new support7 bundle now provides two full
five-minute recordings on 0.10.50. See
[CLIENT_GUI_FRAME_DEVICE_REVIEW_20260916.md](CLIENT_GUI_FRAME_DEVICE_REVIEW_20260916.md).
Both client sessions and server exit normally. InventoryWindow accounts for
85.3%/87.6% of measured GUI heap allocation (45.3/40.4 KiB per render call).
Text reuse remains effective. Viewer averages are 29.70/48.42 FPS; the 60 FPS
frame budget is about 12.01 ms client work + 5.67 ms readback + 2.91 ms publication,
with negligible pacing. Brief stalls align with GC. Memory remains substantial;
this five-minute evidence neither establishes nor excludes a retained leak.
Inventory render workloads differ between tests, so this is not an FPS-only A/B.
Implementation, full local host verification, Android CI and downloaded-APK
verification are complete. Stay on
`mod-launcher-test`; main and prior releases/data remain preserved. Version
0.10.50/code 64 uses separate `.guiframe` package and `v0.10.50-gui-frame`.
See [CLIENT_GUI_FRAME_TEST.md](CLIENT_GUI_FRAME_TEST.md).

- Existing job recording now also attributes six verified GUI call sites to
  top-level component classes and overlays. Same five-minute sampler/lifecycle;
  fixed 128-pair table, all retained GUI rows printed, no receiver/queue retention.
  Production overlay is hash-pinned and byte-exact reversible. Exact imported
  renderer passes JVM verification; authored execution tests preserve dispatch,
  arguments and exception identity. Warmed 100,000-call host measurement:
  128 bytes recording, zero idle. Device attribution now identifies inventory
  rendering as the dominant GUI allocator; the individual allocator still needs
  local measurement before patching.
- Reusable frame publisher preserves exact V3 pixels and atomic open-reader
  lifetime, handles resize and releases views on close. Local 2,000-frame heap
  comparison: 3,104,120 to 1,136,000 bytes (~63% publisher-only reduction).
  Channel/open/rename allocations remain; no device FPS or GUI savings claim.
- Frame logs now expose work between swaps, pacing, capture checks/restoration,
  readback and EGL swap, with maxima/counts/size and reset on target/size changes.
  Readback includes pending rendering; no thermal cause is proven. No GPU
  synchronization, readback algorithm, heap/GC policy or text-pool change.
- New focused tests cover exact patch reversal/verifier, bounded/non-retaining
  scopes, original exceptions, atomic frame readers, publication failures,
  ownership, resize, pack restoration and frame-stage observations. Existing
  full-overlay expected counts updated for the extra optional renderer class.
- Full local host suite: 212 tests, 37 expected unavailable fixture/platform
  skips, no failures. It includes all exact-client text-buffer/geometry/release
  regressions and all four job-profiling/text-reuse overlay combinations.
  The final GUI fixture additionally waits for the initial sampler snapshot
  before asserting per-window counts; its focused rerun passes (192 recording
  bytes / 100,000 calls, zero idle).
- Next implementation: measure/optimize the numeric alignment check in
  `WurmTreeList$TreeListPanel.renderComponent`. Exact private-client inspection
  confirms per-column regex construction and String.matches in this render loop;
  its individual share of device inventory allocation is not measured yet.
  Preserve locale/alignment semantics, use bounded non-retaining state and the
  existing exact/reversible overlay gates. No unchanged diagnostic rerun is
  needed. Then validate inventory behavior/allocations on device and continue
  with the remaining rendering/readback budget. This attribution build is not
  a completed GUI/60 FPS fix. The support7 review changes documentation only.

Released from commit `4000442fbb924b21c6dc03122628560e2a3a11dc`, tree
`1a84584ba5d0b5db06c37282dd6c8a275d693a14`; immutable tag points to that
commit. Main independently remains `2e41fb091ee75a76b9116e934abecff90bc735d9`.

- [Download 0.10.50](https://github.com/Russianranger/wurm-android/releases/download/v0.10.50-gui-frame/Wurm-Server.apk),
  69,342,286 bytes, SHA-256
  `7d398e93125abe7b51b74de7dd416a3a8c7445ae66b519e197d6c339f4c51109`.
  Published 2026-09-16T06:55:46Z as a prerelease with 62 assets. APK matches
  SHA256SUMS and GitHub asset digest. New guide matches tagged source and GitHub
  digest `a934129a2ce394484bd00ae38968fedf781a018bcdd9f3dbc85ded17dae75596`.
  The existing manually enumerated checksum command omitted the new guide/theme
  documents; this maintenance commit adds them for future releases. Published
  APK, guide, checksum manifest and tag are left unchanged.
- [CI 35065373784](https://github.com/Russianranger/wurm-android/actions/runs/35065373784)
  passes: build `104694361360`, publisher `104696153621`. Full CI host suite
  passes 212 tests with 33 expected private/platform skips. All three Android
  variants build, unit test and lint successfully; native graphics/input,
  packaging, source and signature gates pass. Expected host EGL skip remains.
- Downloaded binary manifest confirms `0.10.50-managed-preview`, code 64,
  `io.github.russianranger.wurmlauncher.guiframe`. Managed verifier and independent
  APK v2 verification pass. Certificate SHA-256
  `908f2544dc1beac3d6f8b10af282599f57fa9c24a10b6cc8f469fd8f8291276a`
  matches CI. Prior apps and their signatures/data are preserved.
- Shipped-bytecode checks pass all four focused tests: exact private renderer
  reversal/JVM verification, GUI dispatch/retention/recording, WindowBackend swap
  semantics/stages and RawWriter atomicity/allocation. Published GUI wrapper:
  192 bytes per 100,000 recording calls, zero idle. Published publisher: 3,104,248
  to 1,136,664 heap bytes for 2,000 frames. The initial shipped-window check found
  the authored GL stub had a void createCapabilities signature and placeholder
  enum values. This maintenance commit corrects the fixture to the real return
  descriptor and enum values; the shipped window then passes. No production
  bytecode/APK change was needed. This remains host boundary evidence, not device
  gameplay/GPU performance qualification.
- Versus 0.10.49, six of nine JVM JARs have identical members. Changed members
  are only ClientGraphicsPatch, new GUI helper/adapter classes, ClientJobProfiler
  and its nested classes, FrameFile/RawWriter and WindowBackend. Text pool and
  original text adapters are byte-identical. All 194 JRE-data members, fantasy
  WebP images and medieval font are identical. Of 40 native libraries, 39 match;
  GL4ES differs only in its 20-byte GNU build ID and three build-time digits.
  No native rendering implementation changed.

## Previous release — 0.10.49 oak and stone launcher

User paused performance testing and requested a large oak/fantastical W APK icon,
fantasy homestead/exploration backgrounds for each tab, medieval typography and
castle-like tiles. Work stays on `mod-launcher-test`; main and previous apps/data
are preserved. Version 0.10.49/code 63 uses separate `.oaktheme` package and
immutable tag `v0.10.49-oak-theme`. See [OAK_THEME.md](OAK_THEME.md).

Five original images were created with the built-in image generator and packaged
as bounded-size WebP resources. Each of the existing four tabs has its own scene.
MedievalSharp is bundled with its OFL license. LauncherUI buttons and sections,
header, adaptive icon/splash, page panels and managed theme are updated. Only
one scene is referenced at a time and it is released in onStop. No runtime-probe,
native, game font/rendering or storage logic change. Build and published-APK verification
are complete as recorded below. Next technical work remains allocation/readback
from the review below; do not interpret the visual release as a performance fix.

Released from commit `d336de3c8fd77743635f2ae71cacb2afcf60ccfd`, tree
`2510bb18b06daeff3efcadf1dd4c8a120ae832ce`. The immutable tag points to that
commit. Main is still `2e41fb091ee75a76b9116e934abecff90bc735d9`.

- [Download 0.10.49](https://github.com/Russianranger/wurm-android/releases/download/v0.10.49-oak-theme/Wurm-Server.apk),
  69,329,994 bytes; SHA-256
  `a853dbd29a266d53ca4579d800dc3d52189435e75ba214002866e18f61176a3d`.
  Published 2026-09-16T01:17:49Z as a prerelease, 59 assets. Downloaded APK
  matches both GitHub's asset digest and SHA256SUMS. The release publisher's
  explicit asset list omitted the two new theme documents; they are available
  in the tagged repository and build artifact. The following maintenance commit
  includes them in future publications; this APK/tag is left unchanged.
- [CI 35042772039](https://github.com/Russianranger/wurm-android/actions/runs/35042772039)
  passes. Build job `104625991245`, publisher `104628146389`. The host suite
  passes 208 tests with 32 expected unavailable/private/platform skips. All
  three Android variants pass build, unit tests and lint; native/input and
  source/packaging/signature gates pass. Expected host-EGL skip remains.
- Independent downloaded-APK checks pass: version `0.10.49-managed-preview`,
  code 63, package `io.github.russianranger.wurmlauncher.oaktheme`, APK v2
  signature and managed runtime verifier. Certificate SHA-256
  `61734f573f2abe0dd88d927fdb097583647aa76b5a2849c50823f49adcc7144f`
  matches CI. Five WebP images, medieval font and its full OFL license are
  present in the published APK.
- Every member of all nine JVM JARs and the 194-member JRE data ZIP matches
  0.10.48, including runtime-probe (73), client compatibility (20), graphics
  probe (5), window (27) and LWJGL API (1,573). Of 40 native libraries, 39 are
  identical. GL4ES has exactly 26 different bytes: 20 GNU build-ID bytes plus
  six compilation date/time digits. Native code and JVM behavior are unchanged.
- Local UI-only assembly and lint pass (zero lint errors). The visual-check APK
  deliberately omits native/runtime preparation and is never distributed.
  The Client landscape screen was visually inspected at 1280×800/213 dpi;
  oak icon, medieval font, selected tab, panel contrast and control layout render
  correctly. A fresh-install screenshot is saved under
  `docs/screenshots/oak-client-landscape.webp` (runtime imports are absent).
  Software-emulated Android 33 first boot took about 16 minutes. Its System UI
  had to be disabled locally to obtain that view; subsequent Android system ANRs
  prevented reliable remaining-tab, portrait and large-font verification. These
  test-emulator changes are not in the APK and are not user-device evidence.
  Physical-device visual review remains required. No new gameplay recording is
  requested. Do not claim all layouts were verified on a device.

## Previous release — 0.10.48 completed text buffer release fix

User authorized “Work on the next fix” after the 0.10.47 review. Implementation, tests,
CI and independent published-APK verification are complete. The full host suite
passes 208 tests with 37 expected unavailable fixture/platform skips, no failures.
Stay on `mod-launcher-test`, keep main and prior releases/data. Version 0.10.48,
code 62, separate package `io.github.russianranger.wurmlauncher.textreleasefix`,
immutable tag `v0.10.48-text-release-fix`.
See [CLIENT_TEXT_BUFFER_RELEASE_FIX.md](CLIENT_TEXT_BUFFER_RELEASE_FIX.md).

Latest device evidence: [0.10.48 60/30 FPS review](CLIENT_TEXT_RELEASE_DEVICE_REVIEW_20260915.md).
The user ran 60 FPS first (PID 7031), then 30 FPS (PID 8029), each with a complete
five-minute recording. The release fix now works on-device: 646,491 / 846,573
reuses within recording, 95.730% / 97.959% of text factory calls, with all sampled
rejection counters zero. Successful VBO-layout returns equal all returns. Pool
sampled peaks are only 234,120 / 340,680 bytes and 130 / 173 entries.
Both clients exit zero and the shared server completes requested saves/database
close and exits zero. No crash/OOM/ASan/client-GL failure is found. Existing
startup/shutdown warnings remain; storage audit was not performed.

The 60-target run averages 47.12 displayed FPS and falls from first-minute 54.24
to last-minute 40.73; reported readback rises from 4.89 to 7.94 ms. Its largest
frame gap is 430.54 ms around young+full GC; one full collection is 318.411 ms.
The 30-target recording averages 29.90 FPS, maximum gap 113.28 ms, with two young
collections and no full collection during recording. No thermal cause is proven.

Remaining GUI allocation is workload-dependent: printed GUI rows total 79.286 /
311.625 MiB, 6.292 / 36.349 KiB per listed call. The second run handles about 98.4
text requests per listed GUI call versus 52.3 in the first; this is not a clean
FPS-only comparison. Relative to the old 32.353 KiB/call recording, do not claim
uniform GUI improvement. Larger GUI allocations also occur on other workers;
Job_executor_0 is not shown defective. Top-row coverage is 76.14% / 94.27%.

Client PSS peaks 1,788.5 / 1,927.4 MiB and ends recording 1,749.7 / 1,611.3 MiB.
Direct capacity peaks 355.5 / 346.1 MiB and drops after natural GC. The second
ends near its initial direct capacity (201.3 → 203.9 MiB). This shows reclaimable
memory, not a leak verdict or a complete memory fix. Recorder threads stop on
both deadlines. Keep this successful release fix; next identify the remaining
high-allocation GUI callees and investigate 60-target rendering/readback cost.
No unchanged release-path recording or synthetic stress run is needed.

The exact client was recovered and SHA-verified again. Original legacy VBO
bind leaves `boundBufferObject` true; it records pointer layout, not ownership.
Renderer completion waits for jobs, flushes the pipeline and clears the HUD
queue. The regression now runs original SimpleTextFont, Queue sort/render/clear
and VertexBuffer against authored glyph/GL boundaries. Sort is required even
with sorting disabled to initialize draw indices. Before correction all 120
rendered returns were rejected; only 19 undrawn fallback reuses survived.
After correction: 120 VBO-layout returns, 133 total reuses, zero rejected returns,
zero evictions; all geometry and 120 draw submissions match reuse-off data/order.
These are host boundaries, not a real Android GL context.

The helper no longer rejects the saved layout and does not reset it or change
GL state. Reference/lock/size/GPU-mode/direct-storage/duplicate/disabled checks
remain. New fixed counters classify first rejection reasons at release/borrow,
and count accepted VBO-layout returns. No per-call log records/diagnostic objects.
The existing capacity, zeroing, expiry and original upload/deletion paths remain.
Only ClientTextBuffers changes production JVM behavior; adapters are unchanged.
ClientSession's stale generic gate status is refreshed. Twelve focused tests
pass, including exact private legacy drawing, GPU upload/VAO/deferred deletion,
all rejection guards, bounded/exclusive lifetime and full optional overlays.
Host success workload: 5,376,880 versus 72 allocated heap bytes; forced shared
reference rejection: 5,376,000 original versus 5,376,880 adapter bytes for 16,000
measured iterations. These figures are not device GUI-job or PSS predictions.

The requested short recordings have now been received and evaluated above.
Both use reuse/job profiling on and skip-periodic-cleanup off. Release/reuse is
qualified on-device; complete memory/performance qualification remains pending.
Do not claim the overall memory problem is fixed.

Released from implementation commit `9e3186bfe38e1648c8da26bc9dce46725698f0b5`,
tree `70fe1893e65170ee0279641f34afce150321037f`. This subsequent documentation-only
handoff commit does not change the APK. Main independently confirmed unchanged:
`2e41fb091ee75a76b9116e934abecff90bc735d9`.

- [Download 0.10.48 APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.48-text-release-fix/Wurm-Server.apk),
  68,584,353 bytes, SHA-256
  `d2f2c51bd8e8c32d8cdd36845ab027f3903f7d02bf79a411012be4f8f5db1f27`.
  Published 2026-09-15T14:31:52Z as a prerelease with 59 assets. Downloaded APK
  and test guide match SHA256SUMS and GitHub asset digests; the guide matches
  the release source. Version `0.10.48-managed-preview`, code 62 and separate
  `.textreleasefix` package verified from the binary manifest. APK v2 signature
  and managed packaging checks pass. This build's debug certificate SHA-256 is
  `43a95759854d0b90b93b3a75a6d2c8285a36051aa85df59143746538c5543461`,
  independently matching CI; the separate package preserves older apps.
- [CI run 34981005646](https://github.com/Russianranger/wurm-android/actions/runs/34981005646)
  passes on that implementation commit. Build job `104420996399`; managed
  publisher `104425047490`; unrelated publishers skipped. CI full host suite:
  208 tests, 32 expected unavailable/private/platform skips. All three Android
  variants pass builds, unit tests and lint. Native/input regressions, signature,
  source and packaging gates pass, with the expected unavailable host-EGL skip.
- Compared independently with 0.10.47: all 194 JRE data members and all LWJGL API,
  client compatibility, graphics-probe and window JAR members are byte-identical.
  Of 73 runtime-probe JAR members, **only ClientTextBuffers.class changes**;
  existing adapters, recorder, server and persistence classes are identical.
  Of 40 native libraries, 39 are identical. GL4ES differs only in its GNU build
  ID and six compile-time digits (25 changed bytes total); no native code change.
- The downloaded runtime-probe JAR has SHA-256
  `010a365206b8429005b5322cf91ecbbe89fa47b207166e942aaab21cebf03c01`.
  All 18 focused text-buffer and job-recorder tests pass against these published
  helper bytes and the exact private client. Class-load logs confirm the tested
  helpers come from the downloaded JAR. Full legacy draw submissions/geometry
  match, 120 VBO returns and 133 reuses succeed with zero rejected returns,
  GPU upload/VAO/deferred deletion, ownership guards and optional overlays pass.
  Recorder expiry/cancellation/reference release and real executor callbacks/
  shutdown pass. Published success benchmark: 5,379,072 original versus 72 pooled
  heap bytes; forced-rejection benchmark: 5,378,048 original versus 5,376,000
  adapter bytes, for 16,000 measured iterations. This is focused host/JIT evidence,
  not device performance, memory-residency or a leak verdict.

## Previous release — 0.10.47 bounded text buffer reuse

Latest device evidence: [0.10.47 reuse review](CLIENT_TEXT_BUFFER_REUSE_REVIEW_20260915.md).
User confirms legible text with no visible degradation. The 13:52Z support export
contains one approximately 10m21s 30 FPS session and a complete five-minute memory
recording. Client/server exit cleanly, saves/database close complete, no crash,
OOM, ASan/client-GL error or server SEVERE record is found. However, **reuse does
not work in this device run**: all 82 distinct samples show zero reuses/returns;
523,092 fresh buffers are created during recording, and the last ordinary sample
has 1,015,783 total creations. All three overlays and the enabled property are
confirmed. The release eligibility check rejects registered returns; the stale
fixed-function bound-buffer flag is the leading hypothesis, not a logged reason.
The host VAO-path test did not qualify this complete device draw/release path.

Recorded job allocations are 340.426 MiB versus 225.179 MiB previously; printed
GUI bytes are 278.219 versus 174.937 MiB, 32.353 versus 20.391 KiB per GUI call.
Workload differences are not controlled, so do not assign all excess bytes to the
adapter. Warm viewer mean remains about 29.807 FPS. GC-related gaps reach 617.68
ms; the ten-minute periodic collection adds a 560.548 ms pause after recording.
Client sampled PSS peaks at 1,816.9 MiB then ends recording at 1,647.3 MiB;
lower than before but not a pooling benefit or leak verdict. Detailed limits,
counters and method are in the review. Storage audit was not run.


User authorized the next steps after the completed 0.10.46 memory review. Stay
on mod-launcher-test; keep main and prior working releases/data. See
[CLIENT_TEXT_BUFFER_REUSE_TEST.md](CLIENT_TEXT_BUFFER_REUSE_TEST.md) for scope,
ownership, limits, host evidence and a short 30 FPS device recording.

The exact private client was recovered again and SHA-verified:
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
SimpleTextFont creates and releases one VertexBuffer per string. Authored
ClientTextPatch adapters preserve original instruction bodies and prove byte-exact
reverse hashes of SimpleTextFont, Queue and VertexBuffer. ClientTextBuffers
reuses exact-size eligible buffers only after their original release point.
It caps registered active+idle entries at 256 / 4 MiB system capacity; GPU logical
payload can add up to 4 MiB (driver/deferred/native residency is separate).
Idle expiry is lazy after 30 seconds. Original cleanup handles eviction and
ineligible/shared/locked/bound/incompatible buffers. No scheduler/collector/native
or server changes. The final VertexBuffer factory boolean is unused: GPU mode
comes from useVBO and is checked explicitly; do not assume CPU-only storage.

Reuse defaults on through the Diagnostics preference; off removes the three
text overlay classes next start. Existing job recorder/FPS options remain.
Counters are retained in runtime observations and memory recordings. Approximate
engine buffer accounting is not retained memory. Nine focused tests pass,
including exact private text geometry, GPU upload/VAO/deferred-deletion boundaries,
queue lifetime, full optional overlay combinations, concurrency and limits.
A warmed 16,000-draw buffer workload allocates 5,376,880 bytes without reuse versus
72 with it; this does not establish whole-game or Android improvement. The native
driver/glyph atlas are authored host boundaries, not a real game render.

Released 0.10.47/code 61, version `0.10.47-managed-preview`, separate package
`io.github.russianranger.wurmlauncher.textbuffertest`, immutable tag
`v0.10.47-text-buffer-reuse`. Implementation commit
`3b0320c543c42860536f4d1758878af9e6425b54`, tree
`8e07d8c01256346b8cc1313275836743c477d8d1`. This later handoff-only commit does
not change the APK. Main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`.

- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.47-text-buffer-reuse/Wurm-Server.apk),
  68,583,905 bytes; SHA-256
  `22330e543d6aad28d3f61bbdbfece72a90f63bc689dcb0baf4727ce7cc48d4b7`.
  Published 2026-09-15T01:12:50Z as a prerelease with 58 assets. Independently
  downloaded APK and test guide match SHA256SUMS/GitHub digests; the guide also
  matches the release source. Package/version, managed packaging and APK v2
  signature checks pass. Certificate SHA-256:
  `986e7abc268f8b5afea67223977e7ea90613444c5f71d1dc0a9cc7482a142bdb`.
- [CI run 34915652141](https://github.com/Russianranger/wurm-android/actions/runs/34915652141)
  passes on the implementation commit. Build job `104212569991`, managed publisher
  `104214634070`; unrelated publishers skipped. CI host suite: 205 tests with 31
  expected unavailable/private/platform skips. All three Android variants pass
  builds, unit tests and lint. Required native/input, signature, source and package
  gates pass; one expected unavailable-host-EGL check is skipped. Local suite:
  205 tests, 37 expected unavailable fixture/platform skips, no failures.
- Independently compared with 0.10.46: all 194 JRE data members and all members
  of the LWJGL API, client compatibility, graphics-probe and window JARs are
  byte-identical. Of 40 native libraries, 39 are identical; GL4ES differs only
  in the 20-byte GNU build ID and six compile-date/time digits. Runtime-probe
  adds six authored text helper classes, changes three existing top-level client
  classes, and changes only source line numbers in six nested diagnostic classes.
  The nested classes were compared using normalized verbose javap output; their
  instructions/constants are unchanged. Server/persistence classes are unchanged.
- Executed the downloaded runtime-probe bytes against the exact private client:
  all 15 focused text-reuse and job-recorder tests pass. Class-load logs confirm
  helper classes came from the published JAR, not host compilation. Geometry
  remains byte-identical, GPU upload/VAO/deferred deletion and original queue
  release pass, all four optional overlay combinations pass, and recorder expiry,
  cancellation, reference release and real executor callbacks/shutdown pass.
  Published helper SHA-256:
  `6e0aba9ef4438e23de15d0ebbf4b5eb58f808b52c5ef553f548499f860e2adf3`.
  Its warmed 16,000-draw buffer workload measured 5,377,904 heap bytes without
  reuse and zero with reuse in that run (JIT/host dependent). GPU/glyph boundaries
  are authored fixtures, not a real device rendering qualification.

The requested device recording is received and evaluated above. Next reproduce
and correct the rejected return in the exact fixed-function draw/Queue cleanup
path. Preserve ownership and GPU state; do not blindly remove the binding guard.
Include allocation measurements for the failed-return path and, if needed,
bounded rejection-reason counters. Another unchanged device recording is not
needed. Text reuse can be disabled for ordinary play until a corrected build is
qualified. Continue direct/PSS investigation separately after reuse actually
works. Also refresh ClientSession's stale generic Gate status paragraph in that
next build. This review changes documentation only; the 0.10.47 release is intact.

## Previous release — 0.10.46 job memory recording and FPS targets

User confirms the new character in the last bundle was intentional. No restore
failure is indicated by that creation. User requests Job_executor_0 investigation,
a safe memory-testing path and selectable 40/50/60 FPS alongside 30.

See [CLIENT_JOB_MEMORY_FPS_TEST.md](CLIENT_JOB_MEMORY_FPS_TEST.md). Work stays on
mod-launcher-test. The exact private client was recovered and SHA-verified.
The scheduler scans from worker zero; its high allocation share is not proof of
a broken worker. Multiple rendering/animation jobs use this executor. Do not
claim a job allocation fix before observing which jobs allocate during play.

Latest device evidence: [0.10.46 job memory/FPS review](CLIENT_JOB_MEMORY_FPS_REVIEW_20260915.md).
The 00:15Z support export contains about 6m48s at 30 FPS and 1m07s at a 60 FPS
target, with clean client exits and requested, non-forced server exit 0 after
completed saves/database close. Mean displayed FPS after initial warm-up is
29.855 and 56.171 respectively. User reports FPS controls worked; retained
timing/acceptance evidence covers 30 and 60, not 40/50. No crash, OOM, GL error,
ASan error or recurrence of the prior clothing/spawn cluster was found.

The 30 FPS job recording completes automatically after 300,044 ms. It attributes
at least 77.688% of 225.179 MiB observed completed-job allocations to
com.wurmonline.client.renderer.gui.Renderer (174.937 MiB, about 20.4 KiB per
listed call); 119.038 MiB of those bytes run on Job_executor_0. This is now a
concrete GUI rendering/callee inspection target, not a scheduler fix or proof
of a particular allocated object/leak. The 128-pair cap omits attribution for
2.359% of calls but their bytes still enter totals; all printed rows have zero
failures and allocation counters are available. Top-pair figures are lower
bounds and the 77.688% share is not a share of all client/native memory.

Client PSS during recording rises from 1,201 MiB to a 2,323 MiB peak, then ends
at 1,922 MiB; direct buffers go 159 -> 489 -> 278 MiB at those samples. Natural
collection reclaims some memory, but long-run retention remains unresolved.
The largest 30 FPS viewer gap is 412.42 ms alongside a young+full collection.
There is no later in-play full collection to establish a settled heap floor.
Recorder expiry removes its thread. Both launches have skipPeriodic=false and
end before the ten-minute request; do not compare this with the prior long
on-run as proof of a regression or improvement. Next inspect the exact GUI
Renderer/callees and direct/resource buffer lifetimes; do not ask for another
identical attribution test before that inspection. This review is docs only.

- Optional Diagnostics job-profiling setting defaults off and applies next start.
  Off retains the existing nine-entry private overlay. On adds the verified
  Executor call adapter, reversed to the exact original SHA during verification.
  Only Job.execute dispatch is observed; callback, scheduling, argument ownership
  and exception propagation are preserved. No proprietary bytes are published.
- Gear/Diagnostics memory dialog starts/stops a five-minute bounded recording:
  at most 128 primitive/text thread-job entries, top 12 each 30 seconds, five-second
  memory samples on a daemon. No forced GC, heap dump, stack sampling, workload
  stress, server command or references to jobs, arguments or game threads. Missing counters
  disable recording. Existing normal pressure/shutdown cleanup and ASan remain.
- Record allocations, natural post-GC heap and direct/PSS separately. Job counters
  cannot establish retained memory or native leaks; callback/incomplete/non-job
  work is excluded and elapsed job time includes waits/GC. Runtime observations
  retain recording and frame-target events with PID/time. Targeted diagnosis,
  not a claimed memory cure.
- Graphics offers 30/40/50/60 and legacy 15 FPS; default 30, persisted/live with
  acceptance feedback. Higher targets use 8 ms viewer polling, existing bounded
  frame reuse and single in-flight read. No catch-up bursts after stalled frames.
  Actual FPS/temperature and device profiling overhead remain unqualified.
- Local host suite: 194 tests passed, 37 unavailable fixture/platform skips;
  later focused job suite adds full optional overlay and overhead qualification.
  All six job tests pass, including real private manager/executor callbacks and
  shutdown, exact reverse, object weak-reference release, expiry/cancel/restart.
  Warmed host hook cost for 100,000 no-op calls: inactive 0 heap bytes, active
  128 bytes total; 31/164 ns per call in that run, not Android performance.
  All 101 Kotlin application/test sources compile; 13 focused graphics/frame
  JUnit tests pass. CI and published-APK verification also passed below.
- Released 0.10.46/code 60, separate package
  `io.github.russianranger.wurmlauncher.jobprofiletest`, version name
  `0.10.46-managed-preview`; immutable release tag `v0.10.46-job-memory-fps`.
  Implementation commit `9e72f45c780a07dbc02cea1430a03b29ee29370b`, source tree
  `d54f0c905e6930676d3295193a915ff104e81bef`. Keep prior apps/data/releases.
  Main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.46-job-memory-fps/Wurm-Server.apk),
  68,569,113 bytes, SHA-256
  `5bbb32418bcd093a223a44a3c220d35941a70561d0ea2f8f49b196b847996c75`.
  Published 2026-09-14T17:13:16Z as a prerelease with 57 assets. Independently
  downloaded APK and guide match SHA256SUMS and GitHub asset digests; the guide
  also matches the committed source. Package/version, v2 signature and managed
  APK packaging checks pass. Certificate SHA-256:
  `822c9e52f69895a0519502531ca3b5e8a5d03c532cede04dcc0b257f2b80b94a`.
- [CI run 34872265992](https://github.com/Russianranger/wurm-android/actions/runs/34872265992)
  passed: build `104070799815`, managed publisher `104074409027`; unrelated
  publishers skipped. Host suite: 196 tests, 27 expected unavailable/private/
  platform skips. All three Android variants pass builds, unit tests and lint.
  Required native/input and packaging gates pass; one expected unavailable-host-
  EGL check is skipped.
- Independent comparison with 0.10.45: all 194 JRE members, LWJGL API,
  compatibility and graphics-probe JAR members are byte-identical. Of 40 native
  libraries, 39 are byte-identical; libgl4es differs only in its 20-byte GNU
  build ID and six compilation-time digits. Runtime-probe changes exactly three
  existing classes and adds six job helper classes; server/persistence classes
  are unchanged. Window JAR changes only FramePacer and WindowBackend.
- Executed the published runtime-probe bytes against the exact private client:
  all six job tests pass, including complete optional overlay selection, real
  manager/executor callbacks and shutdown, reverse-byte verification, lifetime,
  bounds, expiry, cancellation and restart. The published FramePacer passes all
  five targets and stalled-deadline recovery. Warmed published helper overhead:
  inactive 0 heap bytes and active 128 bytes total across 100,000 no-op calls;
  approximately 30/144 ns per call, host only. Runtime-probe SHA-256:
  `8e2170a8207227ddca5e88d52f1e0503fed650cacc56ef49188a1ebe9b1ed9ae`.
  Device profiling overhead and retained memory remain to be qualified; no
  allocation or memory-leak fix is claimed. The review above now supplies job
  attribution and short 30/60 FPS observations.
- The requested five-minute recording is received and reviewed above. Continue
  with the identified GUI allocation and direct-memory investigation. Future
  comparisons should keep world, route, FPS, mods and cleanup policy matched.
  Keep the prior working apps and backups; no new character is required.

## Previous release — 0.10.45 mipmap and allocation diagnostics

The user requests continued troubleshooting of the remaining findings. See
[CLIENT_MIPMAP_ALLOCATION_TEST.md](CLIENT_MIPMAP_ALLOCATION_TEST.md) for the exact
scope, limitations and original Thor test. Work remains on mod-launcher-test only.

Latest device evidence: [32-minute 0.10.45 review](CLIENT_MIPMAP_ALLOCATION_REVIEW_20260914.md).
The 13:53Z support export records 32m19s of play, clean client/server exit 0,
completed server saves/database close, and no recurrence of the startup mipmap
error with KHR_debug installed. Mean viewer FPS after 30 seconds is 29.937;
four later full collections take 453–659 ms and correlate with gaps up to
678.88 ms. Three World.tick requests are skipped as configured. Allocation
telemetry works: Job_executor_0 supplies 59.76% of observed heap churn after
warm-up (66.50% in complete final-five-minute windows), with a separate model
loader burst. Thread names do not identify tasks/classes or retained memory.
Client PSS still rises: median 1,361 MiB in minutes 5–10 versus 1,686 MiB in the
final five minutes, peak 1,754 MiB. No descriptor/thread growth or OOM is seen;
memory stability remains open. A login cluster contains 12 clothing warnings
and a spawn-tile/teleport warning; no later repetition or fatal outcome is
recorded. The server creates a new character, so verify expected world/character
continuity if this was intended as a restore test. Storage audit was not run.
Live Map is enabled and server mods are disabled; this is not a matched workload
comparison with 0.10.44. Next technical target: identify the allocating work on
Job_executor_0, separately investigate memory retention/loading bursts, and
track clothing/spawn behavior. Do not repeat the same test solely to prove the
skip switch activates. This review changes documentation only.

- Pinned GL4ES `realize_textures` attempted generation with no base image,
  could run on another driver texture unit when the binding already matched,
  used the caller's active-unit target for every iteration, and hardcoded the
  generation target. Correct those conditions; retain pending work until upload.
  Preserve compression/NPOT/automipmap/no-draw exclusions and explicit calls.
- Three direct GLES mipmap delegates expose fixed TLS context only during their
  calls. The synchronous KHR callback reads it through an exported GL4ES accessor,
  with site/target/texture/unit/format/size/valid/compressed fields. No extra GL
  queries, error consumption, texture bytes or path logging. Existing limits and
  optional-driver behavior remain. Other callback threads see no TLS context.
- The private TextureLoader warning drains an earlier error before a compressed
  upload; it does not prove that upload failed. The new host test reproduces the
  bad original calls against an authored driver boundary and passes patched
  behavior. Four whole patched native translation units compile on the host.
  The startup error is absent in the 32-minute 0.10.45 device run linked above.
  Rendered texture quality remains unqualified; the older in-play error has not
  recurred and is not declared fixed.
- Client-only allocation daemon: 30-second windows, at most 256 live IDs/counters,
  top four contributors, explicit new/reset/departed/unavailable/omitted coverage.
  Only matched live-thread deltas count; no game/thread objects, stack/heap walks,
  GC requests or retained-memory inference. Missing facilities cannot block play.
  Actual JVM churn attribution and counter reset/removal/lifetime tests pass.
- Enable client `gc+heap=info`; retain allocation/GC/safepoint evidence in bounded
  observations through console rotation. No client/server heap or collector
  change, no buffer-ownership change, no global GC suppression. Automatic stalls
  and rising PSS are still being investigated. The raw producer already reuses
  its direct image buffer; small per-frame wrappers are not evidence of a full
  heap-frame allocation. Do not speculate that a producer rewrite cures the GC.
- Local host suite: 190 tests, 20 expected fixture/platform skips. It includes
  actual private client overlay checks and pinned public GL4ES/LWJGL fixtures.
  All 100 Android/test Kotlin sources compile; four focused Kotlin/JUnit checks
  pass. CI and independent published-APK verification also passed (below).
- Released 0.10.45/code 59, package
  `io.github.russianranger.wurmlauncher.mipmaptest`, immutable tag
  `v0.10.45-mipmap-allocation`. Implementation commit
  `4aa6747cddc76353f35ae0a9262d1dcc4c54db82`, source tree
  `ec4ce399d2c02bd2f24f5f83f1e78bb2ce53dd7a`.
  Separate package preserves the prior working app while persistent signing
  remains pending. Do not mutate main or old releases.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.45-mipmap-allocation/Wurm-Server.apk),
  68,542,961 bytes, SHA-256
  `72e49dccf4c83d1be5e8123cbbb9b2d97f825830f1c259e164b7d760ce3df8fd`.
  Published 2026-09-14T01:37:38Z with 56 assets. Independently downloaded APK and
  new guide match SHA256SUMS and GitHub asset digests. Version/package and v2
  signature pass; CI certificate SHA-256
  `493388c77d3541a947c591644f1c0ff0909acaccfc9341201dffc0f5053b85a7`.
- [CI run 34796043544](https://github.com/Russianranger/wurm-android/actions/runs/34796043544)
  passed: build `103829226414`, managed publisher `103830561464`; unrelated
  publishers skipped. Initial host suite: 190 tests, 25 expected fixture/platform
  skips. All three Android variants passed builds, unit tests and lint. Required
  subsequent native/input gates include the new pinned mipmap regression and
  pass; one expected unavailable-host-EGL shader check is skipped. Native source,
  ASan and managed APK packaging checks pass.
- Independent comparison with 0.10.44: all 194 JRE members, all 17 server/persistence
  classes, all 27 window-JAR members, POC, LWJGL API and compatibility JARs are
  byte-identical. Two window notice ZIP timestamps differ. Of 40 native libraries,
  only libgl4es and libwurm_graphics change, as expected; the mipmap accessor is
  exported in the actual ARM64 dynamic symbol table. The only changed preexisting
  runtime helper is ClientBootstrap; exactly four allocation helper classes are new.
- Executed the published runtime-probe JAR against the exact private client:
  all nine overlay entries and real InternalPack mapping/WAV selection pass;
  replacement decodes to 8,820 silent PCM bytes at 44,100 Hz/one channel. Overlay
  content hashes remain the 0.10.44 values. Packaged allocation fixture attributes
  8,390,720 heap bytes to its controlled worker, passes resets/lifetime checks,
  and emits automatic GC/heap occupancy with no System.gc request. These are host
  executions of the packaged Java code, not device gameplay qualification.
  Runtime-probe SHA-256:
  `8c6a702167d32dfef77a1f6fe91836c949802bf3c01b2e7fbd27ce94905c475e`.
- Additional host allocation measurement of unchanged FrameFile.writeRgba:
  persistent 1280x720 direct buffer, 100 warm-up and 300 measured atomic writes,
  518,400 heap bytes total = 1,728 bytes/frame (51,840 bytes/s at 30 FPS), using
  LinuxFileSystemProvider. This isolates the publication method, excludes other
  render work and is not an Android allocation measurement. It supplies no basis
  for claiming this method creates a full heap frame each update.
- Main was independently verified unchanged at
  `2e41fb091ee75a76b9116e934abecff90bc735d9`.
- The requested 30–40-minute startup/allocation observation now has a 32-minute
  device report (linked above). It confirms telemetry and clean saves/exits;
  it does not verify rendered appearance or a restored-world restart. The new
  character creation must not be treated as proof of restore loss. Continue
  targeted executor allocation/retention investigation before another build;
  scheduled-skip activation itself is already qualified.
- No proprietary inputs, disassembly or raw reports are committed or published.

## Previous release — 0.10.44 session fixes

The user authorized work on all three findings from the 0.10.43 extended review.
See [CLIENT_SESSION_FIXES.md](CLIENT_SESSION_FIXES.md) for exact evidence,
implementation and two-run Thor comparison. Work remains on mod-launcher-test.

Latest device evidence: [37-minute on-run review](CLIENT_PERIODIC_GC_REVIEW_20260914.md).
The 2026-09-14 export contains 37m39s of gameplay, client PID 23164/server 23022.
Three actual World.tick requests are skipped at 00:16:46, 00:26:46 and 00:36:46Z;
their viewer windows show only approximately 50 ms maxima. The scheduled-skip
device check is complete. Mean viewer FPS after 30 seconds is 29.934, one reused
3.52 MiB payload and one skipped sequence. Both runtimes exit 0; server saves
and requested, non-forced shutdown are logged. No new fatal/audio/database error.
One familiar startup texture-level 0x502 remains, with no later render exception.
Five later allocation-triggered full collections still take 571–674 ms, matching
viewer gaps up to 682.46 ms. The switch is not a cure for all GC hitches, and
overall off/on superiority is not established. Client PSS peaks at 1,723 MiB;
late post-GC heap is similar but PSS still trends upward (5–10-minute median
1,489 MiB versus final-five-minute 1,707 MiB). No OOM or unbounded descriptor/
thread growth; do not claim no leak or completely settled memory. App/server
memory is comparatively stable. Next technical targets are texture-level error
attribution and client allocation/retention; no need to repeat the same on test
solely to prove it activates. Keep the setting optional and default off pending
broader pause/memory comparison. Version 0.10.44 and its release stay unchanged.

Earlier device evidence: [0.10.44 comparison review](CLIENT_SESSION_FIXES_REVIEW_20260913.md).
The user reports off first, on second, with smoother second play. Policy markers
confirm the order, but the retained runs cover about 18m45s off and 2m29s on.
Off has one 326.718 ms World.tick collection matching a 350.93 ms viewer gap;
on ends before its first periodic request, so no actual skipped request is yet
qualified. Both clients exit 0; the same Adventure server remains running.
No 0x500, Ogg failure or invalid audio sample rate recurs. Each attempt has one
startup 0x502, now paired with the driver message "unable to generate levels for
texture target 3553" near TextureLoader's error cleanup. Exact asset/format/call
still need tracing. The previous in-play GL exception does not recur.
On-run client PSS rises to about 1,942 MiB and direct buffers to 364 MiB before
any periodic skip; do not claim either a leak or a GC-policy cause from this.
That review requested a 20–30-minute on run crossing two expected requests,
followed by normal client/server shutdown and a support export. The September 14
review above supplies this follow-up. Preserve this earlier comparison as history.

- Guard only the inspected engine's NVIDIA/ATI memory queries using real LWJGL
  capabilities; retain other queries and the game's unknown-memory fallback.
  Exact method-reference and offscreen reversals reproduce the engine SHA.
- Replace the known-corrupt missing-sound mapping with an authored silent WAV
  in the private overlay. The real InternalPack and WavData decoder pass; other
  mappings/packs/imported files remain unchanged. A short Ogg candidate exposed
  legacy EOF handling and was discarded; no Ogg binary is shipped.
- Opt-in Diagnostics periodic-cleanup setting defaults off. On redirects only
  World.tick's explicit gc request; ordinary/pressure/other/shutdown GC remains.
  Serial/client heap, server GC/heap, JRE, ASan and buffer cleanup are retained.
- Add bounded non-consuming GLES KHR_debug messages when supported. Two 0x502
  sites remain unproven; collect a Thor report before any speculative renderer
  state patch. Non-debug driver messages are optional, with old breadcrumbs kept.
- Local host suite: 188 tests, 20 expected fixture/platform skips; actual client
  overlay, mapping selection and decoder qualified. Android Kotlin compilation
  passed for 96 application/test sources; four focused Kotlin/JUnit checks passed.
- Released version 0.10.44/code 58, package
  `io.github.russianranger.wurmlauncher.sessionfix`, immutable tag
  `v0.10.44-session-fixes`. Implementation commit
  `05458897263a1a1a66e0f97261b716d2745877a4`, tree
  `a5c1e83ea9d17f23e74d030a2e783643941d9184`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.44-session-fixes/Wurm-Server.apk),
  68,533,205 bytes, SHA-256
  `3e56f8a2a692e491f3e21369728ad697494dd17634ac38b9c4c7d497b59eeb6d`.
  Published 2026-09-13T19:37:34Z with 55 assets, including the new guide and
  corresponding native/runtime sources. Published APK and guide checksums match
  independently downloaded files and GitHub asset digests.
- [CI run 34777691235](https://github.com/Russianranger/wurm-android/actions/runs/34777691235)
  passed: build `103778700199`, publisher `103780269980`; unrelated import/JVM
  publishers skipped. Initial host suite: 188 tests, 24 expected fixture/platform
  skips. All three Android variants passed build, unit tests and lint. Required
  native graphics/input checks and packaging gates passed; one expected real-host
  EGL shader-compilation skip remains in the subsequent native checks. The private
  client fixture is qualified locally, never uploaded to CI.
- Independent APK version/code/package, v2 signature and maintained packaged
  runtime/class verifier passed. Certificate SHA-256:
  `a02cc196066cd05e14ffc2d2badb3612eb61b46dbf13bfc78ec243ec57869962`.
  This is this release's CI debug certificate; persistent owner-controlled signing
  remains pending, and the separate package continues the backup/restore workflow.
- Compared with 0.10.43: all 194 JRE members, all 17 server/persistence helper
  classes, POC, LWJGL API and compatibility JARs are byte-identical. All 27 window
  JAR members are identical; only its two notice-entry ZIP timestamps changed.
  Of 40 native libraries, 38 are byte-identical. libwurm_graphics changes for the
  new callback; libgl4es differs in 25 build-ID/time bytes only. ASan is retained.
- Executed the downloaded APK's runtime-probe JAR against the exact private
  client. All nine generated overlay entries passed selection/integrity checks;
  the real InternalPack selected the mapping/WAV. The original Ogg failure was
  reproduced, and the replacement decoded to 8,820 direct-buffer zero PCM bytes
  at 44,100 Hz/one channel. Packaged runtime-probe SHA-256:
  `6bef5de17bf94b6515bf6eb8cef07423b895b1990790db02fa6db0ff2ad8b5ed`.
  Engine overlay SHA `a162c00ea5e5cf3819208b75a21f9c7fb6197c7fead4003deb82f3ecd843c85b`;
  World overlay SHA `e091ae0a8a67a45e68cd6cee7b878cf6b0d3e421ee6d2e43092229a3328462dc`.
- The extended on test is complete; preserve its pause/memory and normal shutdown
  evidence above. Next technical work is client allocation/retention and texture
  error attribution. The two earlier 0x502 sites remain unresolved;
  startup is narrowed to texture-level generation, while the previous in-play
  site is not reproduced. KHR messages work on this Thor but remain optional on
  other non-debug drivers. Longer soak/lifecycle,
  persistent signing and the separate native instrumentation comparison remain.
  Main was verified unchanged at `2e41fb091ee75a76b9116e934abecff90bc735d9`.
  Private files stay out of git and release assets.

## Previous release — 0.10.43 frame performance

Latest device review: the user reports extended play remains functional, though
shorter than 60 minutes. See
[CLIENT_FRAME_PERFORMANCE_REVIEW_20260913.md](CLIENT_FRAME_PERFORMANCE_REVIEW_20260913.md).
The 18:30:59Z export covers about 35 minutes: client/server exit 0, normal saves,
mean steady viewer windows 29.831 FPS, one reused 3.52 MiB payload allocation,
zero steady-play skipped sequences, no crash/OOM/database-open/frame-copy failure.
Three native GL observations remain (one recovered exception at frame 4904 in
early play), plus an Ogg decoding failure and two invalid-sample-rate warnings.
Periodic full GC aligns with brief viewer gaps up to 519 ms. No obvious runaway
memory growth, but this is partial qualification; lifecycle/restart and a longer
soak are not demonstrated. Keep 0.10.43 as the working baseline; investigate GL
and sound issues next, with GC/native comparisons kept separate. Documentation
review only; no new APK or release identity change.

The user reports “Everything worked” on 0.10.42 and authorizes the next milestone.
Continue phase 4 of PRODUCTION_READINESS_PLAN.md on mod-launcher-test only.
[CLIENT_FRAME_PERFORMANCE.md](CLIENT_FRAME_PERFORMANCE.md) records the support
bundle identity, precise scope, limitations and device steps.

- New support exported 2026-09-13T17:02:32.596781Z: successful login, Survival and
  Live Map READY, normal client exit 0 and server save/shutdown exit 0. The retained
  run uses the older prepared server pin and real localhost/sqlite; fresh-stock
  success is user-reported, not demonstrated by that retained session's input hash.
  No ASan fatal/OOM/fatal signal/CANTOPEN; two startup native GL observations remain.
- FrameBuffers owns at most two leased raw/fallback payload slots. Reader-to-UI
  delivery closes leases on use/discard/error/paused or destroyed activity. One
  in-flight read normally reuses one raw array. Sequence/atomic file validation,
  Bitmap copy, raw/fallback orientation and pointer/input behavior are preserved.
- Normal logging omits routine uniform/attribute lookup pairs and sequence spam;
  compile/link traces, exceptions, native crash/error evidence remain. Verbose
  restores detailed tracing on next client start. Connection counters summarize
  every five seconds while states/reasons remain immediate; app elapsed labels
  no longer force repeated status log writes. No blanket output/error filter.
- Timestamped viewer p50/p95/p99/max, read/copy p95, skipped sequences and payload
  allocation/retained bytes accompany existing producer and memory samples.
  These measure viewer updates, not GPU timing. Pause/epoch resets and bounded
  sample windows are explicit. Observation history now bounds at about 2 MiB.
- Local: all 98 Android application/test Kotlin sources compile; 19 focused
  Kotlin tests pass. Host suite: 182 tests, 30 expected local fixture/platform
  skips. Seven shader trace and four connection tests pass in their applicable
  modes. No proprietary game files are needed for these new tests.
- Released 0.10.43, code 57, `io.github.russianranger.wurmlauncher.frameperf`,
  immutable tag `v0.10.43-frame-performance`. Implementation commit
  `e253dfdbeffe657a1780d84bdb7cf11c876ef0fc`, tree
  `383f18d7bd2c083cd512387178624139a28fd510`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.43-frame-performance/Wurm-Server.apk),
  68,515,069 bytes, SHA-256
  `8bd0c00a9f9a0e91f6a112053dd8131cae4cfe33f772ed7aaa239d2bde340c62`.
- [CI run 34771446319](https://github.com/Russianranger/wurm-android/actions/runs/34771446319)
  passed: build `103761662806`, publisher `103762760363`; unrelated import/JVM
  publishers skipped. Initial host suite: 182 tests, 23 expected fixture/platform
  skips. All three Android variants passed build, unit tests and lint. Required
  native graphics/input and packaging gates passed; one expected real-host-EGL
  shader-compilation skip remains in the later native checks.
- Independently downloaded APK size/checksum, version/code/package, v2 signature
  and maintained packaged-class/runtime verifier passed. Certificate SHA-256:
  `801abd1bd628e32e98aa43b1cfc40daf0095adad103ec969b5b63bec277d116b`.
  All 194 JRE members, POC and 39 of 40 native libraries match 0.10.42 exactly.
  GL4ES differs only in 25 bytes: 20 GNU build-ID bytes and five compile-time
  string characters. No native behavior source changed.
- Main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`. Native graphics/audio/
  ASan, JRE, collectors/heaps, SQLite, POC and stock recipe are retained.
  Subsequent handoff-only commits do not alter the immutable release APK.
  No FPS gain or completed device soak qualification is claimed.
- Device: keep 0.10.42, export a complete backup with both runtimes stopped,
  restore into 0.10.43, keep the same settings, test play/background/resume and
  save/restart. The subsequent 35-minute session is reviewed above; a longer run
  with lifecycle/restart remains useful. This comparison needs no stock reimport.
- Later: remaining GL/audio investigation, separate matched normal/ASan native
  comparison, owner-persisted release signing/stable identity, wider device matrix.
  Do not combine native/GC experiments with this frame/logging comparison.

## Previous release — 0.10.42 stock database path fix

The user tried the untouched ZIP in 0.10.41 and reported a startup error.
The import and overlays succeeded; the game failed in Flyway with
SQLITE_CANTOPEN, exit 1 after 904 ms. Root cause: stock DB_HOST=localhost,
with databases under Adventure/sqlite and Creative/sqlite and no localhost
folder. A subsequent reimport attempt produced the one-original-import message;
that was not the startup cause. Full details and support ZIP identity:
[STOCK_DATABASE_PATH_FIX.md](STOCK_DATABASE_PATH_FIX.md).

- Recipe `thor-stock-sqlite-2` validates all nine databases and repairs only the
  missing default localhost case to the owning world's relative directory.
  All INI bytes except DB_HOST, game JARs, maps and DB contents are preserved.
  Existing real shared/database directories retain their selection. Partial,
  unknown, external or ambiguous paths fail in staging without activation.
- New selected-world database preflight checks paths, headers and writable files
  without creating or opening SQLite databases. The previous scratch-driver
  probe did not test the world's DB_HOST path; this missing coverage is fixed.
- Local: 40 Kotlin/JUnit tests and 179 host tests pass (16 expected local skips).
  Wurm's actual connection factory reproduces the original SQLITE_CANTOPEN;
  the corrected paths open all 18 supplied databases across both worlds.
  Exact stock import checks all 711 source files: only two DB_HOST lines change.
- Actual stock server on a disposable host copy passed initial startup and STOP,
  then restart/TCP 3724 readiness and STOP, both exit 0. Flyway uses Adventure
  databases and personal mode remains true. Imported source remains unchanged.
  This is host qualification; physical Android play/save remains pending.
- Released 0.10.42, code 56, `io.github.russianranger.wurmlauncher.stockdbfix`,
  immutable tag `v0.10.42-stock-database-fix`. Implementation commit
  `c3a43a9e1b1f3d5dd0588a9c54956d25b12c294e`, tree
  `8488369c3f56a55862814f7be1301d2f3b2c9cba`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.42-stock-database-fix/Wurm-Server.apk),
  68,495,973 bytes, SHA-256
  `730a7aa1b92dd476bf18a7ae6a0292172ba375e9241a1c747cf4fce3f66bb180`.
- [CI run 34760103028](https://github.com/Russianranger/wurm-android/actions/runs/34760103028)
  passed: build job `103731318419`, publisher `103732590511`; unrelated
  import/JVM publishers skipped. Initial host suite: 179 tests, 23 expected
  fixture/platform skips. All three Android variants passed build, unit tests
  and lint; required native/input and packaging gates passed. Host real-EGL
  shader compilation retains its one expected unavailable-platform skip.
- Independently downloaded APK size/checksum, version/code/package, v2 signature
  and maintained APK verifier passed. Certificate SHA-256:
  `ac6bad2f2aeffdc2e3151d7b66eb787a7ab6823b3d6f3e679bd4ae6817c3f5b8`.
  All 194 JRE members, POC and 39 of 40 native libraries are byte-identical to
  0.10.41. GL4ES differs in 25 bytes only: 20 GNU build-ID bytes and five
  compile-time string characters. No native behavior source changed.
  Graphics/audio/ASan/GC/input/POC/SQLite policies and versions are retained.
- Main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`. Subsequent
  handoff-only commits do not alter the immutable release APK.
- Retest in fresh 0.10.42 with the same untouched server ZIP. Keep 0.10.40 and
  its complete backup. The failed 0.10.41 full backup preserves wrong DB_HOST
  and the recovery marker; do not use it for the fresh-import test. An exported
  before-start server checkpoint can be prepared through the new importer if
  keeping that setup is needed. Never clear recovery markers merely to retry.
- Remain on mod-launcher-test. No new original game upload is needed. Keep
  proprietary inputs/disassembly/logs out of git and release assets.

## Previous release — 0.10.41 stock preparation

The user confirms 0.10.40 keyboard Send and export/restore work, and supplied
fresh support logs plus the untouched WurmServerLauncher ZIP. Continue only on
`mod-launcher-test`; main remains unchanged. See
[STOCK_RUNTIME_PREPARATION.md](STOCK_RUNTIME_PREPARATION.md) for input identities,
private qualification evidence, implementation and device steps.

- New support: `.keyboardprep`, export 2026-09-13T12:27:22.637585Z; normal client
  exit and requested server exit 0 after 75,661 ms; successful login; Survival
  and LiveMap ready; 11 presentation samples median 30 FPS. Short session only.
- Stock server SHA `ba5301b2e9b56dc9ab7eae9d8ac45188336835e37184e329c90128ea8ab01f64`;
  common.jar equals the previous pin. Exactly four active item SQL classes
  differ; every other server JAR entry matches the prepared baseline.
- Recipe `thor-stock-sqlite-1` flattens dist/ inside unpublished staging, preserves
  original file contents, rejects collisions/unknown game pairs, supplies the
  existing offline dependencies/POC and records the final inventory. Launch
  directs the game's existing distRoot property to the actual resource root.
- Temporary item overlay changes eight pinned constants to the game's own
  personal-server UPDATE statements. Exact class input/output pins; no method
  body changes. Row/child/column identity, missing-row no-op and rollback tested.
  Older prepared imports retain all item classes unchanged. Mod transformation
  of the generated classes works with pinned Javassist.
- Local qualification: all 711 supplied files preserved; both worlds and all
  581 recipes recognized by game APIs; complete host preflight passes without
  modifying imported files. Android API compile, 35 focused Kotlin tests and
  174 host tests (16 expected local skips) pass. New six private item tests pass.
- Released 0.10.41, code 55, `io.github.russianranger.wurmlauncher.stockprep`,
  tag `v0.10.41-stock-preparation`. Implementation commit
  `68fadd36b062cb7739c4b86b7afa6a085d38cefc`, tree
  `c9ed0f8e25c0fd05982db60b8912c651dc45e015`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.41-stock-preparation/Wurm-Server.apk),
  68,482,997 bytes, SHA-256
  `a319bfb0c4141d71c37c17557e449fd8bee6a7036dc73de941855db22a06ba54`.
- [CI run 34758094487](https://github.com/Russianranger/wurm-android/actions/runs/34758094487)
  passed: build job `103725898571`, publisher `103727107383`; unrelated
  import/JVM publishers skipped. Initial host suite: 174 tests, 21 expected
  fixture/platform skips. All three Android variants passed build, unit tests
  and lint; required native/input and packaging gates passed. Host real-EGL
  shader compilation retains its one expected unavailable-platform skip.
- Independently downloaded APK checksum, version/code/package, v2 signature
  and maintained APK verifier passed. Certificate SHA-256:
  `42119b2b58523c213876aac7af610b43e9ae1f3746f2b944e55998d3c7c62a96`.
  A first incomplete local download was discarded; the complete retry verified.
  All 194 JRE members, POC and 39 of 40 native libraries are byte-identical to
  0.10.40. GL4ES differs in 23 bytes only: 20 GNU build-ID bytes and three
  compile-time string characters. No native behavior source changed.
  Keyboard, renderer/audio/ASan/GC policies and SQLite remain unchanged.
- Main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`. Subsequent
  handoff-only commits do not alter the immutable release APK.
- Next physical check: use a fresh app installation, import the untouched server
  ZIP, then the usual client ZIP, test new-world login/item changes/save/reentry,
  and return support logs. Restoring an old complete backup instead occupies
  the existing one-original slot and tests only the preserved prepared path.
  Keep 0.10.40 and its complete backup. No further stock/dependency upload needed.
- Still later: stable release signing, broader setup polish and soak/production
  qualification. Do not claim support for arbitrary server versions. Never
  commit proprietary files/classes/disassembly or disable the established pins.

## Previous release — 0.10.40 keyboard and preparation dependencies

The user resumed **Review Repo Progress** after its length limit and authorized
continuing the last request: fix keyboard chat submission with automatic runtime
preparation. Continue only on **mod-launcher-test**; main remains unchanged.
Recovered the latest `wurm-support.zip`; it matches 0.10.39 `.launcherpreview`.
User reports other features functional. The bundle has two normal client exits,
two requested normal server exits and median sampled presentation 30 FPS; no new
fatal marker was found. This is not a long-duration qualification result.

- Implemented IME Send/Done/Go and composer hardware Enter through one atomic
  draft + CR + key-release batch. Insert remains separate; failure retains draft.
- Supplied client disassembly confirms WurmInputField submits on LF/CR in
  keyTyped, not a zero-character Enter key code. Also fixed the 200-event LWJGL
  queue limit: long pastes now wait for capacity rather than dropping the tail.
- **SQLite gap resolved:** public Maven main and `natives-android` classifier
  artifacts at 3.53.2.1 exactly match both existing device hash pins. Bundled as
  JVM assets with license notices and build/APK checks; no new native build.
- Prepare / import server ZIP uses recipe `thor-prepared-sqlite-1`: verify the
  existing Thor game JAR pins, add missing SQLite/bootstrap offline, stage a
  manifest/final inventory and select atomically. Existing JARs/worlds/mod files
  are preserved. Changed inputs fail without publishing the stage. Existing
  first-launch preflight/checkpoint/mod ordering is retained.
- **Still needed from user:** an untouched Wurm Unlimited dedicated-server ZIP,
  or its original `server.jar` and `common.jar`, for clean-stock item SQLite
  patch qualification. The recovered server JAR is
  the prepared baseline. Older embedded reference classes are not sufficient
  evidence of pristine stock. No SQLite upload is needed now. Generic clean-ZIP
  conversion is not implemented; never bypass pins to advertise it as complete.
- Released **0.10.40**, code **54**, package
  `io.github.russianranger.wurmlauncher.keyboardprep`, tag
  `v0.10.40-keyboard-preparation`. Implementation commit
  `7f36b3fb80935fce767603fc0356662e8948c72d`, tree
  `f81f4e1574b98b44d4cc2993db8e1ff4206faad3`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.40-keyboard-preparation/Wurm-Server.apk),
  **68,472,529 bytes**, SHA-256
  **`e12bbf4e86cb26237751327148af43b48eca0cbd4595ac2923ca30a4c3ebd7a2`**.
- [CI run 34755650835](https://github.com/Russianranger/wurm-android/actions/runs/34755650835):
  build `103719449936` and publisher `103720593433` succeeded; two unrelated
  publishers were intentionally skipped. Host suite: 168 tests, 17 expected
  initial fixture/platform skips. All three Android variants passed build,
  unit tests and lint; subsequent required native and actual LWJGL checks
  passed. Real host EGL shader compilation remained unavailable (one skip).
- Local Android API compilation and 28 focused Kotlin/JUnit tests passed;
  local full host suite: 168 tests, 16 unavailable fixture/platform skips,
  including successful supplied-server overlay checks and actual pinned input.
  The inspected client JAR SHA-256 matches the bundle:
  `79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
- Independently downloaded APK matches release SHA256SUMS and the exact
  package/version/code. APK v2 signature verified; certificate SHA-256
  `724e39057d4b4a10f0032b9094b0c4965cb10c11a3a82cc20d10cdafc2e73897`.
  Maintained packaging verifier passed, including exact SQLite assets, input
  capacity methods, new composer/preparation DEX classes and absence of SQLite
  from the Android DEX classpath. JRE members, POC and 39 native libraries are
  byte-identical to 0.10.39. GL4ES differs in 25 bytes: 20 build-ID bytes and
  five compile-time string bytes; every other native byte matches.
- Full 0.10.39 backups provide migration. Keep the old app installed;
  persistent signing remains unresolved. Next physical test: restore the
  complete backup, select Wurm chat, use Android Send and Send / Enter,
  confirm exactly one message, then export support ZIP. Test offline dependency
  insertion separately with a copy of the supported prepared ZIP missing its
  POC/SQLite files. New device behavior remains unconfirmed.
- Handoff-only commits after the implementation do not alter this immutable APK.
- [Implementation, recovered bundle details and device checklist](KEYBOARD_AND_PREPARATION.md).
  No proprietary assets or disassembly are committed. Native renderer/audio,
  ASan and GC policies remain unchanged. New device behavior is unconfirmed.

## Previous branch release — 0.10.39 launcher, backups and keyboard

The user **authorized the proposed GUI and backup/migration phase**, and added
Android keyboard invocation/dismissal through the gear menu plus the conventional
input path. That authorization supersedes the proposal-only status below.
Continue on **mod-launcher-test**; main is unchanged.

- Implementation: contextual Client Play/Resume, grouped Server controls,
  persistent tab selection, Mods side selector/observed READY labels and
  Diagnostics support bundle/expandable checks. Graphics keeps all 47 options
  with categories/search/reset and acknowledgment status. Gear has grouped
  controls, left/right placement and background-only opacity.
- Android keyboard uses a native text composer invoked/dismissed from gear,
  with Insert/Enter/Backspace/Hide. Hardware keys send LWJGL key/character/repeat
  events; hardware mouse hover/buttons/wheel use normalized frame geometry.
  Focus/menu/device removal releases held input. Visible game keeps screen awake.
  Exact IME/game text behavior still needs a physical test.
- Complete backup includes active original/working server/checkpoint, selected
  client, both mod stores, client user files/bindings, controller and typed app
  settings. Hash-checked staging plus a recoverable multi-root/preference
  transaction; exclusive app/native ownership; startup recovery blocks launch
  if incomplete. Journal cleanup is renamed away before deletion. Restore
  recreates launcher screens to prevent stale selection writes.
- Released **0.10.39**, code **53**, package
  `io.github.russianranger.wurmlauncher.launcherpreview`, tag
  **v0.10.39-launcher-backup**. Implementation commit
  `c7a1c94fca1c639760820633468b1b172d5597b4`, tree
  `8575185df5d52a0ddda460314eda4e5b13ab17d7`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.39-launcher-backup/Wurm-Server.apk),
  **53,868,729 bytes**, SHA-256
  **`7e513956535741071505cd0d85c296527d800fa77f61789b99df8982d62402ef`**.
- Local validation: full Android API compilation and **24 focused Kotlin tests
  passed**, including complete round-trip and process-death injection at each
  publication boundary. **168 host tests passed, 30 expected unavailable local
  fixture/platform skips**; both authored and actual pinned LWJGL queue tests
  then passed, including text, character state, repeat and reset.
- [CI run 34742995721](https://github.com/Russianranger/wurm-android/actions/runs/34742995721):
  build `103685767034` and publisher `103686875178` succeeded. Host suite:
  **168 tests, 17 expected initial skips**. All three Android variants built
  and passed unit/lint gates; subsequent required native and actual-LWJGL
  checks passed. Real host EGL shader compilation remained unavailable (one
  explicit skip). APK v2 signing verified; certificate SHA-256
  `fcf0fe482ba5aa9450fd89fd17bf9fe926c83fe88f78029f2bb4e4d013f8d01b`.
- Independently downloaded APK matches release SHA256SUMS, version/code/package,
  new launcher/backup/input classes and maintained packaging verifier. JRE
  members, server POC and 39 native libraries are byte-identical to 0.10.38.
  GL4ES differs only in 27 bytes: 20 GNU build-ID bytes and seven compile
  date/time bytes. Java input adapter/helper changes are intentional.
- No native graphics/audio/ASan, server POC/SQLite or GC policy changes. The
  new GUI, IME/hardware paths and real-world full restore remain to be checked
  physically. Handoff-only commits after the implementation do not change
  the immutable APK.
- Migration caveat: the old 0.10.38 app cannot export a complete backup and its
  signing key was not persisted. Keep it installed, export its stopped working
  server runtime, and import client/client mods separately. Full backups start
  with this version. Gradle accepts private persistent signing environment
  inputs, but CI key provisioning is not completed; preview signing remains.
- [Migration, design, keyboard usage and device checklist](LAUNCHER_BACKUP_KEYBOARD.md).
  Automatic stock-server ZIP preparation and performance/native qualification
  remain later milestones. No proprietary inputs are committed.

## Historical proposal — production GUI and runtime preparation

The user reports that the additional suggested tests worked and asks for a
production-readiness evaluation: improve GUI flow, recover the earlier server
runtime/custom-JAR preparation, propose in-app automation, and triage remaining
log warnings. This is additional **user-reported success**, not newly measured
duration or log evidence. The latest supplied reports remain those reviewed
below. No new application behavior or APK was requested by this proposal step.

- [Production readiness plan](PRODUCTION_READINESS_PLAN.md) records the source
  audit, proposed four-tab/gear flow, preparation design, warning disposition,
  release/migration work and acceptance checks. It is **not implemented**.
  Await authorization for the proposed implementation; remain on
  `mod-launcher-test`, with main unchanged.
- Retain Server, Client, Mods, Diagnostics order; make Play/Resume contextual,
  group the existing 47 graphics controls, distinguish applied from
  restart-required changes, and finish software-keyboard/hardware-input and
  lifecycle behavior. Add full client/server backup before stable-package
  migration, and persist a release signing key for normal upgrades.
- Personal-context searches did not recover the original separate chat's exact
  commands. Repository instructions establish the Termux working-directory ZIP
  export; POC source/build is already present, and the app already inserts its
  bundled JAR. Do not claim the user needs to compile a custom JAR per import.
- Stock server ZIP preparation still needs a verified item-database patch
  recipe and reproducible Android SQLite dependency pack. The retained prepared
  server JAR has active and older reference copies of ItemDbStrings,
  BodyDbStrings, CoinDbStrings and FrozenItemDbStrings, useful for reconstruction
  but not proof of pristine vendor inputs. Compare a clean supported archive
  and validate SQL bindings/effects before accepting it. Current preparation
  pins must become explicit versioned recipes, not be removed wholesale.
- Existing `ServerPreflight`/`ServerSqlitePatch` already create the position
  SQLite overlay. Proposed Prepare server runtime wizard should stage and
  validate, preserve original/world/checkpoint data and mod ordering, activate
  atomically, and optionally export a prepared backup. Generic clean-ZIP
  import is **not currently available**.
- Latest logs still warrant startup GL attribution, allocation/GC and logging
  improvements; they do not show a new fatal crash or proven leak. Track the
  single spawn warning and feature-specific content/Epic warnings. Expected
  capability/platform fallback and normal teardown messages can be quieter.
  Keep normal-vs-diagnostic native build work separate from UI/GC changes.
- This review changes only plan/handoff documentation. Runtime source/release
  remains 0.10.38 / `ed0267310d247a45e42459accd6674256857044b`.

## Latest physical result — 0.10.38 Live Map renders and restarts

User supplied `wurm-client-report (1)(10).txt`, `wurm-server-report (1)(8).txt`
and `Screenshot_20260912-182923.png`. Live Map 1.8 reaches ready count 1 in
both client sessions, enters the world and is visibly rendering in the screenshot.
Both clients exit 0, and both Survival-enabled servers save and exit 0 after
normal stop requests. Main remains unchanged; only review/handoff docs change.

- Client sessions: 23:28:34–23:34:16Z and 23:36:49–23:38:16Z (about 5m42s and
  1m28s). Render/present/viewer medians are 30 FPS at 1280x720. No loader error,
  fatal crash, ASan memory-error report, OOM or server SEVERE record found.
- Reports are not error-free: seven unique startup/initial-entry GL errors,
  three recoverable pre-readback exceptions in the first run, existing
  content/platform warnings, three initial Epic mission-difficulty warnings,
  and one tile-removal warning during spawn selection. Rendering continues;
  no later GL errors are retained. Do not claim these are caused by Live Map.
- First-client RSS peaks at 1.58 GiB, ends at 1.53 GiB; heap/direct-buffer use
  falls after collections, FDs remain 52. First-server FDs fall 135→75 and end
  85. No swap or proven continuing leak. Gameplay young GC still pauses about
  184–207 ms; longer full collections at exit are shutdown work.
- Only Survival is enabled server-side in this new app; Announcer is absent
  from this manifest. Both client attempts enable Live Map. Loader-only and
  mod-disabled runs are not in these reports. Screenshot confirms rendering,
  not every map control or long-run behavior.
- Next: same APK, 15–20-minute map movement/zoom/hide-show test, check spawn
  position and persisted reentry, then disabled/re-enabled client-mod startup
  and both report exports. No runtime fix is required before that test on
  current evidence. Logging and frame-copy reductions are future work.
- [Detailed review, timestamps, warnings and metrics](CLIENT_MOD_DEVICE_REVIEW_20260912.md).

## Current branch release — 0.10.38 client mods

User confirms server mods working and authorizes client-side implementation. Continue only on **mod-launcher-test**; main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`.

- New reports: `wurm-server-report (1)(7).txt` and `wurm-client-report (1)(9).txt`. Announcer ran 15m47.8s and saved/exited 0 at 19:37:58Z. Announcer + Survival both reached `SERVER_MOD_READY` at 19:44:04Z (count 2); combined server still running at export. Client retained entry exits are 0. User's Survival choice supersedes CropMod as the second server test.
- Released **0.10.38**, code **52**, package `io.github.russianranger.wurmlauncher.clientmods`, tag **v0.10.38-client-mods**. Implementation commit `ed0267310d247a45e42459accd6674256857044b`, tree `e0803e73248c8045fb9c566cdc05391e7e914bb5`. Client loader 0.15 import/switch, shared client mods, early transforming entry and retained startup markers. Live Map 1.8 is the first client-mod target. Exact public ZIP/JAR hashes and direct downloads are in [CLIENT_MODS_TEST.md](CLIENT_MODS_TEST.md).
- All game, client helpers, graphics bridge and LWJGL/native owners remain in one transforming loader. Only the entry stage receives loader/Javassist. Existing Android overlays and bootstrap remain selected; upstream desktop launcher is not used. Init failure exits 42, never a silent vanilla fallback. Loader alone (count 0) is testable; disable all client mods then its loader switch to recover baseline.
- Local validation: 168 host tests, 24 expected unavailable-fixture/platform skips; 17 focused Kotlin tests passed; full Android API compilation passed with one existing resize deprecation warning. New four-case host regression uses the actual client loader and its support hooks with authored game ABI fixtures. Covers shared GUI package access plus an isolated mod, overlay/order, single graphics/game owner, failure and diagnostic isolation. Kotlin checks use the real Live Map ZIP and pinned client loader. Native device rendering remains unverified.
- [CI run 34716051969](https://github.com/Russianranger/wurm-android/actions/runs/34716051969): build `103613369439` and managed release `103614335860` succeeded. The two unrelated publishers were intentionally skipped. Host suite: **168 tests, 17 expected initial fixture/platform skips**. Required subsequent native graphics, depth, OpenAL and actual LWJGL checks passed; real host EGL shader compilation remained unavailable (one explicit skip). All three Android variants built and passed unit/lint gates. APK v2 signature verified by CI; certificate SHA-256 `c996aad5a2aa46cb27acaacd7ef1310c3cc92acd14b2e1a1bf7ddfa1e43f452f`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.38-client-mods/Wurm-Server.apk), **53,770,689 bytes**, SHA-256 **`2c40f7041778a7be0993016108f5fefbe5c4660ffedd9b336fe1d14cfbdf0ded`**. Independently downloaded and matched SHA256SUMS, version/code/package, client loader pin/switch/entry selection, both Java helpers and startup markers. Full `verify-managed-apk.py` passed locally. Relative to 0.10.37, JRE members, server POC and 39 native libraries are byte-identical. GL4ES differs only in 26 bytes covering GNU build ID and compile-time banner. Handoff-only commits after the implementation commit do not alter the APK.
- Longer Announcer server measurements now show FD counts falling from peak 112 to 79; combined Announcer/Survival counts fall from 122 to 93. Client retained entries exit 0 and sampled swap is zero. These reports do not establish a continuing resource leak; no speculative memory changes were made.
- No native, graphics/audio policy or GC changes. Server implementation is retained. No proprietary inputs or upstream mod binaries are committed.
- Original install/test checklist is in CLIENT_MODS_TEST.md. Live Map loading, visible rendering and another client/server session now have device evidence above; longer-run/map-control/disable-path coverage remains pending. No other client mods are qualified yet.

## Earlier physical result — 0.10.37 Announcer on/off passed

User supplied `wurm-client-report(20260912-191104).txt` and `wurm-server-report(20260912-191104).txt`, enabling Announcer for the first session and disabling it for the second. Continue only on **mod-launcher-test**; main is unchanged.

- First server, 19:05:07–19:08:22Z: actual `SERVER_MOD_READY announcer`, `SERVER_LOADER_READY count=1`, Android entry and client game loop. Hooked shutdown saved normally and returned 0. The earlier frozen-class failure did not recur.
- Second server, 19:08:52–19:09:56Z: `SERVER_SELECTION none; baseline startup`, existing Thor character loaded, normal save and exit 0. Both client entry processes also returned 0. Final manifest has Announcer disabled, its generated config retained, and no pending transaction.
- Loading, hook installation/execution and the disable path have device evidence. In-game announcement text is not captured in these reports; visible Announcer behavior and re-enable/config readback remain to check. Client mod execution remains deferred.
- Rendering settles near 29–30 FPS. Recoverable startup graphics/content warnings remain; no new crash, OOM or demonstrated mod-caused regression. Server FD/heap growth appears in both short sessions without intervening GC; earlier baseline counts fell around collections. Do not infer a leak or change GC from this short test.
- Original next steps were Announcer re-enable and a longer test, followed by a second server mod. These were subsequently completed with **Survival**, replacing the proposed CropMod. See the current client-mod release above. No new runtime changes were made in this earlier review.
- [Detailed two-session review, warning attribution, metrics and test checklist](MOD_DEVICE_REVIEW_20260912.md). Source/release identity remains below.

## Current branch release — 0.10.37 mod hook ordering

User's first 0.10.36 server test failed with Announcer enabled. Continue only on **mod-launcher-test**; main remains `2e41fb091ee75a76b9116e934abecff90bc735d9`.

- Report: `upload/wurm-server-report(20260912-183958).txt`; preflight passes, Announcer loads, then `ProxyServerHook.registerOnMessageHook` throws `Communicator class is frozen` at 18:38:53.314Z. Controlled server child exit 1 after 604 ms; no normal Android game-entry/readiness marker. The before-start checkpoint was retained.
- Root cause reproduced locally: our `ServerHook.getMethod("createServerHook")` lookup resolves all its public method signatures, including unrelated event methods with Communicator/Player types. This defines/freezes game classes before the hook factory instruments them. Earlier fixture omitted those signatures and missed this failure.
- Fix: exact `MethodHandles` lookup of the ServerHook factory and listener methods. No defrost workaround or skipped hooks. Four bounded stage markers identify mod initialization, hook installation, callback initialization and Android entry.
- Expanded actual-Ago/Javassist regression reproduces the old frozen-class failure and passes with the fix, including the actual hook effect, two mod transformations/order, class identity, and failure/no-vanilla-fallback behavior. Fixture game/lifecycle code is authored; actual device startup was subsequently confirmed in the on/off review above.
- Released **0.10.37**, code **51**, package `io.github.russianranger.wurmlauncher.modhookfix`, tag `v0.10.37-mod-hook-order`. Implementation commit **`e7ee1eb03fb9911c863e56651540db25b98ed0ac`**, tree `eaaaf6b17ba69549c7ba4fc7ed845baf470e1add`. Separate package preserves 0.10.36 data because CI debug signing keys vary between builds.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.37-mod-hook-order/Wurm-Server.apk), **53,760,901 bytes**, SHA-256 **`8c6f02d5644fd8bca9406f3ad363b793a0eabe19812dea612b5bfebefb4810a0`**.
- [CI run 34712199899](https://github.com/Russianranger/wurm-android/actions/runs/34712199899): build job `103602858718` and mod release job `103604204935` succeeded. Older import/JVM preview publishers were intentionally skipped. Host suite: **164 tests, 17 expected initial skips**; required subsequent native/actual-LWJGL regressions passed. All three Android variants built and passed unit/lint gates. The expanded frozen-class regression passed in CI. APK v2 signature verified; certificate SHA-256 `52bad60512cfa5ecf6b687e6a0fd115f58b218670f9f02d1e37d1268b4de2879`.
- Independently downloaded APK matches release SHA256SUMS, manifest version/code/package, corrected MethodHandles launcher and stage markers; full `verify-managed-apk.py` passed locally. Relative to 0.10.36, JRE members, server POC and 39 native libraries are byte-identical. GL4ES differs only in 24 bytes covering the GNU build ID and compile-time banner; remaining bytes identical. Handoff-only commits after the implementation commit do not change the immutable APK.
- Original retest (completed; see current next steps above): export **before-start checkpoint ZIP** from 0.10.36, install 0.10.37 alongside it, import checkpoint as server runtime (loader/Announcer/manifest included), check Mods, then start Announcer alone. Import normal client ZIP only for login test. [Detailed cause and original steps](MOD_HOOK_ORDER_FIX.md).
- Loader/Javassist pins, mod store/toggles, client staging, world data handling, SQLite/login patches, graphics/audio/native/GC policies remain unchanged. No additional proprietary files are committed.

## Previous branch test — 0.10.36 mod launcher (2026-09-12)

User confirms 0.10.35 functional/stable and authorizes a **separate mod test branch only**. Main remains at `2e41fb091ee75a76b9116e934abecff90bc735d9`. Branch `mod-launcher-test` was created from it. Do not merge to main without a later instruction.

- Implementation commit `ad1f0d7ad8aad88d62b4c308259ce71c82acbf15`, tree `0d4c4676d9ad016a8a491f6b08816ddef74e6122`.
- Version **0.10.36**, code **50**, separate package `io.github.russianranger.wurmlauncher.modtest`. Released tag: `v0.10.36-mod-launcher-test`. [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.36-mod-launcher-test/Wurm-Server.apk), **53,760,457 bytes**, SHA-256 **`e95bed95a7525b023d2ff7c8235ceeed84362c656261297e466710a5c934aab5`**.
- [CI run 34710257609](https://github.com/Russianranger/wurm-android/actions/runs/34710257609): build job `103597617425` and mod release job `103598991469` succeeded; the two unrelated older preview publishers were intentionally skipped on this branch. Host suite: **164 tests, 17 expected initial skips**; required subsequent native/actual-LWJGL regressions passed. All three Android variants built and passed their unit/lint gates. APK v2 signing passed; certificate SHA-256 `7101452a6220b9032618bbe5a057f60bbe1736f8b5adb49ff460d82e934eecf9`.
- Independently downloaded release APK matches SHA256SUMS, manifest version/code/package, required JVM assets and Mods classes. Full `verify-managed-apk.py` passed again locally. Handoff-only commits after the implementation commit do not change this immutable APK.
- Binary comparison with stable 0.10.35: JRE archive members and server POC are byte-identical; 39 native libraries are identical. GL4ES differs in exactly 25 bytes: GNU build ID and compile-time banner; all remaining bytes are identical.
- Implemented tab order: Server, Client, Mods, Diagnostics. Mod-specific file/dependency checks and manifest viewer are in Mods; existing Server/Client exports remain in Diagnostics and include mod manifests/startup evidence.
- ZIP import records each mod separately and starts it disabled. Toggles move owned folder, descriptor and config between `mods/` and `android-mods/disabled/<name>/`. A durable journal completes interrupted moves. Both sides share their existing ownership/native/file locks; no mutation during an affected runtime session. Server checkpoints/exports include the entire mod store and runtime-owned mod data.
- Server: user imports Ago 0.47 loader ZIP. Only its pinned core is installed; bundled optional mods, desktop scripts and old Javassist are not installed. APK carries unmodified Javassist 3.30.2-GA as a Java child-runtime asset with MPL notice/license and release source JAR. The transforming loader is entered before Wurm/POC classes resolve, while existing overlays/SQLite driver/console identity and Android STOP flow are retained. Mod initialization errors exit; no silent vanilla fallback.
- Client: import/manifest/toggle framework is implemented and explicitly labeled **staging only**. Client loader installation and execution are deferred until server physical tests pass. Existing client runtime ignores staged mods; report logs `CLIENT_LOADER_DEFERRED`.
- Compatibility limits: standard descriptor + per-mod JAR layout/direct side interface; duplicate imports do not overwrite existing mods; native/desktop/shared-classloader/ScriptRunner/legacy packages deferred. Metadata merges JAR defaults, `.properties`, `.config`/template. Required/imported dependencies and conflicts checked locally; exact version/order handling remains upstream. Structural config edits are rejected before launch. Mod config editor, update/removal and automatic downloading are not in this pass.
- Local evidence: 13 ModStore cases + 4 ManagedLaunch cases pass, including interrupted import/toggle recovery, config/data retention, dependency/conflict rejection and export/restore. Two Java tests use the actual pinned Ago discovery/resolver/classloader and Javassist with authored game/lifecycle fixtures; two mods transform one class in dependency order before game loading, and initialization failure cannot fall back. Five existing server diagnostics tests pass. Android API compilation passes with one pre-existing resize deprecation warning. Real upstream loader/Announcer/CropMod ZIP import, both-on validation, and both-off file removal pass. Stable APK's exact Java runtime already includes `jdk.zipfs`.
- Limits of evidence: authored game hooks in the host test do **not** demonstrate real Wurm lifecycle hooks, native Android behavior or mod gameplay. The initial physical Announcer test subsequently failed during hook installation; see the 0.10.37 correction above. Native/graphics/audio/GC/default-resolution policies are unchanged.
- Original 0.10.36 test plan (superseded by the 0.10.37 retest above): import normally stopped working export into this separate app; baseline start/stop; install loader; Announcer alone and toggle off/on; then Announcer + CropMod; verify clean STOP/restart and return both reports from Diagnostics. Keep a separate pre-mod export: disabling mods cannot undo persistent world/database changes. [Downloads, storage design and detailed checklist](MOD_LAUNCHER_TEST.md).
- Private server JAR recovered for metadata/reference outside git (`../wurm-mod-inputs`); it was not used to claim a complete running-server test. Upstream source/ZIP/compiler scratch in `../wurm-mod-research`. No proprietary runtime files are committed. Scratch may vanish; implementation and this handoff are repository-backed.

## Latest stable release — 0.10.35

- **0.10.35**, versionCode **49**, package `io.github.russianranger.wurmlauncher.worldcontrols`.
- Release tag `v0.10.35-world-controls`; implementation commit `25ad1233365001032eccda680340a7c9a418a845`, tree `c10f58dfaebdda0a7b3dc0f5e3aa63a1bfc5eccf`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.35-world-controls/Wurm-Server.apk), **52,915,761 bytes**.
- APK SHA-256 **`685c0fef8d58e961038d53cddd7b791a2e4399703cd9e4557817409e0f09714f`**.
- [CI run 34663722528](https://github.com/Russianranger/wurm-android/actions/runs/34663722528), build job **103471299895**: all four jobs succeeded. Host suite: **162 tests, 17 expected initial skips**; required subsequent native/actual-LWJGL regressions passed. All three Android variants built and passed unit/lint gates. APK v2 signing verified; certificate SHA-256 `f5ea58b940536a39fb087fc3eee4a19eb303ab684ed18e339e35f84ed3284699`.
- Downloaded APK independently checked against release SHA256SUMS, manifest version/package, new menu/database/keybind classes and controller-reload marker. Host-test SQLite JDBC did not enter the APK. JRE archive members are byte-identical to 0.10.34, although ZIP packaging changes its outer hash. Server POC and 39 native files are byte-identical. GL4ES differs in exactly 25 bytes: GNU build ID and compile date/time banner; the remaining binary is identical. Existing graphics/audio/native-memory policies are preserved.
- Handoff-only commits after the implementation commit do not change this APK.
- **Device feedback:** user subsequently reported everything working and authorized the separate mod branch above. Original controls checklist: [world settings and bindings](WORLD_SETTINGS_AND_BINDINGS.md).

## Completed request — 2026-09-12

User reports 0.10.34 stable in both server and client. All four requested changes are implemented and released:

1. Server → World gameplay settings: 16 controls, including skill/action rates, five starting-skill values, breeding/field/tree settings, creature population and deed options. Displays verified bounds; three extra app minimums are labeled. Reads actual values, preserves untouched values, edits only while stopped under existing ownership/lock, captures a SQLite-managed WAL-inclusive backup, then updates changed columns in one transaction. Applies on next server start. Worlds sharing DB_HOST share settings; the actual database and local server ID are shown.
2. Default resolution is 1280x720 when absent/invalid; explicit previous choices are retained. A resolution change still requires restarting the client.
3. Gear → Game keybindings: dynamic native catalog, categories, key picker, modifiers and multiple keys; detects aliases/duplicate assignments, protects against stale/external edits, saves through existing atomic storage, and reloads the active game's bindings on its thread with an acknowledgment. Custom-console-command files remain read-only. A saved-but-failed live reload explicitly requests client restart.
4. Gear → Controller mappings: existing mapping editor, held-input release, and saved-profile reload on viewer resume; no client restart needed.

Key source: `WorldSettings.kt`, `WorldSettingsStore.kt`, `WorldSettingsDatabase.kt`, `AndroidWorldSettingsDatabase.kt`, `WorldSettingsDialog.kt`; `GameKeybinds.kt`, `GameKeybindsActivity.kt`, `runtime-probe/src/client/ClientKeybindings.java`; editor extensions in KeybindStore/WurmSettingsFX. SQLite JDBC is a host-test-only dependency.

Focused validation: 14 Kotlin/JUnit cases including real SQLite WAL backup and rollback, 15 keybind/storage cases, and 11 client startup cases. Initial host run caught four old 960x540 viewport-fixture expectations; they were corrected before the successful release CI. Local Android API compilation passed with only an existing deprecation warning. Real imported metadata: 351 built-in actions and 93 nonempty base keys. Actual default binding file: 70 bound actions / 78 keys; the combined editor catalog has 365 actions. Catalog read and no-op save preserved the actual default file bytes. That private check used real metadata/file content with an authored engine/HUD fixture, not a running game.

The earlier runtime JARs were recovered from persistent uploads after scratch cleanup. No proprietary JARs, disassembly or assets were committed. See [implementation details, bounds and device checks](WORLD_SETTINGS_AND_BINDINGS.md). The user subsequently reported this version working; the current unverified physical test is mod support above.

## Earlier device-confirmed stable release — 0.10.34

- **0.10.34**, versionCode **48**, package `io.github.russianranger.wurmlauncher.graphicstabs`.
- Release tag: `v0.10.34-graphics-tabs`; implementation commit `3c586f9ccc36b54ae49fa2cffd7046d34d0fc423`, tree `f64b127f4698ff81fc19b7b6a311e5ebbbfb10cc`.
- [Download APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.34-graphics-tabs/Wurm-Server.apk), 52,831,145 bytes.
- APK SHA-256: `40396cd757aee2c1053c0f2e273459fe6b863464eddb44dc6f678890f74ecb7a`.
- [CI run 34619922092](https://github.com/Russianranger/wurm-android/actions/runs/34619922092): all four jobs succeeded. Build job 103331043313. Host suite: 154 tests with 17 expected initial fixture/platform skips; subsequent required native and actual LWJGL regressions passed, including both depth tests. Three Android variants built and passed unit/lint gates. APK v2 signature verified by CI.
- Downloaded APK independently checked against release SHA256SUMS: correct package/version, new page classes, 47-option adapter including brightness, native depth preference and complete patch metadata, dark resources and runtime packaging. JVM, ASan runtime and server POC are byte-identical to 0.10.32.
- Handoff-only commits after the implementation commit do not change this APK. Preserve this exact release/commit distinction.
- **Device feedback, 2026-09-12:** user reports both server and client stable and asks for world settings and in-game bindings. No separate explicit confirmation of the beam flicker was provided. Next device checks are for 0.10.35 above.

## Earlier measured baseline — 0.10.32

- Repository: Russianranger/wurm-android, branch main.
- Earlier measured baseline: **0.10.32**, tag `v0.10.32-dark-theme`, commit `4f66c65a1eb52d565af067db8697b8fd98ffc88b`, versionCode 46, application suffix `.darktheme`.
- [APK](https://github.com/Russianranger/wurm-android/releases/download/v0.10.32-dark-theme/Wurm-Server.apk), SHA-256 `fb706f5fea9f32f23f173078c18a6ada9484d8715011e99f81eaa0b768d3ee66`.
- CI run 34605432654 passed all four jobs; host tests, actual native regression checks, three Android variant unit/lint/build gates and APK verification passed.
- Physical device: AYN Thor Max, Android 13/API 33, Snapdragon 8 Gen 2/Adreno 740, 16 GB RAM.
- User now confirms repeated logout/login, app quit/reentry, movement and interaction work. Audio works. Object pop-in is resolved. Latest remaining visual issue: cross-beams flicker with viewing distance.

## Previous completed request — 0.10.34

The user authorizes changes and continued device-test releases. Requested on 2026-09-11:

1. Maintain this handoff document.
2. Review the latest stable-session reports and the cross-beam flicker video.
3. Audit the underlying client's graphics options; expose usable options and explain compatibility restrictions.
4. Organize the launcher into Server, Client and Diagnostics tabs. Move tests, reports and diagnostic output into Diagnostics; preserve basic server/client controls and running sessions.

Status: implementation, validation and release completed for 0.10.34; user reports it stable on 2026-09-12. The handoff was first persisted in commit `6c1a9980c3e1f7905818af408d86baf1230bcd8a`. Do not confuse that documentation checkpoint with the release commit.

An intermediate 0.10.33 build at `0505ca4f8749d8834a7d0c1bc720317ae0281861` started CI before the final audit found additional startup-only consumers and postprocess brightness. Its Android unit gate failed on the obsolete 17-field assertion; it was not released. 0.10.34 includes these extra controls and a complete depth-patch manifest marker.

Changes: one launcher host with persistent Server/Client/Diagnostics pages; 47 graphics controls (17 existing + 30 new) with restart-only deferral; verified EGL depth24 preference/depth16 fallback; pinned GL4ES unsized texture/renderbuffer precision changes. Runtime collectors, native memory checking and occlusion-query restrictions are preserved. ClientPage/DiagnosticsPage are new view controllers, not Activities. Return to Game reopens the viewer without starting a new client.

Validation is complete as recorded above. The initial Android run caught an obsolete 17-field test expectation; the corrected Kotlin/JUnit tests passed locally and all CI gates passed on the final implementation commit. See [the detailed audit and test plan](GRAPHICS_AND_TABS.md). Specific visual correction was not explicitly confirmed; general stability is now reported by the user.

Current attachments in the active scratch workspace:

- `upload/wurm-client-report(20260911-152658).txt`
- `upload/wurm-server-report(20260911-152659).txt`
- `upload/Wurm Server_2026-09-11 10_20_44.mp4`

The latest client header is 0.10.32 and reports a normal entry exit code 0, with game-loop observation true. Detailed review is in GRAPHICS_AND_TABS.md: three client entry exits 0, two requested server exits 0, median recorded presented FPS 29.8; recoverable startup GL errors remain, and samples are too short for long-term memory conclusions. Read uploaded files from scratch; do not fetch them through Library. Scratch may disappear between sessions. Recover previous uploads from their persistent file records when available before asking for a reupload.

## Established architecture and constraints

- Managed Android activities and foreground services own separate client/server JVM processes; navigating pages must not stop either process.
- Imported, user-owned Wurm runtime JARs/worlds stay private. Never commit them or game assets. The public repo contains original compatibility code and source-backed probes.
- Packaged OpenJDK 17, LWJGL/Pojav/GL4ES bridge, OpenAL; game renders to a raw RGBA frame file read by Android. Fullscreen 1280 x 720, collapsible gear controls and opacity are implemented.
- Keep downstream GL4ES fixes and compatibility limits. Unsupported occlusion queries are disabled; enabling them previously caused disappearing nearby objects.
- Client uses Serial GC; server uses G1. Do not silently switch collectors, remove ASan, globally disable explicit GC, or alter working runtime/import behavior during UI work.
- Scope is software reliability, performance and usability. The user explicitly excludes offensive-security/authentication-bypass research.

## Key source locations

- `app/src/managedPreview/java/io/github/russianranger/wurmlauncher/`: launcher activities, services, reports, settings and frame viewer.
- `ManagedActivity.kt`, `ClientActivity.kt`: single three-tab host plus compatibility client redirect; `ClientPage.kt` and `DiagnosticsPage.kt` own their page controls.
- `GraphicsOptions.kt`, `GraphicsSettingsDialog.kt`, `runtime-probe/src/client/ClientVisualOptions.java`: Android graphics catalog, settings UI and runtime option application. Keep wire order/validation consistent and extend meaningful tests with new options.
- `GraphicsTestActivity.kt`, `GameFrameView.kt`, `GraphicsFrame.kt`: fullscreen gear panel, frame consumption, pointer and input.
- `graphics-compat/`, `scripts/patch-gl4es.py`: graphics compatibility and checked downstream source fixes.
- `RuntimeMeasurements.java`, `AppMemoryMeasurements.kt`, `RuntimeObservationLog.kt`, `ServerProbeSchedule.kt`: periodic observations and throttled probes.
- `.github/workflows/android.yml`, `scripts/verify-managed-apk.py`: build/release gates.
- [Latest baseline review](DARK_THEME_AND_RUNTIME_REVIEW.md), [release history](RELEASE_GRAPHICS_PREVIEW.md).

## Important previous fixes

- 0.10.17: touch/controller input enabled character creation.
- 0.10.18–19: shader/pointer fixes; player-name editor moved to a dialog to stop scroll focus stealing.
- 0.10.20–22: native graphics menu avoids desktop JavaFX crash; frame sequence tracking fixes apparent 1 FPS; fullscreen/gear/opacity controls.
- 0.10.23: GL4ES VAO buffer-offset bug fixed with native guard-page regression; OpenAL lifecycle brings audio online.
- 0.10.24: unsupported occlusion queries disabled, fixing pop-in.
- 0.10.25–29: working Android ASan diagnostics, BTI/native ELF TLS fixes and isolated native startup test.
- 0.10.30: ASan identified shader-cache cleanup overread. Free hash-map values, then clear maps; retain capacity. Actual source regression reproduces old failure and passes fixed cleanup repeatedly. Stable baseline since this fix.
- 0.10.31: TCP checks slow from 500 ms to 15 seconds after readiness; JVM and viewer memory measured every 30 seconds; bounded GL error attribution retained separately from rotating console.
- 0.10.32: dark Android theme and white outlined pointer. No collector, graphics policy or runtime changes.

## Outstanding performance observations from the preceding run

- Client ran approximately 36 minutes and exited normally. Steady rendering/viewing approximately 29.9 FPS at 1280 x 720. No fatal ASan, OOM or frame-readback failures.
- Post-warmup PSS: client 1592–1777 MiB, server 1007–1030 MiB, Android viewer 111–143 MiB; no swap or proven continuing leak.
- Client Serial full GC pauses roughly 0.38–0.64 seconds and young pauses often 90–215 ms. `World.tick()` calls `System.gc()` about every ten minutes; disabling this globally may affect direct-buffer reclamation. Controlled collector comparison is future work, not an established fix.
- Server file descriptors fluctuate and drop around young collections; inspect owners before claiming a leak.
- Recoverable GL errors occurred during startup only. A GL4ES `glBindFramebuffer` attribution point reads existing driver error state, so it is not conclusive proof that binding caused the error.
- Remaining warnings include missing optional textures/models, unsupported normal-matrix notices, duplicate server templates and role-specific time-sync warning. Details are in the baseline review.
- Potential future work: avoid a new 3.5 MiB frame byte array every frame; reduce unchanged status/shader logging. Maintain ownership/lifecycle correctness. No speculative optimization has been applied.

## Build, publish and continuation

1. Check branch/worktree and remote head; preserve unrelated work. No owned-repo AGENTS.md was present at baseline.
2. Read current release workflow and test scripts. Run relevant host/Kotlin regressions; CI supplies the Android SDK and full native fixtures.
3. Bump version/code/suffix, report labels, workflow tag/title/instructions and release documentation consistently. Releases use distinct application suffixes: importing the existing exported working runtime may be needed in a new variant. Preserve backups.
4. Commit only original source/docs. GitHub plugin Git-tree/commit/ref tools can publish text changes when shell push is unavailable; compare the returned tree SHA with local `git write-tree` and update **mod-launcher-test** without force. Do not modify or merge main without a later user instruction.
5. Follow the exact commit's `Wurm Server APK` Actions run through all jobs. Download the release APK and checksum; verify version, package, signatures, expected native/runtime assets and source identity. Do not call an untested device fix confirmed.
6. Update this handoff with findings, implementation decisions, release link and remaining device checks. Give the user the APK and a short focused checklist.

Useful local investigation caches (not guaranteed to persist): `../wurm-review-inputs/client.jar` for private javap inspection; `../wurm-investigation/graphics-018/gl4es.tar.gz`; `../wurm-investigation/graphics-026/android-ndk-r26b`; `../wurm-investigation/graphics-021/tooling/` for Kotlin/JUnit; `../wurm-investigation/dark-032/release/` for the baseline APK. Use `java com.sun.tools.javap.Main` if the javap executable is absent.
