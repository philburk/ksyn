package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.util.SampleLoader
import com.softsynth.ksyn.math.AudioMath
import com.softsynth.ksyn.shared.time.TimeStamp
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestVariableRateMonoReader : NonRealTimeTestCase() {
    @Test
    fun testClarinetPitch() {
        val localDir = System.getProperty("user.dir")
        val file = File(localDir, "../demo/src/commonMain/composeResources/files/Clarinet.wav")
        if (!file.exists()) {
            println("Skipping test: " + file.absolutePath + " not found.")
            return
        }

        val rawBytes = file.readBytes()
        val sample = SampleLoader.loadFloatSample(rawBytes)

        val reader = VariableRateMonoReader()
        val pitchDetector = PitchDetector()

        synthesisEngine.add(reader)
        synthesisEngine.add(pitchDetector)

        reader.output.connect(0, pitchDetector.input, 0)

        reader.rate.set(sample.frameRate)
        reader.dataQueue.queueOn(sample)

        synthesisEngine.start()
        pitchDetector.start() // start head of tree

        // Wait for enough cycles executing explicitly
        var conf = 0.0f
        var detectedPeriod = 0.0
        
        for (i in 0 until 50) {
            checkSleepUntil(synthesisEngine.currentTime + 0.1) // 100ms jumps
            conf = pitchDetector.confidence.value
            detectedPeriod = pitchDetector.period.value.toDouble()
            println("Test Step $i - period: $detectedPeriod, conf: $conf")
            if (conf > 0.5) break
        }
        
        assertTrue(conf > 0.5, "No sound or pitch detected, confidence was $conf")
        
        // Pitch 60 (Middle C) is 261.6255653005986 Hz
        // Period = frameRate / frequency = 44100 / 261.6255653005986 = ~168.56
        val expectedPeriod = synthesisEngine.frameRate / AudioMath.pitchToFrequency(60.0)
        
        assertEquals(expectedPeriod, detectedPeriod, expectedPeriod * 0.05, "Detected period must be close to pitch 60")
    }
}
