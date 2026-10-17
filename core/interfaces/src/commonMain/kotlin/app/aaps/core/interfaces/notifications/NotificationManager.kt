package app.aaps.core.interfaces.notifications

import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock

interface NotificationManager {

    val notifications: StateFlow<List<AapsNotification>>

    /** Remove expired and validity-failed notifications on demand. */
    fun cleanUp()

    fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel = id.defaultLevel,
        validMinutes: Int = 0,
        sound: AlarmSound? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
    ): NotificationHandle

    fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel = id.defaultLevel,
        date: Long = Clock.System.now().toEpochMilliseconds(),
        validTo: Long = 0L,
        sound: AlarmSound? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
    ): NotificationHandle

    /**
     * Posts a notification whose text is resolved when it is shown.
     *
     * Android callers keep writing `post(id, R.string.x, arg)`: that goes through the extension in
     * `NotificationManagerAndroid`, which wraps the id in a [TextRef.AndroidRes]. Keeping the id out of
     * this declaration is what lets the interface be platform neutral.
     */
    fun post(
        id: NotificationId,
        textRef: TextRef,
        level: NotificationLevel = id.defaultLevel,
        validMinutes: Int = 0,
        date: Long = Clock.System.now().toEpochMilliseconds(),
        validTo: Long = 0L,
        sound: AlarmSound? = null,
        actions: List<NotificationAction> = emptyList(),
        validityCheck: (() -> Boolean)? = null
    ): NotificationHandle

    /** Dismiss all instances of this notification type. */
    fun dismiss(id: NotificationId)

    /** Dismiss a specific instance by handle. */
    fun dismiss(handle: NotificationHandle)

    /**
     * Silence every currently audible alarm. Used by the global "mute all" entry points (Wear
     * snooze/mute gesture, full-screen acknowledge, app onTerminate). Stops both the
     * internal-notification (`AlarmSoundPlayer.OWNER_INTERNAL`) and full-screen
     * (`AlarmSoundPlayer.OWNER_FULLSCREEN`) audio and cancels their system notifications. The alarms
     * stay in the registry, marked [AapsNotification.muted]. For the mute snooze time
     * (`IntKey.AlertsMuteSnoozeTime`) the same alarms stay quiet on this device, also when they are
     * cleared and posted again; after it, one that is still listed sounds again. Only this device
     * is changed. Non-audible notifications are left untouched.
     */
    fun muteAllAlarms()

    companion object {

        /**
         * Do not change: Android keeps the user's settings of a channel (sound, importance, Do Not Disturb)
         * by its ID. A new ID is a new channel with default settings, and the old one stays in the system.
         */
        const val CHANNEL_ID = "AndroidAPS-Overview"
        const val DISMISS_ACTION = "app.aaps.plugins.main.general.overview.notifications.receivers.DismissNotificationReceiver"
    }
}
