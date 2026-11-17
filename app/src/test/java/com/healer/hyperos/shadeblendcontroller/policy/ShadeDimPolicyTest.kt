package com.healer.hyperos.shadeblendcontroller.policy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShadeDimPolicyTest {

    @Test
    fun scaleStockAlphaPreservesBionicsAndClassicModes() {
        val stock = ShadeDimPolicy.STOCK_BIONICS_BLEND_COLORS
        val zero = ShadeDimPolicy.buildBlendColors(stock, 0f)
        assertArrayEquals(intArrayOf(0x00999999, 21, 0x000D8000, 106), zero)

        val twenty = ShadeDimPolicy.buildBlendColors(stock, 20f)
        assertArrayEquals(intArrayOf(0x33999999, 21, 0x330D8000, 106), twenty)

        val hundred = ShadeDimPolicy.buildBlendColors(stock, 100f)
        assertArrayEquals(stock, hundred)
        assertFalse(hundred === stock)

        val classic = ShadeDimPolicy.STOCK_CLASSIC_BLEND_COLORS
        val classicHalf = ShadeDimPolicy.buildBlendColors(classic, 50f)
        assertArrayEquals(intArrayOf(0x29808080, 28), classicHalf)
        assertFalse(classicHalf === classic)
    }

    @Test
    fun buildBlendColorsFallsBackToStockBionicsWhenInputNullOrTooShort() {
        val fromNull = ShadeDimPolicy.buildBlendColors(null, 20f)
        assertArrayEquals(intArrayOf(0x33999999, 21, 0x330D8000, 106), fromNull)

        val fromShort = ShadeDimPolicy.buildBlendColors(intArrayOf(0xFF999999.toInt()), 0f)
        assertArrayEquals(intArrayOf(0x00999999, 21, 0x000D8000, 106), fromShort)
    }

    @Test
    fun buildBlendColorsScalesSaturationAndMultiPairRecipesUniformly() {
        val mixed = intArrayOf(
            0xFF808080.toInt(), 26,
            0xFF0D8000.toInt(), 106,
        )
        val result = ShadeDimPolicy.buildBlendColors(mixed, 50f)
        assertArrayEquals(
            intArrayOf(0x80808080.toInt(), 26, 0x800D8000.toInt(), 106),
            result,
        )
    }

    @Test
    fun windowDimColorScalesAnimatedAlphaAndNormalizesOutOfRangeInputs() {
        assertEquals(0, ShadeDimPolicy.computeWindowDimColor(0x66000000, 0x66, 0f))
        assertEquals(0x33000000, ShadeDimPolicy.computeWindowDimColor(0x66000000, 0x66, 20f))
        assertEquals(0x19000000, ShadeDimPolicy.computeWindowDimColor(0x33000000, 0x66, 20f))
        assertEquals(0, ShadeDimPolicy.computeWindowDimColor(0, 0x66, 100f))

        assertEquals(0f, ShadeDimPolicy.normalizePercent(-12f), 0.001f)
        assertEquals(100f, ShadeDimPolicy.normalizePercent(250f), 0.001f)
        assertEquals(20f, ShadeDimPolicy.normalizePercent(Float.NaN), 0.001f)
        assertEquals(20f, ShadeDimPolicy.normalizePercent(Float.POSITIVE_INFINITY), 0.001f)
    }

    @Test
    fun sanitizedSettingsClampsDimPercentAndFormatsRecipeHex() {
        val clampedLow = DimSettings(enabled = true, dimPercent = -5f).sanitized()
        assertEquals(0f, clampedLow.dimPercent, 0.001f)

        val clampedHigh = DimSettings(enabled = true, dimPercent = 120f).sanitized()
        assertEquals(100f, clampedHigh.dimPercent, 0.001f)

        val recipe = ShadeDimPolicy.buildBlendColors(
            ShadeDimPolicy.STOCK_BIONICS_BLEND_COLORS,
            20f,
        )
        assertEquals("[#33999999 (mode 21), #330D8000 (mode 106)]", ShadeDimPolicy.formatRecipeHex(recipe))
        assertEquals("[]", ShadeDimPolicy.formatRecipeHex(null))
    }
}
