# Installing A9 Service with e-ink recoloring

[Русская версия](INSTALL.ru.md)

This build adds per-app e-ink recoloring to the A9 Accessibility Service:

- text and icons become black or white depending on a brightness threshold;
- backgrounds are reduced to black, white and light grey;
- the panel's contrast, black threshold and white threshold can be set separately for each app.

Everything is set from the pop-up that a double press of the E-Ink button opens.

## Requirements

| What | Tested with |
|---|---|
| Hisense A9 (HLTE556N) with an unlocked bootloader and vbmeta verification disabled | — |
| A Treble GSI patched by [A9PatchingService](https://github.com/damianmqr/A9PatchingService) (the service lives in `/system/priv-app/a9service.apk`) | LineageOS 21 GSI (Android 14, userdebug) |
| Android 10 or newer | Android 14 |
| Magisk with Zygisk enabled | Magisk 30.6 |
| A hooking framework that supports the legacy Xposed API: [Vector](https://github.com/JingMatrix/Vector) (formerly JingMatrix's LSPosed) or LSPosed | Vector v2.2 |
| A computer with `adb` ([platform-tools](https://developer.android.com/tools/releases/platform-tools)) and USB debugging enabled on the phone | — |
| Root access for `adb shell` (Magisk → Superuser → Shell) | — |

## Why installation has two stages

The original service is signed with the ROM author's key. Android refuses a plain `adb install` of an APK with a different signature over it. So the first time, the APK is placed into `/system/priv-app` through a Magisk module. During boot Android accepts the new signature of the system app and keeps its data: per-app refresh modes, button mappings and other settings. Later versions install with a regular `adb install -r`.

## Installation

1. **Enable Zygisk.** In the Magisk app go to Settings → Zygisk, then reboot.

2. **Install Vector.** Download `Vector-*-Release.zip` from its [releases page](https://github.com/JingMatrix/Vector/releases). In Magisk open Modules → Install from storage, pick the zip and reboot. After the reboot a Vector notification appears; it opens the manager.

3. **Download the release files:**
   - `A9Service-<version>.apk` from this repository's release;
   - the [`app/external_scripts/magisk_override`](app/external_scripts/magisk_override) folder (`install.sh`, `uninstall.sh` and `module.prop`). Cloning the whole repository also works.

4. **Connect the phone over USB.** Check that `adb devices` lists it and that `adb shell su -c id` prints `uid=0(root)`.

5. **If you ever installed a service update with `adb install`** (the service shows up as an updated system app), remove that update first:
   ```sh
   adb shell pm uninstall-system-updates com.lmqr.ha9_comp_service
   ```

6. **Install the Magisk module with the new APK:**
   ```sh
   sh app/external_scripts/magisk_override/install.sh A9Service-<version>.apk
   ```
   Magisk now lists the module "A9 Service override". Reboot the phone.

7. **Check that the replacement worked:**
   ```sh
   adb shell dumpsys package com.lmqr.ha9_comp_service | grep versionName
   ```
   It should print the release version, for example `3.8.1-colors.1`. Make sure the "A9 Accessibility Service" accessibility service is still enabled in Settings → Accessibility.

8. **Enable the module in Vector.** In the Vector manager open Modules → "A9 Accessibility Service", enable it and tick the apps to recolor. Then force-stop those apps (Settings → Apps → Force stop) or reboot.

## Usage

- **Double press the E-Ink button** to open the pop-up. Its **Colors** block applies to the app currently on screen:
  - **ON / OFF** turns recoloring on or off for this app;
  - **Reset** drops this app's settings back to the global values;
  - **Text/icons** sets the threshold: anything darker turns black, anything lighter turns white;
  - **Contrast**, **Black** and **White** are the panel's contrast, black threshold and white threshold. They switch along with the app.
- **Global defaults** live in the service settings (gear icon in the pop-up) under **Colors**, together with the fill cutoffs and the light grey level.
- **Changes apply immediately** without restarting the app. The exception is apps built with obfuscated Jetpack Compose: part of their screen redraws only on the next update, for example after scrolling.

## Updating

Release builds are signed with the same key, so new versions install over the current one:
```sh
adb install -r A9Service-<new version>.apk
```

## Rolling back

```sh
sh app/external_scripts/magisk_override/uninstall.sh
```
After a reboot the original service is back and its settings are kept. You can disable the module in Vector or remove Vector itself in Magisk.

If the phone does not boot after installing modules, boot into Magisk safe mode; it disables all modules. If `adb` still works, `adb shell su -c 'magisk --remove-modules'` does the same.

## Limitations

- **Not recolored:**
  - web page content (Chrome, WebView), because a separate process draws it;
  - Flutter apps in SurfaceView mode, video and games;
  - untinted raster images and gradients: photos, avatars, coloured PNG buttons.
- **The system "Color inversion" and "Color correction" do nothing on the A9.** The display hardware applies the SurfaceFlinger colour matrix after the e-ink controller has already taken the frame.
- **Keep banking and other protected apps out of the Vector scope.** They may detect the hooks and refuse to run.
- **Hooks only run in apps inside the scope.** Other apps are not slowed down.

## Building from source

1. You need JDK 17 and the Android SDK (platform 34).
2. Release builds are signed with the key named in `local.properties`:
   ```properties
   a9service.keystore=/path/to/keystore.jks
   a9service.keystore.password=...
   a9service.key.alias=...
   ```
3. Build:
   ```sh
   ./gradlew testDebugUnitTest assembleRelease
   ```
   The APK ends up in `app/build/outputs/apk/release/`.

An APK signed with a different key will not install over a release build with `adb install -r`. Repeat the Magisk module installation (steps 5–7) for it.
