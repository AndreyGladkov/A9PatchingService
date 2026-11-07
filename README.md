Automatically patch system.img for use with Hisense A9, adding eink features support
For advanced users, check xda for pre-patched images.

To run the script on linux and patch your own treble based system.img you simply have to:

- Download a treble-droid based system.img you want to use
- Download and extract a9_system_patcher.zip from https://github.com/damianmqr/a9_accessibility_service/releases/latest
- Go in terminal to the folder containing unzipped files
- Run `sudo bash patch_system_img.sh /path/to/system.img`
- system_patched.img will be saved in the same directory, ready to be flashed

Flashing the resulting file is done the same as any other system.img
**(make sure your bootloader is unlocked! and you have disabled vbmeta verification by reflashing with `fastboot flash vbmeta --disable-verity --disable-verification vbmeta.img`)**

- reboot to fastbootd using `adb reboot fastboot` (or any other method)
- run `fastboot flash system system_patched.img`
- after that's done, run `fastboot -w`
- run `fastboot reboot` and give it a few minutes

## Android 16 GSIs

A ready e-ink LineageOS 23.2 image built from source, with a Magisk module for flash-free scrolling: [docs/eink-gsi.ru.md](docs/eink-gsi.ru.md) (Russian).

Tested with LineageOS 23.2 (Android 16 QPR2) by MisterZtr on the stock Android 11 vendor.

Building the image:

- Use an **ext4** build of the GSI. EROFS builds need kernel 5.4+, the A9 runs 4.19.
- The patcher needs apktool **2.x** (`apktool b -c` was removed in 3.x), plus `apksigner`, `zipalign` and `setfattr`.

What the patcher skips:

TrebleApp, SystemUI and Dialer must be signed with the GSI's platform key. The bundled `platform.pk8` is the AOSP test key, so on a GSI signed with any other key the patcher leaves these three apps untouched and logs a warning. Otherwise `system_server` aborts at boot with a shared-user signature mismatch. On such a GSI you lose the SystemUI e-ink tweaks (static lockscreen and AoD, scrim) and the in-call UI tint, while the e-ink service, refresh modes and frontlight keep working.

After the first boot, set once:

- **Settings → System → Developer options → Window animation scale, Transition animation scale, Animator duration scale → Animation off.** Every animation frame reaches the panel, so with animations on the screen flashes and can stop on an intermediate frame.
- **Settings → System → Gestures → Navigation mode → Gesture navigation.** With 3-button navigation the bar covers the bottom of the E-Ink menu, and a touch there closes it.
- **Settings → System → TrebleDroid Settings → Misc features → Disable HW overlays.** The panel only shows GPU-composed frames, so with HW overlays the camera viewfinder stays black and some frames never appear. The bundled TrebleApp enables this by default, but the GSI's own TrebleApp turns HW overlays back on at every boot until this switch is set.

Default E-ink features Usage:

**Single Press E-Ink Button** - Refresh Screen

**Double Press E-Ink Button** - Open E-Ink Menu with settings for Per-App refresh modes.

These mappings can be easily changed in the E-Ink Settings app

## Licensing

### MIT License
Most of the project is licensed under the MIT License unless specified otherwise

The code and releases are provided “as is,” without any express or implied warranty of any kind including warranties of merchantability, non-infringement, title, or fitness for a particular purpose.

### Apache License 2.0
The file `HardwareGestureDetector.kt` includes code derived from AOSP. The original code is subject to the following license:

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

Modifications and additions to the original code are licensed under the MIT License
