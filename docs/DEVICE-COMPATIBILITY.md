# Device compatibility tracking

One iBar deliberately does not claim universal support based on the Android version alone. Samsung changes SystemUI class names, resource IDs, view parentage and status-bar policies between devices and firmware builds.

| Platform target | Current status | What is required before calling it supported |
|---|---|---|
| One UI 8 / Android 16 | Candidate hook profile included; not physically verified in this build session | Confirm the status icon container, mobile signal/Wi-Fi/battery IDs, QS callback and clock ID on a test device |
| One UI 8.5 / Android 16 | Not verified | Repeat the full mapping on each firmware family; compare A-series and S-series layouts |
| One UI 9 / future Android | Not verified / future target | Re-check libxposed framework support, Android SDK level, method names and view structure after release |
| Other One UI devices with centered cutouts / dual cutouts | Geometry is designed to inherit Samsung's safe status-bar container; not verified | Confirm the detected container excludes the camera cutout and has sufficient horizontal room |

## Required firmware profile data

Record these values for each firmware before enabling a profile by default:

1. Full build fingerprint and One UI build number.
2. Status icon container resource ID and parent class.
3. Actual view class and resource ID for cellular signal, Wi-Fi, battery and network technology text.
4. Method names called on cellular/Wi-Fi/battery state changes.
5. Method names called on Quick Settings expansion/collapse and keyguard fade/unlock.
6. Screenshot measurements in dp for status bar height, icon bounds, cluster spacing and cutout-safe edges.

If the signal and Wi-Fi/battery source views cannot both be identified, the current code skips insertion. Do not broaden matching to all ImageViews or Views just to force a visual result; that could hide unrelated status-bar icons or cause SystemUI instability.
