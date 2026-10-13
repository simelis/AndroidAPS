package app.aaps.ui.compose.overview

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TherapyEventGlucoseTest {

    private fun event(type: TE.Type, glucose: Double?, unit: GlucoseUnit = GlucoseUnit.MGDL) =
        TE(timestamp = 0L, type = type, glucose = glucose, glucoseUnit = unit)

    @Test
    fun `a BG check keeps its value in mg per dl, also when it was entered in mmol`() {
        assertThat(event(TE.Type.FINGER_STICK_BG_VALUE, 112.0).checkedGlucoseMgdl()).isEqualTo(112.0)
        assertThat(event(TE.Type.FINGER_STICK_BG_VALUE, 6.2, GlucoseUnit.MMOL).checkedGlucoseMgdl())
            .isWithin(0.001).of(6.2 * Constants.MMOLL_TO_MGDL)
    }

    @Test
    fun `a Nightscout meter value is in mg per dl`() {
        assertThat(event(TE.Type.NS_MBG, 98.0, GlucoseUnit.MMOL).checkedGlucoseMgdl()).isEqualTo(98.0)
    }

    @Test
    fun `other events and checks without a value have no glucose`() {
        assertThat(event(TE.Type.NOTE, 112.0).checkedGlucoseMgdl()).isNull()
        assertThat(event(TE.Type.FINGER_STICK_BG_VALUE, null).checkedGlucoseMgdl()).isNull()
        assertThat(event(TE.Type.FINGER_STICK_BG_VALUE, 0.0).checkedGlucoseMgdl()).isNull()
    }
}
