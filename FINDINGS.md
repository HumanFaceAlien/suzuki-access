# SXV — Reverse-Engineering Findings

Companion document to the `SXV` Android app (`com.adithyapaib.sxv`). Captures the protocol
and behavioural findings extracted from the official Suzuki app
(`suzuki.com.suzuki`, v7.5, SHA-256 `C7E3466BEEA3D9512C5B6F79A79377389B1BBC57DFC6986E43EFA727E729F5CF`)
and validated against real over-the-air captures (nRF Connect, 2026-07-10).

Workspace: `C:\Users\paiad\Desktop\Suzuki` (jadx export of the official APK).
App project: `C:\Users\paiad\AndroidStudioProjects\SXV`.

---

## 1. Connection (cluster ↔ phone)

- **Advertised name filter:** the stock app keeps scan results whose name contains
  `SA`/`SB`/`SC` **and is exactly 12 chars** (e.g. `SBM01xxxxxx`). It does **not**
  pair/bond — no `createBond` anywhere in the APK.
- **Connect mode:** `BluetoothDevice.connectGatt(ctx, autoConnect=true, cb, TRANSPORT_LE)`.
  `autoConnect=true` is what made the stock app reliable; SXV previously used `false`.
- **Wake read:** ~500 ms after connect the stock app reads the **4th GATT service's 2nd
  characteristic** (`services.get(3).getCharacteristics().get(1)`). This read activates
  the cluster's notification stream. SXV now replicates it.
- **MTU:** the stock app uses FastBle's default (23). SXV requests **247** so the 30-byte
  cluster frame is not truncated (default MTU → 20-byte payload → parser rejects).
- **Service/char UUIDs:** `0000fff0` (service), `0000fff1` (write), `0000fff2` (notify),
  CCCD `00002902`.
- **Hard-coded MAC in SXV:** `XX:XX:XX:XX:XX:XX` (kept as a fallback; scan is primary).

## 2. Cluster → phone frame (command `'7'`, 30 bytes)

```
[0]        0xA5
[1]        '7'  (0x37)
[2..4]     speed in km/h, 3 ASCII digits
[5..10]    odometer, 6 digits (km)
[11..16]   trip A, 6 digits (tenths of km)
[17..22]   trip B, 6 digits (tenths of km)
[23]       'N' in every capture (read as gear by the stock app; an independent
           write-up of the same protocol treats it as a constant separator)
[24]       fuel bars, ASCII '1'..'6'
[25]       engine temperature, raw degrees C, unsigned byte (see §4)
[26..27]   0xFF 0xFF in every capture (padding / unused fuel-economy field)
[28]       checksum  (sum of bytes[1..27] & 0xFF, for this model — see §3)
[29]       0x7F
```

Validation gate (mirrors `C.java:817` / `NavigationActivity:190`):
`length==30 && [0]==0xA5 && [1]=='7' && [29]==0x7F && [28]==checksum`.

## 3. Checksum (model-dependent)

`SuzukiApplication.a(bArr)` is not a fixed algorithm. `K.g(String deviceName)` in
`d.java:110-155` selects the mode from the device name:

- V-STROM SX device name prefix `?BM01…` → **plain sum** (`sum(bytes[1..27]) & 0xFF`).
- Other models → **ones' complement** (`~sum & 0xFF`).

SXV implements both (`SuzukiChecksum.Mode.PLAIN_SUM` / `ONES_COMPLEMENT`), defaulting to
plain sum for the V-STROM SX.

## 4. Field semantics

- **Speed** `[2..4]`: `Integer.parseInt(substring(2,5))`. Confirmed by `j.java:44-46`
  (written to SharedPreferences key `"top_speed"`) and `NavigationActivity:195-202`
  (running max `this.X > this.U`). Real captures: 0 while parked, ramps to 33 km/h.
- **Odometer** `[5..10]`: 6 digits, leading zeros stripped; 0 on all-zero.
- **Trip A/B** `[11..16]` / `[17..22]`: 6 digits, tenths.
- **Gear** `[23]`: always `'N'` in all 110 captured frames.
- **Fuel** `[24]`: ASCII `'1'..'6'` → bars 1..6; else 0 / invalid. SXV assumes
  `fuelLiters = bars * 2.0` — **uncalibrated, marked `TODO(capacity)`**.
  Also `K = unsignedInt(b) - 64` (stored, purpose not fully identified).
- **`[25]` engine temperature (°C, raw byte)** — *supersedes the accumulator reading below.*
  Across 218 frames in five logs it tracks engine temperature: 27 cold and parked, 67 at the
  start of a ride, climbing to a 90 peak, 78 after a 10 minute break, 46→53 on a restart after
  1h40. Likely oil/engine temperature (the V-STROM SX 250 is oil-cooled), **not yet checked
  against the cluster's own reading**. `[26]/[27]` are `FF FF` in every frame (padding).
  Surfaced in SXV as `engineTempC`.
- **`[25..27]` consumption accumulator (old, disproved for byte 25)**: `NavigationActivity:203-219` decodes it as a
  24-bit fixed-point value into `public double Y`, but **`Y` is never read anywhere**
  in the APK; the dashboard parser (`C.java:831-832`) reads and discards it.
  - TFT editions: `parseInt(strC[0..24],2)/10/2048`
  - "Energy Consumption" branch: `/10000`
  - default (V-STROM SX): `(parseInt(strC[0..13],2) + parseInt(strC[13..24],2)/2048)/10`
  - Byte[25] climbs monotonically per session (`0x1B`→`0x58`), `[26]/[27]` stay `0xFF`.
  - **Not surfaced in SXV** (covered by checksum only).
  - **Revised after the 2026-07-10 logs (see §6):** byte `[25]` is not a fuel
    accumulator. It tracks engine temperature in °C, so the monotonic climb above is
    just warm-up. The SX 250 is oil-cooled, so this is probably oil/engine temperature
    rather than coolant. Not yet compared against a cluster readout. `[26..27]` never
    leave `FF FF`, so the 24-bit decoding above does not apply to this bike.

## 5. Phone → cluster commands

Packet layout (30 bytes): `[0]=0xA5 [1]=command [2..27]=payload [28]=checksum [29]=0x7F`.
Checksum = same plain-sum/ones'-complement rule (model-dependent).

- **`'1'` display (dot-matrix):** `"?1"` + 20-char primary (bytes 4..23) + 2-char
  status at bytes 23/24 (`'0'`/`'1'` GPS lock, `'0'`/`'1'` GPS fail) + 3-char
  secondary (bytes 25..27). `MAX_VISIBLE_TEXT_LENGTH = 20`.
  Implemented in `SuzukiDisplaySender.kt` (delegates checksum to `SuzukiChecksum`).
- **`'4'`** (missed-call text): builder in `SuzukiCommandPacket.kt`.
- **`'1'`** (navigation, the `a531` frame): **rewritten 2026-10-30** to the layout documented
  by github.com/pushplekh/redline-suzuki-ride-connect-google-maps `NOTES.md` (the old
  `"?110"` template guess had distance/ETA at the wrong offsets):
  `[2]` maneuver byte, `[3]` FF, `[4..7]` distance to next turn + `[8]` unit `M`/`K`,
  `[9..14]` ETA (`00HHMM` 24 h, `HHMMAM`/`HHMMPM` 12 h), `[15..17]` FF, `[18..21]` distance
  left + `[22]` unit, `[23]` status (`1` normal, `2` re-routing, `5` destination reached,
  `0` exit), `[24]` `1` keep navigating / `0` end, `[25..27]` FF. Distances: `0450` M under
  1 km, `01.2` K under 100 km, whole km above. Not yet seen on the V-STROM SX.
- **Maneuver byte (`[2]`)**: the official app translates its navigation SDK's (Mappls) IDs
  to cluster bytes (default branch, i.e. not Access/Burgman TFT): straight 8, left 1,
  sharp left 2, slight left 3, right 4, sharp right 5, slight right 6, U-turn left 7,
  U-turn right 39, keep left 27, keep right 28, merge left 31, merge right 32, exit left
  44, exit right 10, roundabout 45, compass heads N 40 … NW 19, arrived 9 (with status
  `5`), nothing to show `0x2E`. Taken from the same project's decompile (`ManeuverMap.kt`);
  **glyphs unchecked on this cluster**, hence the arrow tester in Settings.
- **`'6'`** (identity): sent after the CCCD write; see §6a.
- **`'2'`** (call control), **`'3'`** (status request / heartbeat): present in the APK,
  **not yet ported** to SXV.

### 5a. Google Maps → cluster (added 2026-10-30, not built or tested)

`nav/MapsNotificationListener` (a `NotificationListenerService`, needs Notification
access) reads Google Maps' ongoing navigation notification: title/text/sub-text extras
on current Maps ("200 m" / "Turn left onto MG Road" / "12 min · 5.3 km · 18:42"), the
custom `nav_title`/`nav_description`/`nav_time` views on older Maps.
`GoogleMapsNotificationParser` pulls distance, instruction, remaining distance and ETA
(clock time, or now + remaining duration); `Maneuver.fromInstruction` maps English
phrases to a maneuver, anything else is straight. `NavigationController` re-sends the
current step every second while navigation runs (the official app streams ~2.7/s) and
sends an end frame (`0x2E`, status `0`, byte 24 `0`) when the notification goes away.
Nav writes share FFF1's one-write slot with display messages; a busy slot is skipped
and retried next tick. Works only while SXV holds the BLE connection.

## 6. Captured data (4 nRF Connect logs, 110 thirty-byte frames)

- Strict validation `A5 / '7' / sum(1..27)==[28] / 7F`: **110/110 pass**.
- Non-30-byte payloads in the logs are DIS/broadcast noise (`"From Host Controller"`,
  `"SBM01xxxxxx"`, `"Suzuki"`, …) and are correctly rejected by the length gate.
- Observed ranges: speed 0..33 km/h; odometer 8882→8885 km; trip A 861.8→864.0;
  trip B 213.9→216.1; fuel always `'4'` (4 bars); gear always `'N'`.

### 6a. Re-analysis of the 2026-07-10 logs (5 files, 218 frames)

All frames are type `'7'`, all checksums pass, no other frame type ever arrives.
Frames come every 5 s (±30 ms).

**Byte 25 = engine temperature (°C):**

| Time  | Situation                    | Byte 25                       |
|-------|------------------------------|-------------------------------|
| 18:54 | cold, parked                 | 27                            |
| 19:02 | start of ride                | 67 → 88 (steady climb)        |
| 19:17 | after ~10 min stopped        | 78 (cooled), then climbs      |
| 19:19 | riding, then idle            | peak 90, then 89 → 88 stopped |
| 20:49 | restart after ~1h40 parked   | 46 → 53 (warming up)          |

It rises while the engine runs, flattens in the high 80s, dips when stopped, and falls
while parked. Speed, odometer and trips decode correctly and move as expected
(trip A ticks +0.1 km at the moment speed is non-zero).

**Not yet determinable:** fuel bars stayed `'4'` throughout (fuel level never
changed), so the fuel field is untested. `[26..27]` are constant padding.

**Not available over BLE:** no RPM, gear, battery voltage or throttle field, and no
second frame type from the bike. An independent write-up of the same protocol
(github.com/mrwick1/suzuki-connect-re, Gixxer SF 150) reaches the same conclusion:
the official app only parses `'7'`.

**GATT table (nRF Connect):** Generic Access, Generic Attribute, Device Information
(0x180A) and vendor service FFF0 (FFF1 write, FFF2 notify + CCCD). No hidden service.
Device Information contents on this bike:

| Characteristic     | Value                                                      |
|--------------------|------------------------------------------------------------|
| Manufacturer       | `Suzuki`                                                   |
| Model number       | `XF`                                                       |
| Serial number      | `SBM01xxxxxx` (same as the advertised name)               |
| Software revision  | `0.1.5.9`                                                  |
| Firmware/Hardware  | `From Host Controller` (unpopulated placeholder)           |
| System ID          | `XX-XX-XX-XX-XX-XX-XX-XX` (derived from the MAC)           |
| PnP ID             | Texas Instruments (vendor 0x000D), product 0, version 272  |
| Regulatory list    | `FE-00-"experimental"`                                     |

**Connection behaviour (corrects the earlier assumption):**
- ~~nRF Connect wrote nothing to FFF1 and the bike still streamed `'7'` frames, so no
  identity/handshake write is needed on this bike.~~ **Revised 2026-10-30:** SXV's own
  logs show a silent session (CCCD confirmed, zero frames for 68 s, then status 8) and
  the rider reports data appears only after opening the official app. Android shares one
  LE link per device between apps, so frames reach every subscribed app once the
  official app has sent its identity frame (`'6'`). The streaming nRF sessions all began
  with an `ACL_CONNECTED` broadcast before nRF connected, i.e. another app already held
  the link. SXV now sends `'6'` itself after the CCCD write (unconfirmed on the bike).
- A second Suzuki cluster nearby, `SAS02xxxxxx` @ `XX:XX:XX:XX:XX:XX` (Access-family
  name, Telit Terminal I/O service `FEFB`, no FFF0), matched SXV's name filter and was
  connected to repeatedly. SXV now prefers the known bike and skips non-FFF0 devices.
- Connections that streamed had the bike negotiate parameters (7.5 ms → 48.75 ms
  interval, MTU 65) within about a second of connecting. Connections that never
  negotiated stayed silent and ended in GATT error 8 (supervision timeout) after
  20–60 s. This is an observed pattern, not a confirmed cause.
- Bike-side disconnects show as status 19 (peer terminated).

Golden frames used as regression tests:
- Parked: `A5-37-30-30-30-30-30-38-38-38-35-30-30-38-36-34-30-30-30-32-31-36-31-4E-34-28-FF-FF-08-7F`
- Moving: `A5-37-30-33-33-30-30-38-38-38-34-30-30-38-36-33-32-30-30-32-31-35-33-4E-34-57-FF-FF-3E-7F`

## 7. SXV changes made (this session)

- `SuzukiChecksum.kt` (new): plain-sum / ones'-complement, sums bytes 1..27.
- `SuzukiPacketParser.kt` (rewritten): strict gate; `unknownField` → `speed`.
- `SuzukiCommandPacket.kt` (new): builders for `'4'` and `'110'`.
- `VStromTelemetry.kt`: added `speed`; documented fuel `TODO(capacity)`.
- `SuzukiDisplaySender.kt`: delegates checksum to `SuzukiChecksum`.
- `BleManager.kt`: scan-by-name, `autoConnect=true`, MTU 247, wake read, retry-via-scan.
- `DashboardScreen.kt`: SPEED card (KM/H); preview `tripA` 86.1 → 861.8.
- Tests: 31 total, 0 failures (added 2 golden-frame regression tests).
- **Verbose BLE logging** (added later, not yet compiled or run): switch on the Logs tab.
  `BleManager.kt` dumps the GATT table, sweeps readable characteristics, polls RSSI every
  5 s, and logs link parameters, PHY changes and disconnect status codes.
  `BleViewModel.kt` logs a per-field breakdown of each frame (byte 25, bytes 26–27,
  checksum, changed byte positions, gap since last frame) and raises the log cap from
  200 to 5,000 lines. `LogsScreen.kt` adds the switch and a Share button.

### 7a. Later session: UI redesign, temperature, write serialization

- **Dot-matrix writes are now serialized** (`BleManager.sendDisplayText`). The log showed
  the same frame re-sent every ~170 ms; an overlapping `writeCharacteristic` failed
  ("cluster write returned an error"). A write now waits for `onCharacteristicWrite` (2 s
  timeout) and logs `Cluster write completed status=N`. "Dot-matrix message queued
  successfully" only means Android accepted the request, not that the cluster received it.
- **Engine temperature surfaced:** `VStromTelemetry.engineTempC` is byte `[25]` as a raw
  unsigned value, accepted in 0..150, else null. Two parser tests added (real frames:
  `0x2D` → 45, `0x52` → 82; out-of-range byte → null).
- **UI rebuilt as four tabs** (Dashboard, Message, Logs, Settings) with new theme tokens
  (`Color.kt`, `Type.kt`, `Dimens.kt`), reusable components in `ui/components/`, and screens
  in `ui/screens/`. The old single-screen `ui/DashboardScreen.kt` is removed.
- **New ViewModel state:** `sendResults` (event per write, used for haptics and the
  recent-messages list) and `lastTelemetryAtMs` (drives "Updated Xs ago" and stale dimming
  after 15 s).
- **New `settings/AppSettings.kt`:** units (km/°C or mi/°F), keep-screen-on, five recent
  messages, stored in SharedPreferences.
- Previously invisible errors (`BleViewModel.errors`) now appear as snackbars.
- Not done on purpose: range estimate (no consumption data), theme switch (dark only).
- **Not compiled or run** (no JDK/SDK available in that session).

### 7b. Why the display may not update (unconfirmed)

Android accepts every FFF1 write, but the display has not been seen to change. Candidates:
1. The cluster rejects the write at the link level (needs encryption or bonding). The write
   completion status in the Logs tab will show 5 or 15 if so; FFF1 properties and bond
   state are logged at connect.
2. The `'5'` packet layout is off by one byte. `SuzukiDisplaySender` writes text at bytes
   3..24, `'N'` at 25, `FF FF` at 26..27. The sibling `'4'` builder uses a count byte at 3,
   text at 4..23, `'N'` at 24, `FF` at 25..27. This section's earlier note (§5) calls the
   dot-matrix command `'1'` with a `"?1"` template while the code sends `'5'`; one of them
   is wrong. Check against the decompiled APK.
3. The cluster needs a setup command first (`'3'` status request or `'6'` rider name, not
   ported).

## 8. Open items

1. Port commands `'2'` / `'3'`.
2. Calibrate `fuelLiters` (currently `bars * 2.0`, unverified).
3. Map maneuver bytes to the V-STROM SX's glyphs with the Settings arrow tester (bike
   parked), then check a real Google Maps route; note any notification text the parser
   gets wrong (verbose log shows the raw Maps notification).
4. Verify the new connection stack end-to-end with the bike powered on (trace via
   `adb logcat -s SXV-BLE`).
5. Build and run the verbose logging changes (never compiled; no JDK/SDK on the machine
   where they were written), then capture a full ride with verbose on.
6. Confirm byte `[25]` against the cluster's temperature readout (if the SX shows one).
   It is already surfaced on the Dashboard as "Engine temp".
9. Find out why dot-matrix writes don't change the display (see §7b), then build and run
   the whole UI redesign on a device.
7. Change the fuel level (or note the low-fuel light) during a capture to test the fuel
   bars field.
8. Find out why some connections stay silent: compare verbose logs of a streaming and a
   silent connection (parameter update, MTU, bond state).

## 9. Tooling notes

- REA MCP installed for OpenCode only (`--client opencode`); skill aligned (v49, 229 tools).
- APK verified via `apksigner` (v3/v3.1 true, SourceStamp true, Play App Signing, signer
  SHA-256 `fd2c107383de2f148cc11fe8d832049f22b6ae494f24bc2d912782b13306fe91`).
- `rg` is **not** installed; use `grep`/Node scripts. Node is on PATH.
- `keytool` at `C:\Program Files\Java\jdk-26.0.1\bin\keytool.exe`;
  `apksigner.bat` at `C:\Users\paiad\AppData\Local\Android\Sdk\build-tools\36.0.0\`.
