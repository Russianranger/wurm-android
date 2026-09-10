# Wurm Server 0.10.17 — touch and visible pointer controls

## Device evidence

The Thor's 0.10.16 report contains the corrected bootstrap hash
`82a39c9797a394b036785ad366e5c1a6ed0de935ab1f3b82e1fcc80f5181dfa4` and
`SERVER_MODE_ACTIVE personal=true`. The server reports that Thor was created
successfully, and the client reports authenticated=true, loggedIn=true and
GAME_LOOP. The screenshot shows Wurm's Define your character dialog, gender
selection and kingdom selection. Import and initial player creation/login now pass.

A missing POC_UPGRADED message does not invalidate this: the installed hash and
active server mode confirm the intended bootstrap. Final character choices and
terrain/gameplay have not yet been verified.

## Cause and change

The frame viewer was an ImageView with no touch handler. The pinned Pojav/LWJGL
input adapter also provides no native cursor rendering. Existing controller mouse
commands could therefore move an invisible pointer; the supplied rolling log does
not establish whether the user tried those commands.

This release adds:

- Touch positions mapped through FIT_CENTER letterboxing to normalized top-left
  game coordinates. Touch down moves then presses the left mouse button; dragging
  moves it; touch up/cancel releases it. Taps outside the image are ignored.
- A software crosshair drawn by Android at the game thread's actual mouse position.
  It hides when Wurm grabs the mouse for relative input. Frame metadata carries
  pointer coordinates and the applied input-event count without altering game pixels.
- A larger game view by default, with Show diagnostics / Expand game controls.
  The viewer checks for new frames every 100 ms; the existing readback remains
  limited to five frames per second. This is still a diagnostic rendering path.
- Existing controller mappings remain: right stick moves the mouse, A/RT clicks,
  LT right-clicks, left stick sends WASD. Button events use the current cursor
  position, including an initial click before the first movement event.
- The Client Report retains pointer metadata and the applied-event count with the
  last frame, independently of shader log spam.

No game JAR, player database, login checks or server bootstrap are changed.

## Verification

Local tests cover touch geometry for both letterbox orientations, input unavailable,
ordered tap/drag/release/cancel, normalized protocol bounds, slow controller motion,
button limits, reset releases, and atomic frame metadata with preserved pixels.
A separate test against the actual pinned LWJGL adapter and EventQueue verifies
mouse coordinates/Y conversion, press/release order and held-state cleanup. Only
the desktop Display dimensions are stubbed for this test. CI runs this check against
the newly built API after Android builds, unit tests and lint.

The reported GL4ES custom-fragment shader failures involving _gl4es_FragColor and
_gl4es_AlphaRef are still open. The sky/UI screenshot does not prove terrain renders.
The older EGL/Scudo shutdown issue remains open too.

## Thor test

1. Install **0.10.17** (code 31, separate touchinput package). Stop older clients
   and servers, and keep their data.
2. Import the same server and complete client ZIPs; choose **Adventure / Thor**.
3. Start Local Game. In **Define your character**, tap the gender choice and
   kingdom dropdown, then tap **Send**. Complete any subsequent setup prompts.
4. Also try moving the cyan pointer with the right stick and clicking with A or RT.
   Report whether touch and controller input work separately.
5. If setup completes, take a screenshot of the resulting world, then test
   movement/look briefly. Note missing terrain or other rendering problems.
6. Stop Client and export **Client Report**. Stop Server, wait, and export
   **Server Session Report**. Send both reports and the screenshot.
7. If setup and clean shutdown succeed, restart without reimporting to check that
   the same character and location persist.
