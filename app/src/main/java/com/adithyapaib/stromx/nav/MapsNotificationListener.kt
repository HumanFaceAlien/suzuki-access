package com.adithyapaib.stromx.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Watches Google Maps' ongoing turn-by-turn notification and publishes each
 * step on [NavigationBus]. Needs "Notification access" for StromX, granted once in
 * Android settings (Settings tab → Google Maps navigation).
 */
class MapsNotificationListener : NotificationListenerService() {

    private var activeKey: String? = null

    override fun onListenerConnected() {
        NavigationBus.setListenerConnected(true)
        // Navigation may already be running when access is granted or the app starts.
        activeNotifications.orEmpty()
            .filter(::isMapsNavigation)
            .maxByOrNull { it.postTime }
            ?.let(::handle)
    }

    override fun onListenerDisconnected() {
        activeKey = null
        NavigationBus.setListenerConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isMapsNavigation(sbn)) handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PACKAGE && sbn.key == activeKey) {
            activeKey = null
            NavigationBus.clear()
        }
    }

    private fun isMapsNavigation(sbn: StatusBarNotification): Boolean =
        sbn.packageName == MAPS_PACKAGE &&
            (sbn.notification.category == Notification.CATEGORY_NAVIGATION || sbn.isOngoing)

    private fun handle(sbn: StatusBarNotification) {
        val fields = extract(sbn.notification)
        val parsed = GoogleMapsNotificationParser.parse(fields)
        // Other ongoing Maps notifications (location sharing, downloads) don't parse; ignore them.
        if (parsed == null && sbn.key != activeKey) return
        if (parsed != null) activeKey = sbn.key
        NavigationBus.publish(fields, parsed)
    }

    private fun extract(notification: Notification): MapsNotificationFields {
        val extras = notification.extras
        val fields = MapsNotificationFields(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        )
        return if (fields.title.isNullOrBlank() && fields.text.isNullOrBlank()) {
            fields.copy(views = readCustomLayout(notification))
        } else {
            fields
        }
    }

    /** Older Maps versions leave the extras empty and draw everything in a custom layout. */
    private fun readCustomLayout(notification: Notification): Map<String, String> = try {
        val builder = Notification.Builder.recoverBuilder(this, notification)
        val remote = builder.createBigContentView() ?: builder.createContentView()
        if (remote == null) {
            emptyMap()
        } else {
            val root = remote.apply(this, FrameLayout(this))
            mutableMapOf<String, String>().also { collectText(root, it) }
        }
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun collectText(view: View, into: MutableMap<String, String>) {
        if (view is TextView && view.id != View.NO_ID) {
            val name = runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
            val text = view.text?.toString()
            if (name != null && !text.isNullOrBlank()) into[name] = text
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) collectText(view.getChildAt(index), into)
        }
    }

    private companion object {
        const val MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
