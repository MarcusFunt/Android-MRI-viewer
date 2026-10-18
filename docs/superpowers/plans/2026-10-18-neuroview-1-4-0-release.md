# NeuroView 1.4.0 CI, Release, and Review Artifacts

## Scope

Complete the requested 1.4.0 release work from the current `main` checkout. Include the already-requested 3 dp default probe-line thickness while preserving edge-to-edge line geometry. Fix the evidenced CI failures, confirm the exact source commit is green on GitHub Actions, publish a version-scoped 1.4.0 release using the successful CI APK, then deliver a detailed human-readable PDF and an AI-agent handoff Markdown file. Both review artifacts must state that their findings apply to NeuroView 1.4.0 only.

## Plan

1. **Pin down failures and release constraints.** Read CI job logs and uploaded emulator XML, trace the parser assertion to its fixture or implementation, inspect navigation smoke failures, verify version metadata and old release workflow behavior. Avoid replacing the user's emulator installation. Confirmed causes: the parser test expects an unsmoothed vertex although the loader softens each connected position by 2.5%; the empty-library UI hides the Collections filter, blocking the instrumented collection workflow.
2. **Make minimal fixes.** Update the parser expectation to the documented smoothing result and keep collection management reachable when the library has no scans; retain edge-to-edge probe overlay behavior and the 3 dp default. Do not overwrite the existing v0.7.0 release.
3. **Validate locally.** Run applicable existing Python bridge/reference checks and Gradle build, unit-test, lint, and instrumented-test compilation tasks. Run emulator tests only in CI or an isolated disposable AVD, not on the user's configured AVD.
4. **Publish and verify CI.** Commit and push the release candidate; inspect all jobs for the resulting GitHub Actions run. Fix any remaining reproducible failures and repeat until all required jobs are green. Use the APK artifact from that green run, record its SHA-256, and bind the v1.4.0 tag/release to the exact green commit.
5. **Create detailed deliverables.** Produce a PDF with readable layout and screenshots plus an agent-oriented Markdown dossier. Include verdict, checkout/origin comparison, exact release provenance, feature walkthrough, detailed findings, scope and evidence limitations. Make the review's validity boundary explicit on the cover and throughout.
6. **Verify deliverables.** Render and visually inspect PDF pages, check Markdown links and paths, verify release/tag/run URLs and APK hash, and summarize outcomes and limitations.

## Completion criteria

- Required GitHub Actions jobs pass on the exact release commit.
- `v1.4.0` exists as a GitHub release whose source tag resolves to that green commit and whose attached APK comes from its successful CI artifact.
- The user receives both requested files, with screenshots in the PDF and the prominent scope statement: **valid for NeuroView 1.4.0 only**.
