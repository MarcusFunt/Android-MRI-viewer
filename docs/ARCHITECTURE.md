# Phase 1 architecture

## Technology

- Kotlin + Jetpack Compose + Material 3
- One Activity (MainActivity) with edge-to-edge content
- Navigation 3 stable: type-safe serializable destination keys and saveable back stack
- Unit-tested navigation rules (push, avoid duplicate, pop-to-existing, return Home)
- Dark theme via Material3 `darkColorScheme`

## Screens and flow

Home → Series → Viewer
Home → Library

The Viewer has two **distinct** overlays:
- Top-left hamburger → app destinations (Home, Series, Library)
- Left swipe / plane control → end-aligned axial, coronal, sagittal plane selector

Screen data is explicitly placeholder-only in Phase 1, not a real scan. Later phases add a real library repository, series metadata, volume rendering, and file converters behind separate interfaces.

## Privacy and safety

- No INTERNET or broad filesystem permissions
- Android Storage Access Framework (future) rather than granting unrestricted storage access
- Future imports should use app-private encrypted-at-rest options and avoid logging PHI
- Conversion from NIfTI to DICOM cannot recreate discarded DICOM acquisition metadata; mark output as derived
- Always display medical image orientation with validated affine / DICOM patient-space mapping
- Not intended for diagnosis or medical decisions

## Boundaries

Phase 1 deliberately does **not** include the 3D brain asset, image decoding/rendering, DICOM conversion, image-file import, library persistence, or clinical measurement tools.

## Phase 2 brain preview rendering

The Home Card hosts `BrainPreview`, which embeds `BrainGlSurfaceView` (OpenGL ES 2.0) via Compose `AndroidView`. Meshes are synthesized by the pure Kotlin `BrainGeometry` builder (two hemispheres, two cerebellar lobes, one stylized stem) and lit using a compact custom GLSL shader. Model geometry is only a decorative visualization and is not a medical atlas.

`BrainGlSurfaceView` uses `RENDERMODE_WHEN_DIRTY`; touch gestures modify yaw/pitch, while an Activity-lifecycle-bound animator issues approximately 30 render requests per second. Pausing the Activity stops both animation and the GL thread. When Android disables animators or GLES2 isn't supported, animation stops or a text fallback is shown. The preview has no dependency on MRI input, which remains separate for Phase 3.

Manual QA for device/emulator: Home displays the shaded brain, yaw changes with drag, pitch is clamped, Pause holds the view still, Resume restarts, Reset returns to the default angle, Android back navigation still works, and opening Library/Series stops the home rendering. Test portrait/landscape, dark theme, and Android 8.0+; no medical use is intended.
