package app.aaps.core.ui.compose.preference

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.core.os.bundleOf

/**
 * State holder for collapsible preference sections.
 * Tracks which sections are expanded and persists across configuration changes.
 * Uses accordion behavior: only one section can be expanded at a time within the same hierarchy level.
 */
class PreferenceSectionState(
    private val expandedSections: SnapshotStateMap<String, Boolean> = mutableStateMapOf()
) {

    /**
     * Check if a section is expanded (default: false - collapsed)
     */
    fun isExpanded(sectionKey: String): Boolean = expandedSections[sectionKey] ?: false

    /**
     * Toggle the expanded state of a section.
     * Expanding a section collapses only sibling sections (same hierarchy level).
     */
    fun toggle(sectionKey: String) {
        val newState = !isExpanded(sectionKey)

        if (newState) {
            val isTopLevel = sectionKey.endsWith("_main")
            // Get plugin prefix (part before first underscore)
            val pluginPrefix = sectionKey.substringBefore("_", "")

            // Collapse only sibling sections (convert to list to avoid concurrent modification)
            expandedSections.keys.toList().forEach { key ->
                if (key != sectionKey) {
                    val keyIsTopLevel = key.endsWith("_main")
                    val keyPluginPrefix = key.substringBefore("_", "")

                    // Top-level sections (_main): collapse all other top-level sections
                    // Subscreens: collapse only siblings with the same plugin prefix
                    val shouldCollapse = if (isTopLevel && keyIsTopLevel) {
                        true  // All top-level "_main" sections collapse each other
                    } else if (!isTopLevel && !keyIsTopLevel && keyPluginPrefix == pluginPrefix) {
                        true  // Subscreens under same plugin collapse each other
                    } else {
                        false
                    }

                    if (shouldCollapse) {
                        expandedSections[key] = false
                    }
                }
            }
        }

        expandedSections[sectionKey] = newState
    }

    companion object {

        val Saver: Saver<PreferenceSectionState, Bundle> = Saver(
            save = { state ->
                bundleOf(
                    *state.expandedSections.map { (k, v) -> k to v }.toTypedArray()
                )
            },
            restore = { bundle ->
                PreferenceSectionState(
                    expandedSections = mutableStateMapOf<String, Boolean>().apply {
                        bundle.keySet().forEach { key ->
                            put(key, bundle.getBoolean(key))
                        }
                    }
                )
            }
        )
    }
}

/**
 * Remember and save preference section state across configuration changes.
 * Uses accordion behavior by default.
 */
@Composable
fun rememberPreferenceSectionState(): PreferenceSectionState {
    return rememberSaveable(saver = PreferenceSectionState.Saver) {
        PreferenceSectionState()
    }
}
