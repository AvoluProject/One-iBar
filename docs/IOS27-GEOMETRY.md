# One iBar visual geometry

The project targets the supplied iOS 27 reference: filled cellular bars, three Wi-Fi arcs, and a filled capsule battery with a short terminal cap. It intentionally does not draw the iOS 26 outlined battery shell.

## Base dimensions

| Element | Base width | Drawing height | Default spacing |
|---|---:|---:|---:|
| Cellular bars | 24 dp | 18 dp | 6 dp after |
| Wi-Fi | 23 dp | 18 dp | 6 dp after |
| Battery capsule | 34 dp | 18 dp | 0 dp after |
| Network label (optional) | content-sized | 18 dp | 2.1 dp before signal |

These are authored as dp, not percentages of screen width. Display density therefore changes physical pixels while preserving logical composition. The status-bar parent supplied by Samsung owns the camera-cutout safe area; One iBar does not stretch the glyphs to match the notch.

## Per-device calibration

The control panel exposes a small scale range and limited X/Y offsets. Battery has its own small X offset while remaining the right-most element. These are for small OEM inset/height differences, not for redesigning the proportions on every phone.

A firmware profile should only be accepted after screenshots and view-tree inspection establish the icon container. If the candidate container cannot be distinguished safely, the module restores stock visibility and skips insertion.

## Known design boundaries

- The app keeps Samsung's clock text/time source; it only adjusts selected clock TextView styling.
- Notification/status glyphs outside the selected signal/Wi-Fi/battery cluster remain Samsung-owned.
- The animation hooks target the custom indicator group. They do not replace the full Quick Settings panel or reproduce every iOS spring curve.
- The supplied raster reference is a visual guide; exact pixel matching requires a screenshot from each firmware with measured dp bounds.
