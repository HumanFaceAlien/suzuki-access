# StromX — V-Strom instrument link

An Android companion app for the Suzuki V-Strom SX. It connects over Bluetooth LE to the
bike's instrument cluster, shows live ride data, and sends Google Maps turn-by-turn
directions to the cluster. It talks to the cluster directly and does not use the official
Suzuki Ride Connect app.

Protocol details and reverse-engineering notes are in [FINDINGS.md](FINDINGS.md).

## Features

| Tab | What it does |
|---|---|
| Ride | Status line, a ruled header (speed, engine temperature with a sliding needle, six fuel cells) and the roadbook roll: a live "Now" row plus every event of the ride (start, connected, warmed up, stopped, riding again, fuel down, link lost) at the trip distance it happened. Readings dim when data goes stale. |
| Rides | Start a ride and it keeps the date, start/end times, distance from the odometer, top speed and average speed, plus an estimate of the fuel it used (distance at your own recent economy, priced at your average ₹/L). A live card ticks the elapsed time while you ride; finished rides collect below with every detail, and totals across all rides. A running ride survives an app restart. |
| Fuel | Fill-up log (litres, total cost, odometer prefilled from the bike, full tank or top-up). Economy measured full tank to full tank, cost per km, this month's spend and average price per litre; a line chart of economy per full tank and a column chart of spend per month. When the fuel bars jump up by two or more, it asks you to log the fill-up. **Fuel left and range:** counted down from your last full tank at your recent full-to-full economy; each time the gauge steps down, StromX learns what that bar really means on your bike and re-anchors the estimate there, so error doesn't build up. Shown with an honest ± and what it is counted from, a tank gauge with the learned bar levels, a fuel-left chart since the last full tank, and on the Ride header ("FUEL · ≈ 260 KM"). One low-range alert per tank at 40 km, with a "Nearest pump" shortcut. Tank size defaults to 12 L until you set yours. |
| Service | Maintenance reminders counted down from the bike's own odometer and/or the calendar (oil, chain, brakes, service…). Quick-add templates with typical starting intervals to edit, a ruled meter per item that turns highlighter when due soon or overdue, "Done at 12,336 km", a badge on the tab, and one notification when an item becomes due soon and again when due. |
| Logs | Connection details (state, last frame, FFF0/FFF1/FFF2), the BLE activity trace, a verbose-logging switch, copy and clear. |
| Settings | Rider name for the cluster's welcome greeting, Google Maps navigation on the cluster (switch, notification access, arrow tester), units (km/°C or mi/°F), keep screen on while connected, shortcut to system Bluetooth settings. |

Moving parts, all driven by real telemetry: digits that roll like an odometer, trip
distance advancing between frames at the reported speed, a distance strip that scrolls one
tick per 10 m, and a roadbook arrow that flows faster the faster you ride. Also: press-down
buttons with a key tick, a haptic buzz when the connection drops, a
landscape layout with a navigation rail for handlebar mounts, and errors as snackbars.

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

- **Navigation arrows are unconfirmed on the V-Strom SX.** The maneuver bytes come from the
  official app's table for other models; use the arrow tester in Settings to check them.

## Credits

- App icon: [Counter](https://icons8.com/icon/wDGaJlMDoEYy/counter) by [Icons8](https://icons8.com).
- Fonts: Saira Condensed (SIL OFL) and Roboto Mono (Apache 2.0).

## License

[MIT](LICENSE).

## Build

Requirements: JDK 21 and the Android SDK (platform 36, build-tools). Android Studio is
optional.

```bash
./gradlew :app:assembleDebug     # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # unit tests (parser, checksum, packet builders)
```

Set `ANDROID_HOME` (or `sdk.dir` in `local.properties`) to your SDK folder. Minimum
Android version is 13 (API 33). The app asks for the Bluetooth scan and connect
permissions on first launch.

## Project layout

```
app/src/main/java/com/adithyapaib/stromx/
  MainActivity.kt          permissions, Bluetooth state receiver, hosts StromXApp
  ble/                     BleManager (scan, GATT, writes), BleViewModel (state),
                           SuzukiPacketParser, ClusterWriter, SuzukiCommandPacket,
                           SuzukiChecksum, VStromTelemetry
  nav/                     Google Maps notification listener, parser, maneuver table,
                           NavigationController (sends nav frames)
  ride/Roadbook.kt         turns telemetry and link changes into roadbook events
  ride/Rides.kt            ride records: distance, duration, top speed, fuel estimate
  settings/AppSettings.kt  units, keep-screen-on, rider name (SharedPreferences)
  ui/StromXApp.kt             tab shell, haptics, keep-screen-on, snackbars
  ui/screens/              Ride (DashboardScreen), Rides, Fuel, Service, Logs, Settings
  ui/components/           roadbook primitives (ruled boxes, press blocks, rolling digits),
                           header box and roll, tulip diagrams, drawn tab icons
  ui/theme/                colour, type and spacing tokens
  res/font/                Saira Condensed (OFL) and Roboto Mono (Apache 2.0)
app/src/test/              unit tests, including real captured frames
```

## Design

"Roadbook": the ride writes itself onto a rally roadbook roll. White paper, ink and one
rally highlighter orange; heavy ink rules and square corners; Saira Condensed for numerals
and titles, Roboto Mono for labels and data. The highlighter marks only what is live. See
DESIGN.md for the full system. Previews for each screen state are in the `@Preview`
functions under `ui/screens/`.
