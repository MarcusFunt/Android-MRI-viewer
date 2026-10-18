# NeuroView

**NeuroView** is an experimental, offline-first Android MRI viewer for importing and browsing local NIfTI scans, inspecting reconstructed anatomical slices, organizing a scan library, and performing limited DICOM/NIfTI conversion. An optional BrainSuite companion runs selected anatomical processing on a computer you control.

**Research and educational software only — not for medical diagnosis, clinical interpretation, treatment, or patient care.** The viewer, converters, and processing workflow have not been clinically validated. Confirm orientation and results against trusted independent tools before any scientific use.

## Project status and Android builds

- **Current source version:** `1.4.0` (from `app/build.gradle.kts`). Active development includes the redesigned Library and Viewer, voxel probing, and optional BrainSuite processing.
- **Latest published GitHub release:** [v0.7.0 debug prerelease](https://github.com/MarcusFunt/Android-MRI-viewer/releases/tag/v0.7.0). **This APK is older than the current source and does not include all 1.4 features.** [Download the older v0.7.0 APK](https://github.com/MarcusFunt/Android-MRI-viewer/releases/download/v0.7.0/NeuroView-v0.7.0-debug.apk).
- **Build the current source**, or open the [Android CI workflow](https://github.com/MarcusFunt/Android-MRI-viewer/actions/workflows/android.yml) and download the `neuroview-debug` artifact from a successful run for the desired commit. GitHub Actions artifacts are ZIP archives containing `app-debug.apk`; a successful build is not a clinical validation.
- These are **debug-signed development builds**, not production releases. Only install APKs from sources you trust. A CI artifact and the older tagged APK may not have matching signing keys; do not uninstall an existing installation without considering the loss of its app-private library catalog.

## Features

- **MRI library:** import multiple `.nii`/`.nii.gz` files through Android's document picker; browse previews, collections, searchable local labels, sorting, and 3D/4D filters; resume the last opened scan.
- **Three-plane viewer:** switch between axial, coronal, and sagittal views; navigate slices with swipes, a slider, and step controls. World-space resampling uses the scan's NIfTI spatial affine when available.
- **Display and inspection:** brightness/contrast (window/level), window presets, per-scan display preferences, zoom and pan, focus mode, and a voxel-intensity probe with a temporary reference crosshair shared between planes.
- **Local conversions:** import a supported uncompressed single-frame DICOM MR series from a folder or selected files and save a `.nii.gz`; export a NIfTI as a ZIP of newly generated **derived Secondary Capture DICOM**, not reconstructed original scanner DICOM.
- **Optional computer-based BrainSuite:** send an explicitly approved T1-weighted scan to your own HTTPS-connected BrainSuite bridge; run cortical surface extraction (CSE) or CSE plus atlas registration (SVReg), then save the result ZIP. No BrainSuite engine runs on Android.

The rotating Home brain is a **bundled, decorative model**, not an anatomical analysis of the selected MRI.

## Quick start on Android

1. Install a development APK or build and run the app using Android Studio. Minimum supported Android version: **8.0 (API 26)**.
2. From **Home**, tap **Import MRI** and select one or more NIfTI files. You can also open **Browse scans** to reach the Library and import there.
3. In the **MRI Library**, select a scan to open the viewer. Collections organize imported references locally; they do **not** establish patient or clinical study identity.
4. In the viewer, choose **Axial**, **Coronal**, or **Sagittal**. Swipe vertically or use the slice controls to move through the volume. Use **Zoom**, **Probe**, **Adjust**, and **Focus** as needed.
5. For format conversion, open **Library → Conversion tools**. For optional remote processing, open a T1w scan and use **Viewer → Process**; see the [BrainSuite setup guide](docs/BRAINSUITE_INTEGRATION.md).

**Removing a scan from the Library does not delete its original file.** Imported documents remain with the Android document provider; NeuroView stores their read-only references and local metadata.

## Supported data and limitations

- The built-in viewer accepts supported scalar **single-file NIfTI-1 `.nii` and `.nii.gz`** volumes. It uses the first frame of 4D files; **4D timepoint navigation/playback is not implemented**, despite 4D files appearing in the Library filter.
- Reconstructed planes respect NIfTI `sform`/`qform` when present; absent or unreliable orientation metadata requires caution. In particular, do not treat screen laterality as clinically verified.
- NIfTI-2, separate `.hdr/.img` pairs, unsupported datatypes, and many specialized/multidimensional formats are not supported.
- The DICOM importer accepts a constrained subset of **uncompressed DICOM Part 10, single-frame, monochrome 8/16-bit** series with consistent geometry. Compressed transfer syntaxes, Enhanced multiframe, color and scanner-specific variants are not supported.
- NIfTI-to-DICOM outputs are newly derived Secondary Capture objects. Original acquisition metadata, identity, and scanner settings cannot be recovered. Header identifier replacement is **not certified de-identification**: identifiable anatomy can remain in image pixels.
- Larger NIfTI volumes may stream through private disk-backed caches instead of RAM; available storage, memory, device performance, and data-provider behavior still impose practical limits.
- **Not yet implemented:** simultaneous tri-planar display, two-scan comparison/registration, true 4D navigation, direct BrainSuite DFS mesh viewing or ZIP-derivative ingestion, and diagnostic validation.

## Build from source

Requirements: recent **Android Studio**, **JDK 17**, Android SDK **Platform 37**, and a device/emulator running Android 8.0+. Use the included Gradle wrapper (AGP 9.4.0, Gradle 9.6.0, Kotlin 2.2.21).

```bash
git clone https://github.com/MarcusFunt/Android-MRI-viewer.git
cd Android-MRI-viewer
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

On Windows use `gradlew.bat` instead of `./gradlew`. Open the repository root in Android Studio, sync Gradle, and run the `app` configuration. The locally built debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Run lint, independent synthetic interoperability QA, the mocked BrainSuite bridge tests, and emulator tests using [Testing and validation](docs/TESTING.md).

## Privacy and safety

Ordinary scan import, viewing, indexing, thumbnails, and conversion run **locally**. The app uses the Android Storage Access Framework rather than broad filesystem access. The local catalog is stored in **unencrypted app-private preferences**, and derived previews use app-private cache; backups are disabled. Do not assume that app-private storage provides cryptographic protection.

The app declares internet permission for the **optional BrainSuite workflow only**. Upload requires a configured HTTPS host and explicit consent for the selected scan; scan images can contain identifying anatomy. The bridge is a single-user development service and should not be exposed publicly. See [BrainSuite privacy and setup](docs/BRAINSUITE_INTEGRATION.md).

## Documentation

- [Architecture and data flows](docs/ARCHITECTURE.md) — current code structure, local storage, rendering, conversions, and network boundaries.
- [Testing and validation](docs/TESTING.md) — build checks, synthetic reference tests, device QA, and outstanding validation.
- [BrainSuite integration](docs/BRAINSUITE_INTEGRATION.md) — companion setup, security, supported stages, and results.
- [Development log](docs/DEVELOPMENT_LOG.md) — historical phases, version changes, and implementation notes formerly embedded in the README.
- [v1.4 UX rework notes](docs/UX_REWORK_1_4.md) — historical design-change details and release checklist.

**Contributors:** keep this README focused on how the *current* app works. Add historical implementations and completed milestones to the development log, update architecture/usage documentation with relevant changes, and avoid presenting planned features as already available. Do not commit private patient data, sensitive scans, export files, access tokens, or processing job folders.
