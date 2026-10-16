# NeuroView — Android MRI Viewer

## Download Android APK

**[Download NeuroView v0.7.0 APK (Phase 7)](https://github.com/MarcusFunt/Android-MRI-viewer/releases/download/v0.7.0/NeuroView-v0.7.0-debug.apk)**  
[Release notes and SHA-256 checksum](https://github.com/MarcusFunt/Android-MRI-viewer/releases/tag/v0.7.0)

This is an experimental **debug-signed prerelease APK**, not a production or medical diagnostic application. Only install APKs you trust. The APK comes directly from the successfully tested GitHub Actions build on `main` (run `38892440182`).

An offline-first Android application for browsing MRI series, viewing NIfTI volumes in three planes, organizing a local MRI library, and performing supported DICOM/NIfTI conversions.

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

The app performs **world-space multiplanar reformatting** with trilinear interpolation using NIfTI RAS+ **sform** (preferred) or **qform** matrices. Missing orientation is clearly warned about. Supports little/big endian NIfTI-1 scalar data, gzip, and the first volume of 4D sequences. Large volumes are catalogued from their headers without loading voxel arrays; the viewer uses disk-backed memory-mapped voxel access whenever the 48-million-voxel / available-heap in-memory threshold is exceeded. That threshold is now a choice of storage strategy, not an import rejection. Unsupported datatypes, NIfTI-2, multi-file .hdr/.img pairs, DICOM, and multi-volume sequence navigation are not yet implemented.

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

## Phase 6: Offline DICOM ↔ NIfTI conversion

Open **Library** for the two conversion workflows:

**DICOM → NIfTI (.nii.gz)**

1. Tap **Convert DICOM Folder → NIfTI** and choose the directory containing the entire MR series. Subdirectories are scanned too; extensionless DICOM Part 10 files are recognized from their headers. Non-DICOM sidecars are skipped.
2. The folder must contain **one compatible series** of 1–1024 uncompressed, single-frame DICOM slices. If the folder contains multiple studies/series or irregular geometry, conversion is rejected rather than mixing volumes. Files with known DICOM extensions but missing Part 10 headers are reported as unsupported.
3. Choose where to save the generated `.nii.gz`. The original DICOM files are not changed. Tap **Import NIfTI Files** to add the saved result to the library.
4. Alternatively, tap **Or Select Individual DICOM Files** to use the original multi-select picker.
5. The converter sorts slices by their DICOM patient-space positions, converts LPS ImagePositionPatient / ImageOrientationPatient / PixelSpacing to a NIfTI RAS+ sform, and scales pixels to float32. All conversion runs locally.

**NIfTI → derived DICOM (.zip)**

1. Find a NIfTI entry in **Library**.
2. Tap **Export DERIVED DICOM (ZIP)**, choose a destination, and save.
3. The ZIP contains one DICOM Part 10 Secondary Capture `.dcm` file per NIfTI k-slice, using distinct new DICOM UIDs, a derivation description, LPS spatial attributes, and 16-bit monochrome pixels.
4. The converter normalizes intensities to uint16 and supplies RescaleSlope/Intercept. It does not reconstruct scanner settings, original identifiers, patient identity, or acquired MR image objects.

**Explicit compatibility restrictions:**
- DICOM reader: Part 10, Explicit VR Little Endian or Implicit VR Little Endian, single-frame MONOCHROME2, integer 8/16-bit grayscale, consistent positions/orientation/spacing and one StudyInstanceUID + SeriesInstanceUID. JPEG/JPEG-LS/JPEG 2000, RLE, enhanced multiframe, mosaic/Siemens CSA, RGB, undefined-length sequences, non-Part-10 and unusual signed packing are rejected with an explanation.
- NIfTI→DICOM: sform/qform must provide trustworthy orientation; in-plane sheared affines are rejected. Output is **anonymized in header fields but not guaranteed de-identified from voxel imagery**, e.g. a face may still be recognizable.
- Only 3D spatial volumes are output, and the NIfTI loader uses only the first frame of 4D data. Source clinical metadata cannot be losslessly recovered. Exported DICOM is **DERIVED SECONDARY CAPTURE**, not a scanner-original MR series.
- DICOM conversion has not been validated against clinical scanners or diagnostic viewers. For educational use only; **not for diagnostic use**.

All processing happens on the Android device via Storage Access Framework. The app requests no network or broad filesystem permission. If saving fails, it attempts to remove the incomplete output document and warns if cleanup is unavailable.


## Phase 7 — Orientation, interoperability, memory and emulator QA

Phase 7 makes **validation reproducible**, not clinical-grade. `.github/workflows/android.yml` runs two independent jobs:

- **Desktop JVM + external reference libraries**: Kotlin unit tests verify RAS radiological left/right orientation, oblique/mirrored transforms, millimetre normalization, black background under negative windows, and MPR rendering with a 192×192×96 synthetic volume. The CI script uses Python 3.11, NumPy, **nibabel 5.3.2** and **pydicom 3.0.1** to independently generate synthetic input NIfTI/DICOM files. Kotlin decodes/converts these; Python independently verifies output affine matrices, voxel data, DICOM patient-coordinate geometry, UIDs, metadata and rescale. The synthetic fixtures and outputs are downloadable as a CI artifact.
- **Android emulator smoke**: on Android 15 (API 35) x86_64, install and launch the app with Compose UI tests. Assert that Home opens the Series browser, Library exposes conversion and management controls, and capture a fresh-install home screenshot and XML test results.

**Large scans / memory behavior:** Header-only NIfTI catalog imports have no arbitrary voxel-count limit. For viewing, the 48-million-voxel and approximately `Runtime.maxMemory()/12` threshold applies **only** to in-heap decoding; larger `.nii` / `.nii.gz` volumes are streamed into a temporary uncompressed voxel file in the app cache and accessed in 64 MiB memory-mapped windows. This needs free **disk space** approximately equal to the uncompressed first-frame voxel bytes plus 32 MiB, and takes additional time to extract gzip data. The temporary file is removed when switching volumes; stale files are cleaned on later launches. Large-volume thumbnails currently fall back to placeholders instead of repeatedly decompressing scans in the background. DICOM folder conversion validates geometry on a metadata-only pass, then streams one source slice at a time into the output; it does not retain the whole series in RAM. This supports large single-frame, native little-endian grayscale DICOM series, subject to supported codecs, free disk/SAF limits and per-slice memory. Derived DICOM export from large NIfTI may still be limited by available device memory; automated tests are not substitutes for real-device stress tests.

**Run the independent reference test locally:**

```bash
python -m pip install numpy==2.2.6 nibabel==5.3.2 pydicom==3.0.1
python scripts/qa/reference_interop.py generate app/build/reference-qa
NEUROVIEW_REFERENCE_FIXTURE_DIR="$(pwd)/app/build/reference-qa" ./gradlew :app:testDebugUnitTest
python scripts/qa/reference_interop.py verify app/build/reference-qa
```

The Python QA outputs contain only synthetic scans, never patient data. JVM timing output is NOT a device benchmark.

**Remaining manual device-validation gate** (not covered by this PR): install on a real Android device; use de-identified test scans; compare screenshots of all three anatomical planes against a trusted desktop viewer; verify left/right overlays using asymmetric anatomical landmarks and a known affine; navigate multiple imported series; try SAF document providers; compare exported files against dcm2niix/GDCM; stress-test a large real volume on low-RAM devices; test orientation controls and background/foreground behavior. Record model, API version, scan provenance, timings, peak heap, screenshot checks and any mismatches. **Do not use for diagnosis.**

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
- Phase 6 — DICOM to NIfTI and clearly labeled derived-DICOM export. **Implemented with restricted transfer syntax support.**
- Phase 7 — Synthetic reference interoperability, emulator UI and performance/orientation hardening. **Implemented as automated QA; physical-device and clinical validation still outstanding.**

Sensitive images, generated datasets, and patient exports are excluded by `.gitignore`. Use only public or synthetic test data in the source tree.

See [architecture notes](docs/ARCHITECTURE.md).

## Smooth scrolling (v0.9.0)

The MRI viewer now uses a reusable voxel-space sampling grid per anatomical plane,
avoiding repeated spatial-affine computations for every rendered slice.
A conflated renderer processes fast drag/fling/slider updates at a sustainable
rate without queuing hundreds of stale frames. During interactive scrubbing it
displays a 160-pixel preview, then refines to the existing 384-pixel quality
after interaction stops; the original image data, voxel values and anatomical
orientation are not changed. Nearby slices are cached as scalar intensity
buffers, so changing brightness/window width does not repeat the expensive
3-D interpolation. Drag gestures use a fixed starting slice to prevent lost
steps and support gentle kinetic scroll inertia. The plane selector now slides
and fades into view.

All voxel resampling remains off the main UI thread. On-device scrolling
performance depends on scan dimensions and storage speed (especially for
disk-backed imports); do not interpret this as a guaranteed frame rate.

### v0.9.2 slice display fix

The asynchronous MPR render coordinator observes the actual Compose slice index
(instead of capturing a recomposition-local integer on first launch), so
vertical dragging, inertial scrolling, and the slice slider update both the
slice counter and the displayed MRI pixels. This does not alter stored MRI data.


## NeuroView 1.0 UX refresh

The Home screen is now task-first and scrollable on smaller Android phones.
It puts **View MRI series**, **Import NIfTI volumes**, and
**Library & conversion** above the 3-D brain illustration. A local workspace
summary shows volumes and collections; a resume card opens the last MRI viewed
(the ID is stored locally in application preferences; if no scan has been
opened, it instead shows the most recently imported volume).

The MRI viewer now keeps axial, coronal, and sagittal views in always-visible
plane tabs. The scan gets more screen space, and the slice controller groups
a large scrubbing slider, the exact slice number and millimetre position, and
Previous/Next step actions. Vertical drag, kinetic scrolling, left-swipe plane
panel, and high-resolution refinement are retained. Image brightness and
contrast controls are in a dismissible bottom sheet with a Reset action,
avoiding cramped controls or shrinking the scan area during ordinary viewing.

The UI upgrade retains read-only MRI source data, existing in-app library
entries, DICOM/NIfTI conversion flows, and Android package identity; updating
an existing debug-signed installation should not require clearing user data.
The brain preview is a stylized illustration, not a patient-derived model.
Use the viewer for exploration only, not diagnosis.

### v1.0.1 — original home layout and brightness correction

Restores the pre-v1.0 brain-first Home screen: the interactive rotating brain
occupies the main card above **View MRI series** and **Library & conversion**.
The NIfTI import action is still available through either destination.
Viewer layout, plane tabs, recent scan bookkeeping, caching, and conversions
are not changed.

The **Brightness** slider in Image adjustments now moves from **Darker** on the
left to **Brighter** on the right, by reversing the displayed slider position
relative to the image window-center. The underlying NIfTI voxels are unchanged.

### v1.0.2 � Gradient visual refresh

The brain-first Home screen now uses a static midnight-to-teal-to-indigo background with soft radial glows. The 3-D brain card and secondary navigation card have gradient surfaces and accent outlines; the primary View MRI action has a mint/cyan/soft-indigo gradient. The Series browser and Library use matching gradients on their scrollable backgrounds and import buttons, and the viewer's slice-control panel has a subtle gradient. These effects are restricted to application chrome: the actual MRI image remains untinted grayscale against a neutral black background. No additional frame-tick animation is used for these gradients. The previous v1.0.1 brightness-slider direction, app identity and persisted library data are preserved.

### v1.1.0 — brain-led landing screen

Only the welcome screen and its 3D preview renderer were redesigned. The Home
screen uses a neutral navy backdrop with restrained blue lighting, an outlined
NeuroView logomark, direct **Open viewer** and **Import & Library** navigation,
and three small factual capability labels. It has no 3D-model card, redundant
status labels or manual rotation controls in the main layout. A top-right
Settings control opens a functional bottom sheet with auto-rotation, glow, and
orientation-reset options.

The previous procedural OpenGL brain has been replaced with an **offline,
bundled, Brainchop demo-derived 43,872-vertex / 89,528-triangle NVBM mesh**.
The brain model is not extracted from the user's medical scans. It uses
native GLES2 float positions/normals and unsigned-short indices with no
external graphics runtime. The mesh resource has strict format validation
with a non-network, procedural fallback in case of corrupted packaging.

The Series, Library, MRI viewer, format-conversion pipelines, storage and
saved user scans are unchanged. The floating 3D view has a transparent
OpenGL surface over the Home gradient. No diagnostic claims are made.

### v1.1.1 — landing-page lighting refinement

Removes the concentric circular guides behind the rotating brain. The Home
screen now has a restrained, static arrangement of feathered navy-blue
ambient light, off-axis cool illumination and a diffuse low-level reflection
under the brain, based loosely on the provided visual reference. The brain's
OpenGL shader uses a softer overhead key and a restrained cool fill.
The user-facing landing settings can still toggle background glow; turning
it off removes the decorative lighting without hiding the brain.

Only Home screen presentation and brain fragment lighting change. Brain mesh,
MRI rendering, imports, Library and Series UI, storage and user data are
unchanged.
