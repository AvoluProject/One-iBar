# One iBar

**One iBar v0.2.1-alpha04** is an experimental libxposed module for Samsung One UI 8+ that replaces the visible status-indicator cluster inside the existing SystemUI view hierarchy with an iOS-27-inspired group.

It is a real Android/Xposed project, not a screenshot or launcher mock-up. It does **not** create an overlay window, use Accessibility overlays, or patch the SystemUI APK on disk. The module hooks SystemUI lifecycle/update methods and inserts a native `LinearLayout` into a discovered status-icon container, hides the matching stock signal/Wi-Fi/battery views, and draws its own glyphs using Android Canvas.

> **Alpha status:** CI is configured to compile the debug APK on every push. A successful CI build confirms compilation and packaging only; the hook mapping and appearance still need testing on each Samsung firmware/device. Samsung's internal class names and view IDs are not a stable public API. When the module cannot identify a safe container and at least two stock indicator types, it intentionally skips injection rather than modifying an unrelated view.

## Design target

The reference target is the supplied **iOS 27** icon style, not iOS 26:

- Four rounded cellular bars with active/inactive levels.
- Three Wi-Fi arcs and a filled lower glyph.
- Filled horizontal capsule battery with grey remainder and a right-side cap; low battery turns red and charging uses a green fill with a compact bolt.
- Optional network technology label before the cellular bars: `AUTO`, `4G`, `LTE`, `5G`, `5G+`, or `OFF`.
- Base proportions are expressed in dp and remain constant across display sizes: 24 dp cellular, 23 dp Wi-Fi, 30 dp battery, 18 dp glyph height, and 6 dp default gap. The module uses Samsung's own status-bar safe-area/container so a center punch-hole, wide notch, or dual cutout does not cause the icon geometry itself to be stretched. Small calibration offsets are available for unusually short status bars or firmware-specific insets.
- The native Samsung clock text and actual time content are preserved. Optional clock styling adjusts only the clock TextView's font metrics/size; it does not spoof or replace the time.

The group order is: optional network label → cellular signal → Wi-Fi → battery. Battery remains the last/right-most glyph inside this cluster.

## Current controls

| Setting | Behavior |
|---|---|
| Enable One iBar | Enables/disables the in-memory status bar group |
| Signal / Wi-Fi / battery visibility | Individually show or hide each glyph |
| Network label | Optional `AUTO`, `4G`, `LTE`, `5G`, `5G+`, `OFF` text |
| Battery percentage | Optionally draws percentage inside the capsule |
| Clock styling | Keeps the native clock value and applies a restrained iOS-like medium font/size |
| Icon scale | 85–115% around the fixed dp geometry |
| Icon gap | 2–10 dp |
| Vertical offset | -3 to +3 dp |
| Horizontal offset | -6 to +6 dp for the whole cluster |
| Battery position | -4 to +4 dp adjustment, while battery remains last/right-most |
| Theme | Auto / light / dark glyph tint |
| Animations | Glyph reveal and status bar / Quick Settings state transitions, 80–360 ms |
| Presets | Natural / Compact / Bold geometry presets |
| Profile JSON | Copy and import the complete configuration as JSON |
| Battery offset | Independent battery position adjustment, applied as layout spacing |

## How events update

- **Battery:** `ACTION_BATTERY_CHANGED` receiver invalidates/rebinds the glyph state.
- **Wi-Fi/connectivity:** Wi-Fi network-state and connectivity broadcasts trigger a status refresh.
- **Cellular signal and radio type:** on Android 12+ the module also listens through `TelephonyCallback` for signal-strength and display-technology changes; earlier versions rely on the guarded SystemUI hooks/broadcasts. AUTO mode recognizes NR Advanced as `5G+` when the platform reports it.
- **Settings:** a `ContentObserver` watches `Settings.Secure/oneibar_config_v2`. The companion app writes the JSON config there, so changes apply live without reboot once the one-time permission is granted.
- **Quick Settings / keyguard:** guarded hooks attempt a modest alpha/translation transition when the corresponding SystemUI lifecycle methods exist on that firmware. This animates the custom indicator group, not Samsung's entire QS panel.
- **Firmware changes:** known Samsung/AOSP class and method candidates are scanned by reflection. Missing candidates are logged and skipped.

The Quick Settings behavior is a status-indicator transition, **not a complete replacement of Samsung's Quick Settings panel**. A pixel-perfect full QS redesign would need a separate firmware-mapped view implementation.

## Requirements

- Rooted Samsung device.
- Vector or another framework that supports **libxposed API 102**; module scope is only `com.android.systemui`.
- Android 12+ (project minSdk 31); target development focus is One UI 8 / Android 16. One UI 8.5 / 9 compatibility must be mapped and tested per firmware.
- Grant the companion app `WRITE_SECURE_SETTINGS` once so it can send live settings to SystemUI.

The official modern libxposed API uses `META-INF/xposed/java_init.list`, `scope.list`, and `module.prop`; API 102 adds hot reload support when the framework implements it. See links below.

## Install and activate

1. Build/download the APK from the GitHub Actions artifact.
2. Install `One-iBar-debug.apk` for initial testing, or `One-iBar-release.apk` when stable signing secrets are configured.
3. In Vector Manager, enable **One iBar** and keep its scope on **System UI (`com.android.systemui`) only**.
4. Open One iBar and copy the permission command shown in the app.
5. Run this once in a rooted Termux session:

   ```sh
   su -c 'pm grant com.avolu.oneibar android.permission.WRITE_SECURE_SETTINGS'
   ```

6. Return to One iBar and tap **Terapkan Ulang**. Change settings and observe whether the config is applied live.
7. Reboot once after first enabling the Xposed module if the framework does not inject into the already-running SystemUI process.

## Build in GitHub Actions

1. Create a GitHub repository and upload the **contents** of this ZIP to its root.
2. Commit to `main`.
3. Open **Actions → Build One iBar**.
4. Download the `One-iBar-debug-*` artifact for the debug APK. When all four stable-signing secrets are configured, a separate `One-iBar-release-*` artifact is also uploaded.

The workflow uses JDK 17, Gradle 8.14.3, Android platform 36, and Build Tools 36.0.0. It runs source/XML checks before compiling and uploading the APK artifacts.

## Updating the APK without uninstalling

Android supports an in-place package update when all of the following remain the same: **applicationId**, signing certificate, and a higher `versionCode`.

The project keeps `applicationId = com.avolu.oneibar`; GitHub Actions sets `versionCode` to `2100 + run number`, so this alpha line has a higher version code than the earlier 0.2.0 alpha builds. For repeatable updates, use the same private signing key for every release build:

1. Generate a release keystore once and keep a secure backup. Do not commit it to the repository.
2. Add these repository Actions secrets:
   - `SIGNING_KEYSTORE_BASE64` – Base64-encoded `.jks` contents.
   - `SIGNING_KEYSTORE_PASSWORD` – keystore password.
   - `SIGNING_KEY_ALIAS` – alias in the keystore.
   - `SIGNING_KEY_PASSWORD` – key password.
3. Future Actions runs will create `One-iBar-release.apk` signed with the same key. Install it over the previous build using Android's normal update flow; do not uninstall the app.

To create a keystore on Android/Termux with OpenJDK installed:

```sh
keytool -genkeypair -v -keystore "$HOME/oneibar-release.jks" -alias oneibar -keyalg RSA -keysize 3072 -validity 10000
base64 -w0 "$HOME/oneibar-release.jks"
```

Copy the Base64 output to `SIGNING_KEYSTORE_BASE64`, then add the other three secrets in GitHub **Settings → Secrets and variables → Actions**. Keep the keystore and passwords private.

**Important:** debug APKs signed by different CI runner-generated debug keys may not update over one another. Use the stable signed release artifact for repeatable in-place updates. If an older alpha was installed with a different certificate, one initial uninstall/reinstall may be required to change signing keys. After that, keep the same release key forever.

With API-102 hot reload enabled in `module.prop`, a compatible framework may hot-reload the module generation after app update. If Vector/framework or firmware does not support it, the newly installed APK still updates the companion app in place, but SystemUI may need a framework reload/reboot before new hook code is active. Live visual settings remain separate and do not require that code reload.

## Recovery

If SystemUI crashes, icons disappear, or layout is wrong:

1. Disable One iBar in Vector Manager.
2. Reboot so SystemUI starts without the module.
3. Open One iBar and switch **Aktifkan One iBar** off if it can still write settings.

The module does not edit or replace `SystemUI.apk` on disk. Its status bar edits are in-memory view changes and disappear when SystemUI restarts without the module.

## Firmware compatibility and diagnosis

Do not assume that a successful CI build proves a runtime match. Before calling a firmware supported, verify:

- `StatusBarIconControllerImpl` class/method names.
- Actual resource entry names and parent container for mobile signal, Wi-Fi, battery and network type label.
- Whether the battery is an ImageView or a custom `BatteryMeterView`.
- The exact QS/keyguard lifecycle methods called when expanding, collapsing, unlocking and locking.
- Icon ordering and cutout-safe area on portrait, landscape, dual-SIM and large-font configurations.

The current alpha only recognizes a small set of candidate ID/class names. A firmware with different names will be skipped safely or may need a dedicated profile. Do not enable broad hooks on all `View`/`ImageView` methods.

## Source references

- Modern Xposed module guide: https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API
- libxposed API 102 integration: https://github.com/libxposed/api
- libxposed API 102 Javadoc / hot reload: https://libxposed.github.io/api/
- libxposed API 102 module metadata and `autoHotReload`: https://libxposed.github.io/api/io/github/libxposed/api/package-summary.html
- StatusBack example for Samsung One UI 8 and libxposed API 102: https://github.com/fragtion/StatusBack

## License

MIT. libxposed API is a compile-only dependency and is not bundled into this APK.
