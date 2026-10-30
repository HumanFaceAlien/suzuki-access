---
name: SXV
description: A rally roadbook roll for one V-Strom SX, written live from the bike's own telemetry.
colors:
  paper: "#FFFFFF"
  ink: "#141414"
  ink-soft: "#575757"
  ink-faint: "#D6D6D6"
  highlight: "#FF5A1F"
  highlight-wash: "#FFF1EA"
typography:
  display-large:
    fontFamily: "Saira Condensed"
    fontSize: "112sp"
    fontWeight: 800
    lineHeight: "104sp"
    letterSpacing: "-2sp"
    fontFeature: "tnum"
  display-small:
    fontFamily: "Saira Condensed"
    fontSize: "40sp"
    fontWeight: 800
    lineHeight: "40sp"
    fontFeature: "tnum"
  headline-medium:
    fontFamily: "Saira Condensed"
    fontSize: "34sp"
    fontWeight: 800
    lineHeight: "36sp"
    fontFeature: "tnum"
  headline-small:
    fontFamily: "Saira Condensed"
    fontSize: "30sp"
    fontWeight: 800
    lineHeight: "32sp"
    letterSpacing: "0.5sp"
  title-large:
    fontFamily: "Saira Condensed"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: "26sp"
  title-medium:
    fontFamily: "Saira Condensed"
    fontSize: "20sp"
    fontWeight: 700
    lineHeight: "22sp"
  body-large:
    fontFamily: "Roboto Mono"
    fontSize: "15sp"
    fontWeight: 500
    lineHeight: "22sp"
  body-medium:
    fontFamily: "Roboto Mono"
    fontSize: "13sp"
    fontWeight: 500
    lineHeight: "19sp"
  body-small:
    fontFamily: "Roboto Mono"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "17sp"
  label-large:
    fontFamily: "Saira Condensed"
    fontSize: "20sp"
    fontWeight: 800
    lineHeight: "22sp"
    letterSpacing: "1sp"
  label-medium:
    fontFamily: "Roboto Mono"
    fontSize: "12sp"
    fontWeight: 700
    lineHeight: "16sp"
    letterSpacing: "0.5sp"
  label-small:
    fontFamily: "Roboto Mono"
    fontSize: "11sp"
    fontWeight: 700
    lineHeight: "14sp"
    letterSpacing: "1sp"
rounded:
  none: "0dp"
spacing:
  xs: "4dp"
  s: "8dp"
  box-inset: "12dp"
  m: "16dp"
  l: "24dp"
  xl: "32dp"
components:
  rule-heavy:
    backgroundColor: "{colors.ink}"
    height: "2.5dp"
  rule-light:
    backgroundColor: "{colors.ink}"
    height: "1.5dp"
  ink-box:
    backgroundColor: "{colors.paper}"
    rounded: "{rounded.none}"
  press-block-highlight:
    backgroundColor: "{colors.highlight}"
    textColor: "{colors.ink}"
    typography: "{typography.label-large}"
    rounded: "{rounded.none}"
    height: "56dp"
  press-block-highlight-pressed:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.highlight}"
  press-block-paper:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.label-large}"
    rounded: "{rounded.none}"
    height: "56dp"
  press-block-paper-pressed:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
  press-block-disabled:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink-soft}"
  text-action:
    textColor: "{colors.ink}"
    typography: "{typography.label-medium}"
    padding: "0 8dp"
    height: "56dp"
  tag:
    backgroundColor: "{colors.highlight}"
    textColor: "{colors.ink}"
    typography: "{typography.label-medium}"
    rounded: "{rounded.none}"
    padding: "2dp 6dp"
  box-label:
    textColor: "{colors.ink-soft}"
    typography: "{typography.label-small}"
  ink-field:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.title-medium}"
    rounded: "{rounded.none}"
    height: "56dp"
  ink-segmented:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.label-large}"
    rounded: "{rounded.none}"
    height: "56dp"
  ink-segmented-selected:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.paper}"
  nav-item:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink-soft}"
    typography: "{typography.label-medium}"
  nav-item-selected:
    textColor: "{colors.ink}"
  nav-indicator-selected:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.highlight}"
    size: "24dp"
  roadbook-row:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    typography: "{typography.title-medium}"
    padding: "10dp"
    height: "88dp"
  roadbook-row-now:
    backgroundColor: "{colors.highlight-wash}"
    textColor: "{colors.ink}"
    typography: "{typography.title-large}"
    padding: "10dp"
    height: "104dp"
  cluster-preview:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.highlight}"
    typography: "{typography.body-large}"
    padding: "14dp 16dp"
---

# Design System: SXV

## Overview

**Creative North Star: "The Roadbook Roll"**

The app is a rally roadbook: a white paper roll ruled in heavy ink, with one orange highlighter for whatever is live right now. Every reading sits in a ruled box and every event the bike reports becomes a numbered roadbook row (distance, tulip pictogram, note). Nothing floats, nothing is tinted grey, and nothing casts a shadow. Depth and emphasis come from three moves only: a heavier rule, an ink flood, or a highlighter mark.

The density is a navigator's: big condensed numerals packed into narrow boxes, small typewritten labels in the corners, and a strip of distance ticks running down the left edge of the roll. Motion is part of the system, and every moving part is driven by real telemetry: digits roll like an odometer, the temperature needle slides, the distance strip scrolls at 10 m per tick, the tulip arrow's dash flows with speed. When the system animator scale is 0, all of that stops. The live trip distance keeps updating because it is data, not decoration.

The ground is white because the rider asked for it and because it reads in sunlight. There is no dark scheme and no dynamic colour. The world was derived from the shipped Compose source; it has not been compiled or rendered on a device (no Android SDK was available), so rendered values such as Material's own indicator and switch shapes are unverified.

**Key Characteristics:**
- White roll paper, ink, and a single rally highlighter (#FF5A1F) that marks only what is live.
- Square boxes framed in a 2.5dp heavy rule, divided by 1.5dp light rules.
- Saira Condensed for numerals, titles and buttons; Roboto Mono for labels, units and notes.
- Press blocks that flood with ink and sink 3dp inside their rule; no shadow anywhere.
- Every animation is telemetry-driven and goes still when system animations are off.
- Glove-sized controls: 56dp minimum for every tappable target.

## Colors

A three-ink palette on white paper: ink for structure and text, one highlighter for the live thing, and a pale wash for the current row.

### Primary
- **Rally Highlighter** (#FF5A1F): marks what is live. It fills the primary press block, Tags (NOW, STALE, LOW, RESERVE, fuel estimate), the temperature needle, the low-fuel cells, the connected status square, the selected tab icon, the text cursor and selection, the live tulip's accent stroke, and the new-row swipe (at 40% alpha). Against paper it is only 3.1:1, so it is never used for text on paper. Text on it is always Ink (5.9:1).
- **Highlighter Wash** (#FFF1EA): the NOW row's background while riding or stopped. Ink on it is 16.7:1 and Soft Ink is 6.6:1, so both text roles keep working on it.

### Neutral
- **Roll Paper** (#FFFFFF): every surface. Background, surface, every container, nav bar and rail. Cards are not tinted off-white.
- **Ink** (#141414): rules, numerals, titles, body text, pressed-block floods, the selected segment, the selected nav indicator, and the cluster-preview display. 18.4:1 on paper.
- **Soft Ink** (#575757): box labels, notes, units, unselected nav, and stale readings. 7.2:1 on paper, so a stale value is quieter but still fully legible.
- **Faint Ink** (#D6D6D6): disabled strokes (a disabled press block's rule) and the Material outline-variant role. 1.45:1 on paper; never a carrier of readable text.

### Named Rules
**The One Highlighter Rule.** Orange means "live right now": the current row, the selected tab, the needle, the one action on a screen. If two things on a screen are orange for different reasons, one of them is wrong.

**The Ink-On-Orange Rule.** Text sitting on the highlighter is Ink, never white. On paper the highlighter is a fill or a mark, never text; on an ink ground (the cluster preview, a pressed highlight block's label) highlighter text is allowed at 5.9:1.

**The Never-Colour-Alone Rule.** Every state that uses orange also changes shape or words: the status square is filled, empty, breathing or crossed; low fuel adds a LOW or RESERVE tag; stale adds a STALE tag.

## Typography

**Display Font:** Saira Condensed (SemiBold, Bold, ExtraBold; bundled, OFL)
**Body Font:** Roboto Mono (variable, Medium and Bold; bundled, Apache 2.0)
**Label/Mono Font:** Roboto Mono

**Character:** A condensed rally face that packs big tabular digits into narrow boxes, paired with a typewritten mono that does the measured, labelled work of a navigator's notes.

### Hierarchy
- **Display Large** (Saira ExtraBold, 112sp / 104sp, -2sp, tabular): speed in the header box only. It is capped against the system font scale so three digits always fit their box.
- **Display Small** (Saira ExtraBold, 40sp / 40sp, tabular): the live trip distance role. The roll draws it at 28sp so "8888.8" fits the distance column.
- **Headline Medium** (Saira ExtraBold, 34sp / 36sp, tabular): secondary readings: engine temperature, fuel bars.
- **Headline Small** (Saira ExtraBold, 30sp / 32sp, 0.5sp): screen titles and the SXV wordmark in the status line.
- **Title Large** (Saira Bold, 24sp / 26sp): the NOW row title and logged row distances (with tabular figures).
- **Title Medium** (Saira Bold, 20sp / 22sp): row titles, toggle-row titles, preset and recent message text, field input text.
- **Body Large / Medium / Small** (Roboto Mono Medium, 15/13/12sp): notes under titles, detail values, log lines, disabled reasons, empty states. Body Small is the workhorse.
- **Label Large** (Saira ExtraBold, 20sp, 1sp, upper-case): press-block and segmented-control text.
- **Label Medium** (Roboto Mono Bold, 12sp, 0.5sp, upper-case): Tags, text actions, nav labels, status label, units.
- **Label Small** (Roboto Mono Bold, 11sp, 1sp, upper-case): box labels such as "SPEED · KM/H", "ENGINE", "TRIP A · KM", row timestamps, detail-row keys.

### Named Rules
**The Tabular Numeral Rule.** Every number that changes while riding uses tabular figures (`tnum`), so rolling digits never shift their neighbours.

**The Label Corner Rule.** Upper-case is reserved for typewritten box labels, Tags, nav labels and button faces. Titles, notes and body text stay in sentence case.

## Layout

Single column in portrait: 16dp side margins, 8dp top, 16dp bottom, with stacked boxes separated by a 12dp gap. The Ride tab stacks a status line, the header box, and the roadbook roll, which takes all remaining height. In landscape the content splits into two columns (status and header at weight 1, roll at weight 1.15) with 16dp padding and gutter, and the bottom bar becomes a navigation rail separated by a heavy vertical rule. Insets follow `safeDrawing`, so the rail never sits under a cutout.

Spacing runs on an 8dp grid (4, 8, 16, 24, 32dp) with a 12dp box-inset half-step used for padding inside header cells, the gap between stacked boxes, and toggle-row vertical padding. Scrolling screens (Message, Settings) separate groups by 24dp, and a label sits 8dp above its box.

The roadbook roll is a fixed three-column grid: distance column (at least 96dp, widened to fit "8888.8" at the NOW size plus 22dp), tulip column (84dp), then the note column takes the rest. A 16dp distance-tick strip runs down the roll's left edge behind a heavy rule. NOW rows are at least 104dp tall, logged rows at least 88dp, and roll cells are padded 10dp.

Every tappable control is at least 56dp tall.

## Elevation & Depth

The system is flat: no shadows, no tonal elevation (the nav bar sets tonal elevation to 0, and every surface container role is Paper). Depth is conveyed by rule weight (a 2.5dp heavy frame around each box, 1.5dp light rules inside), by ink flood (a pressed block, a selected segment, the selected nav indicator), and by the highlighter wash on the live row.

### Named Rules
**The Ink-Flood Rule.** Pressing is shown by ink, not light: the face floods with ink and sinks 3dp inside its rule. Nothing lifts, glows or casts a shadow.

## Shapes

Every box, block, field, tag, segment, fuel cell and status mark is square-cornered (0dp); the Material shape scale is set to 0dp at every size. Boxes are paper inside a heavy ink rule. The only rounding is inside the drawn pictograms, which use the roadbook's own pen: 3dp round-capped strokes, a 5dp ink dot for "you are here", and a round-ended thermometer bulb, plus the Material switch thumb, which Material draws itself.

## Components

### Buttons (Press Blocks)
Keys on a bike, not floating pills.
- **Shape:** square (0dp), full width, 56dp tall, framed in the 2.5dp ink rule.
- **Highlight (the one action):** highlighter face, Ink label in Label Large upper-case. Used for Connect, Try again, Send to cluster.
- **Paper (everything else that presses):** paper face, Ink label. Used for Cancel and Bluetooth settings.
- **Pressed:** the face floods with Ink over 90ms and sinks 3dp inside the rule on a stiff spring (damping 0.6, stiffness 1800). The label turns highlighter (Highlight style) or paper (Paper style). Fires a virtual-key haptic. No ripple.
- **Disabled:** paper face, Faint Ink rule, Soft Ink label, and the reason written underneath in Body Small ("Connect to the bike to send a message.").
- **Text Action:** a quiet secondary move (Disconnect, Copy, Share, Clear): Label Medium upper-case Ink text, no frame, 56dp minimum height, 8dp side padding.

### Chips (Tags)
- **Style:** highlighter block, Ink Label Medium upper-case text, 6dp by 2dp padding, square.
- **State:** used only for live or warning status: NOW, STALE, LOW, RESERVE, and the marked fuel estimate ("≈ 10.0 L").

### Cards / Containers (Ink Boxes)
- **Corner Style:** square (0dp).
- **Background:** Paper; the NOW row alone uses Highlighter Wash.
- **Shadow Strategy:** none (see Elevation & Depth).
- **Border:** 2.5dp Ink outer rule; 1.5dp Ink rules between rows and columns.
- **Internal Padding:** 12dp in header cells, 10dp in roll cells, 16dp horizontal in list and toggle rows.

### Inputs / Fields
- **Style:** Material outlined field with a square shape, Paper fill, Ink border focused and unfocused, Title Medium input text, a floating label (Ink focused, Soft Ink at rest), and Soft Ink placeholder. The keyboard stays in caps because the cluster's font is upper-case.
- **Focus:** the border stays Ink; the cursor is highlighter.
- **Segmented choice:** a ruled ink box split by 2.5dp rules into 56dp segments; the chosen segment is inked solid with Paper text, others are Paper with Ink text.
- **Switch:** Material switch in ink: inked track with paper thumb when on, paper track with ink outline and ink thumb when off.

### Navigation
- **Style:** Material NavigationBar (portrait) or NavigationRail (landscape) on Paper, separated from content by a 2.5dp heavy rule, with zero tonal elevation.
- **Items:** four tabs (Ride, Message, Logs, Settings) with Label Medium upper-case labels and drawn 24dp icons in a 2dp round-capped stroke (no icon library).
- **States:** the selected tab has an Ink indicator with a highlighter icon and an Ink label; unselected tabs are Soft Ink. Back from any other tab returns to Ride.

### Header Box (signature)
A ruled two-column box. On the left: "SPEED · unit" label and the speed in Display Large. A heavy vertical rule separates the right column, which holds Engine (Headline Medium reading, unit, and a 40 to 120 °C ruled scale with nine marks, the 80 °C "warm" mark full height in Ink, and a 4dp highlighter needle that slides over 900ms) above a light rule, then Fuel ("n/6", a Tag, and six 12dp cells with 3dp gaps that fill Ink, or highlighter at two bars or fewer). Stale readings turn Soft Ink and add a STALE tag. The whole box is announced as one sentence.

### Roadbook Roll (signature)
- **Distance ticks:** a strip down the left edge with a tick every 12dp per 10 m of trip distance and a long 2.5dp Ink tick every 100 m (minor ticks are 1.5dp Soft Ink). It scrolls with the dead-reckoned trip distance, so it moves at riding speed.
- **NOW row:** the top row. Its distance rolls with dead reckoning between 5-second frames: inside the reported 0.1 km band it advances at the reported speed, it never runs backwards, and it follows a trip reset at once. Its tulip is live, and its title and note state one of: Offline, Connecting, Link failed, Linked (waiting for first frame), Riding, Stopped, or No data (stale). When offline, the row carries the full-width link press block (Connect, Cancel, or Try again).
- **Logged rows:** distance (Title Large, tabular), time label, tulip, title (Title Medium) and note (Body Small Soft Ink). Events: Start, Connected, Warmed up, Stopped, Riding again, Fuel down, Message sent, Link lost. New rows slide into place.
- **New-row swipe:** the newest row written in the last 4 seconds gets a highlighter swipe across its note cell (40% alpha, 520ms sweep), holds 1.4s, then dries over 900ms. Only the newest row swipes.
- **Empty roll:** a Body Small Soft Ink note: "Events appear here as you ride: start, warm-up, stops, fuel and messages."

### Tulips (signature)
64dp pictograms in a 3dp round-capped Ink stroke with a 5dp Ink dot for "you are here". The accent stroke is highlighter only on the live NOW row. The Moving tulip's shaft is a highlighter dash that flows upward at about one cycle per second at 40 km/h, scaled to speed; with animations off it is drawn as a static dash. Offline is an empty dot. Tulips are decorative to screen readers; the row title carries the meaning.

### Status Mark
A 14dp square with a 2dp Ink rule: filled highlighter when connected, the same square breathing between full and 25% opacity over 700ms while connecting, empty when offline, and an Ink square crossed in Paper on error. It sits beside an upper-case state label and, when connected, the data age ("UPDATED 2S AGO", or "NO UPDATE FOR …" in Ink once stale).

### Cluster Preview
The message as the cluster will show it: an Ink panel with highlighter Roboto Mono at 20sp and 2sp tracking, single line, "_" when empty.

## Do's and Don'ts

### Do:
- **Do** frame every reading or group in a square ink box with a 2.5dp outer rule and 1.5dp inner rules.
- **Do** keep the highlighter (#FF5A1F) for what is live right now: the one action, the NOW row, the needle, the selected tab, warning tags.
- **Do** put Ink text on highlighter fills, and pair every orange state with a shape or word change.
- **Do** make every tappable control at least 56dp tall.
- **Do** show presses as an ink flood plus a 3dp inset, with a haptic tick.
- **Do** drive every animation from real telemetry, and check `rememberMotionEnabled()` so it goes still when the animator scale is 0. Live data, such as the dead-reckoned trip distance, keeps updating.
- **Do** use tabular figures for any number that changes while riding.
- **Do** show missing data as "—" and stale data in Soft Ink with a STALE tag, never faded below legibility.

### Don't:
- **Don't** add a dark scheme, dynamic colour, or an off-white or grey surface; the white ground is a binding user brief.
- **Don't** add shadows, tonal elevation or glows; press blocks show no ripple, only the ink flood.
- **Don't** round the corners of boxes, blocks, fields, tags or segments. Pictograms keep their round caps and dots.
- **Don't** set highlighter text on paper (3.1:1), or white text on highlighter.
- **Don't** use Faint Ink (#D6D6D6) for any readable text.
- **Don't** animate anything that is not backed by telemetry or a direct press.
- **Don't** use a second accent hue or an icon library; icons are drawn in the tulip stroke.
