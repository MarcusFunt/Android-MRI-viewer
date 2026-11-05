# NeuroView Pass One GUI/UX Implementation Plan

> **For agentic workers:** Implement task by task and keep the existing backend worktree changes untouched.

**Goal:** Complete the phone-first Pass One experience around a large single-slice MRI view, persistent viewer preferences, and scan-linked notes.

**Architecture:** Keep the current Jetpack Compose navigation and catalog. Add small local note and viewer preference models, make Settings and Notes first-class destinations, persist view position by the existing URI digest, and keep the MRI rendering pipeline unchanged apart from configurable preview resolution.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Navigation 3, SharedPreferences, JUnit, Compose UI tests.

**Spec:** `C:\Users\marcu\OneDrive\Dokumenter\ChatGPT\Android MRI\.codex-remote-attachments\01a14f59-7450-7c22-ba16-46b811362c9e\c974df38-ef86-409a-9e03-4fecb6931a08\1-NeuroView_Pass_One_GUI_UX_Plan.md`

## Global Constraints

- Keep the MRI image the dominant visual surface and do not put it in a decorative card.
- Keep the default viewer to one slice; do not add tri-planar layouts in this pass.
- Notes are stored locally, tied to the catalog scan ID, collection and series labels, image plane, slice position, and orientation-verification state; do not store the source URI or send notes over the network. Legacy notes without orientation provenance remain unverified.
- Responsive scrolling remains on by default; while enabled, preview quality is adjustable from 25% to 75% of the stable render size and returns to full quality after movement.
- Preserve existing NIfTI data, catalog schema, DICOM conversion, and BrainSuite behavior.
- Preserve pre-existing uncommitted changes in `.gitignore`, `docs/BRAINSUITE_INTEGRATION.md`, and `tools/brainsuite_bridge/`.

## Review Focus

- Changing scans while a note is open must not apply the note's slice to another scan.
- Unknown orientation must not be presented as a verified anatomical location.
- Returning through Notes/Settings must keep the active scan, note location, and viewer state attached to the correct navigation entry.
- Disabling responsive scrolling must retain slice movement and use the full render size.
- A note whose scan has been removed must remain readable and clearly marked unavailable.
- Small portrait layouts must retain access to Notes, Settings, and slice controls.

### Task 1: Local note and preview-quality models

**Files:** Create `app/src/main/java/com/marcusfunt/neuroview/notes/ScanNoteStore.kt`, `app/src/main/java/com/marcusfunt/neuroview/ui/ViewerRenderPolicy.kt`, `app/src/test/java/com/marcusfunt/neuroview/notes/ScanNoteCodecTest.kt`, and `app/src/test/java/com/marcusfunt/neuroview/ui/ViewerRenderPolicyTest.kt`.

**Interfaces:** `ScanNote(id, scanId, collectionLabel, seriesLabel, planeName, sliceIndex, sliceCount, createdAtMillis, body)`; `ScanNoteCodec.encode(List<ScanNote>): String`; `ScanNoteCodec.decode(String?): List<ScanNote>`; `ScanNoteStore(Context).load(): List<ScanNote>` and `.save(ScanNote): Unit`; `ViewerRenderPolicy.previewResolution(fullResolution: Int, quality: Float): Int`, clamping quality to `0.25f..0.75f` and result to `96..fullResolution-1`.

- [x] Write `roundTripsFieldsWithUnicodeAndNewlines`, `skipsMalformedRecordsButKeepsValidNotes`, `clampsQualityToSupportedRange`, and `previewResolutionScalesFullRender`.
- [x] Run the focused tests first and observe the missing model/policy behavior.
- [x] Implement the pure codec and preview resolution policy, then the app-private SharedPreferences store.
- [x] Run the focused tests and `.\gradlew.bat :app:testDebugUnitTest`.

### Task 2: Shared Settings and persistent viewer behavior

**Files:** Create `app/src/main/java/com/marcusfunt/neuroview/ui/screens/SettingsScreen.kt`; modify `NeuroViewApp.kt`, `HomeScreen.kt`, `Destination.kt`, `ViewerScreen.kt`; modify `app/src/androidTest/java/com/marcusfunt/neuroview/NeuroViewNavigationSmokeTest.kt`.

**Interfaces:** `SettingsScreen` receives the saved responsive-scroll flag/quality, Home appearance preferences, and update callbacks; `ViewerScreen` receives `responsiveScrollEnabled` and `scrollPreviewQuality`. The parent owns the SharedPreferences values so Home and Viewer use the same settings.

- [x] Add `settingsExposesResponsiveScrollControls` and verify the baseline fails while the Settings destination and controls are absent.
- [x] Add shared settings state in `NeuroViewApp` and save it locally.
- [x] Route Home and the viewer menu to the same Settings destination; preserve Home appearance and cache controls there.
- [x] Use the selected preview quality only while scrubbing and restore stable resolution after the gesture.
- [x] Persist plane and slice by scan; restore them on reopening and clamp saved indices to the current volume.

### Task 3: Scan-linked notes and return-to-location flow

**Files:** Create `app/src/main/java/com/marcusfunt/neuroview/ui/screens/NotesScreen.kt`; modify `Destination.kt`, `NeuroViewApp.kt`, `HomeScreen.kt`, `ViewerScreen.kt`; modify `NeuroViewNavigationSmokeTest.kt`.

**Interfaces:** `Destination.Notes`; `Destination.Viewer(seriesId: String, noteId: String? = null)`; `NotesScreen(notes, scanLabels, onBack, onOpenNote)`; `ViewerScreen` receives the active scan ID/labels, an optional resume note, and `onSaveNote(ScanNote)`.

- [x] Add `homeCanOpenNotesAndRecoverToLibraryWhenEmpty` and verify Notes navigation and its empty state.
- [x] Add the visible Add note action at the current plane and slice, plus save confirmation.
- [x] Store note metadata locally without the document URI; list notes with scan/collection labels and location.
- [x] Open an available scan at a selected note's saved plane and slice; mark notes unavailable if their scan is no longer in the library.
- [x] Open the full note body from its list row, including when the source scan has been removed; keep orientation uncertainty visible.
- [x] Apply a saved note location once and only after its matching volume is loaded.

### Task 4: Visual alignment and full-flow review

**Files:** Modify `ViewerScreen.kt`, `LibraryScanRow.kt`, `SeriesScreen.kt`, `docs/UX_REWORK_1_4.md`.

- [x] Remove the frame around the MRI image and replace heavy scan cards with open rows and quiet separators.
- [x] Keep image adjustment, processing, notes, settings, plane selection, and slice movement discoverable with labels and accessible touch targets.
- [x] Run `.\gradlew.bat :app:testDebugUnitTest`, `.\gradlew.bat :app:assembleDebug`, and `.\gradlew.bat :app:connectedDebugAndroidTest`; inspect emulator screenshots in portrait and landscape.
- [x] Review the implementation in a fresh context, resolve reported edge cases, and rerun all JVM, APK, and emulator checks.
- [x] Record that real small/large imported scans were unavailable, so device performance on those datasets remains unexercised.
