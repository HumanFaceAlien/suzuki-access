# StromX — V-Strom Instrument Link

[![Platform](https://img.shields.io/badge/Platform-Android%2013%2B%20%28API%2033%2B%29-brightgreen.svg)](https://developer.android.com)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![Protocol](https://img.shields.io/badge/BLE-Bluetooth%20Low%20Energy-orange.svg)](FINDINGS.md)
[![License](https://img.shields.io/badge/License-MIT-black.svg)](LICENSE)

An open-source Android companion app for the **Suzuki V-Strom SX** (250 cc adventure tourer).

StromX connects directly over Bluetooth Low Energy (BLE) to the bike's instrument cluster without relying on the official Suzuki Ride Connect app. Built with a rally roadbook aesthetic, it renders high-contrast, sunlight-readable telemetry, provides intelligent self-learning fuel and range estimation, tracks ride history and scheduled maintenance, and forwards Google Maps turn-by-turn navigation directly to the cluster's dot-matrix screen.

Protocol reverse-engineering notes, frame structures, and packet captures are documented in [FINDINGS.md](FINDINGS.md). Full UI styling principles and component rules are detailed in [DESIGN.md](DESIGN.md).

---

## Highlights

- **Direct Cluster Communication**: Communicates directly with the bike's GATT characteristics (`0xFFF0` service: `0xFFF1` write, `0xFFF2` notify) without cloud accounts or the proprietary Suzuki app.
- **Hidden Telemetry Unlocked**: Extracts and surfaces readings the stock app discards, including raw engine temperature reported in real time from byte 25.
- **Rally Roadbook Interface**: Designed around a white-paper rally roadbook metaphor with bold ink typography and a single highlighter accent (`#FF5A1F`). All controls are sized for motorcycle gloves (minimum 56 dp targets).
- **Adaptive Fuel & Range Learning**: Because the cluster provides bar levels (1–6) rather than fuel consumption, StromX anchors to full fill-ups, learns the actual remaining fuel capacity corresponding to each gauge bar on your bike, and computes real-time fuel volume and range estimates with confidence intervals.
- **Turn-by-Turn Navigation Forwarding**: Automatically parses Google Maps navigation notifications and streams distance, direction arrows, and ETAs directly to the bike's dot-matrix display.
- **Telemetry-Driven Motion**: Rolling odometer digits, a dynamic roadbook roll scrolling 1 tick per 10 m, and tulip turn arrows that flow faster as bike speed increases.
- **Handlebar Ready**: Seamless switching between portrait pocket view and a dual-column landscape layout with an ink navigation rail designed for handlebar mounts.

---

## App Tabs & Features

| Tab | Key Features |
|---|---|
| **Ride** | **Live Telemetry & Roadbook Roll**<br>• Ruled status header displaying digital speed, an engine temperature gauge with a sliding needle (40–120 °C scale), and a 6-cell fuel gauge.<br>• Dynamic "Now" roadbook row displaying dead-reckoned trip distance that advances smoothly at current speed between 5-second BLE frames.<br>• Continuous event timeline logging start, Bluetooth connection, engine warmup (80 °C), stops, resumptions, fuel level changes, and connection drops.<br>• Stale telemetry detection: numbers dim and a `STALE` indicator appears if data stops arriving. |
| **Rides** | **Ride Tracking & History**<br>• Real-time ride card showing elapsed timer, live distance, top speed, and average speed.<br>• Automatic fuel usage estimation based on historical efficiency and prevailing fuel prices.<br>• Ride persistence surviving app kills and background restarts.<br>• Historical ride log with comprehensive statistics and lifetime aggregates. |
| **Fuel** | **Log & Self-Learning Range Estimator**<br>• Fill-up logging (litres, cost, auto-prefilled odometer from telemetry, full tank vs. top-up).<br>• Full-tank to full-tank economy tracking, cost per kilometre, monthly expense metrics, and visual trend charts.<br>• Automatic fill-up prompt triggered whenever the gauge rises by 2 or more bars.<br>• **Intelligent range estimation**: Computes remaining fuel and honest distance estimates (`± km`). Learns individual bar thresholds as the gauge steps down to eliminate drift.<br>• Low-fuel alerts triggered at 40 km remaining with a one-tap shortcut to find nearby petrol stations. |
| **Service** | **Predictive Maintenance Countdown**<br>• Service reminders tracked by odometer distance and/or calendar time (Engine Oil, Oil Filter, Chain Care, Brake Pads, General Service).<br>• Quick-add presets with factory intervals for the V-Strom SX.<br>• Ruled countdown meters that turn highlighter orange when due soon or overdue.<br>• Android system notifications when maintenance thresholds approach. |
| **Logs** | **BLE Diagnostics & Tracing**<br>• Real-time GATT connection state monitoring, frame capture rates, and packet payloads (`FFF0` / `FFF1` / `FFF2`).<br>• Verbose BLE diagnostic toggle for link parameter updates, RSSI, and MTU changes.<br>• One-tap export, share, and clear tools for debugging OTA packet streams. |
| **Settings** | **Configuration & Calibration**<br>• Rider name input for cluster welcome greeting packets (`command '6'`).<br>• Google Maps navigation integration toggle, notification listener permissions status, and cluster arrow symbol tester.<br>• Units switch (`km / °C` vs. `mi / °F`).<br>• Screen wake lock ("Keep screen on while connected").<br>• Bluetooth device management and system Bluetooth shortcuts. |

---

## Cluster Telemetry & BLE Protocol

The V-Strom SX cluster transmits a 30-byte telemetry frame on GATT characteristic `0000fff2-0000-1000-8000-00805f9b34fb` every 5 seconds (±30 ms):

```
Byte Offset:   [0]    [1]    [2..4]    [5..10]    [11..16]   [17..22]   [23]   [24]     [25]     [26..27]   [28]       [29]
Payload:      0xA5   '7'     Speed     Odometer    Trip A     Trip B     'N'    Fuel     Engine    0xFF 0xFF  Checksum   0x7F
Format:       Start  Cmd     3 ASCII   6 ASCII     6 ASCII    6 ASCII    Sep    1..6     °C byte   Padding    Sum & FF   End
```

### Telemetry Breakdown

- **Speed (`[2..4]`)**: 3-digit ASCII integer in km/h (`000` to `199`).
- **Odometer (`[5..10]`)**: 6-digit ASCII integer in cumulative kilometres.
- **Trip A / Trip B (`[11..16]`, `[17..22]`)**: 6-digit ASCII integers in tenths of a kilometre (e.g., `008632` = 863.2 km).
- **Fuel Bars (`[24]`)**: ASCII character `'1'` through `'6'` corresponding to the LCD gauge bars.
- **Engine Temperature (`[25]`)**: Unsigned 8-bit integer (°C). Real-world ride data confirms this climbs from ambient (27 °C) to operating temperature (88–90 °C) and cools when stopped.
- **Checksum (`[28]`)**: For V-Strom SX models (`SBM01...` device name prefix), the cluster uses **Plain Sum** checksum mode:
  $$\text{checksum} = \left(\sum_{i=1}^{27} \text{byte}[i]\right) \ \& \ 0\text{xFF}$$
  *(Other Suzuki models use ones' complement `~sum & 0xFF`).*

### Downlink Commands (Phone → Cluster FFF1)

- **Command `'1'` (Navigation Step)**: Streams turn maneuver byte, distance to turn, unit (`M`/`K`), and ETA clock time parsed from Google Maps notifications.
- **Command `'1'` / `'5'` (Display Text)**: Dot-matrix text display updates.
- **Command `'6'` (Identity)**: Transmits rider name to the cluster upon establishing the BLE connection.

---

## Architecture & Project Structure

The project is built using modern Android Jetpack libraries: Kotlin Coroutines, StateFlow, Navigation, Material 3, and Compose.

```
app/src/main/java/com/adithyapaib/stromx/
├── MainActivity.kt               # Entry point, Bluetooth permission requests & lifecycle bindings
├── ble/                          # Bluetooth Low Energy protocol layer
│   ├── BleManager.kt             # Scanning, GATT connection lifecycle, MTU negotiation, CCCD subscription
│   ├── BleViewModel.kt           # BLE state container, error streams, telemetry Flow
│   ├── ClusterWriter.kt          # Serialized GATT writes to characteristic FFF1
│   ├── SuzukiChecksum.kt         # Plain-sum and ones'-complement checksum calculators
│   ├── SuzukiCommandPacket.kt    # Packet encoders for commands '1', '4', '6'
│   ├── SuzukiPacketParser.kt     # Strict 30-byte frame validator & parser
│   └── VStromTelemetry.kt        # Immutable telemetry data model
├── garage/                       # Persistent motorcycle stats & analytics
│   ├── Fuel.kt                   # Fuel fill-up entity & cost calculators
│   ├── FuelEstimator.kt          # Self-calibrating bar learning & remaining range estimator
│   ├── GarageStore.kt            # SharedPreferences JSON persistence for rides & maintenance
│   ├── GarageViewModel.kt        # Coordination between live telemetry, logs, and services
│   ├── Maintenance.kt            # Service interval definitions & countdown logic
│   └── ServiceNotifier.kt        # Android notifications for upcoming/due maintenance
├── nav/                          # Google Maps navigation bridge
│   ├── GoogleMapsNotificationParser.kt # Extracts turns, distances, and ETAs from Maps notifications
│   ├── Maneuver.kt               # Maps instruction strings to Suzuki cluster icon codes
│   ├── MapsNotificationListener.kt     # Android NotificationListenerService implementation
│   ├── NavigationBus.kt          # Reactive broadcast bus decoupling nav events from BLE
│   └── NavigationController.kt   # Periodic transmission loop for turn frames
├── ride/                         # Ride recording & roadbook generator
│   ├── Rides.kt                  # Ride session tracker, trip distances, and speed stats
│   └── Roadbook.kt               # Generates roadbook event rows from telemetry changes
├── settings/                     # User preferences
│   └── AppSettings.kt            # Units, wake lock, rider name, paired MAC persistence
└── ui/                           # Jetpack Compose UI layer
    ├── StromXApp.kt              # Main scaffold, navigation bar/rail, haptic dispatcher
    ├── Format.kt                 # Number, distance, and temperature formatting helpers
    ├── components/               # Reusable roadbook components
    │   ├── Charts.kt             # Custom Canvas economy line charts & monthly expense bars
    │   ├── Components.kt         # Ruled ink containers, tactile press blocks, segmented controls
    │   ├── FuelLeft.kt           # Custom tank capacity visualization & learned bar indicators
    │   ├── Icons.kt              # Hand-drawn Canvas tab icons matching roadbook stroke style
    │   ├── RoadbookUi.kt         # Signature telemetry header box and animated roadbook roll
    │   └── Tulips.kt             # Vector tulip navigation pictograms
    ├── screens/                  # App screen implementations
    │   ├── DashboardScreen.kt    # Main Ride screen with roadbook roll & live telemetry
    │   ├── FuelScreen.kt         # Fuel consumption tracking, charts, & tank settings
    │   ├── LogsScreen.kt         # Raw BLE trace viewer with copy & share actions
    │   ├── NavigationSection.kt  # Google Maps integration status & cluster arrow tester
    │   ├── RidesScreen.kt        # Completed ride log & active ride tracker
    │   ├── ServiceScreen.kt      # Maintenance checklist and countdown meters
    │   └── SettingsScreen.kt     # Preferences & BLE pairing management
    └── theme/                    # Design tokens (Color, Dimens, Theme, Type)
```

---

## Design System

StromX implements a distinct, utilitarian design system named **"The Roadbook Roll"**:

- **Paper & Ink Ground**: Pure white background (`#FFFFFF`) with dense, deep ink (`#141414`) borders and typewritten monospace text (`#575757`). No shadows, no gradients, and no rounded card elevations.
- **Rally Highlighter Accent**: High-visibility orange (`#FF5A1F`) is strictly reserved for live, changing data: the active roadbook row, the temperature needle, low-fuel alerts, and primary buttons.
- **Sunlight Readability**: Built with high-contrast text and tabular figures so that critical values (speed, engine temp, distance) can be read in under one second during midday sun.
- **Physical Controls**: Buttons behave like physical motorcycle switchgear—they sink 3 dp, invert into ink, and trigger tactile haptic ticks on tap.
- **Typography**:
  - **Saira Condensed**: Numeric readouts, titles, and button faces.
  - **Roboto Mono**: Labels, units, detail keys, and diagnostic traces.

---

## Getting Started

### Prerequisites

- **Android Device**: Android 13.0 (API 33) or higher with Bluetooth Low Energy support.
- **Java Development Kit**: JDK 21.
- **Android SDK**: Platform API 36 (build-tools 36.0.0).

### Building from Source

Clone the repository and build using Gradle:

```bash
# Clone the repository
git clone https://github.com/adithyapaib/stromx.git
cd stromx

# Make gradlew executable
chmod +x gradlew

# Run unit tests
./gradlew :app:testDebugUnitTest

# Assemble debug APK
./gradlew :app:assembleDebug
```

The resulting APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Device Setup & Pairing

1. **Permissions**: On initial launch, grant **Nearby Devices (Bluetooth)** permissions.
2. **Cluster Auto-Discovery**:
   - Turn on the bike's ignition.
   - Tap **CONNECT** on the Ride tab.
   - StromX scans for Suzuki clusters matching advertised name patterns (`SBM01...`, `SA...`, `SB...`, `SC...`) and automatically connects.
   - Once connected, the app pairs and remembers your bike's MAC address for immediate reconnection.
3. **Turn-by-Turn Navigation Setup**:
   - Navigate to **Settings > Turn-by-Turn Navigation**.
   - Tap **Enable Notification Access** and grant permissions to StromX.
   - Start navigation in Google Maps; directions and distance will begin appearing on the bike's dot-matrix screen.
4. **Mounting**:
   - Rotate the phone horizontally on a handlebar mount to automatically switch to the dual-column cockpit view with the navigation rail.

---

## Unit Testing

StromX includes test suites verifying protocol parsing, checksum calculations, and analytics:

```bash
./gradlew :app:testDebugUnitTest
```

Key test coverage includes:
- `SuzukiPacketParserTest`: Validates frames across real 110-frame capture logs from the V-Strom SX (speed, odometer, trip A/B, engine temp byte 25, checksum validation).
- `SuzukiChecksumTest`: Tests plain-sum and ones'-complement algorithms against edge cases and golden frames.
- `SuzukiCommandPacketTest`: Verifies byte positioning for dot-matrix text and navigation maneuver frames.
- `FuelEstimatorTest`: Tests adaptive learning of fuel gauge bar levels, countdown math, and range prediction bounds.
- `GoogleMapsNotificationParserTest`: Verifies parsing of current and legacy Google Maps Android notification layouts.
- `RidesTest` & `GarageMathTest`: Validates ride distance recording, time calculations, and maintenance countdown thresholds.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

### Third-Party Credits & Assets
- **Saira Condensed**: SIL Open Font License (OFL).
- **Roboto Mono**: Apache License, Version 2.0.
