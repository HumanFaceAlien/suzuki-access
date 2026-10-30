package com.adithyapaib.sxv.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.ui.components.HRule
import com.adithyapaib.sxv.ui.components.VRule
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.Highlight
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.Paper
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
    val roadbook by viewModel.roadbook.collectAsStateWithLifecycle()
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

    // Selected tab: highlighter mark on an inked indicator, the same "live" signal the roll uses.
    val tabIcon: @Composable (AppTab, Boolean) -> Unit = { tab, selected ->
        TabIcon(tab, tint = if (selected) Highlight else InkSoft)
    }
    val tabLabel: @Composable (AppTab) -> Unit = { tab ->
        Text(tab.label.uppercase(), style = MaterialTheme.typography.labelMedium)
    }

    // Material navigation-bar behaviour: Back from another tab returns to Ride before leaving the app.
    BackHandler(enabled = selectedTab != 0) { selectedTab = 0 }

    Scaffold(
        containerColor = Paper,
        // safeDrawing adds the display cutout, so the landscape rail never sits under it.
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!landscape) {
                Column {
                    HRule(thickness = Dimens.RuleHeavy)
                    NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                        AppTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { tabIcon(tab, selectedTab == index) },
                                label = { tabLabel(tab) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Highlight,
                                    selectedTextColor = Ink,
                                    unselectedIconColor = InkSoft,
                                    unselectedTextColor = InkSoft,
                                    indicatorColor = Ink
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { inner ->
        Row(Modifier.padding(inner).consumeWindowInsets(inner)) {
            if (landscape) {
                NavigationRail(containerColor = Paper, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    AppTab.entries.forEachIndexed { index, tab ->
                        NavigationRailItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { tabIcon(tab, selectedTab == index) },
                            label = { tabLabel(tab) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Highlight,
                                selectedTextColor = Ink,
                                unselectedIconColor = InkSoft,
                                unselectedTextColor = InkSoft,
                                indicatorColor = Ink
                            )
                        )
                    }
                }
                VRule(thickness = Dimens.RuleHeavy)
            }
            Box(Modifier.weight(1f).fillMaxHeight().imePadding()) {
                when (AppTab.entries[selectedTab]) {
                    AppTab.Dashboard -> DashboardScreen(
                        connectionState = connectionState,
                        telemetry = telemetry,
                        lastUpdateMs = lastUpdateMs,
                        roadbook = roadbook,
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
                        onKeepScreenOnChange = settings::setKeepScreenOn,
                        navigationSection = {
                            com.adithyapaib.sxv.ui.screens.NavigationSection(
                                navigation = viewModel.navigation,
                                connected = connectionState == com.adithyapaib.sxv.ble.BleConnectionState.CONNECTED
                            )
                        }
                    )
                }
            }
        }
    }
}
