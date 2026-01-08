/*
 * Adaptive Preference List for Jetpack Compose
 * Provides generic rendering for lists of PreferenceItems
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceItem
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.ui.compose.preference.navigable.NavigablePreferenceItem

/**
 * Renders a list of preference items (keys, subscreens, custom items).
 * This is the enhanced version that handles all PreferenceItem types.
 *
 * Supported types:
 * - PreferenceKey: Rendered with AdaptivePreferenceItem
 * - PreferenceSubScreenDef: Rendered as navigation item
 * - DialogIntentPreference: Rendered with dialog handling
 * - ComposablePreferenceItem: Renders custom composable
 *
 * @param items List of PreferenceItems to render
 * @param preferences The Preferences instance
 * @param config The Config instance
 * @param profileUtil Required for UnitDoublePreferenceKey
 * @param visibilityContext Optional context for evaluating runtime visibility conditions
 * @param onNavigateToSubScreen Callback for navigating to subscreens
 */
@Composable
fun AdaptivePreferenceList(
    items: List<PreferenceItem>,
    preferences: Preferences,
    config: Config,
    profileUtil: ProfileUtil? = null,
    visibilityContext: PreferenceVisibilityContext? = null,
    onNavigateToSubScreen: ((PreferenceSubScreenDef) -> Unit)? = null
) {
    items.forEach { item ->
        when (item) {
            is PreferenceKey -> {
                // Handle standard preference keys
                if (visibilityContext == null || item.visibility.isVisible(visibilityContext)) {
                    AdaptivePreferenceItem(
                        key = item,
                        preferences = preferences,
                        config = config,
                        profileUtil = profileUtil,
                        visibilityContext = visibilityContext
                    )
                }
            }

            is PreferenceSubScreenDef -> {
                // Handle subscreens as navigation items
                // Check hideParentScreenIfHidden - if first item with this flag is hidden, hide subscreen
                val shouldShow = shouldShowSubScreen(
                    subScreen = item,
                    preferences = preferences,
                    config = config,
                    visibilityContext = visibilityContext
                )
                if (onNavigateToSubScreen != null && shouldShow) {
                    NavigablePreferenceItem(
                        titleResId = item.titleResId,
                        summaryResId = item.summaryResId,
                        summaryItems = item.effectiveSummaryItems(),
                        onClick = { onNavigateToSubScreen(item) }
                    )
                }
            }

            is DialogIntentPreference -> {
                // Handle dialog preferences
                item.Render { keyToRender ->
                    AdaptivePreferenceItem(
                        key = keyToRender,
                        preferences = preferences,
                        config = config,
                        profileUtil = profileUtil,
                        visibilityContext = visibilityContext
                    )
                }
            }

            is ComposablePreferenceItem -> {
                // Handle custom composables
                item.content()
            }
        }
    }
}

/**
 * Determines if a subscreen should be shown based on hideParentScreenIfHidden logic.
 *
 * If any item in the subscreen has hideParentScreenIfHidden = true and that item
 * would be hidden (e.g., due to simple mode, mode restrictions, or dependencies),
 * the entire subscreen entry is hidden.
 *
 * @param subScreen The PreferenceSubScreenDef to check
 * @param preferences The Preferences instance
 * @param config The Config instance
 * @param visibilityContext Optional context for evaluating runtime visibility conditions
 * @return true if subscreen should be shown, false if it should be hidden
 */
@Composable
private fun shouldShowSubScreen(
    subScreen: PreferenceSubScreenDef,
    preferences: Preferences,
    config: Config,
    visibilityContext: PreferenceVisibilityContext?
): Boolean {
    // Find items with hideParentScreenIfHidden = true
    for (item in subScreen.effectiveItems) {
        if (item is PreferenceKey && item.hideParentScreenIfHidden) {
            val visibility = if (item is IntentPreferenceKey) {
                // Check visibility of intent item
                calculateIntentPreferenceVisibility(
                    intentKey = item,
                    preferences = preferences,
                    visibilityContext = visibilityContext
                )
            } else {
                // Get engineeringModeOnly based on specific type (not all PreferenceKey types have it)
                val engineeringModeOnly = when (item) {
                    is BooleanPreferenceKey -> item.engineeringModeOnly
                    is IntPreferenceKey -> item.engineeringModeOnly
                    is LongPreferenceKey -> item.engineeringModeOnly
                    else -> false
                }
                // Check visibility of regular preference item
                calculatePreferenceVisibility(
                    preferenceKey = item,
                    preferences = preferences,
                    config = config,
                    engineeringModeOnly = engineeringModeOnly,
                    visibilityContext = visibilityContext
                )
            }
            // If this controlling item is hidden, hide the parent subscreen
            if (!visibility.visible) {
                return false
            }
        }
    }
    // No hideParentScreenIfHidden items found, or all are visible
    return true
}
