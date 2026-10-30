# SXV — V-Strom instrument link

An Android companion app for the Suzuki V-Strom SX. It connects over Bluetooth LE to the
bike's instrument cluster, shows live ride data, and can send short text messages to the
cluster's dot-matrix display. It talks to the cluster directly and does not use the
official Suzuki Ride Connect app.

Protocol details and reverse-engineering notes are in [FINDINGS.md](FINDINGS.md).

## Features

| Tab | What it does |
|---|---|
| Dashboard | Connection status, speed (hero), fuel level, engine temperature, odometer, trips A and B. Values dim and show "No update for…" when data goes stale. |
| Message | Two-line message composer with a live counter, quick messages (Fuel stop, Lunch, Follow me), a preview of the cluster line, and recent messages. |
| Logs | Connection details (state, last frame, FFF0/FFF1/FFF2), the BLE activity trace, a verbose-logging switch, copy and clear. |
| Settings | Units (km/°C or mi/°F), keep screen on while connected, shortcut to system Bluetooth settings. |

Also: rolling speed counter, haptic feedback on message sent or failed and when the
connection drops, a landscape layout with a navigation rail for handlebar mounts, and error
messages shown as snackbars.

## Data from the cluster

The cluster sends one 30-byte frame every 5 seconds on characteristic FFF2:

| Bytes | Field |
|---|---|
| 2–4 | Speed, km/h |
| 5–10 | Odometer, km |
| 11–16, 17–22 | Trip A, Trip B (tenths of km) |
| 24 | Fuel bars, 1–6 |
| 25 | Engine temperature, °C (likely oil/engine; not yet checked against the cluster's own readout) |

Litres shown on the fuel card are an estimate (2 L per bar, uncalibrated). There is no
range estimate because the cluster sends no consumption data. See FINDINGS.md for the full
frame layout, checksum rules and captured data.

## Known issues

- **Display writes are unconfirmed.** Writes to FFF1 are accepted by Android, but the
  cluster display has not been seen to update. The `'5'` packet layout may be shifted by one
  byte, or the cluster may need a handshake or bonding. The Logs tab now records the write
  completion status and FFF1 properties and bond state to find out which. See FINDINGS.md §8.
- **Not built or tested on a device in the latest session.** The UI redesign, temperature
  card and verbose logging were written without a JDK or Android SDK on hand. Build and run
  before relying on them.

## Build

Requirements: JDK 21 and the Android SDK (platform 36, build-tools). Android Studio is
optional.

```bash
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # unit tests (parser, checksum, packet builders)
```

Set `ANDROID_HOME` (or `sdk.dir` in `local.properties`) to your SDK folder. Minimum
Android version is 14 (API 34). The app asks for the Bluetooth scan and connect
permissions on first launch.

## Project layout

```
app/src/main/java/com/adithyapaib/sxv/
  MainActivity.kt          permissions, Bluetooth state receiver, hosts SxvApp
  ble/                     BleManager (scan, GATT, writes), BleViewModel (state),
                           SuzukiPacketParser, SuzukiDisplaySender, SuzukiCommandPacket,
                           SuzukiChecksum, VStromTelemetry
  settings/AppSettings.kt  units, keep-screen-on, recent messages (SharedPreferences)
  ui/SxvApp.kt             tab shell, haptics, keep-screen-on, snackbars
  ui/screens/              Dashboard, Message, Logs, Settings
  ui/components/           StatusChip, PrimaryButton, HeroSpeedCard, MetricCard, FuelCard,
                           MessageInput, SegmentedToggle, drawn tab icons
  ui/theme/                colour, type and spacing tokens
app/src/test/              unit tests, including real captured frames
```

## Design

Dark theme (`#121212` background, `#1E1E1E` cards), yellow accent for primary actions only,
green/amber/red for connection status, an 8-point spacing grid, 16dp card radius, and
controls at least 56dp tall for use with gloves. Previews for each screen state are in the
`@Preview` functions under `ui/screens/`.
