package com.healer.hyperos.shadeblendcontroller.policy

import android.content.SharedPreferences

enum class PanelRole {
    CONTROL_CENTER,
    NOTIFICATION_SHADE,
    COMBINED_WINDOW,
}

data class DimSettings(
    val enabled: Boolean = true,
    val dimPercent: Float = DEFAULT_DIM_PERCENT,
) {
    fun sanitized(): DimSettings = copy(
        dimPercent = ShadeDimPolicy.normalizePercent(dimPercent, DEFAULT_DIM_PERCENT),
    )

    companion object {
        const val DEFAULT_DIM_PERCENT = 20f
    }
}

object SettingsContract {
    const val PREFS_NAME = "shade_blend_settings"

    const val KEY_ENABLED = "enabled"
    const val KEY_UNIFIED_DIM_PERCENT = "unified_dim_percent"

    fun read(prefs: SharedPreferences): DimSettings = DimSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        dimPercent = prefs.getFloat(KEY_UNIFIED_DIM_PERCENT, DimSettings.DEFAULT_DIM_PERCENT),
    ).sanitized()

    fun write(prefs: SharedPreferences, settings: DimSettings) {
        val clean = settings.sanitized()
        prefs.edit()
            .putBoolean(KEY_ENABLED, clean.enabled)
            .putFloat(KEY_UNIFIED_DIM_PERCENT, clean.dimPercent)
            .apply()
    }
}
