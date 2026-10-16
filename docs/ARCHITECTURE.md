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
