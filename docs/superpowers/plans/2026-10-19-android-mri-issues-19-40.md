# Android MRI GitHub Issues #19–#40 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address all 22 issues currently open on `MarcusFunt/Android-MRI-viewer`, including repository-owned code, tests, and validation documentation.

**Architecture:** Keep the existing Kotlin and Compose structure. Make viewer behavior stable across recomposition, keep rendering and spatial behavior independently testable, and group UI work around the existing screen and preference owners. Add repeatable synthetic or instrumentation checks where the repository can prove behavior; label physical-device and independent-viewer gates clearly.

**Tech Stack:** Kotlin, Jetpack Compose, Android SDK 37, Gradle wrapper, JUnit, Compose UI tests, Android instrumentation tests.

**Spec:** Open GitHub issues [#19–#40](https://github.com/MarcusFunt/Android-MRI-viewer/issues?q=is%3Aissue+is%3Aopen).

## Global Constraints

- Preserve the existing single-plane MRI viewer and its current Home visual direction.
- MRI display pixels must remain faithful grayscale; never infer out-of-volume geometry from low intensity.
- Keep Android `minSdk 26`, the existing NIfTI/catalog compatibility, and BrainSuite HTTPS, consent, and token safeguards.
- Do not put patient names, paths, image pixels, or secrets into diagnostics or committed fixtures.
- Treat physical-device timing and independent medical-viewer comparisons as unverified until actually recorded.

## Review Focus

- Render commits and preference changes during an active slice gesture must not cancel or jump the gesture.
- Focus entry/exit, rotation, and navigation must preserve slice, zoom, display window, and probe state.
- Large fonts, small widths, landscape, insets, TalkBack, and keyboard focus must keep controls reachable.
- Oblique, mirrored, and anisotropic affine transforms must keep RAS labels, slice position, and probe coordinates aligned.
- Cancellation and stale callbacks must not report completion early, overwrite a newer operation, or leave partial output.

## Tasks

### Task 1: Viewer gestures, navigation controls, and motion semantics (#19–#23)

**Files:** `ViewerScreen.kt`, viewer preference storage/settings, motion preference code, viewer unit and instrumentation tests.

- [x] Trace the active `pointerInput` keys, live state reads, slice accumulator, and fling lifecycle.
- [x] Add regression coverage for bitmap/state commits during an active gesture, long drags, precision scrubbing, and all motion preferences; record real-device flick/repeat testing as pending.
- [x] Keep gesture detectors stable across render frames; preserve fractional movement and exact landing.
- [x] Add the persistent, default-off previous/next setting; expose equivalent slider accessibility increment/decrement when buttons are hidden.
- [x] Decouple useful inertial slice navigation from decorative transition settings with a clear explicit policy.
- [x] Verify RTL/slider semantics in automated tests; record real-touch target and device validation as outstanding.

### Task 2: Image fidelity, quality policy, and spatial QA (#24, #25, #28)

**Files:** `ViewerScreen.kt`, `MprRenderer.kt`, `SpatialAffine.kt`, `RenderQualityPolicy.kt`, `app/src/test/.../qa`, synthetic fixture tooling, validation documentation.

- [x] Add a grayscale ramp and low-intensity regression test, then render MRI pixels opaque and keep the viewport background neutral black.
- [x] Exercise preview-to-still resolution behavior and render-quality policy on sharp synthetic data; avoid arbitrary upscaling.
- [x] Add affine-grounded asymmetric landmarks, flips, non-isotropic spacing, and oblique-coordinate cases with documented expected positions.
- [x] Capture/reference only synthetic non-identifying outputs in repository tests.
- [x] Document which reference-viewer and physical-device comparisons still require external execution.

### Task 3: Library hierarchy, sorting, aliases, and pluralized copy (#29–#31)

**Files:** `LibraryScreen.kt`, `LibraryScanRow.kt`, catalog presentation helpers, `strings.xml`, unit and Compose UI tests.

- [x] Separate browsing mode from sorting and secondary filters while retaining search, collections, and active-filter clearing.
- [x] Reduce scan-row framing and prioritize thumbnail, alias, essential metadata, and a discoverable overflow.
- [x] Add plural resources/helpers for zero, one, and many scans, collections, slices, imports, conversions, cache removals, and status summaries.
- [x] Keep long aliases and filenames safely truncated while exposing full metadata to accessibility and detail surfaces.
- [x] Cover one-scan, dense, long-alias, and no-search-results presentation logic; visual empty-state screenshots remain in the QA matrix.

### Task 4: Contextual settings, motion selector, transitions, and accessibility (#32, #33, #37, #38)

**Files:** `NeuroViewSettingsSheet.kt`, settings callers, `MotionTokens.kt`, theme components, accessibility tests, `docs/TESTING.md` or a focused QA matrix.

- [x] Open the shared settings sheet at the caller-relevant section without resetting stored preferences.
- [x] Theme the motion selector to NeuroChrome and retain System default / Reduced / Off with clear descriptions and selected state.
- [x] Apply restrained press, selection, navigation, sheet, import, and Focus feedback using central motion tokens; preserve direct manipulation.
- [x] Add semantics, standard slider actions, keyboard/focus order, and >=48dp essential controls where missing.
- [x] Document the font-scale, size, orientation, cutout/inset, TalkBack, reduced-motion, contrast, and announcement test matrix; device/manual sweeps remain pending.

### Task 5: Progressive BrainSuite and conversion workflows (#34, #35)

**Files:** `BrainSuitePanel.kt`, `ConversionWorkflow.kt`, `LibraryScreen.kt`, string resources, workflow tests.

- [x] Reduce the default form to method, configured host, consented input, and clear next action; keep advanced connection and previous jobs available.
- [x] Explain disabled submission requirements and keep upload, remote queued/running, download, and failure states truthful.
- [x] Preserve explicit consent, HTTPS-only hosts, secure token handling, and transfer-versus-remote-job cancellation language.
- [x] Compact conversion tools, prioritize folder selection, and give actionable compatibility errors while retaining derived-Secondary-Capture caveats.
- [x] Cover cancellation cleanup, partial outputs, unsupported syntax, provider failures, and workflow state copy with JVM/instrumentation tests.

### Task 6: Home polish and Focus behavior (#36, #40)

**Files:** `HomeScreen.kt`, `BrainGlSurfaceView.kt`, `BrainRenderer.kt`, `ViewerScreen.kt`, activity/window integration, UI tests.

- [x] Make the recent-scan shortcut quieter and alias-led while keeping full metadata available.
- [x] Add restrained press feedback and accessible labels to Home actions.
- [x] Tie GL rendering to lifecycle/visibility so Home rotation suspends when inactive and readiness remains observable.
- [x] Add optional immersive Focus behavior with an accessible exit; preserve viewer state and respect reduced motion and system gestures.
- [x] Measure and report only emulator/device results actually collected; do not claim phone startup or power savings from code inspection.

### Task 7: Provider fault injection and performance evidence (#26, #27)

**Files:** NIfTI load/session code, controllable test `ContentProvider`, instrumentation tests, `docs/PERFORMANCE_BASELINE.md`, `docs/TESTING.md`.

- [x] Trace URI read, progress, cancellation, resource close, temporary-file cleanup, and session replacement paths.
- [x] Add controllable slow, stalled/resuming, failing, truncated, permission-denied, and cancellation-ignoring provider scenarios.
- [x] Assert truthful progress/state, safe navigation, no stale callback overwrite, and eventual cleanup; all four provider instrumentation scenarios passed.
- [x] Expand the benchmark protocol and capture ten-sample v1.4.0/candidate API 35 emulator proxies; report the physical-device dimensions not measured as pending.
- [x] Mark long-running hostile-provider and physical-device benchmark acceptance as pending.

## Execution Ledger

- Ruling: Treat “address all issues” as authorization to implement code, tests, and documentation, but not to close GitHub issues or publish changes — issue closure and publication affect external project state; cost if wrong: follow-up issue administration.
- Ruling: Use synthetic fixtures and emulator evidence for repository-owned validation; leave real-device and independent-viewer gates explicitly pending — the referenced hardware/data are not available in this task; cost if wrong: empirical acceptance remains incomplete.
- [x] Task 1 implementation and emulator regression suite complete; physical touch acceptance pending.
- [x] Task 2 synthetic fidelity/geometry coverage complete; independent viewer/anatomical comparison pending.
- [x] Task 3 library/home presentation and pluralization implementation complete; visual accessibility sweep pending.
- [x] Task 4 contextual settings, motion, and accessibility implementation complete; OS-level/device matrix pending.
- [x] Task 5 BrainSuite, conversion, and provider workflow implementation complete; opt-in long-duration provider run pending.
- [x] Task 6 Home and Focus implementation complete; physical-device performance acceptance pending.
- [x] Task 7 provider regression coverage and emulator benchmark protocol/results complete; physical-device performance acceptance pending.
