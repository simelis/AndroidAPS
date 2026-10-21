package app.aaps.core.interfaces.notifications

/**
 * A notification that is no longer listed, kept for a short time so the user can still read it after
 * a dismiss by mistake. Memory only: an app restart clears it, like the notifications themselves.
 */
data class NotificationHistoryEntry(
    val id: NotificationId,
    val text: String,
    val level: NotificationLevel,
    /** When it was posted, or last updated by a repeat. */
    val date: Long,
    /** When it left the list. */
    val removed: Long,
    val expired: Boolean
)
