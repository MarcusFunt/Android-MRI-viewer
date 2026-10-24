# NeuroView architecture (current source: v1.5.0 candidate)

This document describes the Android source prepared for `main` as inspected on 2026-10-24. The v1.5.0 candidate is not yet a signed distribution build. Historical phase-by-phase implementation notes live in the [development log](DEVELOPMENT_LOG.md).

## Application structure

NeuroView is a Kotlin Android application with a single `MainActivity`, Jetpack Compose / Material 3, Navigation 3 saveable destinations, and local state. The Android package/namespace is `com.marcusfunt.neuroview`. Compile SDK 37, target SDK 36, minimum SDK 26; the dependency versions live in `gradle/libs.versions.toml`.

The main navigation path is **Home → Library → Viewer**. Home includes direct import and conditional resume actions. The older **Series** screen remains reachable for compatibility, but the Library is the main browsing and management experience. Viewer provides a navigation drawer, always-available plane switching, and separate display-adjustment and optional processing sheets.

Key implementation locations:

- `ui/NeuroViewApp.kt` — app navigation, SAF import launcher, catalog state, scan selection, and volume lifetime.
- `ui/screens/HomeScreen.kt` — rotating decorative brain, entry points, recent scan, appearance settings.
- `ui/screens/LibraryScreen.kt` / `SeriesScreen.kt` — collections, scan cards, search, sorting/filtering, entry management and conversions.
- `ui/screens/ViewerScreen.kt` — reconstructed MRI image, plane selection, slice gestures, zoom/pan, probe, Focus, and Adjust/Process sheets.
- `ui/screens/BrainSuitePanel.kt` — optional consent-driven remote processing client UI.

## Import and catalog data flow

1. Android's **Storage Access Framework** picker returns document URIs. `ScanCatalog` inspects NIfTI headers with `NiftiSeriesInspector`, checks supported files, and takes persistable read permission.
2. `ScanCatalog` stores collection IDs, per-scan metadata and optional local display aliases in **app-private SharedPreferences**. A collection is an import grouping or user-defined organization tool, **not a validated DICOM study**.
3. Files remain in their original provider locations; NeuroView does not own or copy them by default. Missing/revoked URIs may stop opening. User-triggered removal edits the local catalog only and releases unneeded permissions.
4. Small preview images are derived lazily and cached in `cacheDir/scan_thumbnails`. They may be evicted or explicitly cleared; the original files are not modified. Large volumes may use placeholders instead.

`CatalogEdits` contains pure management operations. Serialized catalog writes update persistent metadata before cleaning removed thumbnail and URI references. The last-opened scan ID is stored as a local navigation preference. Window/level preferences are kept per selected document; neither changes source voxel data.

## NIfTI decoding, geometry and rendering

- `mri/NiftiReader.kt` handles supported NIfTI-1 scalar `.nii` and `.nii.gz` input, little/big endian headers and data, intensity scaling, and only the **first timepoint** of a 4D file. 4D playback does not exist.
- `mri/SpatialAffine.kt` maps voxel indices to world-space **RAS+ millimetres**, preferring available NIfTI sform/qform information. Missing orientation information is warned about; this is not proof of correct patient orientation.
- `mri/MprRenderer.kt`, `MprRenderSession.kt` and `MprProbe.kt` reconstruct axial, coronal, and sagittal slices with trilinear interpolation, manage fast scrubbing and refined preview rendering, and inspect voxel values/reference positions across planes.
- One active volume is loaded for viewing at a time. `MriMemoryBudget` limits **in-heap** decoding; larger supported volumes can use temporary uncompressed files in `cacheDir/nifti_mapped` and memory-mapped access via `MappedNiftiStore`. This is not an unlimited memory or storage guarantee.
- Display operations (window/level, presets, zoom, crosshair, focus) do not rewrite the MRI. Decorative Home gradients and the bundled `landing_brain.nvbm` never tint the diagnostic-style grayscale image.

**Current scientific boundary:** the planes are viewed separately; simultaneous tri-planar panes, registration/two-scan comparison, true time navigation, and direct 3D patient-derived surface viewing are not implemented.

## Format conversion

`ui/ConversionWorkflow.kt` exposes both conversions via Android document picker/export destinations:

- **DICOM → NIfTI:** `DicomFolderScanner` recursively scans a chosen folder or accepts manually selected files; `DicomReader` checks supported uncompressed Part 10 single-frame grayscale images; `DicomSeriesAssembler` verifies consistent Study/Series UIDs, orientation and geometry, orders slices in patient coordinates, and converts DICOM **LPS** to NIfTI **RAS+**. `NiftiSeriesWriter` outputs gzip-compressed 3D float32 NIfTI using streamed slice data.
- **NIfTI → DICOM ZIP:** `DerivedDicomWriter` creates new **Secondary Capture** objects with new UIDs and derived spatial/intensity mappings. These are not restored originals and cannot reconstruct lost DICOM acquisition metadata.
- Conversion writes a **new user-selected destination document**, leaving source input untouched. Failure handling attempts cleanup of incomplete output.

The reader deliberately rejects compressed transfer syntaxes, Enhanced multiframe DICOM, incompatible geometries and other unsupported encodings. Header anonymization cannot remove identifying facial anatomy from voxels.

## Optional BrainSuite boundary

The application declares `INTERNET` for the optional `Viewer → Process` feature. `processing/BrainSuiteClient.kt` requires a caller-configured **HTTPS** host and bearer token; Android only streams the selected T1w file after explicit consent. The access token is held in transient UI state and is not saved to preferences.

`processing/BrainSuiteJobHistory.kt` saves a limited recent-job reference list (ID, endpoint, stage, submission time), but no scan pixels or credentials. The job history is not a durable host-side processing queue.

The separate Python `tools/brainsuite_bridge/server.py` listens on **127.0.0.1** and invokes `bids/brainsuite:v23a` in Docker, serializing jobs and providing a ZIP of derivatives. It must sit behind a **private trusted HTTPS** reverse proxy (for example Tailscale Serve); it is not suitable as an internet-facing multi-user processing service. Results are not automatically imported or rendered as 3D DFS meshes. See [BrainSuite setup, threat boundaries and cleanup](BRAINSUITE_INTEGRATION.md).

## Privacy and safeguards

- Standard viewing, Library operations, and format conversion do **not upload scans**.
- SAF avoids broad file permissions. Local catalog preferences are app-private **but not encrypted**; cached previews and temporary uncompressed voxels can contain sensitive information. Android automatic backup is disabled.
- Never treat a local label, import collection, or derived output as proof of patient/study identity.
- NIfTI orientation and scanner interoperability need independent verification before research interpretations. There is no clinical/diagnostic approval or validation.

## Verification architecture

`app/src/test` covers geometry, parsing, catalog, conversions, memory-backed volumes, and probe mapping. `app/src/androidTest` exercises Compose navigation. `scripts/qa/reference_interop.py` produces and verifies **synthetic**, externally generated NIfTI/DICOM fixtures using NumPy/nibabel/pydicom. `tools/brainsuite_bridge/test_server.py` mocks the Docker execution path. CI builds, lints, tests, and runs an Android 15 emulator smoke test with screenshot artifacts.

Automated results do **not** establish physical-device performance, real-scanner correctness, BrainSuite scientific accuracy, or medical suitability. See [Testing and validation](TESTING.md) for commands and outstanding gates.
