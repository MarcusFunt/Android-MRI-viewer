# Execution ledger — Android MRI issues #19–#40

- Starting revision: `0836fda` (`origin/main`, 2026-10-19).
- Main checkout `D:\Android\Android MRI` had an untracked `output/` directory; it was left untouched.
- Created implementation worktree `D:\Android\worktrees\Android-MRI-issues-19-40` on `codex/github-issues-19-40`.
- Baseline: `:app:testDebugUnitTest` on Android Studio JBR 25.0.3 — **111 tests, 0 failures/errors, 20 suites**. Build emitted the existing `LocalClipboardManager` deprecation warning.
- Parallel track worktrees created from `0836fda` for viewer/spatial, Library/Home, and workflow/provider changes. Their changes will be reviewed and integrated before final verification.
- Ruling: Keep both existing emulators untouched during implementation. `emulator-5554` is the configured Pixel 8 AVD; `emulator-5556` already has NeuroView and user data. Create/use a separate QA AVD if device tests are needed — no installed state is at risk; cost if wrong: extra AVD setup time.
- Ruling: The existing `codex/neuroview-smooth-responsive-ux` worktree is outside this task and remains untouched — it is a separate local branch; cost if wrong: changes may need later comparison or integration.
- [x] Baseline and plan recorded.
- [x] Task 1 — Viewer gesture continuity, precision navigation, optional step controls, motion policy, grayscale fidelity, synthetic spatial QA, settings and Focus behavior.
- [x] Task 2 — Library sorting/filtering, alias-led scan rows, pluralized summaries, conversion presentation, and Home polish.
- [x] Task 3 — Progressive BrainSuite workflow, actionable conversion errors, provider fault coverage, cancellation and cleanup state.
- [x] Task 4 — Contextual settings, accessible toolbar hierarchy, QA matrix, and benchmark protocol.
- [x] Integrated all three implementation tracks plus provider-dispatch follow-up into `codex/github-issues-19-40`.
- [x] Full integrated Gradle rerun after plural-resource changes: `assembleDebug`, `assembleDebugAndroidTest`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`, and `lintDebug` — BUILD SUCCESSFUL; 135 JVM tests, 0 failures/errors; lint clean.
- [x] Full connected instrumentation suite targeted manually to disposable `emulator-5558` — 30 tests, 0 failures; all 7 viewer touch tests passed. The initial run caught and fixed lost thousands separators in BrainSuite byte totals; both focused conversion/BrainSuite tests and the full suite passed afterward. `emulator-5554` and `emulator-5556` were not used for install or test runs.
- [x] Captured matched ten-sample v1.4.0 and final candidate API 35 emulator proxy runs after one data reset and warm-up per build; documented p50/p95/ranges, APK hashes, source revisions, and the candidate's later/slower GL-draw proxy. No performance improvement is claimed.
- [ ] Long-duration provider mode and physical-device/accessibility/reference-viewer acceptance; documented as external release gates.
