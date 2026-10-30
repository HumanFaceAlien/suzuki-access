package com.adithyapaib.sxv.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.settings.DistanceUnit
import com.adithyapaib.sxv.ui.components.BlockStyle
import com.adithyapaib.sxv.ui.components.InkSegmented
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.PressBlock
import com.adithyapaib.sxv.ui.components.inkBox
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.inkSwitchColors

@Composable
fun SettingsScreen(
    unit: DistanceUnit,
    keepScreenOn: Boolean,
    onUnitChange: (DistanceUnit) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Google Maps navigation controls (see [NavigationSection]). */
    navigationSection: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text("Settings", color = Ink, style = MaterialTheme.typography.headlineSmall)

        navigationSection()

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Units")
            InkSegmented(
                options = listOf("km · °C", "mi · °F"),
                selectedIndex = unit.ordinal,
                onSelect = { onUnitChange(DistanceUnit.entries[it]) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Display")
            Row(
                Modifier
                    .fillMaxWidth()
                    .inkBox()
                    .heightIn(min = Dimens.MinTouch)
                    .toggleable(value = keepScreenOn, role = Role.Switch, onValueChange = onKeepScreenOnChange)
                    .padding(horizontal = Spacing.M, vertical = Spacing.M - 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on", color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text("Only while connected to the bike.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = keepScreenOn, onCheckedChange = null, colors = inkSwitchColors())
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Bluetooth")
            Text(
                "SXV finds and connects to the cluster on its own. Open system settings to manage the bike's Bluetooth entry.",
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall
            )
            PressBlock(
                text = "Bluetooth settings",
                style = BlockStyle.Paper,
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        // No settings handler on this device; nothing useful to do.
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 700)
@Composable
private fun SettingsPreview() = SXVTheme {
    SettingsScreen(DistanceUnit.KILOMETERS, true, {}, {})
}
