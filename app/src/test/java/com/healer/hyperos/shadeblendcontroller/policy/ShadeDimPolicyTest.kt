package com.healer.hyperos.shadeblendcontroller.policy

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShadeDimPolicyTest {

    @Test
    fun linearScrimStripsStockBionicsLightnessCompressionAndAppliesSrcOver() {
        val stock = ShadeDimPolicy.STOCK_BIONICS_BLEND_COLORS
        val atZero = ShadeDimPolicy.buildBlendColors(stock, 0f, DimStrategy.LINEAR_SCRIM)
        assertArrayEquals(intArrayOf(0x00000000, 3), atZero)

        val atTwenty = ShadeDimPolicy.buildBlendColors(stock, 20f, DimStrategy.LINEAR_SCRIM)
        assertArrayEquals(intArrayOf(0x33000000, 3), atTwenty)

        val atHundred = ShadeDimPolicy.buildBlendColors(stock, 100f, DimStrategy.LINEAR_SCRIM)
        assertArrayEquals(intArrayOf(0xFF000000.toInt(), 3), atHundred)

        // Stock array must remain untouched for clean disable/restore
        assertArrayEquals(intArrayOf(0xFF999999.toInt(), 21, 0xFF0D8000.toInt(), 106), stock)
    }

    @Test
    fun linearScrimPreservesSaturationModesOnly() {
        val mixed = intArrayOf(
            0xFF0D8000.toInt(), 106,
            0xFF808080.toInt(), 26,
            0xFF800000.toInt(), 201,
        )
        val result = ShadeDimPolicy.buildBlendColors(mixed, 20f, DimStrategy.LINEAR_SCRIM)
        assertArrayEquals(
            intArrayOf(0xFF808080.toInt(), 26, 0xFF800000.toInt(), 201, 0x33000000, 3),
            result,
        )
    }

    @Test
    fun scaleStockAlphaPreservesBionicsAndClassicModes() {
        val stock = ShadeDimPolicy.STOCK_BIONICS_BLEND_COLORS
        val zero = ShadeDimPolicy.buildBlendColors(stock, 0f, DimStrategy.SCALE_STOCK_ALPHA)
        assertArrayEquals(intArrayOf(0x00999999, 21, 0x000D8000, 106), zero)

        val hundred = ShadeDimPolicy.buildBlendColors(stock, 100f, DimStrategy.SCALE_STOCK_ALPHA)
        assertArrayEquals(stock, hundred)

        val classic = ShadeDimPolicy.STOCK_CLASSIC_BLEND_COLORS
        val classicHalf = ShadeDimPolicy.buildBlendColors(classic, 50f, DimStrategy.SCALE_STOCK_ALPHA)
        assertArrayEquals(intArrayOf(0x29808080, 28), classicHalf)
        assertFalse(classicHalf === classic)
    }

    @Test
    fun unifiedAndSeparateResolutionMatchesActivePanelAndSwitchProgress() {
        val unifiedSettings = DimSettings(
            controlMode = ControlMode.UNIFIED,
            unifiedDimPercent = 35f,
            controlCenterDimPercent = 10f,
            notificationShadeDimPercent = 60f,
        )
        val ccActive = ActivePanelState(controlCenterVisible = true, notificationVisible = false)
        assertEquals(35f, ShadeDimPolicy.resolveDimPercent(unifiedSettings, PanelRole.CONTROL_CENTER, ccActive), 0.001f)
        assertEquals(35f, ShadeDimPolicy.resolveDimPercent(unifiedSettings, PanelRole.NOTIFICATION_SHADE, ccActive), 0.001f)
        assertEquals(35f, ShadeDimPolicy.resolveDimPercent(unifiedSettings, PanelRole.COMBINED_WINDOW, ccActive), 0.001f)

        val separateSettings = unifiedSettings.copy(controlMode = ControlMode.SEPARATE)
        assertEquals(10f, ShadeDimPolicy.resolveDimPercent(separateSettings, PanelRole.CONTROL_CENTER, ccActive), 0.001f)
        assertEquals(60f, ShadeDimPolicy.resolveDimPercent(separateSettings, PanelRole.NOTIFICATION_SHADE, ccActive), 0.001f)
        assertEquals(10f, ShadeDimPolicy.resolveDimPercent(separateSettings, PanelRole.COMBINED_WINDOW, ccActive), 0.001f)

        val notifActive = ActivePanelState(controlCenterVisible = false, notificationVisible = true)
        assertEquals(60f, ShadeDimPolicy.resolveDimPercent(separateSettings, PanelRole.COMBINED_WINDOW, notifActive), 0.001f)

        val switchingHalf = ActivePanelState(switching = true, switchFraction = 0.5f)
        assertEquals(35f, ShadeDimPolicy.resolveDimPercent(separateSettings, PanelRole.COMBINED_WINDOW, switchingHalf), 0.001f)
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
    }

    @Test
    fun separateCombinedWindowFallsBackToLastActivePanelAndFormatsRecipeHex() {
        val settings = DimSettings(
            controlMode = ControlMode.SEPARATE,
            unifiedDimPercent = 50f,
            controlCenterDimPercent = -5f,
            notificationShadeDimPercent = 120f,
        ).sanitized()
        assertEquals(0f, settings.controlCenterDimPercent, 0.001f)
        assertEquals(100f, settings.notificationShadeDimPercent, 0.001f)

        val bothHiddenLastCc = ActivePanelState(
            controlCenterVisible = false,
            notificationVisible = false,
            switching = false,
            lastPanel = PanelRole.CONTROL_CENTER,
        )
        assertEquals(0f, ShadeDimPolicy.resolveDimPercent(settings, PanelRole.COMBINED_WINDOW, bothHiddenLastCc), 0.001f)

        val recipe = ShadeDimPolicy.buildBlendColors(
            ShadeDimPolicy.STOCK_BIONICS_BLEND_COLORS,
            20f,
            DimStrategy.LINEAR_SCRIM,
        )
        assertEquals("[#33000000 (mode 3)]", ShadeDimPolicy.formatRecipeHex(recipe))
        assertEquals("[]", ShadeDimPolicy.formatRecipeHex(null))
    }

    @Test
    fun singleStockEntryScalesOnlyChosenBionicsLayerAndLeavesOtherUntouched() {
        val stock = ShadeDimPolicy.STOCK_BIONICS_BLEND_COLORS

        val lower106 = ShadeDimPolicy.buildBlendColors(
            original = stock,
            dimPercent = 20f,
            strategy = DimStrategy.SINGLE_STOCK_ENTRY,
            singleEntryTarget = SingleEntryTarget.BIONICS_LIGHTNESS_106,
        )
        assertArrayEquals(
            intArrayOf(0xFF999999.toInt(), 21, 0x330D8000, 106),
            lower106,
        )

        val lower21 = ShadeDimPolicy.buildBlendColors(
            original = stock,
            dimPercent = 20f,
            strategy = DimStrategy.SINGLE_STOCK_ENTRY,
            singleEntryTarget = SingleEntryTarget.BIONICS_TINT_21,
        )
        assertArrayEquals(
            intArrayOf(0x33999999, 21, 0xFF0D8000.toInt(), 106),
            lower21,
        )

        // Stock constant must remain untouched
        assertArrayEquals(intArrayOf(0xFF999999.toInt(), 21, 0xFF0D8000.toInt(), 106), stock)
    }

    @Test
    fun singleStockEntryFallsBackToFirstNonSaturationPairWhenTargetModeAbsent() {
        val withSaturation = intArrayOf(
            0xFF808080.toInt(), 26,
            0x52808080, 28,
        )
        val result = ShadeDimPolicy.buildBlendColors(
            original = withSaturation,
            dimPercent = 50f,
            strategy = DimStrategy.SINGLE_STOCK_ENTRY,
            singleEntryTarget = SingleEntryTarget.BIONICS_LIGHTNESS_106,
        )
        assertArrayEquals(intArrayOf(0xFF808080.toInt(), 26, 0x29808080, 28), result)
    }

    @Test
    fun singleStockEntryHandlesNullRecipeBoundariesAndStorageParsing() {
        val fallbackZero = ShadeDimPolicy.buildBlendColors(
            original = null,
            dimPercent = 0f,
            strategy = DimStrategy.SINGLE_STOCK_ENTRY,
            singleEntryTarget = SingleEntryTarget.BIONICS_LIGHTNESS_106,
        )
        assertArrayEquals(intArrayOf(0xFF999999.toInt(), 21, 0x000D8000, 106), fallbackZero)

        val fallbackHundred = ShadeDimPolicy.buildBlendColors(
            original = null,
            dimPercent = 100f,
            strategy = DimStrategy.SINGLE_STOCK_ENTRY,
            singleEntryTarget = SingleEntryTarget.BIONICS_TINT_21,
        )
        assertArrayEquals(intArrayOf(0xFF999999.toInt(), 21, 0xFF0D8000.toInt(), 106), fallbackHundred)

        assertEquals(DimStrategy.SINGLE_STOCK_ENTRY, DimStrategy.fromStorage("SINGLE_STOCK_ENTRY"))
        assertEquals(DimStrategy.LINEAR_SCRIM, DimStrategy.fromStorage("UNKNOWN_VALUE"))
        assertEquals(SingleEntryTarget.BIONICS_TINT_21, SingleEntryTarget.fromStorage("BIONICS_TINT_21"))
        assertEquals(SingleEntryTarget.BIONICS_LIGHTNESS_106, SingleEntryTarget.fromStorage(null))
    }
}
