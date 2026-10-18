# NeuroView 1.4.0 performance baseline

Captured 2026-10-18 from source revision `bbd4cef` (`v1.4.0`) before the UX implementation.
These are local engineering observations, not release targets or clinical validation.

## Environment

- Android 15 / API 35, x86_64 emulator using the Pixel 8 AVD profile and Play Store system image.
- Display: 1080 × 2400 at 420 dpi. The emulator reports `sdk_gphone64_x86_64`; this is not a physical Pixel 8.
- Windows 11, Android SDK 37, Gradle 9.6. Verification used Android Studio JBR 25.0.3 because no default `JAVA_HOME` or complete JDK 17 installation was available. The repository testing guide specifies JDK 17.
- Baseline debug APK SHA-256: `48740c83078dddbc4a61278ff5dd1d7690ce476d69fdf0cf563bcad29b2fd9bb`.
- Debug signing certificate SHA-256: `1962b07fac8a5c0562d3833263e8e392dbd4e87beff7f1a88307414c91ef46e2`.

## Build and test baseline

Command:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

- Build, Android test APK, unit tests and lint succeeded in 51 seconds.
- Unit tests: 66 passed, 0 failed across 15 suites.
- Connected Android tests: 6 passed, 0 failed (5 navigation smoke tests and 1 catalog persistence test; 36 seconds).
- Existing warnings: deprecated `LocalClipboardManager`, deprecated Compose test rule, and JDK 25 compatibility warnings from test tooling.

## Home cold-process launch

Five `am start -W` samples were taken after `am force-stop`; no app data was cleared. `TotalTime` was:

| Sample | TotalTime |
|---:|---:|
| 1 | 5,695 ms |
| 2 | 6,013 ms |
| 3 | 4,527 ms |
| 4 | 4,296 ms |
| 5 | 4,409 ms |

Median: **4,527 ms**; range: **4,296–6,013 ms**. This small, emulator-only sample includes emulator scheduling and rendering overhead. It is a cold process launch, not a cold device boot, and is not a p95 result.

The baseline renderer logged a successful first GL draw on 3 of the 5 launches before the next force-stop. The other two launches were stopped before a first-draw log was observed. Because the old app exposed no readiness signal, these observations are incomplete and not suitable for a first-frame percentile.

## Limits

No physical-device startup, jank, slice-scroll or memory measurements were available for this baseline. Repeat the scenarios on the same physical devices and datasets as the candidate before making performance claims. Compare APK SHA-256 and signing identity for the exact build under review.
