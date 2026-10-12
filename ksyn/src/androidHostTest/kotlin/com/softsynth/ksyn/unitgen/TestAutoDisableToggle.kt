package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.data.FloatSample
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * After turning auto disable off and on again, the original target should be disabled
 * when the unit finishes, not the unit itself.
 */
class TestAutoDisableToggle : NonRealTimeTestCase() {

    private fun advance(seconds: Double) {
        checkSleepUntil(synthesisEngine.currentTime + seconds)
    }

    /** Set up the target, toggle off and on, let the unit finish, then check who got disabled. */
    private fun checkTargetRemembered(unit: UnitGenerator, finish: () -> Unit) {
        val target = PassThrough()
        synthesisEngine.add(unit)
        synthesisEngine.add(target)
        val name = unit::class.simpleName
        val supporter = unit as SupportsAutoDisable

        supporter.setupAutoDisable(target)
        supporter.setAutoDisableEnabled(false)
        supporter.setAutoDisableEnabled(true)

        synthesisEngine.start()
        unit.start()
        finish()
        advance(0.2)

        assertFalse(target.isEnabled, "$name should disable the original target when it finishes")
        assertTrue(unit.isEnabled, "$name should not disable itself")
    }

    @Test
    fun testEnvelope() {
        val envelope = EnvelopeAttackDecay()
        envelope.attack.set(0.01)
        envelope.decay.set(0.01)
        checkTargetRemembered(envelope) {
            envelope.input.trigger()
        }
    }

    @Test
    fun testReader() {
        val reader = FixedRateMonoReader()
        checkTargetRemembered(reader) {
            reader.dataQueue.queue(FloatSample(FloatArray(100) { 0.5f }))
        }
    }

    @Test
    fun testStringResonator() {
        // With no excitation the string is quiet, so it should disable the target soon.
        checkTargetRemembered(FixedStringResonator()) {}
    }
}
