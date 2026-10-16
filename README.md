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


## Phase 4: Multiple scans & series browser

- Select **View MRI Series → Add NIfTI Series** or **Library → Import NIfTI Files**.
- Pick **multiple** `.nii` or `.nii.gz` documents at once using the Android document picker.
- Each selection batch becomes an **import collection**, never an inferred medical study.
- Series cards show a real central axial preview, original filename, dimensions, voxel spacing, scalar datatype, orientation-source summary, and any NIfTI description.
- Tap a card to load its volume in the existing axial/coronal/sagittal viewer.
- The read-only catalog stores file URIs and header metadata only in app-private preferences. Android document permissions survive app restarts.
- Preview thumbnails are generated lazily, one large volume at a time, stored as small app-private cache images. The original NIfTI files are not copied or modified.
- Duplicate file URIs and unsupported formats are skipped with a visible import summary; up to 64 files can be selected in one batch.

**Limitations:** A batch is not necessarily one patient or study. NIfTI files do not reliably provide DICOM StudyInstanceUID or SeriesInstanceUID; this app does not invent or infer them. Thumbnail generation reads the full volume and can be slow for large files. If an external document is moved, revoked or unavailable, the stored URI may no longer open. Deletion, rename, multi-file migration, storage ownership and DICOM conversion belong to later phases.

## Phase 5: Manage your MRI library

From **Library** you can now:

- Create empty named collections (for your own organizational purposes; never inferred DICOM studies).
- Rename collections and assign **local-only display aliases** to NIfTI entries. The original filename remains visible and unchanged.
- Restore an MRI entry's original display name.
- Move an MRI from one collection to another without re-importing or copying its source.
- Search across collection names, local labels, original filenames and NIfTI descriptions.
- Remove a single MRI entry, or remove an entire collection and all its listed entries, using explicit confirmation prompts.
- Clear generated thumbnail cache without losing imports or touching the original medical images.
- Import additional NIfTI files and open any listed volume in the 3-plane viewer.

**Safety behavior:** “Remove” means **unlink from this app**, not delete from phone storage. This is deliberate: scans may be stored in an external SAF/cloud document provider, and accidental deletion is irrecoverable. Removing an entry deletes its app-private thumbnail and relinquishes the persisted read grant if it is no longer referenced. Editing names only changes app-private metadata, not source NIfTI headers. Actual DICOM conversion and durable source file ownership are not implemented.

**Privacy:** The catalog lives in app-private SharedPreferences (not encrypted); cached derived MRI thumbnails live in app-private cache. Neither is sent over the network. Android backups are disabled. If the app is uninstalled, the private catalog is lost but original source MRI documents remain where they were. Avoid making diagnostic decisions from this educational viewer.

## Open in Android Studio

1. Clone this repository and open its root directory in a recent Android Studio.
2. Use **JDK 17** and install **Android SDK Platform 37** in the SDK Manager.
3. Allow Gradle sync, then run the `app` configuration on an Android device or emulator (minimum Android 8.0 / API 26).
4. For command-line verification: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (Windows: `gradlew.bat`).

Versions: AGP 9.4.0, Gradle 9.6.0, Kotlin 2.2.21, Compose BOM 2026.09.00, Navigation 3 1.2.0.

## Planned progression

- Phase 2 — Home screen 3D brain visualization. **Implemented.**
- Phase 3 — Real NIfTI loading and multiplanar viewing. **Implemented.**
- Phase 4 — Scan and series selection from imported datasets. **Implemented for multiple NIfTI documents.**
- Phase 5 — Local library with SAF import and safe removal of in-app references. **Implemented.**
- Phase 6 — DICOM to NIfTI and clearly labeled derived-DICOM export.
- Phase 7 — Device validation, performance, and clinical-orientation sanity checks.

Sensitive images, generated datasets, and patient exports are excluded by `.gitignore`. Use only public or synthetic test data in the source tree.

See [architecture notes](docs/ARCHITECTURE.md).
