package com.softsynth.ksyn.unitgen

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import kotlin.concurrent.Volatile

/**
 * A multi-channel oscilloscope probe UnitGenerator.
 *
 * Data arriving on [input] is accumulated until a trigger event occurs, at which point
 * [displayBufferSize] samples are captured per channel and transferred to [displayBuffer].
 *
 * Triggering: a rising edge on [trigger] (or on input channel 0 when trigger is unconnected)
 * crossing the auto-detected midpoint starts a capture. If no rising edge is detected for
 * [displayBufferSize] * 8 samples, a capture is forced so the display never goes blank.
 *
 * Thread safety: [isDisplayReady] is `@Volatile` and serves as the publication barrier.
 * The audio thread writes [displayBuffer] then sets [isDisplayReady] = true. The UI thread
 * reads [displayBuffer] while [isDisplayReady] is true, then clears it to re-arm.
 *
 * Usage:
 *   val scope = ScopeProbe(numChannels = 2)
 *   synth.add(scope)
 *   scope.start()                              // must be in runningUnitList to get pulled
 *   source.output.connect(0, scope.input, 0)
 */
class ScopeProbe(
    val numChannels: Int,
    val displayBufferSize: Int = 512,
) : UnitGenerator() {

    val input   = UnitInputPort(numChannels, "Input",   0f)
    val trigger = UnitInputPort("Trigger", 0f)

    // Double-buffer: audio fills captureBuffer, then swaps references with displayBuffer.
    // @Volatile on isDisplayReady acts as the memory barrier (JMM publication idiom).
    // @Volatile on displayBuffer ensures the UI sees the swapped reference after the barrier.
    @Volatile var displayBuffer: Array<FloatArray> = Array(numChannels) { FloatArray(displayBufferSize) }
    private var captureBuffer: Array<FloatArray> = Array(numChannels) { FloatArray(displayBufferSize) }

    /** Set to true by the audio thread when a new frame is ready. Clear from the UI thread to re-arm. */
    @Volatile var isDisplayReady = false

    /** Auto-detected signal minimum across all channels. Updated every tracking period. */
    @Volatile var autoRangeMin: Float = -1f
    /** Auto-detected signal maximum across all channels. Updated every tracking period. */
    @Volatile var autoRangeMax: Float = 1f

    // ── Trigger state (audio-thread only) ────────────────────────────────────
    private var captureIndex = 0
    private var isCapturing = false
    private var lastTriggerValue = 0f
    private var triggerThreshold = 0f

    // ── Auto-range tracking (audio-thread only) ───────────────────────────────
    private var trackingMin = Float.MAX_VALUE
    private var trackingMax = -Float.MAX_VALUE
    private var trackingCount = 0
    private val trackingPeriod = displayBufferSize * 4   // update range every ~N samples

    // Force a capture if no rising edge is seen for this many samples (handles DC signals).
    private var noTriggerCount = 0
    private val maxNoTriggerSamples = displayBufferSize * 8

    init {
        addPort(input)
        addPort(trigger)
    }

    override fun generate() {
        val channelBuffers = Array(numChannels) { ch -> input.getValues(ch) }
        val triggerValues  = if (trigger.isConnected()) trigger.getValues() else channelBuffers[0]

        for (i in 0 until Synthesizer.FRAMES_PER_BLOCK) {
            val trigSample = triggerValues[i]

            // Accumulate min/max across all channels for auto-ranging.
            for (ch in 0 until numChannels) {
                val s = channelBuffers[ch][i]
                if (s < trackingMin) trackingMin = s
                if (s > trackingMax) trackingMax = s
            }
            trackingCount++
            if (trackingCount >= trackingPeriod) {
                val range = trackingMax - trackingMin
                if (range > 1e-6f) {
                    autoRangeMin = trackingMin
                    autoRangeMax = trackingMax
                    triggerThreshold = (trackingMin + trackingMax) * 0.5f
                }
                trackingMin = Float.MAX_VALUE
                trackingMax = -Float.MAX_VALUE
                trackingCount = 0
            }

            // Only arm the trigger when the display buffer has been consumed by the UI.
            if (!isCapturing && !isDisplayReady) {
                noTriggerCount++
                val risingEdge = lastTriggerValue < triggerThreshold && trigSample >= triggerThreshold
                if (risingEdge || noTriggerCount >= maxNoTriggerSamples) {
                    isCapturing = true
                    captureIndex = 0
                    noTriggerCount = 0
                }
            }
            lastTriggerValue = trigSample

            if (isCapturing) {
                for (ch in 0 until numChannels) {
                    captureBuffer[ch][captureIndex] = channelBuffers[ch][i]
                }
                captureIndex++
                if (captureIndex >= displayBufferSize) {
                    // Swap buffer references — no element copy needed.
                    // The volatile write to isDisplayReady publishes the swap to the UI thread.
                    val tmp = captureBuffer
                    captureBuffer = displayBuffer
                    displayBuffer = tmp
                    isDisplayReady = true   // volatile write — all prior writes visible to reader
                    isCapturing = false
                }
            }
        }
    }
}
