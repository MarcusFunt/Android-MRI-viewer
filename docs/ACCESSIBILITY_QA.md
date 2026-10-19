# NeuroView accessibility and responsive-layout QA

This matrix tracks Android accessibility coverage requested by [issue #38](https://github.com/MarcusFunt/Android-MRI-viewer/issues/38). It is a validation record, not a claim that every device scenario has passed. Use only synthetic or appropriately de-identified scans; do not commit patient data, identifying paths, or diagnostic logs.

## Test matrix

| Scenario | Setup | Expected result | Evidence to record | Status |
|---|---|---|---|---|
| Standard phone | Portrait, default font/display size, empty and populated library | Primary actions remain obvious; no overlapping labels; MRI remains large in Viewer | Home, Library (empty / one / dense), Viewer, Settings screenshots | API 35 emulator navigation/touch instrumentation passed; visual screenshot review pending |
| Large text | Android font scale 1.5× then 2× | Settings and workflow text wraps or scrolls; Close/Done and primary actions remain reachable | Screenshots plus screen-reader traversal notes | Pending |
| Small phone | Approximately 320 dp wide, portrait | Toolbar controls reflow or scroll; no clipped required action; essential targets remain at least 48 dp | Library, Viewer, BrainSuite, conversion screenshots | Pending |
| Tablet | At least 600 dp wide; portrait and landscape | Layout uses available room without spreading controls away from their content; no unnecessary permanent multi-plane viewer | Screenshots and device dimensions | Pending |
| Landscape and cutouts | Rotate phone; test a display cutout or inset emulator profile | Safe drawing insets protect controls; rotation does not reset slice, zoom, display window, probe, or operation state | Before/after Viewer screenshots and state notes | Pending |
| TalkBack | Enable TalkBack; traverse Home, Library, Viewer, Settings, import, conversion, BrainSuite | Every icon has a useful label; slice and adjustment values are announced; hidden step buttons retain slider increment/decrement semantics; state changes are concise | Accessibility tree/XML and traversal notes | Pending |
| Touch targets | Inspect essential icon, slider, Close, Done, and confirmation actions | Essential controls provide at least 48 dp targets, even when the visible glyph is smaller | Layout bounds or Compose semantics bounds | Pending |
| Keyboard and D-pad | Attach keyboard or use emulator D-pad; traverse dense Settings and BrainSuite sections | Focus order follows visual/action order, selected state is visible, and all controls can be activated | Focus-order notes and screenshots | Pending |
| Reduced motion | Test System default with Android animation scales, then Reduced and Off | Decorative transitions follow platform/app preference; slice navigation and functional scrolling remain available; direct manipulation is not delayed | Preference state and short capture clips | Pending |
| Contrast and appearance | Test supported light/dark/system appearance and selected menu state | Text and selection remain legible against NeuroChrome surfaces; no default purple/brown popup surface | Screenshots and contrast audit notes | Pending |
| Long and changing content | Synthetic long scan alias/filename; trigger empty, unsupported, failed, queued, and running states | Full metadata remains available; errors include a useful next action; status announcements are not repetitive | Screenshots, semantics tree, sanitized state log | Pending |

## Repeatable emulator setup

Capture build identity and emulator configuration before testing:

```powershell
Get-FileHash app/build/outputs/apk/debug/app-debug.apk -Algorithm SHA256
adb -s <emulator-serial> shell getprop ro.build.version.sdk
adb -s <emulator-serial> shell wm size
adb -s <emulator-serial> shell wm density
adb -s <emulator-serial> shell settings get system font_scale
```

Use a dedicated QA AVD for install and settings changes. Do not wipe or replace the user's existing emulator profile. Reset font scale, display size, animation scales, and orientation after each scenario. Keep screenshots free of patient identifiers and record the exact APK hash beside each capture.

For each run, record the Git commit, APK hash, Android API, AVD/device model, logical size/density, font scale, accessibility service state, scenario, pass/fail, screenshot filenames, and any defect. Emulator coverage does not establish physical-device usability or TalkBack behavior on every OEM build; record those separately when performed.
