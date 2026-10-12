/*
 * Copyright 2026 Phil Burk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.softsynth.ksyn.dsp

/**
 * Delay line that can delay by a fractional number of frames.
 * Use it one frame at a time inside a feedback loop: [read] the delayed value, then [write] the new one.
 *
 * @param maxDelay largest delay in frames
 * @see AllPassFractionalDelay
 * @see LagrangeFractionalDelay
 */
abstract class FractionalDelay(val maxDelay: Int) {
    private val delayLine = SimpleDelay(maxDelay + EXTRA_FRAMES)

    /**
     * True if the delay can be changed on every frame without clicks.
     * Otherwise set the delay only when it changes, for example once per block.
     */
    abstract val isSmooth: Boolean

    /**
     * @param delay in frames, limited to between [MIN_DELAY] and [maxDelay]
     * @param omega radian frequency where the delay should be exact, 2 * PI * frequency / frameRate.
     *     Only needed by interpolators whose delay varies with frequency.
     */
    abstract fun setDelay(delay: Double, omega: Double)

    /** @return the value written [setDelay] frames ago */
    abstract fun read(): Float

    /** Write the newest value and advance. Call once per frame after [read]. */
    fun write(input: Float) {
        delayLine.write(input)
        delayLine.advance()
    }

    /** Set all of the delayed values to zero. */
    open fun clear() {
        delayLine.clear()
    }

    /** @return the value written this many frames ago */
    protected fun tap(position: Int): Float = delayLine.read(position)

    protected fun limitDelay(delay: Double): Double = delay.coerceIn(MIN_DELAY, maxDelay.toDouble())

    companion object {
        /** Smallest delay that can be interpolated. */
        const val MIN_DELAY = 2.0

        /** Extra frames for the interpolation taps. */
        private const val EXTRA_FRAMES = 4
    }
}
