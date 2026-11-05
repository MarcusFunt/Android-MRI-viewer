# NeuroView 1.5.0 Release Plan

> **For agentic workers:** execute the release steps in order and record the resulting commit and artifact provenance.

**Goal:** Commit the complete current working tree on top of the latest upstream `main`, then publish its Android APK as GitHub release `v1.5.0`.

**Architecture:** Preserve the existing working changes in a commit, rebase that commit onto `origin/main`, build the debug APK from the resulting source, compute its SHA-256, and attach both files to a GitHub prerelease tied to that commit.

**Tech Stack:** Git, Gradle Android plugin, Android SDK, GitHub CLI.

**Spec:** User request in this Codex conversation (commit all uncommitted changes, then make the release from that commit).

## Global Constraints

- Keep all current tracked and untracked working-tree changes in the committed source.
- Base the release on the latest fetched `origin/main` commit.
- Use Android version name `1.5.0` and version code `20` from the upstream app configuration.
- Describe the APK as a debug-signed research preview and include its source commit and SHA-256.
- Do not run tests unless the user asks; build the APK as required for the release artifact.

## Review Focus

- Rebase conflicts in files changed by both local work and upstream; preserve the intent of both sides.
- Release tag collision or version mismatch; verify `v1.5.0` is unused and app metadata remains `1.5.0`/`20`.
- Artifact/source mismatch; build only after recording the final commit and tag that exact commit.
- APK integrity; compute and publish a matching SHA-256 checksum.

---

### Task 1: Preserve and integrate the working tree

**Files:** all currently modified and untracked project files.

- [ ] Stage and commit every current working-tree change.
- [ ] Rebase that commit onto the fetched `origin/main` tip and resolve any overlapping edits while retaining both upstream and local behavior.
- [ ] Confirm the final commit contains all requested working-tree changes and the app version is `1.5.0` (version code `20`).

### Task 2: Build the release asset

**Files:** generated `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] Run `./gradlew :app:assembleDebug` from the final commit.
- [ ] Copy the APK to `NeuroView-v1.5.0-debug.apk` and write its SHA-256 file.
- [ ] Record the source commit and verify the APK and checksum are non-empty.

### Task 3: Publish the GitHub release

**Files:** GitHub release `v1.5.0` and its APK/checksum assets.

- [ ] Push the final commit to `main` and create the `v1.5.0` release from that exact commit.
- [ ] Attach the APK and checksum, with research-preview safety notes and build provenance.
- [ ] Read the published release metadata and verify the tag target and both asset names.
