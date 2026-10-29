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
[23]       gear position — always 'N' in every capture so far
[24]       fuel bars, ASCII '1'..'6'
[25..27]   cumulative fuel-consumption accumulator (see §4)
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
- **`[25..27]` consumption accumulator**: `NavigationActivity:203-219` decodes it as a
  24-bit fixed-point value into `public double Y`, but **`Y` is never read anywhere**
  in the APK; the dashboard parser (`C.java:831-832`) reads and discards it.
  - TFT editions: `parseInt(strC[0..24],2)/10/2048`
  - "Energy Consumption" branch: `/10000`
  - default (V-STROM SX): `(parseInt(strC[0..13],2) + parseInt(strC[13..24],2)/2048)/10`
  - Byte[25] climbs monotonically per session (`0x1B`→`0x58`), `[26]/[27]` stay `0xFF`.
  - **Not surfaced in SXV** (covered by checksum only).

## 5. Phone → cluster commands

Packet layout (30 bytes): `[0]=0xA5 [1]=command [2..27]=payload [28]=checksum [29]=0x7F`.
Checksum = same plain-sum/ones'-complement rule (model-dependent).

- **`'1'` display (dot-matrix):** `"?1"` + 20-char primary (bytes 4..23) + 2-char
  status at bytes 23/24 (`'0'`/`'1'` GPS lock, `'0'`/`'1'` GPS fail) + 3-char
  secondary (bytes 25..27). `MAX_VISIBLE_TEXT_LENGTH = 20`.
  Implemented in `SuzukiDisplaySender.kt` (delegates checksum to `SuzukiChecksum`).
- **`'4'`** and **`'110'`** (navigation): added builders in `SuzukiCommandPacket.kt`.
  The navigation packet assembles `fieldM`/`fieldN`/`fieldO` into bytes 2..27; their
  widths are **unconfirmed** (Mappls callbacks didn't decompile) — the builder returns
  `null` if the assembled payload is not exactly 24 bytes.
- **`'2'`** (call control), **`'3'`** (status request), **`'6'`** (rider name): present
  in the APK, **not yet ported** to SXV.

## 6. Captured data (4 nRF Connect logs, 110 thirty-byte frames)

- Strict validation `A5 / '7' / sum(1..27)==[28] / 7F`: **110/110 pass**.
- Non-30-byte payloads in the logs are DIS/broadcast noise (`"From Host Controller"`,
  `"SBM01xxxxxx"`, `"Suzuki"`, …) and are correctly rejected by the length gate.
- Observed ranges: speed 0..33 km/h; odometer 8882→8885 km; trip A 861.8→864.0;
  trip B 213.9→216.1; fuel always `'4'` (4 bars); gear always `'N'`.

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

## 8. Open items

1. Port commands `'2'` / `'3'` / `'6'`.
2. Calibrate `fuelLiters` (currently `bars * 2.0`, unverified).
3. Confirm navigation packet field widths (`fieldM`/`fieldN`/`fieldO`) — builder returns
   `null` if assembly ≠ 30 bytes, so it won't transmit garbage.
4. Verify the new connection stack end-to-end with the bike powered on (trace via
   `adb logcat -s SXV-BLE`).
5. SXV is **not** a git repo — `git init` + commit before further changes.

## 9. Tooling notes

- REA MCP installed for OpenCode only (`--client opencode`); skill aligned (v49, 229 tools).
- APK verified via `apksigner` (v3/v3.1 true, SourceStamp true, Play App Signing, signer
  SHA-256 `fd2c107383de2f148cc11fe8d832049f22b6ae494f24bc2d912782b13306fe91`).
- `rg` is **not** installed; use `grep`/Node scripts. Node is on PATH.
- `keytool` at `C:\Program Files\Java\jdk-26.0.1\bin\keytool.exe`;
  `apksigner.bat` at `C:\Users\paiad\AppData\Local\Android\Sdk\build-tools\36.0.0\`.
