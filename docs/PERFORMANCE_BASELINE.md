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

## Issue #27 comparison protocol

Use the exact v1.4.0 APK/source and candidate APK on the same physical devices, Android builds, display settings, provider, and safe datasets. The emulator figures above remain a separate emulator-only baseline; they cannot stand in for phone responsiveness.

For each build, record the source commit, APK SHA-256, signing-certificate SHA-256, device model/API, RAM, refresh rate, Android build, data source/provider, and sanitized dataset dimensions/voxel type. Capture at least ten cold-process Home launches, including time to first usable Home interaction and the `NeuroViewBrain` first-GL-draw event. Report p50/p95/p99 and sample count rather than inferring a percentile from a few runs.

| Scenario | Measurement | v1.4.0 | Candidate | Device / dataset / notes |
|---|---|---:|---:|---|
| Cold Home launch | First usable interaction, p50/p95/p99 | Pending same-device capture | Pending same-device capture | Record model, API, sample count |
| Brain preview | First GL draw, p50/p95; offscreen frame/draw activity | Pending same-device capture | Pending same-device capture | Include inactive Home interval |
| Library | Browse, search, thumbnail latency, missed frames | Pending same-device capture | Pending same-device capture | Record catalog size and provider |
| Import / open | NIfTI import stages, first displayed slice, cancellation latency | Pending same-device capture | Pending same-device capture | Record dimensions, gzip status, source |
| Viewer interaction | 256-slice drag/flick, slider, plane switch, Focus; touch-to-committed-frame p50/p95/p99 and missed frames | Pending same-device capture | Pending same-device capture | Capture gesture trace without scan identifiers |
| Settled rendering | Preview-to-still refinement time and useful image detail | Pending same-device comparison | Pending same-device comparison | Compare the same sharp synthetic and non-identifying anatomical volume |
| Resource use | Max heap, temp disk, thermal behavior, fallback events | Pending same-device capture | Pending same-device capture | Record start/end state and available storage |

Use Android tracing/JankStats or an equivalent repeatable method for frame timing where available. Record the exact test steps and keep raw logs sanitized. Do not claim a performance improvement without paired results from the same device and dataset. As of this revision, no paired physical-device candidate results have been captured; the comparison remains an open release gate.

The repository's `scripts/benchmark_home_startup.ps1` captures process-start `am start -W` time and the logged first GL draw for ten cold-process launches, plus device configuration and APK hash. Run it only on a dedicated QA device with the exact APK already installed:

```powershell
.\scripts\benchmark_home_startup.ps1 -Serial '<dedicated QA device serial>' -Samples 10
```

It writes CSV under `app/build/reports/performance/`, including source commit, APK and signing-certificate hashes, device configuration, and a bounded wait for a launch-matched first-GL-draw log. It force-stops/starts the app without clearing data. The two launch values are proxies: `TotalTime` is not first usable interaction, and the script does not measure touch latency, dropped frames, memory, thermals, or a physical phone. Use tracing and the full protocol above for those measures.

### Initial v1.4.0 API 35 emulator proxy run (2026-10-19)

The exact `v1.4.0` commit (`bbd4cef65953d14d31c24bbcb72c5952ccf9bde7`) was installed on a newly created Pixel 8 profile / Google Play API 35 x86_64 emulator (`sdk_gphone64_x86_64`, 1080 × 2400, 420 dpi, 60 Hz). Ten launches used `am force-stop` followed by `am start -W`; app data was retained. Nearest-rank p95 is shown, so with ten samples it equals the maximum. This was an initial unpaired run, before the integrated test suite warmed the emulator.

| Process-start proxy | Samples | p50 | p95 | Range |
|---|---:|---:|---:|---:|
| `am start -W` `TotalTime` | 10 | 7,162 ms | 8,223 ms | 5,560–8,223 ms |
| First logged GL draw from launch | 10 | 8,406 ms | 10,169 ms | 5,683–10,169 ms |

The APK SHA-256 was `48740c83078dddbc4a61278ff5dd1d7690ce476d69fdf0cf563bcad29b2fd9bb`; the debug signing-certificate SHA-256 was `1962b07fac8a5c0562d3833263e8e392dbd4e87beff7f1a88307414c91ef46e2`. The raw sanitized samples are in [`docs/performance/2026-10-19-v1.4.0-api35-emulator.csv`](performance/2026-10-19-v1.4.0-api35-emulator.csv). A matched v1.4.0/candidate emulator run appears below. These emulator proxies do not satisfy the issue's real-phone, first-usable-interaction, scroll-jank, memory, touch-latency, or thermal checks.

### Matched API 35 emulator proxy run (2026-10-19)

To reduce first-run and instrumentation-warmup differences, both builds were installed in turn on the same disposable API 35 Pixel 8 profile emulator. App data was cleared and one uncounted warm-up launch was completed before each ten-sample capture. No imported studies were present; the bundled Home brain preview was the only image workload, and no external document provider was used. Measurements used `am force-stop` / `am start -W` and the first logged GL draw. With ten samples, nearest-rank p95 equals the maximum. The device reports `sdk_gphone64_x86_64`, 1080 × 2400, 420 dpi, and 60 Hz; it is not a physical Pixel 8.

| Proxy | Build/source | Samples | p50 | p95 | Range |
|---|---|---:|---:|---:|---:|
| `am start -W` `TotalTime` | v1.4.0 / `bbd4cef` | 10 | 2,564.5 ms | 2,775 ms | 2,331–2,775 ms |
| `am start -W` `TotalTime` | Candidate / `04ba76e` | 10 | 2,300 ms | 3,729 ms | 2,082–3,729 ms |
| First logged GL draw | v1.4.0 / `bbd4cef` | 10 | 2,616 ms | 2,882 ms | 2,382–2,882 ms |
| First logged GL draw | Candidate / `04ba76e` | 10 | 3,023.5 ms | 4,808 ms | 2,663–4,808 ms |

In this emulator sample, candidate `TotalTime` has a lower median but a higher p95, while its first GL draw is later at both p50 and p95. These are process-start/rendering proxies; they do not establish a user-perceived speedup or explain the draw delay, and no performance improvement is claimed. The raw samples are in [`paired v1.4.0 CSV`](performance/2026-10-19-paired-v1.4.0-api35-emulator.csv) and [`candidate CSV`](performance/2026-10-19-candidate-api35-emulator.csv).

Baseline APK SHA-256: `48740c83078dddbc4a61278ff5dd1d7690ce476d69fdf0cf563bcad29b2fd9bb`. Candidate APK SHA-256: `3b0c10e54437ede8cfd1feca7037161f256eb7f0c9d96ac2c56015f7886483fd`. Both debug APKs use certificate SHA-256 `1962b07fac8a5c0562d3833263e8e392dbd4e87beff7f1a88307414c91ef46e2`. The candidate source commit is `04ba76eea77fa2c4a1415909a2fb1b32579cbe56`.
