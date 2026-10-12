package com.softsynth.ksyn.unitgen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSupportsAutoDisable {

    private fun createUnits(): List<UnitGenerator> = listOf(
        EnvelopeAttackDecay(),
        EnvelopeDAHDSR(),
        FixedRateMonoReader(),
        FixedRateStereoReader(),
        VariableRateMonoReader(),
        VariableRateStereoReader(),
        FixedStringResonator(),
        VariableStringResonator(),
        FilterComb(),
    )

    @Test
    fun testDefaultTargetIsSelf() {
        for (unit in createUnits()) {
            val name = unit::class.simpleName
            assertTrue(unit is SupportsAutoDisable, "$name should support auto disable")
            assertTrue(unit.isEnabled, "$name should start enabled")
            unit.setupAutoDisable()
            assertFalse(unit.isEnabled, "$name should disable itself until it is started")
        }
    }

    @Test
    fun testOtherTarget() {
        for (unit in createUnits()) {
            val name = unit::class.simpleName
            val target = PassThrough()
            (unit as SupportsAutoDisable).setupAutoDisable(target)
            assertFalse(target.isEnabled, "$name should disable the target until it is started")
            assertTrue(unit.isEnabled, "$name should not disable itself when given a target")
        }
    }

    @Test
    fun testToggle() {
        for (unit in createUnits()) {
            val name = unit::class.simpleName
            val supporter = unit as SupportsAutoDisable
            val target = PassThrough()
            assertFalse(supporter.isAutoDisableEnabled, "$name should start with auto disable off")

            supporter.setupAutoDisable(target)
            assertTrue(supporter.isAutoDisableEnabled, "$name setup should turn auto disable on")
            assertFalse(target.isEnabled, "$name setup should disable the target")

            supporter.setAutoDisableEnabled(false)
            assertFalse(supporter.isAutoDisableEnabled, "$name should turn auto disable off")
            assertTrue(target.isEnabled, "$name should enable the target when turned off")

            supporter.setAutoDisableEnabled(true)
            assertTrue(supporter.isAutoDisableEnabled, "$name should turn auto disable on again")
            assertTrue(target.isEnabled, "$name should not cut off the target when turned on")
        }
    }

    @Test
    fun testTurningOffWhenNeverEnabledLeavesUnitAlone() {
        for (unit in createUnits()) {
            val name = unit::class.simpleName
            unit.isEnabled = false
            (unit as SupportsAutoDisable).setAutoDisableEnabled(false)
            assertFalse(unit.isEnabled, "$name should not be enabled by turning off auto disable that was never on")
        }
    }

    @Test
    fun testUnsupportedUnit() {
        val unit: UnitGenerator = SineOscillator()
        assertFalse(unit is SupportsAutoDisable, "SineOscillator does not support auto disable")
    }
}
