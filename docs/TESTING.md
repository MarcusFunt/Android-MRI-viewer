# Testing and validation

These are **developer and QA procedures for the current source**, not medical-device validation. The v0.7.0 tagged APK is older than the v1.4.0 source. See [README](../README.md) for installation and [Architecture](ARCHITECTURE.md) for module boundaries.

## Basic Android checks

Use **JDK 17**, Android SDK Platform **37**, and the included Gradle wrapper from the repository root:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat`. A build only proves that source compiles for the configured toolchain; it is not evidence of physical-device rendering, DICOM scanner compatibility, or diagnostic fitness.

## Independent synthetic NIfTI/DICOM interoperability

The independent test path creates synthetic input using **Python 3.11**, NumPy, nibabel and pydicom. Kotlin then imports/exports data, and Python independently verifies output geometry, intensities, identifiers and spatial metadata. No patient images are needed.

```bash
python -m pip install numpy==2.2.6 nibabel==5.3.2 pydicom==3.0.1
python scripts/qa/reference_interop.py generate app/build/reference-qa
NEUROVIEW_REFERENCE_FIXTURE_DIR="$(pwd)/app/build/reference-qa" ./gradlew :app:testDebugUnitTest
python scripts/qa/reference_interop.py verify app/build/reference-qa
```

The environment-variable assignment above is **POSIX-shell syntax**. On Windows set `NEUROVIEW_REFERENCE_FIXTURE_DIR` to the **absolute** generated `app/build/reference-qa` folder in your shell before running `gradlew.bat :app:testDebugUnitTest`. Inspect all failures: a Kotlin-only round trip can reproduce its own geometric mistakes; the independent Python check is intentional.

## BrainSuite companion tests

```bash
python -m unittest discover -s tools/brainsuite_bridge -p "test_*.py"
```

These tests cover the HTTP bridge, upload validation, stage arguments and output packaging with a **mocked Docker run**. They do not run BrainSuite itself or demonstrate the correctness of CSE/SVReg. A real end-to-end test needs a trusted private HTTPS host and a safe synthetic or appropriately de-identified T1w test volume; follow [BrainSuite integration](BRAINSUITE_INTEGRATION.md).

## Emulator and CI

The [Android CI workflow](https://github.com/MarcusFunt/Android-MRI-viewer/actions/workflows/android.yml) runs build/unit/lint/reference checks and the mocked bridge tests, then an **API 35 / Android 15 x86_64 emulator** navigation smoke test. It uploads debug APK, synthetic fixtures and QA screenshot/log/XML artifacts as applicable. Inspect the status for the **specific commit** under review; older passing workflows do not establish that current code passed.

To run instrumented UI tests locally with a booted device/emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Do not claim that the emulator tests visually verify every screen or rendering correctness. In particular, review the captured screenshot and OpenGL diagnostic log before relying on the Home 3D preview.

## Manual device and release gates

The following remain **verification tasks, not claims of completed validation**:

- [ ] Install a build corresponding to the intended commit on physical Android hardware; record model, OS/API, memory, source SHA, and APK provenance.
- [ ] Launch Home and check the 3D brain is actually visible, rendered correctly, and stops using resources off-screen; verify settings and reduced-motion behavior.
- [ ] Test small screens, tablet/landscape, system font scaling (including 1.5×/2×), TalkBack, focus order, touch-target sizes, contrast, and non-clipped dialogs/sheets.
- [ ] Import supported `.nii`, `.nii.gz`, oblique and known-asymmetric affine-grounded scans; compare all three planes, laterality and probe coordinates to an independent reference viewer.
- [ ] Verify 4D import displays **only its first frame**, and that the UI does not suggest time navigation exists.
- [ ] Exercise Library collections, search/sort/filter, filename aliases, removal, SAF permissions, document-provider revocation and process recreation. Verify source files are never deleted when removing local references.
- [ ] Test vertical slice scrolling, fling, pan/zoom, Probe, Focus, window presets, and per-scan adjustments in combination.
- [ ] Compare supported real-world DICOM conversions to established independent tooling (e.g. dcm2niix / nibabel / pydicom / GDCM), including geometry, left/right mapping, rescale and failure handling for unsupported syntaxes.
- [ ] Stress-test large gzipped volumes, disk-backed rendering, low-storage behavior, low-memory devices, background/foreground switching, and cache cleanup. Record timing, storage use, heap pressure and error behavior.
- [ ] Check BrainSuite private HTTPS connectivity, expired/invalid credentials, consent reset, offline/timeout, host restart, failed processing, ZIP export, host retention and cleanup using safe test data.
- [ ] Verify upgrade and rollback behavior **without clearing app-private data**. Test APK signing-key compatibility separately; do not recommend uninstalling to fix an update failure without warning about local catalog loss.

## Evidence to record

For a reproducible QA report, record the Git commit and APK SHA-256; Android model/API; sanitized dataset source and NIfTI/DICOM variants; tested operations; external viewer comparison and screenshots; measured memory/storage/time; exact errors; and pass/fail status. Never commit identifying patient scans, confidential logs, or access credentials.

**Release readiness is separate from diagnostic validation.** Even passing all technical tests does not make NeuroView a certified medical device.
