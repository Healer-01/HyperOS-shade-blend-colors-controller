package com.healer.hyperos.shadeblendcontroller.policy

import kotlin.math.roundToInt

/**
 * Pure color and blend-recipe math for HyperOS 4 shade backdrops.
 * Kept free of Android framework classes so JVM tests can verify every branch directly.
 */
object ShadeDimPolicy {
    /** SkBlendMode::kSrcOver in Android / Xiaomi libhwui native blend arrays. */
    const val BLEND_MODE_SRC_OVER = 3

    /** Stock HyperOS 4 soft-light (bionics) array: shade_blend_colors_bionics. */
    val STOCK_BIONICS_BLEND_COLORS = intArrayOf(
        0xFF999999.toInt(), 21,
        0xFF0D8000.toInt(), 106,
    )

    /** Stock classic frosted blur array: shade_blend_colors. */
    val STOCK_CLASSIC_BLEND_COLORS = intArrayOf(
        0x52808080, 28,
    )

    /**
     * Saturation-only blend modes verified in libhwui:
     * 26 = mi_blend_saturation, 201 = adjust_saturation.
     * Mode 106 remaps Lab lightness (r/g bounds) and therefore adds stock darkness if retained.
     */
    fun preservesSaturation(mode: Int): Boolean = mode == 26 || mode == 201

    fun normalizePercent(percent: Float, fallback: Float = DimSettings.DEFAULT_DIM_PERCENT): Float {
        if (!percent.isFinite()) return fallback
        return percent.coerceIn(0f, 100f)
    }

    fun blackScrimColor(percent: Float, animationRatio: Float = 1f): Int {
        val ratio = if (animationRatio.isFinite()) animationRatio.coerceIn(0f, 1f) else 0f
        val baseAlpha = (255f * normalizePercent(percent) / 100f).roundToInt()
        val alpha = (baseAlpha * ratio).toInt().coerceIn(0, 255)
        return alpha shl 24
    }

    fun scaleColorAlpha(color: Int, percent: Float): Int {
        val factor = normalizePercent(percent) / 100f
        val origAlpha = (color ushr 24) and 0xFF
        val scaledAlpha = (origAlpha * factor).roundToInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (scaledAlpha shl 24)
    }

    fun buildBlendColors(
        original: IntArray?,
        dimPercent: Float,
        strategy: DimStrategy,
    ): IntArray {
        val normalized = normalizePercent(dimPercent)
        return when (strategy) {
            DimStrategy.LINEAR_SCRIM -> buildLinearScrim(original, normalized)
            DimStrategy.SCALE_STOCK_ALPHA -> buildScaledStock(original, normalized)
        }
    }

    private fun buildLinearScrim(original: IntArray?, dimPercent: Float): IntArray {
        var retained = 0
        if (original != null) {
            var i = 0
            while (i + 1 < original.size) {
                if (preservesSaturation(original[i + 1])) retained += 2
                i += 2
            }
        }
        val result = IntArray(retained + 2)
        var next = 0
        if (original != null) {
            var i = 0
            while (i + 1 < original.size) {
                if (preservesSaturation(original[i + 1])) {
                    result[next++] = original[i]
                    result[next++] = original[i + 1]
                }
                i += 2
            }
        }
        result[next] = blackScrimColor(dimPercent, 1f)
        result[next + 1] = BLEND_MODE_SRC_OVER
        return result
    }

    private fun buildScaledStock(original: IntArray?, dimPercent: Float): IntArray {
        val source = if (original != null && original.size >= 2) {
            original
        } else {
            STOCK_BIONICS_BLEND_COLORS
        }
        val pairCount = source.size / 2
        val result = IntArray(pairCount * 2)
        for (pair in 0 until pairCount) {
            val idx = pair * 2
            result[idx] = scaleColorAlpha(source[idx], dimPercent)
            result[idx + 1] = source[idx + 1]
        }
        return result
    }

    fun computeWindowDimColor(
        originalColor: Int,
        stockMaxAlpha: Int,
        dimPercent: Float,
    ): Int {
        val ratio = if (stockMaxAlpha > 0) {
            ((originalColor ushr 24) and 0xFF).toFloat() / stockMaxAlpha.toFloat()
        } else {
            0f
        }
        return blackScrimColor(dimPercent, ratio)
    }

    fun resolveDimPercent(
        settings: DimSettings,
        role: PanelRole,
        panelState: ActivePanelState,
    ): Float {
        if (settings.controlMode == ControlMode.UNIFIED) {
            return normalizePercent(settings.unifiedDimPercent)
        }
        val cc = normalizePercent(settings.controlCenterDimPercent)
        val notif = normalizePercent(settings.notificationShadeDimPercent)
        return when (role) {
            PanelRole.CONTROL_CENTER -> cc
            PanelRole.NOTIFICATION_SHADE -> notif
            PanelRole.COMBINED_WINDOW -> {
                if (panelState.switching) {
                    val t = if (panelState.switchFraction.isFinite()) {
                        panelState.switchFraction.coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    // ShadeSwitchControllerImpl: 0f = Control Center, 1f = Notification Center.
                    cc + (notif - cc) * t
                } else if (panelState.controlCenterVisible && !panelState.notificationVisible) {
                    cc
                } else if (panelState.notificationVisible && !panelState.controlCenterVisible) {
                    notif
                } else if (panelState.lastPanel == PanelRole.CONTROL_CENTER) {
                    cc
                } else {
                    notif
                }
            }
        }
    }

    fun formatRecipeHex(colors: IntArray?): String {
        if (colors == null || colors.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        var i = 0
        while (i + 1 < colors.size) {
            if (i > 0) sb.append(", ")
            sb.append(String.format("#%08X", colors[i]))
            sb.append(" (mode ")
            sb.append(colors[i + 1])
            sb.append(')')
            i += 2
        }
        if (colors.size % 2 != 0) {
            if (colors.size > 1) sb.append(", ")
            sb.append(colors.last())
        }
        sb.append(']')
        return sb.toString()
    }
}
