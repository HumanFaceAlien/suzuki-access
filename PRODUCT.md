# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The owner of a Suzuki V-Strom SX (250 cc, oil-cooled adventure tourer), riding in India.
*Inferred, not confirmed:* the phone is used both ways, mounted on the handlebar and glanced
at while moving in daylight, and pocketed and checked at stops. *Inferred, not confirmed:*
this is a personal app for one rider and one bike, not a store product.

## Product Purpose

SXV links the phone to the bike's instrument cluster over Bluetooth LE without the official
Suzuki Ride Connect app. It shows what the cluster reports live and can push short text to
the cluster's dot-matrix display. Success: a rider trusts it at a glance, and it feels like
part of the bike rather than a debugging tool.

## Positioning

It reads the bike's own frames directly (reverse-engineered protocol) and surfaces fields the
official app hides, such as engine temperature from byte 25. It is honest about what the
link does and does not carry.

## Operating Context

- One 30-byte telemetry frame every 5 seconds: speed, odometer, trip A/B, fuel bars (1-6),
  engine temperature (°C, likely oil/engine). No RPM, gear, voltage or consumption data.
- Connection states: disconnected, connecting, connected, error. Data can go stale.
- Riding in sun, with gloves, at speed; also parked, warming up, at fuel stops.
- Phone portrait in the pocket; landscape on a handlebar mount.

## Capabilities and Constraints

- Jetpack Compose, Material 3, minSdk 34. No icon or navigation library dependency.
- Litres are an estimate (2 L per bar, uncalibrated). No range estimate: there is no data.
- Dot-matrix writes are accepted by Android but not yet confirmed on the cluster display.
- Engine temperature is not yet checked against the cluster's own readout.
- Tabs: Dashboard, Message, Logs, Settings. Units km/°C or mi/°F.

## Brand Commitments

Binding, from the user: a CRED-app vibe; dynamic, with moving parts rather than only numbers;
very few colours; a white background; built for a bike. Name: SXV.

## Evidence on Hand

Real captured frames in `app/src/test/` and `FINDINGS.md` (218 frames, 5 ride logs). No
photos or brand assets of the bike exist in the repo; any bike illustration must be authored.

## Product Principles

1. Glance first: the riding view is read in under a second; detail waits for stops.
2. Truthful motion: everything that moves is driven by real telemetry, never decoration.
3. Never fake data: estimates are marked, missing data is shown as missing.
4. Feel like hardware: controls behave like physical switches and buttons on a bike.

## Accessibility & Inclusion

Glove-sized targets (56dp minimum), legible in direct sunlight, colour never the only signal.
