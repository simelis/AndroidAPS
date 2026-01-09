/*
 * Adaptive Preference Renderer for Jetpack Compose
 * Provides generic rendering for any PreferenceKey type
 */

package app.aaps.core.ui.compose.preference

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.keys.interfaces.StringKeyWithEntriesProvider
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey

/**
 * Renders a preference based on its PreferenceKey type and preferenceType.
 * Automatically selects the appropriate composable.
 *
 * For LIST types, loads entries from resources using entriesResId/entryValuesResId.
 * For URL/ACTIVITY types on IntentPreferenceKey, requires additional parameters.
 *
 * @param key The PreferenceKey to render
 * @param preferences The Preferences instance
 * @param config The Config instance
 * @param profileUtil Required for UnitDoublePreferenceKey
 * @param onIntentClick Optional click handler for IntentPreferenceKey with CLICK type
 * @param intentUrl Optional URL for IntentPreferenceKey with URL type
 * @param intentActivityClass Optional Activity class for IntentPreferenceKey with ACTIVITY type
 */
@Composable
fun AdaptivePreferenceItem(
    key: PreferenceKey,
    preferences: Preferences,
    config: Config,
    profileUtil: ProfileUtil? = null,
    visibilityContext: PreferenceVisibilityContext? = null,
    onIntentClick: (() -> Unit)? = null,
    intentUrl: String? = null,
    intentActivityClass: Class<*>? = null
) {
    when (key) {
        is BooleanPreferenceKey -> {
            AdaptiveSwitchPreferenceItem(
                preferences = preferences,
                config = config,
                booleanKey = key,
                visibilityContext = visibilityContext
            )
        }

        is IntPreferenceKey      -> {
            when (key.preferenceType) {
                PreferenceType.LIST       -> {
                    // Check for runtime-resolved entries first (from withEntries)
                    val resolved = key.resolvedEntries
                    if (resolved != null) {
                        AdaptiveListIntPreferenceItem(
                            preferences = preferences,
                            config = config,
                            intKey = key,
                            entries = resolved.values.toList(),
                            entryValues = resolved.keys.toList(),
                            visibilityContext = visibilityContext
                        )
                    } else if (key.entries.isNotEmpty()) {
                        AdaptiveListIntPreferenceItem(
                            preferences = preferences,
                            config = config,
                            intKey = key,
                            entries = key.entries.values.map { stringResource(it) },
                            entryValues = key.entries.keys.toList(),
                            visibilityContext = visibilityContext
                        )
                    }
                }

                PreferenceType.TEXT_FIELD -> {
                    AdaptiveIntPreferenceItem(
                        preferences = preferences,
                        config = config,
                        intKey = key,
                        visibilityContext = visibilityContext
                    )
                }

                else                      -> {
                    // Default to text field for unsupported types
                    AdaptiveIntPreferenceItem(
                        preferences = preferences,
                        config = config,
                        intKey = key,
                        visibilityContext = visibilityContext
                    )
                }
            }
        }

        is DoublePreferenceKey   -> {
            AdaptiveDoublePreferenceItem(
                preferences = preferences,
                config = config,
                doubleKey = key,
                visibilityContext = visibilityContext
            )
        }

        is StringKeyWithEntriesProvider -> {
            // Handle context-dependent entries provider
            val context = LocalContext.current
            val entries = remember(key) { key.entriesProvider(context) }
            val emptyMessageResId = key.emptyEntriesMessageResId

            if (entries.isNotEmpty()) {
                AdaptiveStringListPreferenceItem(
                    preferences = preferences,
                    config = config,
                    stringKey = key,
                    entries = entries,
                    visibilityContext = visibilityContext
                )
            } else if (emptyMessageResId != null) {
                // Show disabled preference with empty message
                Preference(
                    title = { Text(stringResource(key.titleResId)) },
                    summary = { Text(stringResource(emptyMessageResId)) },
                    enabled = false
                )
            }
        }

        is StringPreferenceKey   -> {
            // Handle password/PIN fields specially
            if (key.isPassword || key.isPin) {
                val passwordCheck = LocalPasswordCheck.current
                val context = LocalContext.current
                if (passwordCheck != null) {
                    // Special handling for master password (requires current password first)
                    if (key == StringKey.ProtectionMasterPassword) {
                        AdaptiveMasterPasswordPreferenceItem(
                            preferences = preferences,
                            config = config,
                            passwordCheck = passwordCheck,
                            context = context
                        )
                    } else {
                        AdaptivePasswordPreferenceItem(
                            preferences = preferences,
                            config = config,
                            stringKey = key,
                            passwordCheck = passwordCheck,
                            context = context,
                            visibilityContext = visibilityContext
                        )
                    }
                }
            } else {
                when (key.preferenceType) {
                    PreferenceType.LIST       -> {
                        // Check for runtime-resolved entries first (from withEntries)
                        val entriesMap = key.resolvedEntries
                            ?: key.entries.takeIf { it.isNotEmpty() }?.mapValues { (_, resId) -> stringResource(resId) }

                        if (entriesMap != null) {
                            AdaptiveStringListPreferenceItem(
                                preferences = preferences,
                                config = config,
                                stringKey = key,
                                entries = entriesMap,
                                visibilityContext = visibilityContext
                            )
                        }
                    }

                    PreferenceType.TEXT_FIELD -> {
                        AdaptiveStringPreferenceItem(
                            preferences = preferences,
                            config = config,
                            stringKey = key,
                            visibilityContext = visibilityContext
                        )
                    }

                    else                      -> {
                        AdaptiveStringPreferenceItem(
                            preferences = preferences,
                            config = config,
                            stringKey = key,
                            visibilityContext = visibilityContext
                        )
                    }
                }
            }
        }

        is UnitDoublePreferenceKey -> {
            profileUtil?.let {
                AdaptiveUnitDoublePreferenceItem(
                    preferences = preferences,
                    config = config,
                    profileUtil = it,
                    unitKey = key,
                    visibilityContext = visibilityContext
                )
            }
        }

        is IntentPreferenceKey   -> {
            // Priority: 1) runtime properties from withClick/withActivity/withUrl
            //           2) function parameters
            //           3) key's static properties
            val resolvedClick = key.onClick ?: onIntentClick
            val resolvedActivity = key.runtimeActivityClass ?: intentActivityClass ?: key.activityClass
            val resolvedUrl = key.runtimeUrl ?: intentUrl ?: key.urlResId?.let { stringResource(it) }

            when {
                resolvedClick != null -> {
                    AdaptiveIntentPreferenceItem(
                        preferences = preferences,
                        intentKey = key,
                        onClick = resolvedClick,
                        visibilityContext = visibilityContext
                    )
                }
                resolvedActivity != null -> {
                    AdaptiveDynamicActivityPreferenceItem(
                        preferences = preferences,
                        intentKey = key,
                        activityClass = resolvedActivity,
                        visibilityContext = visibilityContext
                    )
                }
                resolvedUrl != null -> {
                    AdaptiveUrlPreferenceItem(
                        preferences = preferences,
                        intentKey = key,
                        url = resolvedUrl,
                        visibilityContext = visibilityContext
                    )
                }
            }
        }
    }
}

