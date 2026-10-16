# NeuroView — Android MRI Viewer

A local-first Android application for browsing MRI series, examining volumes, and eventually importing and converting DICOM/NIfTI data.

## Phase 1: Android foundation

- Kotlin, Jetpack Compose and Material 3 dark theme
- Single-activity architecture with type-safe Navigation 3 routes and a saved back stack
- Four connected UI screens: Home, Series, Viewer, Library
- Viewer navigation drawer, and a right-hand placeholder plane selector
- Unit tests for navigation semantics; GitHub Actions Android build/test workflow
- No network or storage permissions requested

## Phase 2: Rotating brain home preview

The home screen now displays a lightweight, **procedurally generated stylized brain** using native OpenGL ES 2.0. The model has distinct hemispheres, folded surfaces, a simplified cerebellum and brainstem, and diffuse/rim lighting. No assets or images are downloaded and no third-party 3D engine is required.

- Automatically rotates at a gentle pace while Home is visible.
- Drag to orbit horizontally and tilt vertically; release to resume auto-rotation.
- Pause/resume rotation and reset the view with accessible controls.
- Render-on-demand, approximately 30 FPS cap; stops animating while paused or off-screen.
- Honors the system's disabled animator preference, falls back gracefully when GLES2 is absent.
- Geometry validation tests cover normals, dimensions and GLES2 index limits.

**This preview is decorative and not an anatomical ground truth or patient-derived MRI.** The real 3D MRI renderer is planned for Phase 3.

## Phase 3: Real NIfTI volume viewer

1. Open **View MRI series** or **Library**, then tap **Open NIfTI**.
2. Select an accessible `.nii` or `.nii.gz` file with Android's document picker.
3. The selected scan is loaded privately on device; no server or account is used.
4. Use the slice slider, ± Slice buttons, or vertical swipes to move through MRI slices.
5. Swipe left on the image or tap the plane label to select **Axial**, **Coronal**, or **Sagittal**.
6. Adjust the two image windowing sliders for brightness and contrast.
7. Use the upper-left hamburger menu to return to Home, Series, or Library.

The app performs **world-space multiplanar reformatting** with trilinear interpolation using NIfTI RAS+ **sform** (preferred) or **qform** matrices. Missing orientation is clearly warned about. Supports little/big endian NIfTI-1 scalar data, gzip, and the first volume of 4D sequences. Mobile safety limits cap an active volume to 48 million voxels (about 183 MiB as 32-bit floats). Unsupported datatypes, NIfTI-2, multi-file .hdr/.img pairs, DICOM, and multi-volume sequence navigation are not yet implemented.

**Important:** This is a research/education viewer, **not for medical diagnosis**. Always independently verify radiological orientation against trusted software before interpreting images. The Phase 3 library holds just one open document; multi-series management and conversion are later phases.


## Open in Android Studio

1. Clone this repository and open its root directory in a recent Android Studio.
2. Use **JDK 17** and install **Android SDK Platform 37** in the SDK Manager.
3. Allow Gradle sync, then run the `app` configuration on an Android device or emulator (minimum Android 8.0 / API 26).
4. For command-line verification: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (Windows: `gradlew.bat`).

Versions: AGP 9.4.0, Gradle 9.6.0, Kotlin 2.2.21, Compose BOM 2026.09.00, Navigation 3 1.2.0.

## Planned progression

- Phase 2 — Home screen 3D brain visualization. **Implemented.**
- Phase 3 — Real NIfTI loading and multiplanar viewing. **Implemented.**
- Phase 4 — Scan and series selection from imported datasets.
- Phase 5 — Local library with SAF import and deletion.
- Phase 6 — DICOM to NIfTI and clearly labeled derived-DICOM export.
- Phase 7 — Device validation, performance, and clinical-orientation sanity checks.

Sensitive images, generated datasets, and patient exports are excluded by `.gitignore`. Use only public or synthetic test data in the source tree.

See [architecture notes](docs/ARCHITECTURE.md).
