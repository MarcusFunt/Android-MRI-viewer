# Lesion Measurement Workspace Design

Date: 2026-11-05

## Goal

Add a mobile-friendly workspace for measuring a lesion in a loaded 3D MRI volume. Use Cornerstone3D for the measurement and segmentation tools. The user chose true 3D segmentation for volume, so the reported volume must come from the traced voxel mask rather than an ellipsoid estimate.

The workspace should provide physical dimensions in millimetres, 2D ROI area in mm², and segmented volume in mm³ and mL. It must preserve the current offline workflow and keep the existing native Compose viewer available.

## Current application context

NeuroView is a native Kotlin/Jetpack Compose app. It reads supported single-file NIfTI-1 volumes and displays reconstructed planes using a native renderer. Supported DICOM series are converted to NIfTI by the app, but DICOM is not currently a direct viewer input. Files in the library remain at their original Storage Access Framework URI; the app does not copy source scans by default. Large volumes can use a disk-backed voxel store.

Cornerstone3D is a browser-based imaging stack, so using its tools inside this app requires a locally bundled web workspace in Android WebView. This is a contained integration, not a replacement of the existing viewer.

## User flow

1. The user opens a supported NIfTI scan in the existing viewer and chooses **Measure lesion**.
2. NeuroView checks that the scan is readable and that the device WebView can initialize the required WebGL renderer. It opens a full-screen measurement workspace with axial, coronal, and sagittal views.
3. The user places 2D measurements and traces one or more lesion segments with touch-friendly brush/freehand tools on the orthogonal planes. The app saves work locally as it changes.
4. A measurement panel shows named measurements, selected-segment dimensions and stats, including physical volume derived from the mask voxel count and the NIfTI affine.
5. The user returns to the existing viewer or Library. Reopening the same scan restores its measurements when the saved geometry matches.

For a supported DICOM series, the first version uses the app's existing DICOM-to-NIfTI conversion and then measures the resulting NIfTI after it is imported into the Library. Opening an unconverted DICOM series directly in the measurement workspace is outside this first version.

## Measurement behavior

- **Length and dimensions:** Cornerstone3D length and bidirectional tools report distances in mm. The bidirectional tool reports two perpendicular in-plane axes. The 3D segment summary also reports its physical bounding-box extent along the scan's voxel axes, transformed using the source affine.
- **Area:** Ellipse/rectangle ROI and planar freehand ROIs report the traced cross-sectional area in mm² on the active plane. Area is a 2D slice measurement, not a computed 3D surface area.
- **Volume:** A manual 3D labelmap segment is traced/painted on the volume. Report volume as the number of included voxels multiplied by the absolute determinant of the affine's 3×3 spatial matrix; display mm³ and mL. This accounts for anisotropic and oblique voxel geometry. Do not estimate volume from three axes.
- **Multiple lesions:** Allow multiple named segments, each with its own color, visibility, measurements, and volume. Provide undo/redo and clear-segment actions.
- **Units and geometry:** Validate spacing, orientation, dimensions, and spatial units before enabling measurements. Convert all output to mm, mm², and mm³. If the source geometry is missing, invalid, or cannot be reconciled with the Cornerstone volume, block drawing and calibrated results and explain why; do not save an uncalibrated mask.

## Technical design

### Embedded Cornerstone workspace

Bundle the Cornerstone3D runtime, tools, and required loader files inside the Android app. Load them from an AndroidX WebKit WebViewAssetLoader origin such as https://appassets.androidplatform.net/; do not fetch app code, fonts, or scan data from a remote server. Configure WebView with JavaScript and WebGL only for this trusted local workspace. Keep navigation restricted to the app asset origin and disable general file/content access.

Use a narrow Android-to-WebView adapter that serves only the currently selected scan through an opaque, short-lived local token. The web side receives dimensions, affine, and the local resource token, never an arbitrary URI or filesystem path. Revoke the token and dispose Cornerstone image/volume caches when the workspace closes. Do not put scan data in a JavaScript bridge message or base64 string.

The initial implementation should use the existing cataloged NIfTI source as input and create the Cornerstone volume in the web workspace. Since Cornerstone may hold a decoded copy and GPU texture, unload native render resources before loading it and release all web volume resources on exit. Set a tested maximum volume size/device-memory policy and show a clear unsupported/low-memory state instead of crashing. Keep the native viewer as the fallback for devices where WebGL initialization fails.

### Geometry adapter

Use the official Cornerstone NIfTI volume loader if it can represent NeuroView's supported NIfTI-1 data correctly. Add an adapter at the boundary for axis ordering and affine conventions. Do not trust nominal spacing alone: compare dimensions, voxel-to-world positions at corners and center, and physical voxel volume against NeuroView's SpatialAffine. Correct known spacing-order differences in one adapter and reject mismatches.

Do not silently resample a scan for measurement in the first version. A resampled grid would require transforming saved annotations and can change voxel counts. Preserve the source voxel grid and affine so the mask maps one-to-one to the source volume.

### Local persistence

Store small annotation metadata (scan ID, source geometry fingerprint, names, colors, timestamps, and independent 2D measurements) in a versioned app-private format. 2D measurements are not children of a 3D segment and remain if a segment is deleted. Store the 3D labelmap as a compressed app-private binary mask separate from preferences; write through a temporary file and atomically replace on save. Keep no patient name or other imported identifiers in measurement records. Provide in-app delete/export-free management for each segment; medical-image SEG/SR export is not part of this release.

On first measurement use, compute and persist a streaming SHA-256 of the source NIfTI file, alongside its scan ID, dimensions, and affine. On reopen, recheck the content hash before applying saved masks so that a changed file with the same dimensions and affine cannot inherit old annotations. Hashing runs off the UI thread with progress. If the source cannot be re-read or its hash/geometry differs, do not apply the old mask; show it as unavailable and allow the user to remove it. Deleting a scan from the library should also remove its measurements and labelmaps.

### DICOM path

Keep the app's current supported DICOM restrictions. The existing DICOM converter outputs a NIfTI volume with an RAS+ affine; measurement starts only after that converted file is added to the Library and opened. Validate that DICOM-derived anisotropic spacing and oblique orientations survive conversion and Cornerstone loading unchanged.

## Scope boundaries

- Manual tracing only; no automatic or AI lesion detection/segmentation.
- No 3D surface area, radiology report, DICOM SEG, DICOM SR, cloud sync, or external server.
- No new 4D timepoint support; measurements operate on the first scalar 3D frame supported by the current loader.
- No rewrite of the existing native MPR viewer.
- This is a measurement feature, not a claim of clinical validation or diagnostic suitability.

## Verification plan for implementation

- Unit tests for affine-based voxel volume, physical distances, and ROI area with isotropic, anisotropic, oblique, and sheared synthetic geometries.
- Compare Cornerstone and native geometry at representative voxel coordinates and verify source values/orientation for synthetic NIfTI files, including a DICOM-converted fixture.
- Verify a known binary mask's voxel count, computed mm³/mL, bounding dimensions, save/reopen round trip, geometry mismatch handling, and interrupted/atomic writes.
- Android checks for offline asset loading, origin restriction, one-scan resource bridge, WebGL failure fallback, touch drawing, undo/redo, background/foreground, and cleanup after exit.
- Physical-device checks across at least one low-memory and one current Android device. Exercise large and anisotropic scans and inspect memory usage; emulator success alone is insufficient.
- Verify that no scan data or measurements are sent over the network and that the complete workspace works in airplane mode.

## External constraints to verify during implementation

Cornerstone3D is an evolving web library, and Android WebView GPU behavior varies by device. Pin compatible package versions and validate them on physical Android hardware before treating the integration as supported. The current Cornerstone NIfTI loader has a reported open issue around in-plane spacing order, and Android WebGL shader failures have also been reported; both require a tested fix/workaround or a supported-device gate.

References:

- [Cornerstone3D tools overview](https://www.cornerstonejs.org/docs/getting-started/overview/)
- [Cornerstone3D segmentation tutorial](https://www.cornerstonejs.org/docs/tutorials/basic-segmentation-tools/)
- [Cornerstone3D segment stats calculator](https://www.cornerstonejs.org/docs/api/tools/namespaces/utilities/namespaces/segmentation/classes/segmentstatscalculator/)
- [Android WebView local content](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)
- [Cornerstone3D NIfTI spacing issue](https://github.com/cornerstonejs/cornerstone3D/issues/2914)
- [Cornerstone3D Android shader issue](https://github.com/cornerstonejs/cornerstone3D/issues/2892)
