package app.aaps.core.interfaces.notifications

import kotlin.time.Clock

data class AapsNotification(
    val id: NotificationId,
    val instanceKey: Int,
    val text: String,
    val level: NotificationLevel,
    val date: Long = Clock.System.now().toEpochMilliseconds(),
    val validTo: Long = 0L,
    val sound: AlarmSound? = null,
    val actions: List<NotificationAction> = emptyList(),
    val validityCheck: (() -> Boolean)? = null,
    /** An alarm silenced by "mute all". It stays in the list, quiet, until it is dismissed or expires. */
    val muted: Boolean = false
)
