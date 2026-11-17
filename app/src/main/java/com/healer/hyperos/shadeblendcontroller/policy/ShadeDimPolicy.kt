package com.healer.hyperos.shadeblendcontroller.policy

import kotlin.math.roundToInt

/**
 * Pure color and blend-recipe math for HyperOS 4 shade backdrops.
 * Uses the stock overall alpha scaling strategy for blend arrays and keeps
 * Android framework classes out so JVM unit tests can verify every branch directly.
 */
object ShadeDimPolicy {
    /** Stock HyperOS 4 soft-light (bionics) array: shade_blend_colors_bionics. */
    val STOCK_BIONICS_BLEND_COLORS = intArrayOf(
        0xFF999999.toInt(), 21,
        0xFF0D8000.toInt(), 106,
    )

    /** Stock classic frosted blur array: shade_blend_colors. */
    val STOCK_CLASSIC_BLEND_COLORS = intArrayOf(
        0x52808080, 28,
    )

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

    /**
     * Keep the system recipe's blend modes (e.g. 21 + 106 for bionics or 28 for classic)
     * and scale each color entry's alpha channel proportionally by [dimPercent].
     */
    fun buildBlendColors(
        original: IntArray?,
        dimPercent: Float,
    ): IntArray {
        val normalized = normalizePercent(dimPercent)
        val source = if (original != null && original.size >= 2) {
            original
        } else {
            STOCK_BIONICS_BLEND_COLORS
        }
        val pairCount = source.size / 2
        val result = IntArray(pairCount * 2)
        for (pair in 0 until pairCount) {
            val idx = pair * 2
            result[idx] = scaleColorAlpha(source[idx], normalized)
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
