# NeuroView 1.4 — interface overhaul and QA checklist

This pass follows the full-screen and micro-interaction review of NeuroView
1.3. It updates the Android UI without replacing or mutating MRI voxel data,
DICOM conversions or BrainSuite's upstream processing algorithms.

## Implemented

- **Navigation** — Home now has *Browse scans*, direct *Import MRI* and a
  conditional *Continue viewing* shortcut for the last opened series. The
  normal browsing path goes to a unified MRI Library rather than another
  largely duplicated Series Browser.
- **Library** — The main browser has preview thumbnails, loading/unavailable
  states, flat and collection-grouped modes, recent-import/name sorting,
  3D/4D filters, search clearing, empty states and compact overflow menus.
  DICOM conversion controls are collapsed until needed. Group operations
  clearly distinguish app references from original files.
- **Viewer** — Zoom/Pan mode supports pinch gestures and a double-tap/reset
  action. Normal vertical dragging still scrolls slices. Probe mode lets
  users inspect a voxel's intensity, places a temporary on-image crosshair
  and carries its RAS+ reference point across axial/coronal/sagittal planes.
  Unknown-orientation files don't display affirmative R/L markers. Focus
  mode removes the top bar but retains an exit action. Single-slice controls
  are disabled when they cannot change the image.
- **Display** — Separate *Adjust* and *Process* sheets, window presets,
  explicit reset behavior and per-scan saved display adjustments.
- **BrainSuite** — Validated HTTPS entry, optional access-token visibility,
  per-scan/per-destination consent, readable processing states, auto-refresh
  while visible, copyable job IDs and a small recent-job list. Only job IDs,
  stages, timestamps and HTTPS server addresses are stored for recall; no
  scan filenames, pixels or credentials are persisted in job history.
- **Home/settings** — More accessible supporting typography, less duplicated
  decorative chrome, consistent accent colors, stronger white-on-blue button
  contrast, scrollable small-screen layout, persistent landing appearance
  preferences and a safe MRI-preview-cache cleanup action.
- **Testing** — Added pure Kotlin tests covering the new RAS-space probe
  coordinates and cross-plane projection; updated emulator navigation tests
  for the new screen hierarchy.

## Remaining feature boundaries

A complete scientific imaging workstation needs separate engineering and
validation beyond this UI pass:

- **Multi-volume comparison** needs a bounded two-volume memory model and
  validated synchronized spatial registration.
- **True 4D timepoint playback** needs changes to the NIfTI loader; the viewer
  still shows frame one and honestly labels that limitation.
- **Simultaneous tri-planar display** requires another rendering coordinator
  and image-size/performance benchmarks on phone and tablet hardware.
- **BrainSuite surface rendering and direct ZIP derivative ingestion** need
  validated DFS/NIfTI derivative parsers plus source/provenance association.
- **Production processing queue** still requires host-side resumable job state,
  cancellation, persistent statuses and verified cross-device restoration.
- **Clinical tools or diagnostic claims** require image-orientation accuracy
  checks on real anonymized datasets, human-factor validation and appropriate
  regulatory work. This remains a research/education viewer.

Do not present these as implemented, and do not infer missing metadata.

## Release/device QA

- [ ] Android compile, JVM tests, lint and synthetic reference checks pass.
- [ ] Fresh emulator screenshot reviewed for clipping, text contrast and
      actual button hierarchy.
- [ ] Open an existing app install; existing catalog and scan IDs survive.
- [ ] Import several NIfTI files and navigate through Library/Collections.
- [ ] Check sorting, 3D/4D filtering, large filenames and empty states.
- [ ] Test portrait/landscape and system font sizes, especially 1.5× and 2×.
- [ ] Verify orientation labels and probe crosshair against asymmetric,
      affine-grounded test fixtures, including oblique scans.
- [ ] Verify zoom/pan, scrolling, Focus and sheet dismissal do not conflict.
- [ ] Exercise optional BrainSuite with disposable non-identifying test data,
      including offline host, invalid token, timeouts and result export.
- [ ] Check TalkBack labels, touch target sizes and reduced motion.

## Rollback

This is an additive UI pass; no catalog schema or original scan data changes.
If a UI regression appears, roll back the app version without deleting
application data or imported files. The optional recent BrainSuite job-reference
preference can be ignored by earlier releases.
