package com.adithyapaib.sxv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.garage.FillUpPrompt
import com.adithyapaib.sxv.garage.FuelMath
import com.adithyapaib.sxv.garage.FuelRecord
import com.adithyapaib.sxv.settings.DistanceUnit
import com.adithyapaib.sxv.ui.components.BlockStyle
import com.adithyapaib.sxv.ui.components.ChartPoint
import com.adithyapaib.sxv.ui.components.HRule
import com.adithyapaib.sxv.ui.components.InkField
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.PressBlock
import com.adithyapaib.sxv.ui.components.RoadColumnChart
import com.adithyapaib.sxv.ui.components.RoadLineChart
import com.adithyapaib.sxv.ui.components.Tag
import com.adithyapaib.sxv.ui.components.TextAction
import com.adithyapaib.sxv.ui.components.VRule
import com.adithyapaib.sxv.ui.components.inkBox
import com.adithyapaib.sxv.ui.formatRupees
import com.adithyapaib.sxv.ui.formatRupeesCompact
import com.adithyapaib.sxv.ui.formatWhole
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.HighlightWash
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.inkSwitchColors
import java.text.SimpleDateFormat
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

private val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())

/** The fuel log: what you put in, what it cost, and what it got you. */
@Composable
fun FuelScreen(
    records: List<FuelRecord>,
    odometerKm: Int?,
    unit: DistanceUnit,
    prompt: FillUpPrompt?,
    onAdd: (litres: Double, cost: Double, odometerKm: Int, fullTank: Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = remember(records) { System.currentTimeMillis() }
    val summary = remember(records) { FuelMath.summary(records, now) }
    val segments = remember(records) { FuelMath.segments(records).sortedBy { it.endAtMs } }
    val monthly = remember(records) { FuelMath.monthlySpend(records, now, months = 6) }
    val segmentByRecord = remember(segments) { segments.associateBy { it.endRecordId } }
    var formOpen by rememberSaveable { mutableStateOf(false) }
    var selectedRecord by rememberSaveable { mutableStateOf<Long?>(null) }
    val perUnit = "${unit.distance}/L"

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text("Fuel", color = Ink, style = MaterialTheme.typography.headlineSmall)

        if (prompt != null && !formOpen) {
            Column(
                Modifier.fillMaxWidth().inkBox(fill = HighlightWash).padding(Spacing.M),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                Tag("Filled up?")
                Text(
                    "Fuel rose to ${prompt.fuelBars}/6 at ${formatWhole(prompt.odometerKm)} km. Log what you put in.",
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PressBlock("Log it", { formOpen = true }, Modifier.weight(1f))
                    Spacer(Modifier.width(Spacing.S))
                    TextAction("Not now", onDismissPrompt)
                }
            }
        }

        if (formOpen) {
            FillUpForm(
                suggestedOdometer = prompt?.odometerKm ?: odometerKm,
                lastOdometer = records.maxOfOrNull { it.odometerKm },
                onSave = { l, c, o, f ->
                    onAdd(l, c, o, f)
                    formOpen = false
                },
                onCancel = { formOpen = false }
            )
        } else {
            PressBlock("Log fill-up", { formOpen = true })
        }

        // Summary: four readings in one ruled box.
        Column(Modifier.fillMaxWidth().inkBox()) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 84.dp)) {
                SummaryCell(
                    "Economy",
                    summary.avgKmPerLitre?.let { String.format(Locale.getDefault(), "%.1f", it * unit.perKm) } ?: "—",
                    perUnit,
                    Modifier.weight(1f)
                )
                VRule()
                SummaryCell(
                    "Cost per ${unit.distance}",
                    summary.avgCostPerKm?.let { formatRupees(it / unit.perKm, 2) } ?: "—",
                    "",
                    Modifier.weight(1f)
                )
            }
            HRule()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 84.dp)) {
                SummaryCell("This month", formatRupees(summary.thisMonthCost), "", Modifier.weight(1f))
                VRule()
                SummaryCell(
                    "Avg price",
                    summary.avgPricePerLitre?.let { formatRupees(it, 2) } ?: "—",
                    "/L",
                    Modifier.weight(1f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Economy per full tank · $perUnit")
            Column(Modifier.fillMaxWidth().inkBox().padding(Spacing.M - 4.dp)) {
                if (segments.isEmpty()) {
                    Text(
                        "Log two full-tank fill-ups to measure economy. Partial top-ups between them count too.",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    val points = segments.map {
                        val value = it.kmPerLitre * unit.perKm
                        ChartPoint(
                            dayFormat.format(Date(it.endAtMs)),
                            value,
                            String.format(Locale.getDefault(), "%.1f %s over %s %s", value, perUnit, formatWhole((it.distanceKm * unit.perKm).toInt()), unit.distance)
                        )
                    }
                    RoadLineChart(
                        points,
                        axisFormat = { String.format(Locale.getDefault(), "%.0f", it) },
                        description = "Economy per full tank, ${points.size} measurements. " +
                            "Latest ${points.last().readout}. Average ${String.format(Locale.getDefault(), "%.1f", (summary.avgKmPerLitre ?: 0.0) * unit.perKm)} $perUnit."
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Spend per month")
            Column(Modifier.fillMaxWidth().inkBox().padding(Spacing.M - 4.dp)) {
                val points = monthly.map {
                    ChartPoint(
                        it.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        it.cost,
                        formatRupees(it.cost)
                    )
                }
                RoadColumnChart(
                    points,
                    highlightIndex = points.lastIndex,
                    axisFormat = ::formatRupeesCompact,
                    description = "Fuel spend for the last six months: " +
                        points.joinToString { "${it.label} ${it.readout}" } + "."
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("Fill-ups", Modifier.weight(1f))
                Label("${records.size} logged · ${formatRupees(summary.totalCost)}")
            }
            if (records.isEmpty()) {
                Text(
                    "No fill-ups yet. Log one each time you fill the tank.",
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Column(Modifier.fillMaxWidth().inkBox()) {
                    records.forEachIndexed { index, record ->
                        if (index > 0) HRule()
                        FillUpRow(
                            record,
                            economy = segmentByRecord[record.id]?.let { String.format(Locale.getDefault(), "%.1f %s", it.kmPerLitre * unit.perKm, perUnit) },
                            selected = selectedRecord == record.id,
                            onSelect = { selectedRecord = if (selectedRecord == record.id) null else record.id },
                            onDelete = {
                                onDelete(record.id)
                                selectedRecord = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, suffix: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.M - 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Row(verticalAlignment = Alignment.Bottom) {
            // Standalone figures use proportional numerals; tabular ones are for columns.
            Text(value, color = Ink, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "pnum"), maxLines = 1)
            if (suffix.isNotEmpty()) {
                Text(" $suffix", Modifier.padding(bottom = 5.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FillUpRow(record: FuelRecord, economy: String?, selected: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) HighlightWash else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = if (selected) "Hide actions" else "Show actions", onClick = onSelect)
            .heightIn(min = Dimens.MinTouch)
            .padding(horizontal = Spacing.M, vertical = Spacing.S + 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${dayFormat.format(Date(record.atMs))} · ${formatWhole(record.odometerKm)} km",
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    buildString {
                        append(String.format(Locale.getDefault(), "%.2f L at %s/L", record.litres, formatRupees(record.pricePerLitre, 2)))
                        if (!record.fullTank) append(" · top-up")
                        if (economy != null) append(" · $economy")
                    },
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(formatRupees(record.cost), color = Ink, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"))
        }
        if (selected) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Delete fill-up", onDelete)
            }
        }
    }
}

@Composable
private fun FillUpForm(
    suggestedOdometer: Int?,
    lastOdometer: Int?,
    onSave: (Double, Double, Int, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var litres by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var odometer by rememberSaveable { mutableStateOf(suggestedOdometer?.toString().orEmpty()) }
    var fullTank by rememberSaveable { mutableStateOf(true) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val l = litres.replace(',', '.').toDoubleOrNull()
    val c = cost.replace(',', '.').toDoubleOrNull()
    val o = odometer.filter { it.isDigit() }.toIntOrNull()
    val litresError = when {
        l == null -> "Enter the litres you put in."
        l <= 0 || l > 50 -> "Litres must be between 0 and 50."
        else -> null
    }
    val costError = when {
        c == null -> "Enter the total you paid."
        c <= 0 -> "Cost must be more than zero."
        else -> null
    }
    val odometerError = when {
        o == null -> "Enter the odometer reading."
        lastOdometer != null && o < lastOdometer -> "That's below your last fill-up at ${formatWhole(lastOdometer)} km."
        else -> null
    }
    val valid = litresError == null && costError == null && odometerError == null

    Column(
        Modifier.fillMaxWidth().inkBox().padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.M - 4.dp)
    ) {
        Text("Log fill-up", color = Ink, style = MaterialTheme.typography.titleLarge)
        InkField(
            litres, { litres = it.take(6) }, label = "Litres", hint = "8.40",
            keyboardType = KeyboardType.Decimal, capitalization = KeyboardCapitalization.None,
            error = if (tried) litresError else null
        )
        InkField(
            cost, { cost = it.take(8) }, label = "Total cost (₹)", hint = "880",
            keyboardType = KeyboardType.Decimal, capitalization = KeyboardCapitalization.None,
            error = if (tried) costError else null
        )
        InkField(
            odometer, { odometer = it.take(7) }, label = "Odometer (km)", hint = "12336",
            keyboardType = KeyboardType.Number, capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
            error = if (tried) odometerError else null
        )
        Text(
            if (l != null && c != null && l > 0) "${formatRupees(c / l, 2)} per litre" else "Price per litre appears here.",
            color = InkSoft,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.MinTouch)
                .toggleable(value = fullTank, role = Role.Switch, onValueChange = { fullTank = it }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Filled to the brim", color = Ink, style = MaterialTheme.typography.titleMedium)
                Text("Economy is measured from one full tank to the next.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = fullTank, onCheckedChange = null, colors = inkSwitchColors())
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PressBlock(
                "Save",
                {
                    tried = true
                    if (valid) onSave(l!!, c!!, o!!, fullTank)
                },
                Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.S))
            PressBlock("Cancel", onCancel, Modifier.weight(1f), style = BlockStyle.Paper)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 1400)
@Composable
private fun FuelPreview() = SXVTheme {
    val day = 86_400_000L
    val t = System.currentTimeMillis()
    FuelScreen(
        records = listOf(
            FuelRecord(5, t - 2 * day, 12_336, 9.1, 960.0, true),
            FuelRecord(4, t - 20 * day, 11_980, 9.4, 990.0, true),
            FuelRecord(3, t - 45 * day, 11_610, 4.0, 420.0, false),
            FuelRecord(2, t - 60 * day, 11_400, 9.8, 1_020.0, true),
            FuelRecord(1, t - 95 * day, 11_020, 10.2, 1_050.0, true)
        ),
        odometerKm = 12_402,
        unit = DistanceUnit.KILOMETERS,
        prompt = FillUpPrompt(12_402, 6, t),
        onAdd = { _, _, _, _ -> },
        onDelete = {},
        onDismissPrompt = {}
    )
}
