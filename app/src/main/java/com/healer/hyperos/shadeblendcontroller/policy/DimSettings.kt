package com.healer.hyperos.shadeblendcontroller.policy

import android.content.SharedPreferences

enum class ControlMode {
    UNIFIED,
    SEPARATE;

    companion object {
        fun fromStorage(value: String?): ControlMode =
            entries.firstOrNull { it.name == value } ?: UNIFIED
    }
}

enum class DimStrategy {
    /**
     * Strip stock Lab lightness compression (mode 106) and dark tint (mode 21 / 28),
     * keeping saturation-only modes (26 / 201), then apply linear black SrcOver (mode 3).
     */
    LINEAR_SCRIM,

    /**
     * Keep the system recipe's blend modes (e.g. 21 + 106 for bionics or 28 for classic)
     * and scale each color entry's alpha channel proportionally by dimPercent.
     */
    SCALE_STOCK_ALPHA,

    /**
     * Keep the stock recipe array intact and scale ONLY one chosen entry's alpha channel
     * (either mode 106 Lab lightness compression or mode 21 neutral gray tint),
     * leaving all other stock entries completely untouched.
     */
    SINGLE_STOCK_ENTRY;

    companion object {
        fun fromStorage(value: String?): DimStrategy =
            entries.firstOrNull { it.name == value } ?: LINEAR_SCRIM
    }
}

enum class SingleEntryTarget(val mode: Int) {
    /** Entry 1 in shade_blend_colors_bionics: #FF0D8000 (mode 106 Lab lightness compression). */
    BIONICS_LIGHTNESS_106(106),

    /** Entry 0 in shade_blend_colors_bionics: #FF999999 (mode 21 neutral gray tint). */
    BIONICS_TINT_21(21);

    companion object {
        fun fromStorage(value: String?): SingleEntryTarget =
            entries.firstOrNull { it.name == value } ?: BIONICS_LIGHTNESS_106
    }
}

enum class PanelRole {
    CONTROL_CENTER,
    NOTIFICATION_SHADE,
    COMBINED_WINDOW,
}

data class ActivePanelState(
    val controlCenterVisible: Boolean = false,
    val notificationVisible: Boolean = false,
    val switching: Boolean = false,
    val switchFraction: Float = 1f,
    val lastPanel: PanelRole = PanelRole.NOTIFICATION_SHADE,
)

data class DimSettings(
    val enabled: Boolean = true,
    val controlMode: ControlMode = ControlMode.UNIFIED,
    val dimStrategy: DimStrategy = DimStrategy.LINEAR_SCRIM,
    val singleEntryTarget: SingleEntryTarget = SingleEntryTarget.BIONICS_LIGHTNESS_106,
    val unifiedDimPercent: Float = DEFAULT_DIM_PERCENT,
    val controlCenterDimPercent: Float = DEFAULT_DIM_PERCENT,
    val notificationShadeDimPercent: Float = DEFAULT_DIM_PERCENT,
) {
    fun sanitized(): DimSettings = copy(
        unifiedDimPercent = ShadeDimPolicy.normalizePercent(unifiedDimPercent, DEFAULT_DIM_PERCENT),
        controlCenterDimPercent = ShadeDimPolicy.normalizePercent(controlCenterDimPercent, DEFAULT_DIM_PERCENT),
        notificationShadeDimPercent = ShadeDimPolicy.normalizePercent(notificationShadeDimPercent, DEFAULT_DIM_PERCENT),
    )

    companion object {
        const val DEFAULT_DIM_PERCENT = 20f
    }
}

object SettingsContract {
    const val PREFS_NAME = "shade_blend_settings"

    const val KEY_ENABLED = "enabled"
    const val KEY_CONTROL_MODE = "control_mode"
    const val KEY_DIM_STRATEGY = "dim_strategy"
    const val KEY_SINGLE_ENTRY_TARGET = "single_entry_target"
    const val KEY_UNIFIED_DIM_PERCENT = "unified_dim_percent"
    const val KEY_CONTROL_CENTER_DIM_PERCENT = "control_center_dim_percent"
    const val KEY_NOTIFICATION_SHADE_DIM_PERCENT = "notification_shade_dim_percent"

    fun read(prefs: SharedPreferences): DimSettings = DimSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        controlMode = ControlMode.fromStorage(prefs.getString(KEY_CONTROL_MODE, null)),
        dimStrategy = DimStrategy.fromStorage(prefs.getString(KEY_DIM_STRATEGY, null)),
        singleEntryTarget = SingleEntryTarget.fromStorage(prefs.getString(KEY_SINGLE_ENTRY_TARGET, null)),
        unifiedDimPercent = prefs.getFloat(KEY_UNIFIED_DIM_PERCENT, DimSettings.DEFAULT_DIM_PERCENT),
        controlCenterDimPercent = prefs.getFloat(KEY_CONTROL_CENTER_DIM_PERCENT, DimSettings.DEFAULT_DIM_PERCENT),
        notificationShadeDimPercent = prefs.getFloat(KEY_NOTIFICATION_SHADE_DIM_PERCENT, DimSettings.DEFAULT_DIM_PERCENT),
    ).sanitized()

    fun write(prefs: SharedPreferences, settings: DimSettings) {
        val clean = settings.sanitized()
        prefs.edit()
            .putBoolean(KEY_ENABLED, clean.enabled)
            .putString(KEY_CONTROL_MODE, clean.controlMode.name)
            .putString(KEY_DIM_STRATEGY, clean.dimStrategy.name)
            .putString(KEY_SINGLE_ENTRY_TARGET, clean.singleEntryTarget.name)
            .putFloat(KEY_UNIFIED_DIM_PERCENT, clean.unifiedDimPercent)
            .putFloat(KEY_CONTROL_CENTER_DIM_PERCENT, clean.controlCenterDimPercent)
            .putFloat(KEY_NOTIFICATION_SHADE_DIM_PERCENT, clean.notificationShadeDimPercent)
            .apply()
    }
}
