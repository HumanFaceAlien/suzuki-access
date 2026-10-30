package com.adithyapaib.sxv.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adithyapaib.sxv.garage.DueState
import com.adithyapaib.sxv.garage.MaintenanceItem
import com.adithyapaib.sxv.garage.MaintenanceMath
import com.adithyapaib.sxv.garage.MaintenancePresets
import com.adithyapaib.sxv.garage.MaintenanceStatus
import com.adithyapaib.sxv.garage.ServiceNotifier
import com.adithyapaib.sxv.ui.components.BlockStyle
import com.adithyapaib.sxv.ui.components.InkField
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.PressBlock
import com.adithyapaib.sxv.ui.components.Tag
import com.adithyapaib.sxv.ui.components.TextAction
import com.adithyapaib.sxv.ui.components.inkBox
import com.adithyapaib.sxv.ui.formatWhole
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.Highlight
import com.adithyapaib.sxv.ui.theme.HighlightWash
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.Paper
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val doneFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

/** Service reminders counted down from the bike's own odometer and the calendar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceScreen(
    statuses: List<MaintenanceStatus>,
    odometerKm: Int?,
    connected: Boolean,
    onAdd: (name: String, intervalKm: Int?, intervalDays: Int?, lastDoneKm: Int?, lastDoneAtMs: Long?) -> Unit,
    onMarkDone: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<Draft?>(null) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun ensureNotificationPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Service", color = Ink, style = MaterialTheme.typography.headlineSmall)
            Text(
                when {
                    odometerKm == null -> "Connect once so SXV can read your odometer."
                    connected -> "Odometer ${formatWhole(odometerKm)} km, live from the bike."
                    else -> "Odometer ${formatWhole(odometerKm)} km, last read from the bike."
                },
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall
            )
        }

        val current = draft
        if (current != null) {
            ServiceForm(
                initial = current,
                odometerKm = odometerKm,
                onSave = { name, km, days, lastKm ->
                    onAdd(name, km, days, lastKm, System.currentTimeMillis())
                    if (statuses.isEmpty()) ensureNotificationPermission()
                    draft = null
                },
                onCancel = { draft = null }
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Label("Add a reminder")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    MaintenancePresets.forEach { preset ->
                        Box(
                            Modifier
                                .inkBox()
                                .heightIn(min = Dimens.MinTouch)
                                .clickable(role = Role.Button) {
                                    draft = Draft(preset.name, preset.intervalKm?.toString().orEmpty(), preset.intervalDays?.toString().orEmpty())
                                }
                                .padding(horizontal = Spacing.M),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(preset.name, color = Ink, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                PressBlock("Custom reminder", { draft = Draft("", "", "") }, style = BlockStyle.Paper)
            }
        }

        if (statuses.isEmpty()) {
            Text(
                "Nothing tracked yet. Add what you service, like oil, chain and brakes. SXV counts down from the bike's own odometer and reminds you when it's due.",
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M - 4.dp)) {
                statuses.forEach { status ->
                    ServiceCard(
                        status = status,
                        odometerKm = odometerKm,
                        expanded = expanded == status.item.id,
                        onToggle = { expanded = if (expanded == status.item.id) null else status.item.id },
                        onMarkDone = { onMarkDone(status.item.id) },
                        onDelete = {
                            onDelete(status.item.id)
                            expanded = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(
    status: MaintenanceStatus,
    odometerKm: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit
) {
    val item = status.item
    val urgent = status.state == DueState.Due || status.state == DueState.Soon
    val interval = listOfNotNull(
        item.intervalKm?.let { "every ${formatWhole(it)} km" },
        item.intervalDays?.let { "every $it days" }
    ).joinToString(" or ")
    Column(
        Modifier
            .fillMaxWidth()
            .inkBox(fill = if (status.state == DueState.Due) HighlightWash else Paper)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide actions" else "Show actions", onClick = onToggle)
            .padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleLarge)
            when (status.state) {
                DueState.Due -> Tag("Due")
                DueState.Soon -> Tag("Soon")
                else -> Unit
            }
        }
        ServiceMeter(status.used, urgent, Modifier.semantics {
            contentDescription = "${(status.used.coerceAtMost(9.99f) * 100).toInt()} percent of the interval used"
        })
        Text(ServiceNotifier.describe(status), color = Ink, style = MaterialTheme.typography.bodyMedium)
        Text(
            buildString {
                append(interval.replaceFirstChar { it.uppercase() })
                if (item.lastDoneKm != null || item.lastDoneAtMs != null) {
                    append(" · last done")
                    item.lastDoneKm?.let { append(" at ${formatWhole(it)} km") }
                    item.lastDoneAtMs?.let { append(" on ${doneFormat.format(Date(it))}") }
                }
            },
            color = InkSoft,
            style = MaterialTheme.typography.bodySmall
        )
        if (expanded) {
            PressBlock(
                text = if (odometerKm != null) "Done at ${formatWhole(odometerKm)} km" else "Done today",
                onClick = onMarkDone,
                style = if (urgent) BlockStyle.Highlight else BlockStyle.Paper
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Delete reminder", onDelete)
            }
        }
    }
}

/** A ruled meter: ink fill toward the due point, highlighter once it's due soon or overdue. */
@Composable
private fun ServiceMeter(used: Float, urgent: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val rule = 1.5.dp.toPx()
        val fill = used.coerceIn(0f, 1f)
        drawRect(if (urgent) Highlight else Ink, size = Size(size.width * fill, size.height))
        drawRect(Ink, style = Stroke(rule))
        // Quarter marks, like a roadbook scale.
        for (i in 1..3) {
            val x = size.width * i / 4f
            drawLine(if (x < size.width * fill) Paper else InkSoft, Offset(x, size.height * 0.5f), Offset(x, size.height), rule)
        }
    }
}

/** What the add form starts with; numbers kept as text so the fields keep exactly what was typed. */
data class Draft(val name: String, val intervalKm: String, val intervalDays: String)

private val DraftSaver = androidx.compose.runtime.saveable.Saver<Draft?, List<String>>(
    save = { it?.let { d -> listOf(d.name, d.intervalKm, d.intervalDays) } ?: emptyList() },
    restore = { if (it.size == 3) Draft(it[0], it[1], it[2]) else null }
)

@Composable
private fun ServiceForm(
    initial: Draft,
    odometerKm: Int?,
    onSave: (name: String, intervalKm: Int?, intervalDays: Int?, lastDoneKm: Int?) -> Unit,
    onCancel: () -> Unit
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial.name) }
    var km by rememberSaveable(initial) { mutableStateOf(initial.intervalKm) }
    var days by rememberSaveable(initial) { mutableStateOf(initial.intervalDays) }
    var lastDone by rememberSaveable(initial) { mutableStateOf(odometerKm?.toString().orEmpty()) }
    var tried by rememberSaveable { mutableStateOf(false) }

    val kmValue = km.filter { it.isDigit() }.toIntOrNull()
    val daysValue = days.filter { it.isDigit() }.toIntOrNull()
    val lastValue = lastDone.filter { it.isDigit() }.toIntOrNull()
    val nameError = if (name.isBlank()) "Name what you're tracking." else null
    val intervalError = when {
        kmValue == null && daysValue == null -> "Set a distance, a number of days, or both."
        kmValue != null && kmValue <= 0 -> "Distance must be more than zero."
        daysValue != null && daysValue <= 0 -> "Days must be more than zero."
        else -> null
    }
    val lastError = when {
        kmValue != null && lastValue == null -> "Enter the odometer when it was last done."
        lastValue != null && odometerKm != null && lastValue > odometerKm -> "That's past the bike's odometer (${formatWhole(odometerKm)} km)."
        else -> null
    }
    val valid = nameError == null && intervalError == null && lastError == null

    Column(
        Modifier.fillMaxWidth().inkBox().padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.M - 4.dp)
    ) {
        Text(if (initial.name.isEmpty()) "Custom reminder" else "New reminder", color = Ink, style = MaterialTheme.typography.titleLarge)
        InkField(
            name, { name = it.take(40) }, label = "What", hint = "Engine oil and filter",
            capitalization = KeyboardCapitalization.Sentences,
            error = if (tried) nameError else null
        )
        Row(verticalAlignment = Alignment.Top) {
            InkField(
                km, { km = it.take(6) }, label = "Every km", hint = "3000",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number, capitalization = KeyboardCapitalization.None,
                error = if (tried) intervalError else null
            )
            Spacer(Modifier.width(Spacing.S))
            InkField(
                days, { days = it.take(4) }, label = "Every days", hint = "180",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number, capitalization = KeyboardCapitalization.None
            )
        }
        InkField(
            lastDone, { lastDone = it.take(7) }, label = "Last done at (km)", hint = "12336",
            keyboardType = KeyboardType.Number, capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Done,
            error = if (tried) lastError else null
        )
        Text(
            "Typical intervals are pre-filled as a starting point. Set the real ones from your service book; the day count starts today.",
            color = InkSoft,
            style = MaterialTheme.typography.bodySmall
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            PressBlock(
                "Save",
                {
                    tried = true
                    if (valid) onSave(name, kmValue, daysValue, lastValue)
                },
                Modifier.weight(1f)
            )
            Spacer(Modifier.width(Spacing.S))
            PressBlock("Cancel", onCancel, Modifier.weight(1f), style = BlockStyle.Paper)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 1100)
@Composable
private fun ServicePreview() = SXVTheme {
    val day = 86_400_000L
    val now = System.currentTimeMillis()
    val items = listOf(
        MaintenanceItem(1, "Engine oil and filter", 3_000, 180, 9_400, now - 120 * day),
        MaintenanceItem(2, "Chain clean and lube", 500, null, 12_100, now - 9 * day),
        MaintenanceItem(3, "Brake fluid", null, 730, null, now - 100 * day)
    )
    ServiceScreen(
        statuses = items.map { MaintenanceMath.status(it, 12_336, now) },
        odometerKm = 12_336,
        connected = true,
        onAdd = { _, _, _, _, _ -> },
        onMarkDone = {},
        onDelete = {}
    )
}
