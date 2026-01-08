package app.aaps.core.ui.compose.preference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.LongPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.ui.compose.preference.navigable.NavigablePreferenceContent
import app.aaps.core.ui.compose.preference.navigable.addNavigablePreferenceContent

/**
 * Helper function to add any preference content (legacy or new) inline in a LazyListScope.
 * Handles both NavigablePreferenceContent (legacy) and PreferenceSubScreenDef (new).
 */
fun LazyListScope.addPreferenceContent(
    content: Any,
    sectionState: PreferenceSectionState? = null,
    preferences: Preferences? = null,
    config: Config? = null,
    profileUtil: ProfileUtil? = null
) {
    when (content) {
        is NavigablePreferenceContent -> addNavigablePreferenceContent(content, sectionState)
        is PreferenceSubScreenDef -> addPreferenceSubScreenDef(content, sectionState, preferences, config, profileUtil)
    }
}

/**
 * Helper function to add PreferenceSubScreenDef inline in a LazyListScope.
 * This displays as one collapsible card with main content and nested subscreens inside.
 * Content is rendered using the new pattern (no NavigablePreferenceContent interface).
 */
fun LazyListScope.addPreferenceSubScreenDef(
    def: PreferenceSubScreenDef,
    sectionState: PreferenceSectionState? = null,
    preferences: Preferences? = null,
    config: Config? = null,
    profileUtil: ProfileUtil? = null
) {
    val sectionKey = "${def.key}_main"
    item(key = sectionKey) {
        val isExpanded = sectionState?.isExpanded(sectionKey) ?: false
        // Get visibility context from CompositionLocal
        val visibilityContext = LocalVisibilityContext.current
        CollapsibleCardSectionContent(
            titleResId = def.titleResId,
            summaryItems = def.effectiveSummaryItems(),
            expanded = isExpanded,
            onToggle = { sectionState?.toggle(sectionKey) }
        ) {
            // Show custom content first (if any)
            if (def.customContent != null) {
                def.customContent.invoke(sectionState)
            } else {
                // Render items in order, preserving the original structure
                RenderPreferenceItems(
                    items = def.effectiveItems,
                    parentKey = def.key,
                    sectionState = sectionState,
                    preferences = preferences,
                    config = config,
                    profileUtil = profileUtil,
                    visibilityContext = visibilityContext
                )
            }
        }
    }
}

/**
 * Helper composable to render a list of preference items with visibility support.
 */
@Composable
private fun RenderPreferenceItems(
    items: List<Any>,
    parentKey: String,
    sectionState: PreferenceSectionState?,
    preferences: Preferences?,
    config: Config?,
    profileUtil: ProfileUtil?,
    visibilityContext: PreferenceVisibilityContext?
) {
    items.forEach { item ->
        when (item) {
            is PreferenceKey -> {
                // Render using AdaptivePreferenceItem which handles visibility reactively
                if (preferences != null && config != null) {
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
                // Check hideParentScreenIfHidden before rendering subscreen
                val shouldShow = if (preferences != null && config != null) {
                    shouldShowSubScreenInline(
                        subScreen = item,
                        preferences = preferences,
                        config = config,
                        visibilityContext = visibilityContext
                    )
                } else true

                if (shouldShow) {
                    // Render nested subscreen as simple collapsible section (no extra card)
                    val subSectionKey = "${parentKey}_${item.key}"
                    val isSubExpanded = sectionState?.isExpanded(subSectionKey) ?: false

                    // Header without card
                    ClickablePreferenceCategoryHeader(
                        titleResId = item.titleResId,
                        summaryItems = item.effectiveSummaryItems(),
                        expanded = isSubExpanded,
                        onToggle = { sectionState?.toggle(subSectionKey) },
                        insideCard = true
                    )

                    // Content without card wrapper
                    if (isSubExpanded) {
                        if (item.customContent != null) {
                            item.customContent.invoke(sectionState)
                        } else if (preferences != null && config != null) {
                            // Auto-render nested subscreen items (including DialogIntentPreference)
                            if (item.effectiveItems.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    AdaptivePreferenceList(
                                        items = item.effectiveItems,
                                        preferences = preferences,
                                        config = config,
                                        profileUtil = profileUtil,
                                        visibilityContext = visibilityContext,
                                        onNavigateToSubScreen = null // Nested subscreens not supported here
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Determines if a subscreen should be shown based on hideParentScreenIfHidden logic.
 * Used in inline rendering context (AllPreferencesScreen).
 */
@Composable
private fun shouldShowSubScreenInline(
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
                // Get engineeringModeOnly based on specific type
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
