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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.adithyapaib.sxv.settings.DistanceUnit
import com.adithyapaib.sxv.ui.components.ButtonVariant
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.PrimaryButton
import com.adithyapaib.sxv.ui.components.SegmentedToggle
import com.adithyapaib.sxv.ui.theme.Accent
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.OnAccent
import com.adithyapaib.sxv.ui.theme.OutlineStrong
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.SurfaceRaised
import com.adithyapaib.sxv.ui.theme.TextPrimary
import com.adithyapaib.sxv.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    unit: DistanceUnit,
    keepScreenOn: Boolean,
    onUnitChange: (DistanceUnit) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Units")
            SegmentedToggle(
                options = listOf("Kilometres", "Miles"),
                selectedIndex = unit.ordinal,
                onSelect = { onUnitChange(DistanceUnit.entries[it]) }
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Display")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouch)
                    .toggleable(value = keepScreenOn, role = Role.Switch, onValueChange = onKeepScreenOnChange),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Only while connected to the bike.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = OnAccent,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = TextPrimary,
                        uncheckedTrackColor = SurfaceRaised,
                        uncheckedBorderColor = OutlineStrong
                    )
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Bluetooth")
            Text(
                "SXV finds and connects to the cluster on its own. Open system settings to manage the bike's Bluetooth entry.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            PrimaryButton(
                text = "Open Bluetooth settings",
                variant = ButtonVariant.Outlined,
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

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 700)
@Composable
private fun SettingsPreview() = SXVTheme {
    SettingsScreen(DistanceUnit.KILOMETERS, true, {}, {})
}
