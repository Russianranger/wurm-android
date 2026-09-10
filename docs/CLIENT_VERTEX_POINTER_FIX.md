# Wurm Server 0.10.19 — vertex pointer and name editor fix

## What the 0.10.18 report established

The shader undeclared-identifier failures seen in 0.10.17 are absent. The viewer
reported approximately 10–12 presented FPS during the later game loop, with an
internal swap rate around 27–46 FPS. The client nevertheless crashed before the
user finished character setup; completed creation is not claimed for this test.

The final child (PID 21052) exited 139 after about 49 seconds. Its native draw record
was still at `entered-driver`: GL_LINES, 192 unsigned-short indices, program 4,
no element buffer, and an enabled four-float vertex attribute with no real VBO.
The recorded vertex pointer `0xea6451c8b0` exactly matches both the fault address
and memcpy's source register. The stack is Adreno glDrawElements through GL4ES
fpe_glDrawElements and draw_renderlist, called from glDrawArrays.

An earlier child in the same report aborted in eglDestroyContext with a Scudo
corrupted-chunk-header diagnostic. A GL_INVALID_OPERATION also appears before
the final crash. These observations remain tracked separately: the patch below
is not evidence that all native corruption, GL errors or shutdown failures are fixed.
The server report shows a subsequent normal requested shutdown with exit 0.

## Native correction

GL4ES's internal FPE pointer setters receive absolute host addresses from render
lists. They incorrectly attach the application's currently bound array-buffer
object. The later attribute realization adds that buffer's data base to the
already complete pointer. This is a concrete source defect consistent with the
invalid high address and intercepted draw in the report.

The checked downstream patch clears that buffer association in all six internal
setters: vertex, color, secondary color, normal, texture coordinate and fog coordinate.
It preserves the public vertex-attrib offset path, application buffer binding,
render-list VBO activation, alpha testing and crash breadcrumbs. It does not skip
draw calls or guess which addresses are safe. Whole-file source checks reject drift.

The native regression compiles the original and patched fpe.c against upstream
structures and uses its actual pointer-realization expression. With a bound VBO,
the original misaddresses all six attributes; the patched version preserves every
host address. Both versions are checked with no buffer bound, and the test verifies
that the application's binding remains intact. Device stability is still to be tested.

## Name editing and display timing

The client page previously left an EditText focused while its status/log labels
were updated every second. Relayout could scroll that focused editor back into
view. Name editing now uses a Save/Cancel dialog beside the launch buttons; no
editable field remains in the scrolling status page. Invalid names remain in the
dialog, and the keyboard's Done action saves a valid name. Unchanged labels are
not assigned again during refresh.

Frame transfer time is now included in the 15 FPS presentation interval, instead
of starting an extra full interval after readback/publication finishes. This
corrects avoidable cadence loss; 15 FPS remains a target, not a speed guarantee.
The display still uses the interim readback/file transfer path.

## Thor test

1. Keep the previous app and backups. Stop any older server normally. If preserving
   the latest world, use **Export working runtime ZIP** after it stops.
2. Install **0.10.19** (code 33, separate `vertexfix` package). Import your working
   server ZIP and the same complete client ZIP; select **Adventure**.
3. On the Client page, use **Change Player Name**, enter an unused name such as
   **Thorvertex**, then **Save**. Check that scrolling stays where you leave it and
   **Start Local Game** is easy to reach. Cancel and invalid-name handling can also
   be checked without launching the client.
4. Start Local Game, finish character setup and move/look around for two minutes.
   Note smoothness, missing graphics and whether the same crash recurs.
5. On a crash, export **Client Report before Retry**. Stop the server normally and
   export **Server Session Report**. Send both and a screenshot.
6. If stable, stop client/server normally, export both reports, then restart within
   this same app without reimporting to check the character's persistence.

The world export contains old character data but does not migrate the separate
client login identity. Use an unused test name in the new preview. Existing Thor
and Thorperf remain associated with their earlier apps; keep those apps installed.
No desktop launcher or replacement game files are required for this test.
