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

## Phase 3: MRI volume and MPR

- `mri/NiftiReader`: bounded, streamed NIfTI-1 `.nii` or `.nii.gz` decoder, with datatype/bitpix validation, endian handling, proper slope/intercept semantics, first-frame 4D behavior, and volume-size limits.
- `mri/SpatialAffine`: sform/qform/fallback voxel-to-RAS+ mm transforms and inverse mapping. The header's qform/sform are defined in NIfTI RAS+; they are not DICOM LPS without conversion.
- `mri/MprRenderer`: world-coordinate axial/coronal/sagittal slices, 384-pixel max width/height, trilinear sampling, grayscale window/level. Radiological viewing convention is patient-right screen-left for axial/coronal; anterior screen-left for sagittal. Medical use is NOT supported.
- `ui/NeuroViewApp`: Android Storage Access Framework OpenDocument with read-only URI permission. Only one open NIfTI is held as a session-wide active volume, reloaded on activity recreation. No custom storage or sync.
- `ui/screens/ViewerScreen`: asynchronous bitmap updates, orientation warning, vertical image drag and slider slice navigation, right-hand MPR selector, and brightness/window-width sliders. No DICOM yet.
- Tests use small synthetic volumes (including big endian and gzip) to catch spatial flips, reading errors, and bad affine inverses.

### Safety boundaries and limitations

No network permission and no PHI logging are introduced. The app does not upload data. Arbitrarily large volumes are rejected rather than attempting to allocate beyond device capacity. NIfTI-2, vector/RGB/complex data, masks with unusual intent, 5D/6D series and DICOM are not supported. Slices are resampled (not independently acquired images), and anisotropic scans can look blurry in off-axis views. Volumes without orientation metadata are flagged with an explicit warning. This app has not undergone medical-device or diagnostic validation.

## Phase 4: Scan and series browsing

- `mri/NiftiSeriesInspector` reads only the initial 348-byte NIfTI-1 header (gzip supported) to index dimensions, voxel spacing, dtype, timepoints, spatial metadata provenance, and descriptive text, without allocating the complete volume.
- `catalog/ScanCatalog` persists a small JSON catalog in app-private SharedPreferences and retains SAF read permissions. Imported files are **referenced, not duplicated**; no network permission or external file writes. A collection groups files selected in a single import operation, not a verified clinical study.
- `ui/screens/SeriesScreen` is a real grouped, lazy scrolling series browser with searchable-by-eye metadata labels and preview cards. Touching one opens its selected NIfTI volume in Phase 3's `ViewerScreen`.
- Preview generation uses a `Mutex` to serialize full-volume loads, avoiding concurrent NIfTI allocations, and caches 144-pixel axial center-slice thumbnails as private PNGs in `cacheDir`. This cache may be evicted by Android and will be rebuilt on demand.
- `ui/NeuroViewApp` uses `OpenMultipleDocuments` and routes selected series IDs through Navigation 3. Only one selected NIfTI volume is held in memory at a time.

**Not a full study library.** No patient grouping, DICOM study UID assignment, metadata mutation, scanner/protocol inference, deletion or conversion is performed. Plain NIfTI generally cannot prove a series belongs to a given study; Phase 5 should introduce explicit user-owned collections rather than implicit patient matching. Preserve privacy by never logging scan filenames or descriptions.
