# 0.10.46 — job allocation recording and frame targets

The 0.10.45 device run completed 32m19s, with no recurrence of the startup
mipmap error and normal saves/exits. Automatic full-GC pauses and rising client
PSS remain. The user confirmed that creating a new character was intentional;
that creation is not a restore failure.

## What the executor inspection establishes

The exact supported client JAR has SHA-256
`79e7a5c822a4b744e56fb3aeef3a4f58d2943307163f3cd9fd4d6e9f7ea71a19`.
Its scheduler scans workers starting at slot zero and dispatches the first
queued job to the first available worker. A high Job_executor_0 share therefore
does not itself prove a faulty worker or imbalance that needs correcting.
The executor delegates to a job, invokes the completion callback, then notifies
the manager. Multiple rendering and animation job implementations can use it.

The last recording identifies heap churn by thread, not the actual jobs or
allocated object types. This build adds the missing job attribution. It does
not claim an allocation reduction or a memory-leak fix. No jobs are disabled,
rescheduled, skipped or pooled on speculative evidence.

## Optional job recording

Enable **Diagnostics → Enable job profiling · next client start** while the
client is stopped. It defaults off. For that client attempt only, the app adds
a tenth entry to its private graphics overlay: the verified Executor class.
The original imported JAR remains unchanged. With profiling off, the existing
nine-entry overlay is retained and the job helper is not used.

The exact original Executor class SHA-256 is
`1ee869cee678c32ad38a6993ba0bd619cc3c0d106e33b37ee2c457e97c9ae621`.
The adapter changes one interface invocation in `run()` to an authored static
delegate with the same two object operands. Two no-op bytes preserve instruction
offsets and all existing branches/stack maps. The original call reference is
retained; reversing the adapter must reproduce the complete original class
hash. The delegate resolves the public Job interface once and forwards the
same job/argument exactly once, preserving the original exception identity.
Callback and scheduler instructions remain unchanged. Unsupported bytes fail
the opted-in preparation check; disabling profiling restores the normal path.

During play, **gear → Memory recording → Record 5 minutes** starts observation.
The same dialog is available in Diagnostics. Close returns to play while
recording continues. **Stop recording** ends it early; otherwise it expires
after five minutes. Another recording can be started in the same session.

Boundaries:

- At most 128 thread/job-name pairs, with primitive counters and bounded text;
  no references to jobs, arguments, callbacks, or game threads.
- Approximate current-thread heap allocation and elapsed time around completed
  job calls. Top 12 pairs are reported approximately every 30 seconds and at
  recording end, with coverage/omission/failure counts.
- Memory/proc samples every five seconds on a daemon; observations retain the
  normal bounded support history. The original 30-second counters continue.
- No forced GC, heap dump, stack walk, event stream, synthetic game workload,
  server command, database access or change to the game's cleanup policy.
- Automatic expiry, explicit cancellation, one recording at a time, and daemon
  lifetime. Missing allocation counters disable recording, not ordinary play.
- Measurement adds overhead. Elapsed job time includes scheduling/GC waits;
  bytes describe allocation, not retained memory. Callback/waiting allocations,
  unfinished jobs, non-job threads, and native/direct/GPU allocations are outside
  the job byte totals. Unlisted top contributors are not zero.

`[client-memory-test]` BEGIN/JOBS/JOB/END records carry timestamps/PIDs. Five-second
memory rows use `role=client-recording` and the same client PID; correlate these
with ordinary `role=client`, GC/heap records and viewer timing. A short recording
that never crosses a natural full GC cannot establish the retained heap floor.
Ending the client also ends the recording process; it may not emit END during
shutdown, so export while the client is still running if the recording itself
needs a complete final boundary.

## Frame targets

Graphics settings now offer **30, 40, 50 and 60 FPS**, plus the existing 15 FPS
low-load option. The default remains 30. Saved targets apply on launch and can
be changed during play. Save waits for both graphics and frame-target feedback;
the runtime reports the accepted target with a timestamp and PID.

The frame pacer keeps one deadline and never emits catch-up bursts after a
stall. The viewer polls at 8 ms for targets above 30, retaining the existing
single read in flight and bounded frame-buffer reuse; lower targets retain
16 ms polling. No resolution or image-buffer ownership change is included.

These are targets, not guaranteed displayed rates or display refresh settings.
Rendering, readback, frame publication, bitmap copying and the device display
can limit results. Higher targets can increase power, heat and allocation/GC
pressure. Compare their measured frame timing independently of the memory test.

## Suggested Thor test

1. Save/stop client and server in 0.10.45 and export a full backup. Keep that
   working app. Install 0.10.46 alongside it (`.jobprofiletest`) and restore the
   backup. No stock-server reimport is required.
2. Keep resolution, world, mods and cleanup-switch setting consistent with the
   prior run. Use 30 FPS and normal logging. Enable job profiling before launch.
3. Once the world is loaded, start a five-minute recording. Spend about two
   minutes standing in one spot, then follow a short repeatable route and return.
   Let recording finish. After ordinary play/loading settles, record the same
   route again. Export a support bundle so we can compare job types, natural
   post-GC heap, direct buffers and PSS. Report which part was stationary/moving.
4. Separately try 40, 50 and 60 FPS through Graphics → Save. Check the live
   confirmation and controls; return to 30 if pacing or temperature is worse.
   Export after this comparison, then stop both runtimes normally.

A spare/test world restored from the backup can isolate these observations from
the main save. The recorder itself does not modify world state; normal gameplay
continues to do so. No need to recreate a character again just for this test.

The existing **JVM Memory Test** is another bounded option with the client
stopped: separate G1 and Serial child processes, 256 MiB heap limits, synthetic
heap/direct/classloader work, no Wurm or graphics classpath and no connection.
It tests the JVM foundations; it cannot reproduce an in-game leak. Its explicit
GC exercises remain confined to those isolated test children.

## Validation

Host checks cover all five pacing targets, live target reset, deadline recovery,
invalid values, bounded recording/automatic expiry/cancellation/restart,
exception forwarding, weak-reference release, and omission/unavailable counters.
The exact private client passes reverse-byte verification, complete optional
overlay selection, and real manager/executor execution of authored jobs with
all 64 completion callbacks and shutdown. No proprietary input or disassembly
is committed or published.

An isolated warmed host benchmark measured 100,000 no-op dispatches: 0 heap
bytes while recording was inactive and 128 bytes total while active, approximately
31 versus 164 ns per call in that run. This is a host helper-overhead measurement,
not an Android performance result or an allocation reduction in Wurm.

The local host suite passed 194 tests with 37 unavailable-fixture/platform skips;
subsequent focused job checks also passed the complete overlay and overhead
cases. All 101 Android/test Kotlin sources compiled, and 13 focused graphics/
frame-buffer/history tests passed. The changed window backend compiled against
the verified published 0.10.45 APIs. CI builds, packaged verification and device outcome
are recorded in the maintained handoff as they complete. Device memory and
high-FPS qualification remain open.
