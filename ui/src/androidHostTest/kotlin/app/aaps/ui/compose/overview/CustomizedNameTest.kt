package app.aaps.ui.compose.overview

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CustomizedNameTest {

    private val hour = 3_600_000L

    @Test
    fun `the percentage and time shift are split off the name`() {
        assertThat(splitCustomizedName("Darbadienis (95%)", 95, 0L)).isEqualTo("Darbadienis" to "(95%)")
        assertThat(splitCustomizedName("Weekday (100%,1h)", 100, hour)).isEqualTo("Weekday" to "(100%,1h)")
    }

    @Test
    fun `brackets in the name itself stay in the name`() {
        assertThat(splitCustomizedName("Sport (light) (80%)", 80, 0L)).isEqualTo("Sport (light)" to "(80%)")
        assertThat(splitCustomizedName("Sport (light)", 100, 0L)).isEqualTo("Sport (light)" to "")
    }

    @Test
    fun `a name without the switch part is kept whole`() {
        assertThat(splitCustomizedName("Darbadienis", 95, 0L)).isEqualTo("Darbadienis" to "")
    }
}
