package app.aaps.ui.compose.notificationsSheet

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationHistoryEntry
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.setAapsContent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sheet lists the recently dismissed notifications below the active ones. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NotificationBottomSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var screen: AapsScreenFixture
    private lateinit var context: Context

    @Before
    fun setUp() {
        screen = AapsScreenFixture()
        context = RuntimeEnvironment.getApplication()
        whenever(screen.dateUtil.timeString(POSTED)).thenReturn("10:20")
        whenever(screen.dateUtil.timeString(REMOVED)).thenReturn("10:32")
    }

    private fun setSheet(notifications: List<AapsNotification>, history: List<NotificationHistoryEntry>) {
        compose.setAapsContent(screen) {
            NotificationBottomSheet(
                notifications = notifications,
                onDismissSheet = {},
                onDismissNotification = {},
                onNotificationActionClick = {},
                history = history
            )
        }
    }

    private fun entry(expired: Boolean = false) = NotificationHistoryEntry(
        id = NotificationId.BG_READINGS_MISSED, text = "Missed BG readings", level = NotificationLevel.URGENT,
        date = POSTED, removed = REMOVED, expired = expired
    )

    @Test
    fun aDismissedNotificationIsListedWithItsTimes() {
        setSheet(emptyList(), listOf(entry()))

        compose.onNodeWithText(context.getString(app.aaps.ui.R.string.notifications_recently_dismissed)).assertExists()
        compose.onNodeWithText("Missed BG readings").assertExists()
        compose.onNodeWithText(context.getString(app.aaps.ui.R.string.notification_history_removed, "10:20", "10:32")).assertExists()
    }

    @Test
    fun anExpiredNotificationSaysSo() {
        setSheet(emptyList(), listOf(entry(expired = true)))

        compose.onNodeWithText(context.getString(app.aaps.ui.R.string.notification_history_expired, "10:20", "10:32")).assertExists()
    }

    @Test
    fun noHistoryNoSection() {
        setSheet(listOf(AapsNotification(id = NotificationId.SCENE_ENDED, instanceKey = 1, text = "Scene ended", level = NotificationLevel.INFO, date = POSTED)), emptyList())

        compose.onNodeWithText("Scene ended").assertExists()
        compose.onNodeWithText(context.getString(app.aaps.ui.R.string.notifications_recently_dismissed)).assertDoesNotExist()
    }

    private companion object {

        const val POSTED = 1_700_000_000_000L
        const val REMOVED = 1_700_000_720_000L
    }
}
