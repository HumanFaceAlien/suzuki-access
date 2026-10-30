package com.adithyapaib.sxv.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ble.BleViewModel
import com.adithyapaib.sxv.settings.SettingsViewModel
import com.adithyapaib.sxv.ui.components.AppTab
import com.adithyapaib.sxv.ui.components.TabIcon
import com.adithyapaib.sxv.ui.screens.DashboardScreen
import com.adithyapaib.sxv.ui.screens.LogsScreen
import com.adithyapaib.sxv.ui.screens.MessageScreen
import com.adithyapaib.sxv.ui.screens.SettingsScreen
import com.adithyapaib.sxv.ui.theme.Bg
import com.adithyapaib.sxv.ui.theme.SurfaceCard
import com.adithyapaib.sxv.ui.theme.SurfaceRaised
import com.adithyapaib.sxv.ui.theme.TextPrimary
import com.adithyapaib.sxv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SxvApp(viewModel: BleViewModel, settings: SettingsViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val lastUpdateMs by viewModel.lastTelemetryAtMs.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val verbose by viewModel.verbose.collectAsStateWithLifecycle()
    val displayInput by viewModel.displayInput.collectAsStateWithLifecycle()
    val displaySecondaryInput by viewModel.displaySecondaryInput.collectAsStateWithLifecycle()
    val isSending by viewModel.isSendingDisplayText.collectAsStateWithLifecycle()
    val unit by settings.unit.collectAsStateWithLifecycle()
    val keepScreenOn by settings.keepScreenOn.collectAsStateWithLifecycle()
    val recentMessages by settings.recentMessages.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Errors used to be emitted but never shown.
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(message) }
        }
    }

    // Haptic confirmation (or rejection) for each display write, and remember what was sent.
    LaunchedEffect(viewModel) {
        viewModel.sendResults.collect { result ->
            if (result.success) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                settings.addRecentMessage(result.message)
            } else {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
            }
        }
    }

    // A buzz when the link drops, since the rider may not be looking at the screen.
    var previousState by remember { mutableStateOf(connectionState) }
    LaunchedEffect(connectionState) {
        if (previousState == BleConnectionState.CONNECTED && connectionState != BleConnectionState.CONNECTED) {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        }
        previousState = connectionState
    }

    val holdScreen = keepScreenOn && connectionState == BleConnectionState.CONNECTED
    DisposableEffect(holdScreen) {
        view.keepScreenOn = holdScreen
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!landscape) {
                NavigationBar(containerColor = SurfaceCard) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { TabIcon(tab, tint = if (selectedTab == index) TextPrimary else TextSecondary) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = SurfaceRaised
                            )
                        )
                    }
                }
            }
        }
    ) { inner ->
        Row(Modifier.padding(inner).consumeWindowInsets(inner)) {
            if (landscape) {
                NavigationRail(containerColor = SurfaceCard, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { TabIcon(tab, tint = if (selectedTab == index) TextPrimary else TextSecondary) },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = SurfaceRaised
                            )
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight().imePadding()) {
                when (AppTab.entries[selectedTab]) {
                    AppTab.Dashboard -> DashboardScreen(
                        connectionState = connectionState,
                        telemetry = telemetry,
                        lastUpdateMs = lastUpdateMs,
                        unit = unit,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect
                    )
                    AppTab.Message -> MessageScreen(
                        connectionState = connectionState,
                        primary = displayInput,
                        secondary = displaySecondaryInput,
                        isSending = isSending,
                        recentMessages = recentMessages,
                        onPrimaryChange = viewModel::updateDisplayInput,
                        onSecondaryChange = viewModel::updateDisplaySecondaryInput,
                        onSend = viewModel::sendDisplayText,
                        onClearRecent = settings::clearRecentMessages
                    )
                    AppTab.Logs -> LogsScreen(
                        connectionState = connectionState,
                        lastUpdateMs = lastUpdateMs,
                        logs = logs,
                        verbose = verbose,
                        onVerboseChange = viewModel::setVerbose,
                        onClearLogs = viewModel::clearLogs
                    )
                    AppTab.Settings -> SettingsScreen(
                        unit = unit,
                        keepScreenOn = keepScreenOn,
                        onUnitChange = settings::setUnit,
                        onKeepScreenOnChange = settings::setKeepScreenOn
                    )
                }
            }
        }
    }
}
