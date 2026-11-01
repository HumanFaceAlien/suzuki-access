---
version: 1
slug: "hyapaib-sxv-ui-screens-dashboardscreen-kt-4328442f"
primary_target: "app/src/main/java/com/adithyapaib/stromx/ui/screens/DashboardScreen.kt"
related_targets: ["app/src/main/java/com/adithyapaib/stromx/ui/StromXApp.kt"]
---

# Surface brief: StromX app shell (Ride, Message, Logs, Settings)

Scope: the whole Android app, Operate mode. Audience: one V-Strom SX owner, phone mounted or
pocketed. Task: read the bike at a glance, send short cluster messages, diagnose the link.
Constraints: white ground, very few colours, CRED-level finish, moving parts driven by real
telemetry only, 56dp targets, Material navigation. Unresolved: display writes unconfirmed;
no device render available in the building session.

## Direction contract

THESIS: The ride writes itself onto a rally roadbook roll. Refuses the category default of
ring gauges on soft cards: every reading is a ruled box, every event a roadbook row.

OWN-WORLD: White roll paper (#FFFFFF), ink (#141414) and one rally highlighter orange
(#FF5A1F), plus a pale orange wash for the current row. Heavy 2.5dp ink rules, square
corners, no shadows. Saira Condensed ExtraBold for numerals and row titles; Roboto Mono for
labels and data. Buttons are ink blocks that press down; highlighter only on what is live.

STORY: The rider sees speed, engine and fuel in one ruled header, then the ride as a
roadbook: where it started, when it warmed up, stopped, sent a message. They trust it
because every row is a real event at a real distance.

FIRST VIEWPORT: Status line (connection, data age). Header box: speed at display scale on
the left, engine and fuel stacked right. Below, the roll: the NOW row on top in orange with
live distance rolling, past rows beneath, distance ticks running down the left edge at
riding speed. Primary action (connect) sits in the roll when offline.

FORM: Rally roadbook, Impeccable's pick (rank 1 of 7 grounded). seed:bfe3059e9b6398b4

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
