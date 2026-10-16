# NeuroView — Android MRI Viewer

A local-first Android application for browsing MRI series, examining volumes, and eventually importing and converting DICOM/NIfTI data.

## Phase 1: Android foundation

- Kotlin, Jetpack Compose and Material 3 dark theme
- Single-activity architecture with type-safe Navigation 3 routes and a saved back stack
- Four connected UI screens: Home, Series, Viewer, Library
- Viewer navigation drawer, and a right-hand placeholder plane selector
- Unit tests for navigation semantics; GitHub Actions Android build/test workflow
- No network or storage permissions requested

**Important:** This phase intentionally uses *demo labels and empty viewer placeholders*. It does not read, render, import, convert, or store real medical images. Do not use this app for diagnosis.

## Open in Android Studio

1. Clone this repository and open its root directory in a recent Android Studio.
2. Use **JDK 17** and install **Android SDK Platform 36** in the SDK Manager.
3. Allow Gradle sync, then run the `app` configuration on an Android device or emulator (minimum Android 8.0 / API 26).
4. For command-line verification: `./gradlew :app:assembleDebug :app:testDebugUnitTest` (Windows: `gradlew.bat`).

Versions: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose BOM 2026.09.00, Navigation 3 1.2.0.

## Planned progression

- Phase 2 — Home screen 3D brain visualization.
- Phase 3 — Real NIfTI loading and multiplanar viewing.
- Phase 4 — Scan and series selection from imported datasets.
- Phase 5 — Local library with SAF import and deletion.
- Phase 6 — DICOM to NIfTI and clearly labeled derived-DICOM export.
- Phase 7 — Device validation, performance, and clinical-orientation sanity checks.

Sensitive images, generated datasets, and patient exports are excluded by `.gitignore`. Use only public or synthetic test data in the source tree.

See [architecture notes](docs/ARCHITECTURE.md).
