# Viewer navigation and image validation — 2026-10-19

This report covers issues #19–#25, #28, #33, #36 and the viewer portion of #37.
It is engineering regression evidence, not clinical validation. Every committed
fixture is synthetic and contains no patient metadata.

## Changes and cause

- **#19:** Rendered bitmap and decorative motion preference were pointer detector
  restart keys. They are now read through updated state. A gesture owns its
  fractional slice coordinate until release; rendering and preference changes
  cannot replace it. Volume, plane and explicit interaction mode changes still
  start a fresh detector. Existing 18 dp per slice sensitivity is preserved.
- **#20:** The explicit persisted **Inertial slice scrolling** switch defaults on
  independently of System/Reduced/Off decorative motion. Android animator scale
  zero disables momentum, including during continuation. Direct drag and slider
  distance are identical in all motion preferences. Slider release never flings.
- **#21:** A single custom pointer owner supports thumb dragging with continuous
  upward sensitivity interpolation (1× through 30 dp, 1/3 at 70 dp, 1/8 at 120 dp,
  1/16 at 160 dp). Changing height alone never changes selection. Fractional
  movement survives sensitivity changes. RTL, tap to seek, a 56 dp target,
  keyboard arrows/Home/End, TalkBack progress and previous/next actions are included.
- **#22:** Previous/next controls default off for existing and new installations
  unless explicitly enabled. A persisted Navigation switch updates the open
  viewer immediately. The scrubber expands into the freed width. Settings
  switches expose one labeled accessible toggle with a >=48 dp target.
- **#24:** Bitmap creation now preserves the renderer's opaque grayscale pixels
  verbatim. The viewport is neutral opaque black. Cached scalar geometry already
  uses NaN for genuinely outside-volume locations; brightness is never used as a
  geometry mask.
- **#33:** Settings menus use the navy NeuroChrome surface, zero tonal elevation,
  16 dp radius, explicit foreground colors, cyan selected state and semantics,
  standard focus handling and >=48 dp rows. Material popup transitions use
  Compose's Android duration scale.
- **#36:** Optional persisted Immersive Focus hides system bars, supports normal
  edge gestures, keeps Exit focus visible and lets Back leave Focus. Default Focus
  remains unchanged. Zoom mode, scale and pan are saveable and independent of
  Focus visibility; plane, slice, window and probe state remain in their existing
  owners. Image gestures remain immediate.
- **#37 audit:** Navigation, Library expansion/import progress, operation feedback
  and Focus already consume MotionToken. Material buttons, icon actions and plane
  tabs retain standard press/ripple feedback. Plane label color now uses QUICK
  tokens; the selected underline and image/label updates remain immediate. The
  bundled stable Material3 artifact does not expose MotionScheme publicly, so
  built-in sheet/menu timings retain their supported Android-scaled behavior.

## Reproducible reference geometry (#28)

Run `python scripts/qa/viewer_reference.py` to regenerate the committed resources
under `app/src/test/resources/viewer-reference`. This uses only Python's standard
library and a closed-form inverse of an asymmetric flipped, anisotropic, oblique
RAS transform. The 7×6×5×2 NIfTI deliberately gives its second frame entirely
different values. The independent field is `17*i + 3.5*j + 0.75*k - 50`.

`ViewerReferenceTest` checks all pixels through both MprRenderer and
MprRenderSession, achromatic channels, opaque alpha, physical slice locations,
an explicit world landmark, probe IJK/intensity consistency and first-frame-only
4D loading. The three golden planes contain 24,768 pixels in total:

| Plane | Reference dimensions | Intensity tolerance | Physical tolerance |
| --- | --- | --- | --- |
| Axial | 96×83 | <=1 of 255 | 0.00001 mm |
| Coronal | 94×96 | <=1 of 255 | 0.00001 mm |
| Sagittal | 81×96 | <=1 of 255 | 0.00001 mm |

The tolerance allows NIfTI float32 affine storage and grayscale quantization.
These checks passed for both render paths. Screenshot comparisons against an
established viewer and anatomical scan comparisons remain pending.

## Sharp fixture results (#25)

`SettledRenderDetailTest` samples a known 513×513 sharp stripe fixture and compares
linear display reconstruction against the known source voxel centers. Lower
grayscale mean absolute error (MAE) means more recovered source detail.

| Quality | Preview max side | Settled max side | Preview MAE | Settled MAE |
| --- | ---: | ---: | ---: | ---: |
| Auto | 160 | 384 | 89.78 | 37.83 |
| Smooth | 112 | 256 | 118.62 | 56.61 |
| Balanced | 160 | 384 | 89.78 | 37.83 |
| High detail | 224 | 576 | 64.80 | 25.16 |
| Manual fixture setting | 160 | 576 | 89.78 | 25.16 |

This is a desktop JVM synthetic detail comparison, **not** a phone performance or
anatomical image-quality result. Policy caps were intentionally left unchanged.
Source dimensions, viewport/output size, p50/p95 latency, peak memory and thermal
fallback require the separate device experiment before evidence-based tuning.

## Automated validation

With JAVA_HOME `D:\Android\Studio\jbr`:

```powershell
python scripts/qa/viewer_reference.py
.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest
```

Result: **BUILD SUCCESSFUL in 11s**, 120 JVM tests across 23 suites; zero failures,
errors or skipped tests on the original viewer implementation worktree. After
integration, the combined repository rerun built the app and test APKs, compiled
Android tests, ran lint, and passed **135 JVM tests** with zero failures/errors.
The full instrumentation suite then passed **30 tests** on the isolated API 35
emulator (`emulator-5558`), including all seven viewer touch/navigation tests and
the provider fault scenarios. The existing `emulator-5554` and `emulator-5556`
profiles were not used for installs or test runs. New JVM coverage includes fractional drag continuity,
precision interpolation and RTL, clamping, the momentum policy, independent
geometry, 4D first-frame loading, sharp fixture detail and grayscale levels 0–32.

`ViewerTouchNavigationTest` includes seven instrumented regressions: a held drag
with 24 deliberately replaced bitmaps and live motion preference changes, equal
manual drag/accessible slider navigation across all motion modes, upward slider
precision without jumps, Focus zoom/slice retention, exact dark grayscale Bitmap
copying, immediate persisted step-button activation without slice movement, and
visible toolbar/overflow actions. All seven passed in the integrated emulator run.

Opt-in system traces expose `NeuroView.pointer.start/stop`,
`NeuroView.drag.start/move/end/cancel`, `NeuroView.commit.preview/still` and counters
for slice index, generation and output dimensions. They contain no file names,
scan identity, patient metadata or voxel values.

## Release acceptance still pending

- **#19/#20/#21/#22/#23:** Test long/slow/alternating/repeated drags and rapid flicks on a real touchscreen,
  both button states, Zoom/Probe modes, RTL/large font/small screen, rotation,
  back/forward and process recreation. The real-device run is a release gate.
- **#24/#28:** Capture all-plane/window/zoom/probe/Focus reference screenshots with
  the independent fixture and a legitimate non-identifying anatomical NIfTI.
  Check unknown orientation warnings and 4D wording in the UI.
- **#25:** Phone visual comparison, no stuck preview, frame pacing, p50/p95,
  memory and thermal observations. This report does not establish those results.
- **#33/#36/#37:** Check light/dark/system settings, keyboard/D-pad/TalkBack,
  Android animation scales, immersive system gestures, motion clips and frame
  pacing on supported devices. Material sheet/menu app-specific timing remains
  constrained by the supported public API; Android animation scale still applies.
