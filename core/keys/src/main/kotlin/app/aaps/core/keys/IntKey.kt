package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.PreferenceEnabledCondition
import app.aaps.core.keys.interfaces.PreferenceVisibility

enum class IntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val entries: Map<Int, Int> = emptyMap(),
    override val defaultedBySM: Boolean = false,
    override val calculatedDefaultValue: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true,
    override val visibility: PreferenceVisibility = PreferenceVisibility.ALWAYS,
    override val enabledCondition: PreferenceEnabledCondition = PreferenceEnabledCondition.ALWAYS,
    override val unitsResId: Int? = null
) : IntPreferenceKey {

    OverviewCarbsButtonIncrement1(key = "carbs_button_increment_1", defaultValue = 5, min = -50, max = 50, titleResId = R.string.pref_title_carbs_button_increment_1, summaryResId = R.string.carb_increment_button_message, defaultedBySM = true, dependency = BooleanKey.OverviewShowCarbsButton, unitsResId = R.string.units_format_grams_range),
    OverviewCarbsButtonIncrement2(key = "carbs_button_increment_2", defaultValue = 10, min = -50, max = 50, titleResId = R.string.pref_title_carbs_button_increment_2, summaryResId = R.string.carb_increment_button_message, defaultedBySM = true, dependency = BooleanKey.OverviewShowCarbsButton, unitsResId = R.string.units_format_grams_range),
    OverviewCarbsButtonIncrement3(key = "carbs_button_increment_3", defaultValue = 20, min = -50, max = 50, titleResId = R.string.pref_title_carbs_button_increment_3, summaryResId = R.string.carb_increment_button_message, defaultedBySM = true, dependency = BooleanKey.OverviewShowCarbsButton, unitsResId = R.string.units_format_grams_range),
    OverviewEatingSoonDuration(key = "eatingsoon_duration", defaultValue = 45, min = 15, max = 120, titleResId = R.string.pref_title_eating_soon_duration, defaultedBySM = true, hideParentScreenIfHidden = true, unitsResId = R.string.units_format_min_range),
    OverviewActivityDuration(key = "activity_duration", defaultValue = 90, min = 15, max = 600, titleResId = R.string.pref_title_activity_duration, defaultedBySM = true, unitsResId = R.string.units_format_min_range),
    OverviewHypoDuration(key = "hypo_duration", defaultValue = 60, min = 15, max = 180, titleResId = R.string.pref_title_hypo_duration, defaultedBySM = true, unitsResId = R.string.units_format_min_range),
    OverviewCageWarning(key = "statuslights_cage_warning", defaultValue = 48, min = 24, max = 240, titleResId = R.string.pref_title_cage_warning, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_hours_range),
    OverviewCageCritical(key = "statuslights_cage_critical", defaultValue = 72, min = 24, max = 240, titleResId = R.string.pref_title_cage_critical, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_hours_range),
    OverviewIageWarning(key = "statuslights_iage_warning", defaultValue = 72, min = 24, max = 240, titleResId = R.string.pref_title_iage_warning, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, visibility = PreferenceVisibility.NON_PATCH_PUMP, unitsResId = R.string.units_format_hours_range),
    OverviewIageCritical(key = "statuslights_iage_critical", defaultValue = 144, min = 24, max = 240, titleResId = R.string.pref_title_iage_critical, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, visibility = PreferenceVisibility.NON_PATCH_PUMP, unitsResId = R.string.units_format_hours_range),
    OverviewSageWarning(key = "statuslights_sage_warning", defaultValue = 216, min = 24, max = 720, titleResId = R.string.pref_title_sage_warning, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_hours_range),
    OverviewSageCritical(key = "statuslights_sage_critical", defaultValue = 240, min = 24, max = 720, titleResId = R.string.pref_title_sage_critical, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_hours_range),
    OverviewSbatWarning(key = "statuslights_sbat_warning", defaultValue = 25, min = 0, max = 100, titleResId = R.string.pref_title_sbat_warning, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_percent_range),
    OverviewSbatCritical(key = "statuslights_sbat_critical", defaultValue = 5, min = 0, max = 100, titleResId = R.string.pref_title_sbat_critical, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_percent_range),
    OverviewBageWarning(key = "statuslights_bage_warning", defaultValue = 216, min = 24, max = 1000, titleResId = R.string.pref_title_bage_warning, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_hours_range),
    OverviewBageCritical(key = "statuslights_bage_critical", defaultValue = 240, min = 24, max = 1000, titleResId = R.string.pref_title_bage_critical, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_hours_range),
    OverviewResWarning(key = "statuslights_res_warning", defaultValue = 80, min = 0, max = 300, titleResId = R.string.pref_title_res_warning, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_insulin_int_range),
    OverviewResCritical(key = "statuslights_res_critical", defaultValue = 10, min = 0, max = 300, titleResId = R.string.pref_title_res_critical, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_insulin_int_range),
    OverviewBattWarning(key = "statuslights_bat_warning", defaultValue = 51, min = 0, max = 100, titleResId = R.string.pref_title_batt_warning, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_percent_range),
    OverviewBattCritical(key = "statuslights_bat_critical", defaultValue = 26, min = 0, max = 100, titleResId = R.string.pref_title_batt_critical, defaultedBySM = true, dependency = BooleanKey.OverviewShowStatusLights, unitsResId = R.string.units_format_percent_range),
    OverviewBolusPercentage(key = "boluswizard_percentage", defaultValue = 100, min = 10, max = 100, titleResId = R.string.pref_title_bolus_percentage, summaryResId = R.string.deliverpartofboluswizard, unitsResId = R.string.units_format_percent_range),
    OverviewResetBolusPercentageTime(key = "key_reset_boluswizard_percentage_time", defaultValue = 16, min = 6, max = 120, titleResId = R.string.pref_title_reset_bolus_percentage_time, summaryResId = R.string.deliver_part_of_boluswizard_reset_time, defaultedBySM = true, engineeringModeOnly = true, unitsResId = R.string.units_format_hours_range),
    ProtectionTimeout(
        key = "protection_timeout",
        defaultValue = 1,
        min = 1,
        max = 180,
        titleResId = R.string.pref_title_protection_timeout,
        defaultedBySM = true,
        unitsResId = R.string.units_format_sec_range,
        visibility = PreferenceVisibility.stringNotEmpty(StringKey.ProtectionMasterPassword)
    ),
    ProtectionTypeSettings(
        key = "settings_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        titleResId = R.string.pref_title_protection_type_settings,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            ProtectionType.NONE.ordinal to R.string.noprotection,
            ProtectionType.BIOMETRIC.ordinal to R.string.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to R.string.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to R.string.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to R.string.custom_pin
        ),
        visibility = PreferenceVisibility.stringNotEmpty(StringKey.ProtectionMasterPassword)
    ),
    ProtectionTypeApplication(
        key = "application_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        titleResId = R.string.pref_title_protection_type_application,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            ProtectionType.NONE.ordinal to R.string.noprotection,
            ProtectionType.BIOMETRIC.ordinal to R.string.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to R.string.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to R.string.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to R.string.custom_pin
        ),
        visibility = PreferenceVisibility.stringNotEmpty(StringKey.ProtectionMasterPassword)
    ),
    ProtectionTypeBolus(
        key = "bolus_protection",
        defaultValue = ProtectionType.NONE.ordinal,
        min = ProtectionType.NONE.ordinal,
        max = ProtectionType.CUSTOM_PIN.ordinal,
        titleResId = R.string.pref_title_protection_type_bolus,
        preferenceType = PreferenceType.LIST,
        entries = mapOf(
            ProtectionType.NONE.ordinal to R.string.noprotection,
            ProtectionType.BIOMETRIC.ordinal to R.string.biometric,
            ProtectionType.MASTER_PASSWORD.ordinal to R.string.master_password,
            ProtectionType.CUSTOM_PASSWORD.ordinal to R.string.custom_password,
            ProtectionType.CUSTOM_PIN.ordinal to R.string.custom_pin
        ),
        visibility = PreferenceVisibility.stringNotEmpty(StringKey.ProtectionMasterPassword)
    ),
    SafetyMaxCarbs(key = "treatmentssafety_maxcarbs", defaultValue = 48, min = 1, max = 200, titleResId = R.string.pref_title_max_carbs, unitsResId = R.string.units_format_grams_range),
    LoopOpenModeMinChange(key = "loop_openmode_min_change", defaultValue = 30, min = 0, max = 50, titleResId = R.string.pref_title_open_mode_min_change, summaryResId = R.string.loop_open_mode_min_change_summary, defaultedBySM = true, unitsResId = R.string.units_format_percent_range),
    ApsMaxSmbFrequency(key = "smbinterval", defaultValue = 3, min = 1, max = 10, titleResId = R.string.pref_title_smb_frequency, defaultedBySM = true, dependency = BooleanKey.ApsUseSmb, unitsResId = R.string.units_format_min_range),
    ApsMaxMinutesOfBasalToLimitSmb(key = "smbmaxminutes", defaultValue = 30, min = 15, max = 120, titleResId = R.string.pref_title_smb_max_minutes, defaultedBySM = true, dependency = BooleanKey.ApsUseSmb, unitsResId = R.string.units_format_min_range),
    ApsUamMaxMinutesOfBasalToLimitSmb(
        key = "uamsmbmaxminutes", defaultValue = 30, min = 15, max = 120, titleResId = R.string.pref_title_uam_smb_max_minutes, summaryResId = R.string.uam_smb_max_minutes, defaultedBySM = true, dependency = BooleanKey.ApsUseSmb,
        visibility = PreferenceVisibility { it.preferences.get(BooleanKey.ApsUseUam) },
        unitsResId = R.string.units_format_min_range
    ),
    ApsCarbsRequestThreshold(key = "carbsReqThreshold", defaultValue = 1, min = 1, max = 100, titleResId = R.string.pref_title_carbs_request_threshold, summaryResId = R.string.carbs_req_threshold_summary, defaultedBySM = true, unitsResId = R.string.units_format_grams_range),
    ApsAutoIsfHalfBasalExerciseTarget(key = "half_basal_exercise_target", defaultValue = 160, min = 120, max = 200, titleResId = R.string.pref_title_half_basal_exercise_target, summaryResId = R.string.half_basal_exercise_target_summary, defaultedBySM = true, unitsResId = R.string.units_format_mgdl_range),
    ApsAutoIsfIobThPercent(key = "iob_threshold_percent", defaultValue = 100, min = 10, max = 100, titleResId = R.string.pref_title_iob_threshold_percent, summaryResId = R.string.openapsama_iob_threshold_percent_summary, defaultedBySM = true, unitsResId = R.string.units_format_percent_range),
    ApsDynIsfAdjustmentFactor(key = "DynISFAdjust", defaultValue = 100, min = 1, max = 300, titleResId = R.string.pref_title_dynisf_adjustment_factor, summaryResId = R.string.dyn_isf_adjust_summary, dependency = BooleanKey.ApsUseDynamicSensitivity, unitsResId = R.string.units_format_percent_range),
    AutosensPeriod(key = "openapsama_autosens_period", defaultValue = 24, min = 4, max = 24, titleResId = R.string.pref_title_autosens_period, summaryResId = R.string.openapsama_autosens_period_summary, calculatedDefaultValue = true, unitsResId = R.string.units_format_hours_range),
    MaintenanceLogsAmount(key = "maintenance_logs_amount", defaultValue = 2, min = 1, max = 10, titleResId = R.string.pref_title_logs_amount, defaultedBySM = true),
    AlertsStaleDataThreshold(key = "missed_bg_readings_threshold", defaultValue = 30, min = 15, max = 10000, titleResId = R.string.pref_title_stale_data_threshold, defaultedBySM = true, dependency = BooleanKey.AlertMissedBgReading, unitsResId = R.string.units_format_min_range),
    AlertsPumpUnreachableThreshold(key = "pump_unreachable_threshold", defaultValue = 30, min = 30, max = 300, titleResId = R.string.pref_title_pump_unreachable_threshold, defaultedBySM = true, dependency = BooleanKey.AlertPumpUnreachable, unitsResId = R.string.units_format_min_range),
    InsulinOrefPeak(key = "insulin_oref_peak", defaultValue = 75, min = 35, max = 120, titleResId = R.string.pref_title_insulin_oref_peak, hideParentScreenIfHidden = true, unitsResId = R.string.units_format_min_range),

    AutotuneDefaultTuneDays(key = "autotune_default_tune_days", defaultValue = 5, min = 1, max = 30, titleResId = R.string.pref_title_autotune_days, summaryResId = R.string.autotune_default_tune_days_summary, unitsResId = R.string.units_format_days_range),

    SmsRemoteBolusDistance(
        key = "smscommunicator_remotebolusmindistance",
        defaultValue = 15,
        min = 3,
        max = 60,
        titleResId = R.string.pref_title_sms_remote_bolus_distance,
        unitsResId = R.string.units_format_min_range,
        // Enabled only when multiple phone numbers are configured (2FA requirement)
        enabledCondition = PreferenceEnabledCondition { ctx ->
            val allowedNumbers = ctx.preferences.get(StringKey.SmsAllowedNumbers)
            allowedNumbers.split(";").filter { it.trim().isNotEmpty() }.size >= 2
        }
    ),

    BgSourceRandomInterval(key = "randombg_interval_min", defaultValue = 5, min = 1, max = 15, titleResId = R.string.pref_title_random_bg_interval, defaultedBySM = true, unitsResId = R.string.units_format_min_range),
    NsClientAlarmStaleData(key = "ns_alarm_stale_data_value", defaultValue = 16, min = 15, max = 120, titleResId = R.string.pref_title_alarm_stale_data, unitsResId = R.string.units_format_min_range),
    NsClientUrgentAlarmStaleData(key = "ns_alarm_urgent_stale_data_value", defaultValue = 31, min = 30, max = 180, titleResId = R.string.pref_title_urgent_alarm_stale_data, unitsResId = R.string.units_format_min_range),

    SiteRotationUserProfile(key = "site_rotation_user_profile", defaultValue = 0, min = 0, max = 2, titleResId = R.string.pref_title_site_rotation_profile),
}