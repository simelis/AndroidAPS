package app.aaps.implementation.notifications

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * The notification registry, with nothing platform specific in it.
 *
 * This is the half of notification handling that is the same everywhere: which notifications are
 * live, which replaces which, when they expire, and which alarm owns the sound. The system tray and
 * the audio are reached through [SystemNotificationPlatform].
 *
 * Both platforms use this now. `NotificationManagerImpl` is gone: Android keeps its channel,
 * receiver and `NotificationCompat` code in `AndroidSystemNotificationPlatform`, and iOS in
 * `IosSystemNotificationPlatform`. There is one registry.
 */
class CommonNotificationManager(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val platform: SystemNotificationPlatform,
    scope: CoroutineScope,
    /** How long [muteAllAlarms] keeps the muted alarms quiet, in minutes. */
    private val muteSnoozeMinutes: () -> Int,
    private val clock: Clock = Clock.System
) : NotificationManager {

    private val _notifications = MutableStateFlow<List<AapsNotification>>(emptyList())
    override val notifications: StateFlow<List<AapsNotification>> = _notifications.asStateFlow()

    /**
     * Guards every read-modify-write of [_notifications].
     *
     * The Android class says this with `@Synchronized`, which does not exist in common code. The
     * lock is not reentrant, so private helpers state in their own docs that the caller must hold
     * it - they never take it themselves.
     */
    private val lock = AapsLock()

    /** instanceKey of the URGENT alarm currently owning the audio, or null when silent. */
    private var soundingKey: Int? = null

    /** Only ever touched under [lock], so a plain Int is enough. */
    private var nextInstanceKey = 10_000

    /**
     * Alarms muted by [muteAllAlarms], and until when they stay quiet. Kept apart from the list,
     * because an alarm can be cleared and posted again while muted: a Nightscout alarm is cleared by
     * the next BG and sent again by Nightscout. Only touched under [lock].
     */
    private val snoozedUntil = mutableMapOf<NotificationId, Long>()

    init {
        platform.onDismissed { instanceKey -> dismiss(NotificationHandle(instanceKey)) }

        // Periodic cleanup for expiration when no new posts arrive.
        scope.launch {
            while (true) {
                delay(30_000L)
                cleanUp()
            }
        }
    }

    override fun cleanUp() = lock.withLock {
        removeExpired()
        endSnoozes()
    }

    override fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        validMinutes: Int,
        sound: AlarmSound?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle {
        val now = now()
        return postInternal(
            id = id, text = text, level = level,
            date = now,
            validTo = if (validMinutes > 0) now + validMinutes.toLong().minutes.inWholeMilliseconds else 0L,
            sound = sound, actions = actions, validityCheck = validityCheck
        )
    }

    override fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        date: Long,
        validTo: Long,
        sound: AlarmSound?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle =
        postInternal(
            id = id, text = text, level = level,
            date = date, validTo = validTo,
            sound = sound, actions = actions, validityCheck = validityCheck
        )

    override fun post(
        id: NotificationId,
        textRef: TextRef,
        level: NotificationLevel,
        validMinutes: Int,
        date: Long,
        validTo: Long,
        sound: AlarmSound?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle =
        postInternal(
            id = id, text = rh.gs(textRef), level = level,
            date = date,
            validTo = if (validMinutes > 0) date + validMinutes.toLong().minutes.inWholeMilliseconds else validTo,
            sound = sound, actions = actions, validityCheck = validityCheck
        )

    private fun postInternal(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        date: Long,
        validTo: Long,
        sound: AlarmSound?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle = lock.withLock {
        // Clean up expired notifications piggyback on writes.
        removeExpired()
        endSnoozes()

        val current = _notifications.value.toMutableList()

        // A repeat of a listed alarm only updates it: no new system notification or sound. A muted one stays muted
        // until its snooze ends (endSnoozes above).
        val listed = if (id.allowMultiple) null else current.firstOrNull { it.id == id }
        if (listed != null && listed.isAudibleAlarm && level == NotificationLevel.URGENT && sound != null) {
            val updated = listed.copy(
                text = text, date = date, validTo = validTo, sound = sound, actions = actions, validityCheck = validityCheck
            )
            _notifications.value = current.map { if (it === listed) updated else it }
            refreshAlarmSound()
            aapsLogger.debug(LTag.NOTIFICATION, "Alarm repeated${if (listed.muted) " (muted)" else ""}: [${id.name}] $text")
            return@withLock NotificationHandle(listed.instanceKey)
        }

        val instanceKey: Int
        if (id.allowMultiple) {
            instanceKey = nextInstanceKey++
        } else {
            instanceKey = id.ordinal
            // Cancel just the replaced notification's own sound, not any other concurrent alarm.
            current.filter { it.id == id }.forEach { cancelSystemNotification(it) }
            current.removeAll { it.id == id }
        }

        val notification = AapsNotification(
            id = id,
            instanceKey = instanceKey,
            text = text,
            level = level,
            date = date,
            validTo = validTo,
            sound = sound,
            actions = actions,
            validityCheck = validityCheck
        )
        // An alarm that comes again while snoozed is listed, but stays quiet until the snooze ends
        val snoozed = notification.isAudibleAlarm && isSnoozed(id)
        current.add(if (snoozed) notification.copy(muted = true) else notification)
        current.sortBy { it.level.priority }
        _notifications.value = current

        // The platform gets the whole notification and decides what, if anything, to show.
        if (!snoozed) showOnPlatform(notification)
        refreshAlarmSound()

        aapsLogger.debug(LTag.NOTIFICATION, "Notification posted${if (snoozed) " (snoozed)" else ""}: [${id.name}] $text")
        NotificationHandle(instanceKey)
    }

    override fun dismiss(id: NotificationId) = lock.withLock {
        removeMatching("Notification dismissed: ${id.name}") { it.id == id }
    }

    override fun dismiss(handle: NotificationHandle) = lock.withLock {
        removeMatching("Notification dismissed by handle: ${handle.instanceKey}") { it.instanceKey == handle.instanceKey }
    }

    /**
     * Silence every active audible alarm - the global "mute all" path.
     *
     * Marks every audible URGENT notification [AapsNotification.muted] so [refreshAlarmSound] falls
     * to silence, then clears whatever the platform is still showing for them. Non-audible
     * notifications are left alone.
     */
    override fun muteAllAlarms() = lock.withLock {
        val current = _notifications.value
        val until = now() + muteSnoozeMinutes().toLong().minutes.inWholeMilliseconds
        // A new mute also makes the snooze of an alarm that is muted already longer
        current.filter { it.isAudibleAlarm }.forEach { snoozedUntil[it.id] = until }
        val audible = current.filter { it.isAudibleAlarm && !it.muted }
        if (audible.isNotEmpty()) {
            audible.forEach { cancelSystemNotification(it) }
            _notifications.value = current.map { if (it in audible) it.copy(muted = true) else it }
        }
        refreshAlarmSound()
        platform.cancelAll()
        aapsLogger.debug(LTag.NOTIFICATION, "Muted all alarms for ${muteSnoozeMinutes()} min")
    }

    /** Caller must hold [lock]. */
    private fun isSnoozed(id: NotificationId): Boolean = (snoozedUntil[id] ?: 0L) > now()

    /**
     * An alarm that is still listed when its snooze ends sounds again.
     *
     * Caller must hold [lock].
     */
    private fun endSnoozes() {
        val now = now()
        val ended = snoozedUntil.filterValues { it <= now }.keys
        if (ended.isEmpty()) return
        ended.forEach { snoozedUntil.remove(it) }
        val again = _notifications.value.filter { it.muted && it.id in ended }
        if (again.isEmpty()) return
        _notifications.value = _notifications.value.map { if (it in again) it.copy(muted = false) else it }
        again.forEach { n ->
            showOnPlatform(n.copy(muted = false))
            aapsLogger.debug(LTag.NOTIFICATION, "Snooze ended, alarm again: [${n.id.name}] ${n.text}")
        }
        refreshAlarmSound()
    }

    private fun showOnPlatform(notification: AapsNotification) = platform.show(
        notification = notification,
        title = rh.gs(if (notification.level == NotificationLevel.URGENT) CoreUiStrings.urgent_alarm else CoreUiStrings.info)
    )

    /** Caller must hold [lock]. */
    private fun removeMatching(logLine: String, predicate: (AapsNotification) -> Boolean) {
        val current = _notifications.value
        val removed = current.filter(predicate)
        if (removed.isEmpty()) return
        removed.forEach { cancelSystemNotification(it) }
        _notifications.value = current - removed.toSet()
        refreshAlarmSound()
        aapsLogger.debug(LTag.NOTIFICATION, logLine)
    }

    /** Caller must hold [lock]. */
    private fun removeExpired() {
        val now = now()
        val current = _notifications.value
        val expired = current.filter { n ->
            (n.validTo != 0L && n.validTo < now) || (n.validityCheck?.invoke() == false)
        }
        if (expired.isEmpty()) return
        expired.forEach { n ->
            cancelSystemNotification(n)
            aapsLogger.debug(LTag.NOTIFICATION, "Notification expired: ${n.text}")
        }
        _notifications.value = current - expired.toSet()
        refreshAlarmSound()
    }

    private fun cancelSystemNotification(n: AapsNotification) = platform.cancel(n.instanceKey)

    private val AapsNotification.isAudibleAlarm: Boolean
        get() = level == NotificationLevel.URGENT && sound != null

    /**
     * Re-evaluate which alarm owns the sound.
     *
     * The newest active URGENT notification carrying a sound, and not muted, owns it. Replacing a single "currently
     * sounding" slot is what makes concurrent alarms hand off correctly: dismissing the audible one
     * promotes the next remaining one instead of going silent.
     *
     * Caller must hold [lock].
     */
    private fun refreshAlarmSound() {
        val top = _notifications.value
            .filter { it.isAudibleAlarm && !it.muted }
            .maxByOrNull { it.date }
        when {
            top == null                    ->
                if (soundingKey != null) {
                    soundingKey = null
                    platform.setAudibleAlarm(null, null)
                }

            top.instanceKey != soundingKey -> {
                soundingKey = top.instanceKey
                platform.setAudibleAlarm(top.instanceKey, top.sound)
            }
            // else: already playing the top alarm - leave the ramp running.
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()
}
