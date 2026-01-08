package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.PreferenceKey

/**
 * Lightweight preference subscreen definition.
 * Can contain both PreferenceKeys and nested PreferenceSubScreenDefs for hierarchical structure.
 * Content is auto-generated from items using AdaptivePreferenceList unless customContent is provided.
 *
 * @param key Unique key for this subscreen
 * @param titleResId String resource ID for the screen title
 * @param items List of preference items (keys and/or nested subscreens)
 * @param summaryResId Optional string resource ID for summary shown in parent list
 * @param customContent Optional custom content - when null, content is auto-generated from items
 */
data class PreferenceSubScreenDef(
    val key: String,
    val titleResId: Int,
    val items: List<PreferenceItem> = emptyList(),
    val summaryResId: Int? = null,
    @Deprecated("Use PURE declarative preferences with items list. Visibility/enabled conditions should be in PreferenceKey definitions.")
    val customContent: (@Composable (PreferenceSectionState?) -> Unit)? = null
) : PreferenceItem {

    /** Effective summary items - from items' titleResId */
    fun effectiveSummaryItems(): List<Int> =
        items.mapNotNull { item ->
            when (item) {
                is PreferenceKey -> item.titleResId.takeIf { it != 0 }
                is PreferenceSubScreenDef -> item.titleResId.takeIf { it != 0 }
                else -> null
            }
        }
}
