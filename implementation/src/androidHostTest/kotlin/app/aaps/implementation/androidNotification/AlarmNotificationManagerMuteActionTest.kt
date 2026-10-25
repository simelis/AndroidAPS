package app.aaps.implementation.androidNotification

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import app.aaps.core.ui.R as CoreUiR

/**
 * The Mute button on the alarm notifications.
 *
 * A missed-readings or Nightscout alarm had no button at all, and the notification cannot be swiped
 * away, so it took a tap, an unlock and a choice in the app. Mute from the lock screen snoozes the
 * alarms on this phone for the time set in the alarm settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmNotificationManagerMuteActionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun sut(): AlarmNotificationManager {
        val preferences: Preferences = mock()
        whenever(preferences.get(IntKey.AlertsMuteSnoozeTime)).thenReturn(30)
        val rh: ResourceHelper = mock()
        whenever(rh.gs(CoreUiR.string.format_mins, 30)).thenReturn("30 min")
        whenever(rh.gs(R.string.alarm_mute_for, "30 min")).thenReturn("Mute 30 min")
        val iconsProvider: IconsProvider = mock()
        whenever(iconsProvider.getNotificationIcon()).thenReturn(android.R.drawable.ic_dialog_alert)
        whenever(iconsProvider.getIcon()).thenReturn(android.R.drawable.ic_dialog_alert)
        val notificationHolder: NotificationHolder = mock()
        return AlarmNotificationManager(
            context = context,
            aapsLogger = mock<AAPSLogger>(),
            preferences = preferences,
            iconsProvider = iconsProvider,
            uiInteractionProvider = { mock<UiInteraction>() },
            notificationHolderProvider = { notificationHolder },
            alarmSoundPlayer = mock<AlarmSoundPlayer>(),
            rh = rh
        )
    }

    @Test
    fun aMissedReadingsAlarmCanBeMutedFromTheNotification() {
        sut().postSilentAlarmNotification(notificationKey = 7, title = "Urgent alarm", body = "Missed BG readings", urgent = true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = shadowOf(manager).getNotification(AlarmNotificationManager.SOUND_ID_OFFSET + 7)
        val actions = checkNotNull(posted.actions)
        assertThat(actions).hasLength(1)
        assertThat(actions[0].title.toString()).isEqualTo("Mute 30 min")
        val intent = shadowOf(actions[0].actionIntent)
        assertThat(intent.isBroadcastIntent).isTrue()
        assertThat(intent.savedIntent.component?.className).isEqualTo(AlarmMuteReceiver::class.java.name)
    }
}
