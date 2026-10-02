package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.data.FloatSample
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Demonstrates that units inside a started [Circuit] are generated twice per block
 * when their outputs are also pulled by a downstream unit.
 *
 * [UnitGenerator.pullData] guards against running a unit twice in one block using
 * lastFrameCount, but [Circuit.generate] calls generate() directly on its subunits,
 * bypassing that guard.
 */
class TestCircuitPullData : NonRealTimeTestCase() {

    /** Counts how many times generate() is called. */
    private class GenerateCounter : UnitGenerator() {
        val output = UnitOutputPort("Output")
        var count = 0

        init {
            addPort(output)
        }

        override fun generate() {
            count++
        }
    }

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

    private class CounterCircuit : Circuit() {
        val counter = GenerateCounter()

        init {
            add(counter)
        }
    }

    private class ReaderCircuit : Circuit() {
        val reader = VariableRateMonoReader()

        init {
            add(reader)
        }
    }

    private fun runBlocks(numBlocks: Int) {
        val startFrame = synthesisEngine.frameCount
        val endFrame = startFrame + numBlocks * Synthesizer.FRAMES_PER_BLOCK
        checkSleepUntil(endFrame / synthesisEngine.frameRate.toDouble())
    }

    @Test
    fun testUnitInStartedCircuitGeneratesOncePerBlock() {
        val circuit = CounterCircuit()
        val recorder = Recorder()
        synthesisEngine.add(circuit)
        synthesisEngine.add(recorder)
        circuit.counter.output.connect(recorder.input)

        synthesisEngine.start()
        circuit.start()
        recorder.start()
        runBlocks(4) // let the start commands execute

        val countBefore = circuit.counter.count
        val frameBefore = synthesisEngine.frameCount
        runBlocks(100)
        // The engine may run a whole buffer of blocks at a time, so measure what actually ran.
        val numBlocks = ((synthesisEngine.frameCount - frameBefore) / Synthesizer.FRAMES_PER_BLOCK).toInt()
        val calls = circuit.counter.count - countBefore

        assertEquals(numBlocks, calls,
            "Unit inside a started Circuit should generate once per block, " +
                "but generated $calls times in $numBlocks blocks.")
    }

    /** Control case: the same reader outside a Circuit plays continuously. */
    @Test
    fun testReaderOutsideCircuitIsContinuous() {
        val reader = VariableRateMonoReader()
        val recorder = Recorder()
        synthesisEngine.add(reader)
        synthesisEngine.add(recorder)
        reader.output.connect(recorder.input)

        checkRampPlayback(reader, recorder)
    }

    /** The bug: a reader inside a started Circuit skips 8 frames at every block boundary. */
    @Test
    fun testReaderInStartedCircuitIsContinuous() {
        val circuit = ReaderCircuit()
        val recorder = Recorder()
        synthesisEngine.add(circuit)
        synthesisEngine.add(recorder)
        circuit.reader.output.connect(recorder.input)

        circuit.start()
        checkRampPlayback(circuit.reader, recorder)
    }

    /**
     * Play a ramp at its natural rate. Each output frame should be exactly one
     * ramp step above the previous one.
     */
    private fun checkRampPlayback(reader: VariableRateMonoReader, recorder: Recorder) {
        val numFrames = 4096
        val step = 1.0f / numFrames
        val sample = FloatSample(FloatArray(numFrames) { it * step })

        reader.rate.set(sample.frameRate)
        reader.dataQueue.queue(sample)

        synthesisEngine.start()
        recorder.start()
        runBlocks(numFrames / Synthesizer.FRAMES_PER_BLOCK / 4)

        val data = recorder.recorded
        // Skip the silence before the queued sample starts.
        val first = data.indexOfFirst { it > 0f }
        assertTrue(first >= 0, "Reader produced no output.")
        val end = minOf(data.size, first + 256)
        assertTrue(end - first > 64, "Not enough output recorded.")

        for (i in first + 1 until end) {
            val delta = data[i] - data[i - 1]
            assertTrue(abs(delta - step) < step * 0.01f,
                "Discontinuity at recorded frame $i: jumped ${delta / step} ramp steps, expected 1.")
        }
    }
}
