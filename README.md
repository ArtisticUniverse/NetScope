# NetScope

An Android app that gives you **visibility into the devices around you** — on your
local Wi‑Fi network and over Bluetooth — plus a Multi‑Audio screen for playing music
to Bluetooth speakers.

It is built to stay strictly inside an **ethical visibility boundary**: it only
surfaces information that devices publicly reveal to anyone on the same network. It
never captures traffic, breaks into devices, or reads private data.

## Features

### 1. Network devices (Wi‑Fi / LAN)
- Shows your own Wi‑Fi SSID, IP, gateway, and subnet.
- Sweeps the local `/24` subnet for live hosts (ICMP + TCP).
- Per device: IP, MAC (when the OS exposes it via ARP), vendor (OUI lookup),
  reverse‑DNS hostname, and open **well‑known** ports mapped to service names.
- Discovers advertised services via **mDNS/DNS‑SD** (Chromecast, AirPlay, printers,
  HomeKit, Sonos, Spotify Connect, file shares, TVs…).

### 2. Bluetooth devices
- Classic inquiry + BLE scan.
- Per device: name, address, type (Classic/LE/Dual), pairing state, signal (RSSI),
  device class, and advertised service UUIDs. Audio‑capable devices are tagged.

### 4. Wi‑Fi Motion Sensing
- Polls the associated AP's RSSI (~250 ms) and measures its rolling variance.
- A still room has a low, flat noise floor; a person walking makes the signal
  jitter, which is flagged as "MOVEMENT DETECTED".
- Live RSSI graph, intensity bar, and a "Calibrate (stand still)" button to learn
  the room's baseline. **Honest note:** this is variance‑based, not real CSI
  sensing (stock Android doesn't expose CSI), so it's approximate and can be fooled
  by interference or by moving the phone.

### 5. AR Wi‑Fi Heatmap (ARCore)
- Uses ARCore (via SceneView) to track the camera's real 6‑DoF world pose.
- As you walk, it samples RSSI every ~0.35 m into world‑anchored points and paints
  green→red patches, projected back to screen with the camera's view/projection
  matrices.
- Reports the dead zone (weakest RSSI) and suggests moving the router toward it.
- Degrades honestly: on a non‑ARCore device or without "Google Play Services for
  AR", it shows an "AR unavailable" message instead of crashing.

### 3. Multi‑Audio
- Lists the phone's current audio outputs and flags Bluetooth/LE‑Audio ones.
- Lets you pick a local track and play it.
- **Honest capability detection** — see the note below.

## ⚠️ The truth about "play to multiple Bluetooth speakers at once"

There is a hard platform limit here, and NetScope is built to tell you the truth
rather than fake it:

- **Classic Bluetooth (A2DP)** — the public Android SDK routes audio to **one**
  speaker at a time. No third‑party app can force synchronized playback to several
  classic BT speakers.
- **Samsung "Dual Audio"** — a *vendor* feature (2 devices), controlled in system
  Bluetooth settings, not via any public app API. NetScope links you straight there.
- **Bluetooth LE Audio / Auracast broadcast** (Android 13+/14+) — the real path to
  one‑to‑many audio, but **every speaker must support LE Audio**. NetScope detects
  whether your phone supports LE Audio and Auracast broadcast at runtime and tells
  you what's actually possible on your hardware.

So the Multi‑Audio screen enumerates and lets you select sinks, plays a track, and
clearly states — based on your device — whether multiple selected speakers will truly
play together or whether you need Dual Audio / LE‑Audio hardware.

## Ethical & legal note

Only scan networks you **own or are explicitly authorised to test**. Unauthorised
network scanning may be illegal where you live. The app gates all scanning behind an
authorisation confirmation on first launch. It deliberately does **not** include
packet capture, deauthentication, exploitation, or any covert access to other
devices.

## Build

Requirements: Android Studio (or CLI), JDK 17, Android SDK with platform 36 and
build‑tools 36.x.

```bash
cd NetScope
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Install to a connected device:

```bash
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press Run.

- `compileSdk` / `targetSdk` 36, `minSdk` 26 (Android 8.0).
- Kotlin 2.4.10, Jetpack Compose (Material 3), AGP 8.13, Gradle 8.14.3.
- ARCore via `io.github.sceneview:arsceneview:4.34.0` (4.35.0+ require compileSdk 37,
  which AGP 8.13 cannot target). Debug APK is ~50 MB due to Filament/ARCore natives.
- The AR tab needs an ARCore‑supported device with "Google Play Services for AR".

## Project layout

```
app/src/main/java/com/netscope/app/
  MainActivity.kt        tabs + consent gate
  AppViewModel.kt        state + orchestration
  net/NetworkScanner.kt  subnet sweep, ARP, reverse DNS, port probe
  net/NsdDiscovery.kt    mDNS / DNS-SD service discovery
  bt/BluetoothScanner.kt classic + BLE discovery
  audio/MultiAudioManager.kt  output enumeration, capability detection, playback
  model/Models.kt        data classes
  util/Oui.kt            MAC vendor lookup (starter table — swap in full IEEE OUI)
  util/Perms.kt          runtime permission sets
  ui/                    Compose screens + theme
```

### Known limitations
- MAC addresses are often hidden by Android 10+ (ARP is restricted); the vendor
  column then shows nothing. Reading a full IEEE OUI file into `Oui.kt` improves
  vendor coverage.
- The subnet sweep assumes a `/24`. Widen `NetworkScanner.scan()` for other masks.

## Releasing to GitHub

CI is set up in `.github/workflows/`:

- **build.yml** — builds a debug APK on every push to `main` / PR and uploads it as
  a run artifact.
- **release.yml** — on pushing a `v*` tag, builds the APK and attaches it to a
  GitHub Release (with auto-generated notes). No signing secrets needed (debug APK).

Cut a release:

```bash
git tag v1.0.0
git push origin v1.0.0     # release.yml builds + publishes the APK automatically
```

The 50 MB debug APK is intentionally **not** committed to the repo — it is produced
by CI and attached to the Release as a downloadable asset.
