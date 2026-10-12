package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestStringResonator : NonRealTimeTestCase() {

    /** Records every sample that arrives on its input. */
    private class Recorder : UnitGenerator() {
        val input = UnitInputPort("Input")
        val recorded = ArrayList<Float>()

        init {
            addPort(input)
        }

        override fun generate() {
            val values = input.getValues()
            for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
                recorded.add(values[i])
            }
        }
    }

    /** Complex value of one frequency component. */
    private class Component(val real: Double, val imaginary: Double) {
        val magnitude: Double
            get() = hypot(real, imaginary)
        val phase: Double
            get() = atan2(imaginary, real)
    }

    /** One period of zero-mean noise, like the classic Karplus-Strong excitation. */
    private fun makeBurst(frequency: Double): FloatSample {
        val random = Random(1234)
        val numFrames = (synthesisEngine.frameRate / frequency).toInt()
        val data = FloatArray(numFrames) { random.nextFloat() - 0.5f }
        val mean = data.average().toFloat()
        for (i in data.indices) data[i] -= mean
        return FloatSample(data)
    }

    private fun advance(seconds: Double) {
        checkSleepUntil(synthesisEngine.currentTime + seconds)
    }

    /**
     * Excite the resonator and record its output.
     * The brightness is only used by a StringResonator.
     */
    private fun record(resonator: FilterComb, frequency: Double, decayTime: Double,
                       brightness: Double, seconds: Double): List<Float> {
        val exciter = FixedRateMonoReader()
        val recorder = Recorder()
        synthesisEngine.add(exciter)
        synthesisEngine.add(resonator)
        synthesisEngine.add(recorder)
        exciter.output.connect(resonator.input)
        resonator.output.connect(recorder.input)

        resonator.frequency.set(frequency)
        resonator.decay.set(decayTime)
        (resonator as? StringResonator)?.brightness?.set(brightness)
        exciter.dataQueue.queue(makeBurst(frequency))

        synthesisEngine.start()
        recorder.start()
        advance(seconds)
        return recorder.recorded
    }

    /**
     * Measure the component at the frequency using a Hann window that is several periods long.
     * The phase is relative to frame zero, so phases from different windows can be compared.
     */
    private fun measureComponent(data: List<Float>, startFrame: Int, frequency: Double): Component {
        val frameRate = synthesisEngine.frameRate.toDouble()
        val windowSize = (8 * frameRate / frequency).roundToInt()
        var real = 0.0
        var imaginary = 0.0
        for (n in 0 until windowSize) {
            val window = 0.5 - (0.5 * cos(2.0 * PI * n / windowSize))
            val frame = startFrame + n
            val angle = 2.0 * PI * frequency * frame / frameRate
            val sample = data[frame] * window
            real += sample * cos(angle)
            imaginary -= sample * sin(angle)
        }
        return Component(real, imaginary)
    }

    /** Measure the frequency from how far the phase of the fundamental drifts between two windows. */
    private fun measureCentsError(resonator: FilterComb, frequency: Double): Double {
        val seconds = 0.5
        val data = record(resonator, frequency, decayTime = 5.0, brightness = 0.5, seconds = seconds)
        val frameRate = synthesisEngine.frameRate.toDouble()
        val start1 = (0.05 * frameRate).toInt()
        val start2 = (0.30 * frameRate).toInt()
        val first = measureComponent(data, start1, frequency)
        val second = measureComponent(data, start2, frequency)

        var phaseDrift = second.phase - first.phase
        while (phaseDrift > PI) phaseDrift -= 2.0 * PI
        while (phaseDrift < -PI) phaseDrift += 2.0 * PI
        val elapsed = (start2 - start1) / frameRate
        val frequencyError = phaseDrift / (2.0 * PI * elapsed)
        return 1200.0 * log2((frequency + frequencyError) / frequency)
    }

    private fun checkTuning(name: String, create: () -> FilterComb) {
        for (frequency in doubleArrayOf(55.0, 110.0, 220.0, 445.5, 880.0, 1234.5, 2000.0)) {
            setUp() // fresh engine for each frequency
            val cents = measureCentsError(create(), frequency)
            println("$name at $frequency Hz: error = $cents cents")
            assertTrue(abs(cents) < 0.1, "$name at $frequency Hz is $cents cents out of tune")
            tearDown()
        }
    }

    @Test
    fun testFixedTuning() {
        checkTuning("Fixed") { FixedStringResonator() }
    }

    @Test
    fun testVariableTuning() {
        checkTuning("Variable") { VariableStringResonator() }
    }

    @Test
    fun testCombAllPassTuning() {
        checkTuning("Comb AllPass") { FilterComb(interpolation = FilterComb.Interpolation.ALL_PASS) }
    }

    @Test
    fun testCombLagrangeTuning() {
        checkTuning("Comb Lagrange") { FilterComb(interpolation = FilterComb.Interpolation.LAGRANGE) }
    }

    /**
     * The fundamental should drop 30 dB in half of the decay time.
     * The decay should not depend on the brightness.
     */
    private fun checkDecay(name: String, brightnessValues: DoubleArray = doubleArrayOf(0.0, 0.5, 1.0),
                           create: () -> FilterComb) {
        val frequency = 220.0
        for (decayTime in doubleArrayOf(0.5, 3.0))
        for (brightness in brightnessValues) {
            setUp()
            val data = record(create(), frequency, decayTime, brightness, seconds = 0.15 + (decayTime / 2))
            val frameRate = synthesisEngine.frameRate.toDouble()
            val start1 = (0.05 * frameRate).toInt()
            val start2 = start1 + (0.5 * decayTime * frameRate).toInt()
            val before = measureComponent(data, start1, frequency).magnitude
            val after = measureComponent(data, start2, frequency).magnitude
            val dropDb = 20.0 * log10(before / after)
            println("$name decay $decayTime, brightness $brightness: fundamental dropped $dropDb dB in ${decayTime / 2} seconds")
            assertTrue(abs(dropDb - 30.0) < 0.2,
                "$name with decay $decayTime and brightness $brightness dropped $dropDb dB, expected 30")
            tearDown()
        }
    }

    @Test
    fun testFixedDecay() {
        checkDecay("Fixed") { FixedStringResonator() }
    }

    @Test
    fun testVariableDecay() {
        checkDecay("Variable") { VariableStringResonator() }
    }

    @Test
    fun testCombDecay() {
        // A comb has no brightness, so only run it once per decay time.
        val noBrightness = doubleArrayOf(0.5)
        checkDecay("Comb AllPass", noBrightness) { FilterComb(interpolation = FilterComb.Interpolation.ALL_PASS) }
        checkDecay("Comb Lagrange", noBrightness) { FilterComb(interpolation = FilterComb.Interpolation.LAGRANGE) }
    }

    @Test
    fun testAutoDisable() {
        val exciter = FixedRateMonoReader()
        val resonator = FixedStringResonator()
        val recorder = Recorder()
        synthesisEngine.add(exciter)
        synthesisEngine.add(resonator)
        synthesisEngine.add(recorder)
        exciter.output.connect(resonator.input)
        resonator.output.connect(recorder.input)

        resonator.frequency.set(220.0)
        resonator.decay.set(0.5)
        resonator.setupAutoDisable()
        assertFalse(resonator.isEnabled, "Resonator should start disabled")

        synthesisEngine.start()
        recorder.start()
        advance(0.05)
        assertFalse(resonator.isEnabled, "Resonator should stay disabled until excited")

        // Enable and excite together, like a noteOn would.
        synthesisEngine.queueCommand {
            resonator.isEnabled = true
            exciter.dataQueue.queue(makeBurst(220.0))
        }
        advance(0.2)
        assertTrue(resonator.isEnabled, "Resonator should stay enabled while ringing")

        // The level falls below -90 dB after about 1.5 * decay.
        advance(1.0)
        assertFalse(resonator.isEnabled, "Resonator should disable itself when quiet")

        // It wakes up for the next note.
        recorder.recorded.clear()
        synthesisEngine.queueCommand {
            resonator.isEnabled = true
            exciter.dataQueue.queue(makeBurst(220.0))
        }
        advance(0.1)
        assertTrue(resonator.isEnabled, "Resonator should ring again after being enabled")
        val peak = recorder.recorded.maxOf { abs(it) }
        assertTrue(peak > 0.1f, "Resonator should make sound after being enabled, peak = $peak")
    }
}
