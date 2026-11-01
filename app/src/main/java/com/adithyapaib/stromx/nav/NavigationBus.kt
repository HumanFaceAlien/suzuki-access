package com.adithyapaib.stromx.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hands the current Google Maps step from [MapsNotificationListener] to the app. */
object NavigationBus {
    private val _instruction = MutableStateFlow<NavInstruction?>(null)
    /** The step Maps is showing now; null when no navigation is running. */
    val instruction: StateFlow<NavInstruction?> = _instruction.asStateFlow()

    private val _lastFields = MutableStateFlow<MapsNotificationFields?>(null)
    /** Raw text of the latest Maps navigation notification, for the log. */
    val lastFields: StateFlow<MapsNotificationFields?> = _lastFields.asStateFlow()

    private val _listenerConnected = MutableStateFlow(false)
    /** True while Android has the notification listener bound (notification access granted). */
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    fun publish(fields: MapsNotificationFields, instruction: NavInstruction?) {
        _lastFields.value = fields
        // A notification we can't read (e.g. a transition frame) keeps the previous step.
        if (instruction != null) _instruction.value = instruction
    }

    fun clear() {
        _instruction.value = null
    }

    fun setListenerConnected(connected: Boolean) {
        _listenerConnected.value = connected
        if (!connected) clear()
    }
}
