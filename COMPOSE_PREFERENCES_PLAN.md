# Compose Preferences Migration Plan

## Goal

**Migrate ALL preference screens to PURE `PreferenceSubScreenDef` pattern** like OverviewPlugin.

### Target State
- NO separate `*PreferencesCompose.kt` files
- ALL plugins return `PreferenceSubScreenDef` directly from `getPreferenceScreenContent()`
- REMOVE `NavigablePreferenceContent` interface and related code after migration

### Reference Implementation
`plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewPlugin.kt` (line 236+)

### Critical Migration Rules
1. **NO DUPLICATION** - Never duplicate resources or code
2. **Move shared code to common module** if needed (e.g., `core:ui`, `core:keys`)
3. **Reuse existing PreferenceKeys** - don't create new ones if equivalent exists
4. **Check for shared patterns** - if multiple plugins need same logic, extract to utility
5. **PRESERVE ORIGINAL ORDER** - Preferences must appear in same order as legacy implementation
6. **VERIFY RESOURCE STRINGS** - Check title/summary from `addPreferenceScreen()` match PreferenceKey definitions
7. **REUSE RESOURCES** - Never create new string resources if existing ones work
8. **ALLOWED:** Update resource IDs in `addPreferenceScreen()` if resources moved to another module

---

## Overview

AndroidAPS has **THREE different approaches** for rendering preferences:

1. **XML/Legacy** (`addPreferenceScreen`) - Old Android PreferenceFragment approach
2. **Compose Legacy** (`NavigablePreferenceContent`) - Separate compose class files
3. **Compose Target** (`PreferenceSubScreenDef`) - Pure declarative inline in plugin

Many plugins currently have BOTH `addPreferenceScreen()` AND `getPreferenceScreenContent()` running in parallel.

**Migration removes BOTH legacy approaches.**

---

## Approach 0: addPreferenceScreen (XML/Legacy Pattern)

### Location
Each plugin overrides `PluginBase.addPreferenceScreen()`

### Implementation
```kotlin
override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
    val category = PreferenceCategory(context)
    parent.addPreference(category)
    category.apply {
        key = "settings_key"
        title = rh.gs(R.string.title)
        addPreference(AdaptiveDoublePreference(ctx = context, doubleKey = DoubleKey.Xxx, ...))
        addPreference(preferenceManager.createPreferenceScreen(context).apply {
            key = "subscreen_key"
            // nested preferences
        })
    }
}
```

### Status
- Used by `MyPreferenceFragment` (legacy XML preferences)
- Running in parallel with Compose for backward compatibility
- **TO BE REMOVED** after full Compose migration
- **EXCEPTION:** Resource IDs can be updated if resources are moved to another module (to avoid duplication)

---

## Approach 1: NavigablePreferenceContent (Compose Legacy Pattern)

### Location
`core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/navigable/`

### Key Files
- `NavigablePreferenceContent.kt` - Interface definition
- `PreferenceNavigationHost.kt` - Handles rendering with AnimatedContent
- `PreferenceSubScreen.kt` - Subscreen definition
- `NavigablePreferenceItem.kt` - Item wrapper

### Implementation Pattern
- **Class-based**: Plugins create a separate class implementing `NavigablePreferenceContent`
- **Manual composition**: Developer writes `@Composable` content via lambdas
- **Navigation**: Uses `AnimatedContent` with slide animations

### Structure
```kotlin
class SomePreferencesCompose(...) : NavigablePreferenceContent {
    override val titleResId: Int = R.string.title
    override val mainKeys: List<PreferenceKey> = listOf(...)
    override val mainContent: (@Composable (PreferenceSectionState?) -> Unit) = { _ ->
        // Manual composable code
    }
    override val subscreens: List<PreferenceSubScreen> = listOf(...)
}
```

### Files Using This Approach (6 files remaining)

#### Pumps (0 remaining - ALL MIGRATED ✅)
- [x] `pump/combov2/.../ComboV2PreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/danar/.../DanaRPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/danars/.../DanaRSPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/diaconn/.../DiaconnG8PreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/eopatch/.../EopatchPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/equil/.../EquilPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/insight/.../InsightPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/medtronic/.../MedtronicPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/medtrum/.../MedtrumPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/omnipod/dash/.../OmnipodDashPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/omnipod/eros/.../OmnipodErosPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `pump/virtual/.../VirtualPumpPreferencesCompose.kt` ✅ MIGRATED TO PURE

#### APS Plugins (3 files remaining - Phase 3)
- [x] `plugins/aps/.../AutotunePreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/aps/.../LoopPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [ ] `plugins/aps/.../OpenAPSAMAPreferencesCompose.kt` (Phase 3)
- [ ] `plugins/aps/.../OpenAPSAutoISFPreferencesCompose.kt` (Phase 3)
- [ ] `plugins/aps/.../OpenAPSSMBPreferencesCompose.kt` (HYBRID - Phase 3)

#### Sync Plugins (1 file remaining)
- [x] `plugins/sync/.../GarminPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/sync/.../NSClientPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [ ] `plugins/sync/.../NSClientV3PreferencesCompose.kt` (HYBRID - Phase 3)
- [x] `plugins/sync/.../OpenHumansPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/sync/.../TidepoolPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/sync/.../WearPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/sync/.../XdripPreferencesCompose.kt` ✅ MIGRATED TO PURE

#### Other Plugins (0 remaining - ALL MIGRATED ✅)
- [x] `plugins/sensitivity/.../SensitivityAAPSPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/sensitivity/.../SensitivityOref1PreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/source/.../RandomBgPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/main/.../SmsCommunicatorPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/automation/.../AutomationPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/configuration/.../MaintenancePreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/constraints/.../SafetyPreferencesCompose.kt` ✅ MIGRATED TO PURE
- [x] `plugins/insulin/.../InsulinOrefFreePeakPreferencesCompose.kt` ✅ MIGRATED TO PURE

#### Base Classes (2 files remaining)
- [ ] `plugins/source/.../AbstractBgSourcePlugin.kt`
- [ ] `plugins/source/.../AbstractBgSourceWithSensorInsertLogPlugin.kt`

---

## Approach 2: PreferenceSubScreenDef (New Pattern)

### Location
`core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/`

### Key Files
- `PreferenceSubScreenDef.kt` - Data class definition
- `PreferenceSubScreenRenderer.kt` (in app module) - Handles rendering
- `PreferenceContentExtensions.kt` - Helper extensions
- `AdaptivePreferenceList.kt` - Auto-generates UI from keys

### Implementation Pattern
- **Data-driven**: Plugins return a `PreferenceSubScreenDef` data class
- **Auto-composition**: Framework auto-generates UI from preference keys
- **Navigation**: Stack-based (push/pop screens)

### Structure
```kotlin
override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
    key = "screen_key",
    titleResId = R.string.title,
    items = listOf(
        BooleanKey.SomeKey,
        IntKey.AnotherKey,
        PreferenceSubScreenDef(  // Nested subscreen
            key = "nested",
            titleResId = R.string.nested_title,
            items = listOf(...)
        )
    )
)
```

### Files Using This Approach (Pure or Hybrid)
- `plugins/main/.../OverviewPlugin.kt` - **PURE** (direct PreferenceSubScreenDef in plugin)
- `plugins/aps/.../OpenAPSSMBPreferencesCompose.kt` - **HYBRID** (uses PreferenceSubScreenDef inside NavigablePreferenceContent)
- `plugins/sync/.../NSClientV3PreferencesCompose.kt` - **HYBRID** (uses PreferenceSubScreenDef inside NavigablePreferenceContent)

---

## Comparison Table

| Aspect | NavigablePreferenceContent | PreferenceSubScreenDef |
|--------|---------------------------|------------------------|
| Pattern | Interface-based (class) | Data class (declarative) |
| Rendering | Manual composables | Auto-generated from keys |
| Navigation | AnimatedContent (animations) | Stack-based (push/pop) |
| Nesting | Separate subscreen objects | Hierarchical in items list |
| Customization | Full composable control | `customContent` DEPRECATED - use PURE |
| Separate File | Yes (dedicated class) | No (inline in plugin) |
| Status | Legacy | Recommended |

---

## Routing in PluginPreferencesScreen.kt

Located at: `app/src/main/kotlin/app/aaps/compose/preferences/PluginPreferencesScreen.kt`

```kotlin
when (preferenceScreenContent) {
    is PreferenceSubScreenDef -> {
        PreferenceSubScreenRenderer(...)
    }
    is NavigablePreferenceContent -> {
        PreferenceNavigationHost(...)
    }
}
```

---

## Migration Strategy

### Goal
Migrate all `NavigablePreferenceContent` implementations to `PreferenceSubScreenDef`.

### Steps per File
1. Identify the `*PreferencesCompose.kt` file
2. Extract `mainKeys` list
3. Convert `subscreens` to nested `PreferenceSubScreenDef` items
4. Move the definition directly into the plugin's `getPreferenceScreenContent()`
5. Remove the separate compose class file
6. **Verify against legacy `addPreferenceScreen()`:**
   - a. Compare screen/category `key` matches
   - b. Compare `title`/`titleResId` matches
   - c. Compare preference list: same keys, same order
   - d. Compare subscreens: same keys, same nested preferences
   - e. Compare special behaviors (e.g., Bluetooth list, dynamic entries)
   - f. Document any intentional differences
7. Compile and verify build succeeds
8. **Verify `preprocessPreferences()` logic (if present):**
   - Some plugins have `preprocessPreferences()` that modifies preference behavior for legacy code
   - Check if logic needs to be adapted to PURE preferences (e.g., dynamic entries, visibility)
   - Plugins with `preprocessPreferences`: MedtrumPlugin, ComboV2Plugin, SmsCommunicatorPlugin, OpenAPSAutoISFPlugin, OpenAPSSMBPlugin
9. (Optional) Runtime test: navigation and preference rendering

### Benefits of Migration
- Simpler, more declarative code
- No separate class files needed
- Consistent navigation behavior
- Easier to maintain hierarchy
- Auto-rendering reduces boilerplate

---

## Migration Priority

### High Priority (Complex pumps with subscreens) - ✅ ALL DONE
1. ~~ComboV2PreferencesCompose.kt~~ ✅ DONE - Uses PURE PreferenceSubScreenDef with `withDialog` for unpair
2. ~~MedtronicPreferencesCompose.kt~~ ✅ DONE - Uses PURE PreferenceSubScreenDef
3. ~~OmnipodDashPreferencesCompose.kt~~ ✅ DONE - Uses PURE PreferenceSubScreenDef with nested subscreens
4. ~~OmnipodErosPreferencesCompose.kt~~ ✅ DONE - Uses PURE PreferenceSubScreenDef with nested subscreens

### Medium Priority (APS algorithms) - 2 remaining (Phase 3)
5. OpenAPSAMAPreferencesCompose.kt
6. OpenAPSAutoISFPreferencesCompose.kt
7. ~~LoopPreferencesCompose.kt~~ ✅ DONE
8. ~~AutotunePreferencesCompose.kt~~ ✅ DONE

### Lower Priority (Simpler preferences) - ✅ ALL DONE
9. ~~All Dana pumps (DanaR, DanaRS)~~ ✅ DONE
10. ~~Other pumps (Diaconn, Eopatch, Equil, Insight, Medtrum, Virtual)~~ ✅ DONE
11. ~~Sync plugins~~ ✅ DONE (except NSClientV3 - Phase 3)
12. ~~Sensitivity plugins~~ ✅ DONE
13. ~~Other plugins (SmsCommunicator, Automation, Maintenance, Safety)~~ ✅ DONE

---

## Notes & Findings

### Finding 1: Hybrid Pattern Exists
Some files (OpenAPSSMBPreferencesCompose, NSClientV3PreferencesCompose) use a **hybrid approach** - they implement `NavigablePreferenceContent` but use `PreferenceSubScreenDef` internally for nested content. This shows a transition path.

### Finding 2: OverviewPlugin is the Reference
`OverviewPlugin.kt` was the first **pure** implementation using `PreferenceSubScreenDef` directly from the plugin's `getPreferenceScreenContent()` method. Use this as a reference. Now all pumps and many other plugins also use this pattern.

### Finding 3: Base Classes Need Attention
`AbstractBgSourcePlugin.kt` and `AbstractBgSourceWithSensorInsertLogPlugin.kt` are base classes - changing them affects multiple BG source implementations.

### Finding 4: customContent is Deprecated (Jan 2026)
`customContent` in `PreferenceSubScreenDef` should NOT be used. Marked as `@Deprecated`. All preferences should be PURE - using only the `items` list with keys that define their own visibility/enabled conditions.

### Finding 5: Plugin-specific State via Preference Keys
For plugin-specific state like "isPumpPaired" (ComboV2):
- **DO NOT** add methods to the Pump interface for plugin-specific state
- **DO** use lambdas in `enabledCondition` that check existing preferences
- Example: ComboV2 checks `ComboStringNonKey.BtAddress.isEmpty()` to determine pairing state
```kotlin
enabledCondition = PreferenceEnabledCondition { ctx ->
    ctx.preferences.get(ComboStringNonKey.BtAddress).isEmpty()
}
```

### Finding 6: withDialog for Compose Confirmation Dialogs
For intent preferences that need confirmation dialogs:
- **DO NOT** use `uiInteraction.showOkCancelDialog()` in click handlers
- **DO** use `IntentPreferenceKey.withDialog()` extension which manages dialog state in Compose
```kotlin
ComboIntentKey.UnpairPump.withDialog(
    titleResId = R.string.confirm_unpair_title,
    messageResId = R.string.confirm_unpair_message,
    onConfirm = { unpair() }
)
```

### Finding 7: Global PreferenceVisibilityContext
The `PreferenceVisibilityContextImpl` (in `implementation` module) provides runtime context:
- `isPatchPump`, `isBatteryReplaceable`, etc. come from `activePlugin.activePump`
- `advancedFilteringSupported` comes from `activePlugin.activeBgSource`
- `isPumpInitialized` comes from `activePlugin.activePump.isInitialized()`
- Used by `enabledCondition` and `visibility` lambdas in preference keys

---

## Open Questions

1. Should we keep animation transitions? (NavigablePreferenceContent has slide animations)
2. How to handle custom composable content that some preferences need?
3. Migration timeline and testing strategy?

---

## Visibility & Enabled State Analysis

### Current Mechanisms (7 total)

| # | Mechanism | Location | Purpose |
|---|-----------|----------|---------|
| 1 | `visibility: PreferenceVisibility` | PreferenceKey | Runtime visibility condition |
| 2 | `enabledCondition: PreferenceEnabledCondition` | PreferenceKey | Runtime enabled condition |
| 3 | `dependency: BooleanPreferenceKey` | PreferenceKey | Show only if dependency=TRUE |
| 4 | `negativeDependency: BooleanPreferenceKey` | PreferenceKey | Show only if dependency=FALSE |
| 5 | `showInApsMode/NSClientMode/PumpControlMode` | PreferenceKey | Mode-based visibility |
| 6 | `defaultedBySM: Boolean` | PreferenceKey | Hide in simple mode |
| 7 | `engineeringModeOnly: Boolean` | PreferenceKey | Engineering mode only |

### Built-in Visibility Conditions
```kotlin
PreferenceVisibility.ALWAYS              // Default
PreferenceVisibility.NON_PATCH_PUMP      // Hide for Omnipod
PreferenceVisibility.PATCH_PUMP_ONLY     // Show only for Omnipod
PreferenceVisibility.BATTERY_REPLACEABLE // Battery pumps only
PreferenceVisibility.ADVANCED_FILTERING  // BG source capability
PreferenceVisibility.intEquals(key, value)    // Check IntKey value
PreferenceVisibility.stringNotEmpty(key)      // Check StringKey not empty
```

### PreferenceVisibilityContext (runtime data)
```kotlin
interface PreferenceVisibilityContext {
    val isPatchPump: Boolean
    val isBatteryReplaceable: Boolean
    val isBatteryChangeLoggingEnabled: Boolean
    val advancedFilteringSupported: Boolean
    val preferences: Preferences
    val isPumpPaired: Boolean
    val isPumpInitialized: Boolean
}
```

### Visibility vs Enabled

| Aspect | Visibility | Enabled |
|--------|-----------|---------|
| When false | Not rendered | Rendered but grayed out |
| Use case | Hide irrelevant options | Disable until prerequisites met |

### Current Issues / Redundancy

1. **`dependency` vs `visibility`** - `dependency` is just sugar for `visibility { preferences.get(depKey) }`
2. **Manual filtering in some plugins** - Some *PreferencesCompose.kt files do manual filtering instead of using declarative visibility
3. **Duplicate patterns** - OpenAPSSMBPreferencesCompose has manual `filteredKeys` logic that could be declarative

### Recommendation: UNIFY

**Rule: ALL visibility/enabled logic should be in PreferenceKey definition, NOT in composables**

1. **Remove manual filtering** from *PreferencesCompose files
2. **Use `visibility` property** for all conditions
3. **Use `enabledCondition`** for enabled state
4. **Extend built-in conditions** if common patterns emerge

### Example: Current (manual filtering in compose)
```kotlin
// BAD - in OpenAPSSMBPreferencesCompose
val filteredKeys by remember {
    derivedStateOf {
        mainKeys.filter { key ->
            when (key) {
                BooleanKey.ApsUseSmbAlways -> smbEnabled && advancedFiltering
                else -> true
            }
        }
    }
}
```

### Example: Target (declarative in key)
```kotlin
// GOOD - in BooleanKey definition
ApsUseSmbAlways(
    ...,
    dependency = ApsUseSmb,
    visibility = PreferenceVisibility.ADVANCED_FILTERING
)
```

### Migration Impact
- Most keys already use declarative visibility correctly
- OpenAPSSMB and similar complex plugins need migration from manual to declarative
- After migration, no `customContent` needed for visibility - use pure `PreferenceSubScreenDef`

---

## Missing Feature: hideParentScreenIfHidden

### Status: NOT IMPLEMENTED in Compose!

**Legacy (XML)**: Implemented in `core/validators/preferences/Adaptive*Preference.kt`
```kotlin
if (preferenceKey.hideParentScreenIfHidden) {
    parent?.isVisible = isVisible
    parent?.isEnabled = isEnabled
}
```

**Compose**: NOT IMPLEMENTED - property exists in PreferenceKey but not used in `core/ui/compose/`

### What it does
When first item of a subscreen has `hideParentScreenIfHidden = true`:
- If that item becomes hidden (e.g., in simple mode)
- The parent subscreen entry is ALSO hidden
- Cascading visibility without manual logic

### Keys using this (examples)
- `OverviewShowTreatmentButton` - hides "Buttons settings" subscreen
- `OverviewShowStatusLights` - hides "Status lights" subscreen
- `OverviewEatingSoonDuration` - hides "Temp targets" subscreen
- `ActionsFillButton1` - hides "Fill settings" subscreen
- `OverviewUseSuperBolus` - hides "Advanced settings" subscreen

### TODO: Implement in Compose
Location: `AdaptivePreferenceList.kt` or `PreferenceSubScreenRenderer.kt`

Logic needed:
1. When rendering `PreferenceSubScreenDef` entry in list
2. Check if first item has `hideParentScreenIfHidden = true`
3. If yes, evaluate that item's visibility
4. If item hidden → hide the subscreen entry too

### Simplification Impact
Once implemented:
- PURE code doesn't need conditional subscreens
- Visibility cascades automatically from keys
- Cleaner `getPreferenceScreenContent()` definitions

---

## Critical Implementation Details

### PreferenceSubScreenDef Data Class
Location: `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/PreferenceSubScreenDef.kt`

```kotlin
data class PreferenceSubScreenDef(
    val key: String,
    val titleResId: Int,
    val items: List<PreferenceItem> = emptyList(),    // NEW: supports nested screens
    val keys: List<PreferenceKey> = emptyList(),      // DEPRECATED
    val summaryResId: Int? = null,
    val customContent: (@Composable (PreferenceSectionState?) -> Unit)? = null
) : PreferenceItem
```

Key features:
- `items` can contain both `PreferenceKey` and nested `PreferenceSubScreenDef`
- `customContent` allows escape hatch for complex UI
- Implements `PreferenceItem` so it can be nested

### PreferenceSubScreenRenderer
Location: `app/src/main/kotlin/app/aaps/compose/preferences/PreferenceSubScreenRenderer.kt`

Features:
- Stack-based navigation (push/pop screens)
- Supports `visibilityContext` for preference visibility
- Auto-renders via `AdaptivePreferenceList` when no `customContent`
- Falls back to `customContent` composable if provided

### Migration Challenge: Dynamic Visibility

Some preferences have **dynamic visibility** based on OTHER preference values. Example from OpenAPSSMBPreferencesCompose:

```kotlin
// SMB options hidden/shown based on smbEnabled state
mainKeys.filter { key ->
    when (key) {
        BooleanKey.ApsUseSmbAlways -> smbEnabled && advancedFiltering
        BooleanKey.ApsUseSmbAfterCarbs -> smbEnabled && !smbAlwaysEnabled
        IntKey.ApsUamMaxMinutesOfBasalToLimitSmb -> smbEnabled && uamEnabled
        else -> true
    }
}
```

**Solution Options:**
1. Keep `customContent` for screens with complex visibility
2. Enhance `PreferenceKey` with visibility predicates
3. Add visibility rules to `PreferenceSubScreenDef`

### Migration Pattern: Simple Case

**Before (NavigablePreferenceContent):**
```kotlin
class FooPreferencesCompose(...) : NavigablePreferenceContent {
    override val titleResId = R.string.foo
    override val mainKeys = listOf(BooleanKey.A, IntKey.B)
    override val mainContent = { _ ->
        AdaptivePreferenceListForListKeys(keys = mainKeys, ...)
    }
    override val subscreens = emptyList()
}
```

**After (PreferenceSubScreenDef):**
```kotlin
// In plugin's getPreferenceScreenContent():
override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
    key = "foo",
    titleResId = R.string.foo,
    items = listOf(BooleanKey.A, IntKey.B)
)
```

### Migration Pattern: With Subscreens

**Before:**
```kotlin
override val subscreens = listOf(
    PreferenceSubScreen(
        key = "advanced",
        titleResId = R.string.advanced,
        keys = listOf(BooleanKey.X, BooleanKey.Y),
        content = { AdaptivePreferenceListForListKeys(...) }
    )
)
```

**After:**
```kotlin
items = listOf(
    BooleanKey.A,
    PreferenceSubScreenDef(
        key = "advanced",
        titleResId = R.string.advanced,
        items = listOf(BooleanKey.X, BooleanKey.Y)
    )
)

```

---

## Post-Migration Cleanup

### Files to DELETE after all migrations complete:

#### Core UI - Navigable Package (entire directory)
- `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/navigable/NavigablePreferenceContent.kt`
- `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/navigable/NavigablePreferenceExtensions.kt`
- `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/navigable/NavigablePreferenceItem.kt`
- `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/navigable/PreferenceNavigationHost.kt`
- `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/navigable/PreferenceSubScreenScaffold.kt`

#### All *PreferencesCompose.kt Files (31 files)
- See Phase 1-4 checklist below

### Code to REMOVE from each plugin (during migration):
1. `*PreferencesCompose` class instantiation
2. Related imports
3. `*PreferencesCompose.kt` file

### Code to REMOVE later (final cleanup phase - SEPARATE):
1. `addPreferenceScreen()` methods from all plugins
2. `MyPreferenceFragment` and XML preference system
3. Related imports

### Code to UPDATE:
- `AllPreferencesScreen.kt` - Remove NavigablePreferenceContent handling (lines 148-153)
- `PluginPreferencesScreen.kt` - Remove NavigablePreferenceContent branch
- `PluginBase` - Eventually remove `addPreferenceScreen()` abstract method

---

## Migration Checklist

### Phase 0: Prerequisites (before plugin migrations)

- [x] **Implement `hideParentScreenIfHidden` in Compose** ✅ DONE (tested & working)
  - Files modified:
    1. `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/AdaptivePreferenceList.kt`
       - Added `shouldShowSubScreen()` composable function
       - Used for navigation-based subscreen rendering
    2. `core/ui/src/main/kotlin/app/aaps/core/ui/compose/preference/PreferenceContentExtensions.kt`
       - Added `shouldShowSubScreenInline()` composable function
       - Used for inline collapsible subscreen rendering (AllPreferencesScreen)
  - Logic: Find items with `hideParentScreenIfHidden = true`, evaluate visibility, hide subscreen if controlling item hidden
  - Handles: BooleanPreferenceKey, IntPreferenceKey, LongPreferenceKey, IntentPreferenceKey

### Phase 1: Migrate Simple Plugins (no dynamic visibility)

**NOTES:**
- Pause after every 5-6 simple plugins or 1 complex plugin for build verification.
- If migration to PURE declarative preferences is not possible/easy, pause and elaborate before using `customContent`.
- Re-check recently migrated code for potential improvements.

**Improvements Made:**
- Added `StringKeyWithEntriesProvider` and `withEntriesProvider()` extension for context-dependent entries with empty message fallback
- This eliminates need for `customContent` in DanaR (Bluetooth device list pattern)
- Migrated DanaRv2Plugin and DanaRKoreanPlugin to use same `withEntriesProvider` pattern (were sharing DanaRPreferencesCompose.kt)

- [x] VirtualPumpPreferencesCompose.kt ✅
- [x] DanaRPreferencesCompose.kt ✅ (uses `withEntriesProvider` for Bluetooth device list)
- [x] DanaRv2Plugin ✅ (was using DanaRPreferencesCompose, now has own PreferenceSubScreenDef with `withEntriesProvider`)
- [x] DanaRKoreanPlugin ✅ (was using DanaRPreferencesCompose, now has own PreferenceSubScreenDef with `withEntriesProvider`)
- [x] DanaRSPreferencesCompose.kt ✅
- [x] DiaconnG8PreferencesCompose.kt ✅
- [x] EopatchPreferencesCompose.kt ✅
- [x] EquilPreferencesCompose.kt ✅
- [x] InsightPreferencesCompose.kt ✅
- [x] MedtrumPreferencesCompose.kt ✅ **FIXED:**
  - Fixed title to `R.string.medtrum_pump_setting`
  - Added `withEntriesProvider` for dynamic alarm entries based on pump type (NANO/300U: only Beep/Silent)
  - Added `updateMaxInsulinLimitsForPumpType()` for dynamic max values with value clamping
  - Added `EventPreferenceChange` subscription to update max limits when serial number changes
  - Serial number enabled state already in key via `enabledCondition = PreferenceEnabledCondition { !it.isPumpInitialized }`
  - Serial number validation already in key via `validator = StringValidator { ... }` (hex format + device type check)
- [x] GarminPreferencesCompose.kt ✅
- [x] OpenHumansPreferencesCompose.kt ✅
- [x] TidepoolPreferencesCompose.kt ✅ (only subscreens, no main content)
- [x] XdripPreferencesCompose.kt ✅ (has nested advanced subscreen)
- [x] SensitivityAAPSPreferencesCompose.kt ✅ (has nested advanced subscreen)
- [x] SensitivityOref1PreferencesCompose.kt ✅ (has nested advanced subscreen)
- [x] RandomBgPreferencesCompose.kt ✅
- [x] InsulinOrefFreePeakPreferencesCompose.kt ✅

### Phase 2: Migrate Medium Complexity (subscreens, no dynamic visibility) - ✅ ALL DONE
- [x] ComboV2PreferencesCompose.kt ✅ (uses `withDialog` for unpair confirmation)
- [x] MedtronicPreferencesCompose.kt ✅ (uses `withEntriesProvider` for RileyLink list)
- [x] OmnipodDashPreferencesCompose.kt ✅ (nested subscreens for notifications/alerts)
- [x] OmnipodErosPreferencesCompose.kt ✅ (nested subscreens for notifications/alerts)
- [x] NSClientPreferencesCompose.kt ✅ (4 nested subscreens: sync, alarm, connection, advanced)
- [x] WearPreferencesCompose.kt ✅ (3 nested subscreens: wizard, watchface, general)
- [x] SmsCommunicatorPreferencesCompose.kt ✅ (flat structure, no subscreens)
- [x] AutomationPreferencesCompose.kt ✅ (single key: location)
- [x] MaintenancePreferencesCompose.kt ✅ (2 nested subscreens: data choice, export)
- [x] SafetyPreferencesCompose.kt ✅ (uses `withEntries` for age)
- [x] AutotunePreferencesCompose.kt ✅ (flat structure, 4 keys)
- [x] LoopPreferencesCompose.kt ✅ (single key: open mode min change)

### Phase 3: Migrate Complex (dynamic visibility - may need customContent)
- [ ] OpenAPSAMAPreferencesCompose.kt
- [ ] OpenAPSAutoISFPreferencesCompose.kt
- [ ] OpenAPSSMBPreferencesCompose.kt (already hybrid)
- [ ] NSClientV3PreferencesCompose.kt (already hybrid)

### Phase 4: Base Classes
- [ ] AbstractBgSourcePlugin.kt
- [ ] AbstractBgSourceWithSensorInsertLogPlugin.kt

### Phase 5: Compose Cleanup (after ALL plugins migrated)

**DELETE entire `core/ui/.../navigable/` directory:**
- [ ] `NavigablePreferenceContent.kt`
- [ ] `NavigablePreferenceExtensions.kt`
- [ ] `NavigablePreferenceItem.kt`
- [ ] `PreferenceNavigationHost.kt`
- [ ] `PreferenceSubScreenScaffold.kt`

**UPDATE screens:**
- [ ] `AllPreferencesScreen.kt` - remove NavigablePreferenceContent branch
- [ ] `PluginPreferencesScreen.kt` - remove NavigablePreferenceContent branch

**CLEANUP:**
- [ ] Remove all NavigablePreferenceContent imports across codebase
- [ ] Remove unused dependencies

**RESULT:** Only `PreferenceSubScreenDef` + `PreferenceSubScreenRenderer` remain

**DELETE unused code created during migration:**

*Unused LazyListScope extensions:*
- [ ] `BasicPreference.kt:29-48` - `LazyListScope.basicPreference()`
- [ ] `Preference.kt:35-56` - `LazyListScope.preference()`
- [ ] `ListPreference.kt:58-89` - `LazyListScope.listPreference<T>()`

*Unused utilities:*
- [ ] `ScrollIndicators.kt:49-61` - `horizontalScrollIndicators()` (never called)
- [ ] `PaddingValuesExtensions.kt` - entire file unused:
  - `PaddingValues.copy()`
  - `PaddingValues.offset()`
  - `CopiedPaddingValues` class
  - `OffsetPaddingValues` class

*Deprecated code to remove:*
- [ ] `AdaptivePreferenceItem.kt:244-272` - `AdaptivePreferenceListForListKeys()` (deprecated)
- [ ] `PreferenceSubScreenDef.kt:23-24` - `keys` parameter (deprecated, use `items`)

*Icon helpers (evaluate if needed):*
- [ ] `Icons.kt:70-103` - `materialIcon()`, `materialPath()`, `MaterialIconDimension`

### Phase 6: XML/Legacy Cleanup (SEPARATE - future)
- [ ] Remove addPreferenceScreen() from all plugins
- [ ] Remove MyPreferenceFragment
- [ ] Remove XML preference infrastructure
- [ ] Remove PluginBase.addPreferenceScreen() method

---

## Per-Plugin Migration Log

### Template for Each Migration:
```
## [PluginName] Migration

### Source Files:
- Compose: `path/to/XxxPreferencesCompose.kt`
- Plugin: `path/to/XxxPlugin.kt`

### Current Structure:
- mainKeys: [list keys]
- subscreens: [list subscreens]
- Has customContent: yes/no
- Has dynamic visibility: yes/no

### Verification Checklist:
- [ ] Preference ORDER matches original `addPreferenceScreen()`
- [ ] All title resources present in PreferenceKey definitions
- [ ] All summary/dialogMessage resources present
- [ ] No new string resources created (reuse existing)

### Duplication Check:
- [ ] Any shared resources that need moving to core module?
- [ ] Any shared code patterns with other plugins?
- [ ] Any new keys needed? (prefer reusing existing)

### Migration Steps:
1. [ ] Read addPreferenceScreen() as REFERENCE (order, title, summary resources)
2. [ ] Read *PreferencesCompose.kt current implementation
3. [ ] Verify PreferenceKey has correct title/summary resources (compare with addPreferenceScreen)
4. [ ] Check for duplication opportunities
5. [ ] Move shared resources/code if needed
6. [ ] Replace getPreferenceScreenContent() with PreferenceSubScreenDef (PURE)
7. [ ] Verify order matches addPreferenceScreen()
8. [ ] Test preference screen works
9. [ ] Remove XxxPreferencesCompose.kt file
10. [ ] Remove compose class instantiation from plugin
11. [ ] Clean up unused imports
12. [ ] Build and verify

**NOTE:** addPreferenceScreen() stays UNCHANGED - it's removed in a separate final cleanup phase

### Notes:
(Any special considerations)
```

---

## CURRENT STATUS

**Completed:** 31 plugins migrated to PURE PreferenceSubScreenDef
**Remaining:** 6 files

### Remaining files to migrate:

**Phase 2:** ✅ ALL DONE

**Phase 3 (4 files - complex with dynamic visibility):**
- OpenAPSAMAPreferencesCompose.kt
- OpenAPSAutoISFPreferencesCompose.kt
- OpenAPSSMBPreferencesCompose.kt
- NSClientV3PreferencesCompose.kt

**Phase 4 (2 files - base classes):**
- AbstractBgSourcePlugin.kt
- AbstractBgSourceWithSensorInsertLogPlugin.kt

