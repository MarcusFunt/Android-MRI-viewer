# Release signing and prerelease process

NeuroView prereleases are tag-bound Android APKs. The build must come from the exact tag commit, pass automated verification, use the project's stable signing identity, and publish the APK checksum and provenance. The v1.6 candidate source is `1.6.0-alpha.4` / Android versionCode `25`; its tag is `v1.6.0-alpha.4`.

## Stable signing identity

The Android release key must remain stable across distributed builds. If the signing certificate changes, Android may reject an in-place update; uninstalling first deletes NeuroView's app-private scan catalog and notes. Export important notes before a destructive reinstall or signing-key migration. Demonstrate a data-preserving upgrade only on a disposable device with two builds signed by the same certificate. No distribution keystore is checked into this repository or available in the current checkout.

The repository owner must provision a dedicated upload keystore outside the repository, keep a secure offline backup, and configure these Actions secrets in a protected GitHub environment named `neuroview-signing`:

| Secret | Value |
|---|---|
| `NEUROVIEW_RELEASE_KEYSTORE_BASE64` | Base64 of the upload keystore file |
| `NEUROVIEW_RELEASE_STORE_PASSWORD` | Keystore password |
| `NEUROVIEW_RELEASE_KEY_ALIAS` | Signing key alias |
| `NEUROVIEW_RELEASE_KEY_PASSWORD` | Key password |

Keep signing secrets scoped to `neuroview-signing`. Configure a separate GitHub environment named `neuroview-prerelease` with required reviewers and restrict deployments to the intended release tag; that reviewer approval gates the public release job after a signed candidate artifact exists. Do not put the keystore, passwords, or private key in source control, issue comments, logs, or release assets. Automated verification runs with read-only repository permissions and without signing secrets. It uploads an unsigned candidate; a separate fresh runner downloads that artifact and performs only APK signing and verification. Signing-runner setup and artifact actions are pinned to full commit SHAs. The signing credentials are exposed only to the final signing step, which decodes the key into the runner's temporary directory and removes it when the step ends. Local Gradle release builds consume the same credential names as environment variables and a private keystore path. Without all four Gradle values, the release variant remains unsigned, and the signing job cannot proceed without all Actions secrets.

Set the environment variable `NEUROVIEW_RELEASE_CERT_SHA256` in `neuroview-signing` to the 64-character SHA-256 fingerprint from the first trusted inspection of the certificate. The workflow rejects a signed APK whose fingerprint differs from this pinned value, preventing an accidental key rotation from publishing an un-updatable build. It publishes this public certificate fingerprint and APK SHA-256 only; it never publishes the private key or keystore.

## Candidate workflow

`.github/workflows/publish-v1.6-prerelease.yml` triggers only for the `v1.6.0-alpha.4` candidate tag. Future versions require an intentional version and workflow update. It:

1. Checks out the tagged commit and verifies that the tag, source SHA, Gradle versionName, and versionCode agree.
2. Runs the mocked BrainSuite bridge tests, synthetic NIfTI/DICOM reference checks, Android build/unit tests/lint, and the API 35 Android instrumentation suite with read-only repository permission and no signing secrets. It uploads the resulting unsigned release APK with its checksum.
3. After verification succeeds, a separate fresh runner downloads the unsigned APK, exposes signing credentials only in the signing step, signs it with `apksigner`, checks the certificate fingerprint, calculates the signed APK SHA-256, and records both candidate and signed hashes with the source tag/SHA, workflow run, and Android version.
4. Uploads the signed candidate for up to 14 days as a workflow artifact. A separate reviewer-gated publish job rechecks the checksum and creates a GitHub prerelease with the APK, checksum, and provenance file.

Before creating a candidate tag, run the documented local checks in [Testing and validation](TESTING.md), review outstanding physical-device and reference-viewer gates, provision the protected secrets/environment, and confirm the candidate version code is higher than the preceding distributed Android build. The workflow only creates a prerelease when a tag is pushed; no tag is created or pushed as part of preparing this source change.

The current historical release `v1.6-alpha` is marked as a full GitHub release, not a prerelease. The new workflow correctly marks future alpha tags as prereleases. Editing the existing public release metadata is a separate release-owner action and does not rewrite the old tag or APK.
