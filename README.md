# ADB Controller

Control and manage other Android devices **from your Android phone** — over Wi-Fi or a USB OTG cable, no computer needed. ADB Controller speaks the ADB protocol natively, so every action is a button instead of a shell command.

<p align="center">
  <img src="screenshots/home.png" width="260" alt="Home screen with recent devices" />
  <img src="screenshots/about.png" width="260" alt="About screen" />
  <img src="screenshots/settings.png" width="260" alt="Settings with theme options" />
</p>

## Features

**Connecting**
- Wireless ADB by IP and port, with a list of recent devices (model, Android version, last connected; rename or remove)
- USB OTG — devices are detected automatically when plugged in
- Wireless-debugging pairing screens for QR code and pairing code (experimental: connecting through the Android 11+ wireless-debugging port isn't supported yet, so use `adb tcpip` mode for Wi-Fi)

**Control**
- Live remote control — see the screen, tap, long-press and swipe; navigation, volume and power keys; type text; open URLs
- Shell terminal with quick commands
- Quick settings — Wi-Fi, Bluetooth, mobile data, airplane mode, dark theme, battery saver, stay awake, show taps, layout bounds, animations, brightness, font size, screen timeout, resolution and DPI
- Screenshots, saved to your phone

**Management**
- Apps — search, launch, force stop, clear data, enable/disable, uninstall, view details, extract APK, manage runtime permissions
- Install APKs from your phone with upload progress
- File manager — browse, upload, download, rename, delete, install APKs on the device

**Diagnostics**
- Device info (battery, RAM, storage, CPU, uptime), logcat with level filters, processes, network, battery, system properties, kernel log

**Rooted devices** (Magisk / KernelSU / APatch)
- Root status, manager and version, SELinux mode
- Module manager — enable/disable, remove/restore, install from zip, run module actions, enable/disable all
- Root shell and root file browser
- Performance — per-core CPU governor and frequency, temperatures, ZRAM
- Diagnostics — crash (tombstone) and ANR reports, wakelocks, battery-stats reset
- Soft reboot and SystemUI restart

**App**
- Light, dark and system themes, plus Material You dynamic color on Android 12+
- No accounts, analytics or cloud services

## Building

Requirements: JDK 17+ and the Android SDK (API 36).

```bash
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On Windows, `build-app.ps1` builds, installs and launches it on a connected device or emulator.

## Using it

1. On the target device, enable **Developer options** and **USB debugging**.
2. Connect:
   - **USB:** plug the device into your phone with an OTG cable.
   - **Wi-Fi:** connect once over USB and use **WiFi ADB** on the dashboard, or run `adb tcpip 5555` from a computer, then enter the device's IP address.
3. Approve the **Allow USB debugging?** prompt on the target. The fingerprint shown there matches the one under **Settings → Security** in the app.

## Tech stack

Kotlin, Coroutines, Jetpack Compose, Material 3, AndroidX Navigation, CameraX, ML Kit Barcode Scanning and Bouncy Castle. The ADB protocol (transport, authentication, streams and file sync) is implemented from scratch in `app/src/main/java/com/adbcontroller/adb`.

## Author

Made by [imkamaran](https://github.com/imkamaran).
