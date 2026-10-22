# Extend Support for Granular Synthesis

## Goals
* Use samples as source material for granular synthesis.
* Use samples as source material for waveshaping of a signal.
* Use a delay line as a grain source so we can use granulation as a live effect.

## Requirements
* Use a polymorphic design for the grain sources so that all can use the same grainFarm.
* Use a -1.0 to +1.0 for the position into a sampled grain source so that it is size agnostic.
* Large sample data and delay lines must be sharable.

## Design Issues

Granulating a sample involves modulating an index into a large sample with a low amplitude signal.
This can cause floating point precision problems when adding a small signal to a large position.
So the internal fractional index calculations should use 64-bit double precision.

Preserve double precision for the fractional position when indexing into large samples and being driven by a low amplitude signal.

## Current Design

```kotlin
interface GrainSource {
var frameRate: AudioSample
    /** Generate one more value or the source signal. */
    fun next(): AudioSample
    /** Reset the internal phase of the grain. */
    fun reset()
    fun setRate(rate: Double)
}
```

The GrainSourceSIne implements this. It uses a fast Taylor expansion of the sine function.

## Proposed API

### SampleScrubber

This was proposed as SampleWaveShaper and implemented as `SampleScrubber`.

Ports
* data - the sample data
* input - is the audio rate signal that will be processed. It may have a very low amplitude.
* position - -1 to +1 spans the width of the sample
* time - in seconds for the position to reach its target (should we use timeMs?)
* output - the processed signal.

The input signal and the position can be added internally using double precision to generate an index
and interpolate into the sample.
The position may have some internal smoothing, like a linear ramp to avoid aliasing when the position changes abruptly.

Input signals should not have large discontinuities. Examples are sine waves, triangle waves and rednoise.
(What if we used a band limited sawtooth?)

### Polymorhic Sample/DelayLine

It would be nice to have a polymorphic interface for both a static sample and a delay line.
There are differences in how they behave.

The -1 to +1 position into the sample would span from the beginning to the end.
The -1 to +1 position into the delay line would span from the oldest data written to the newest.
So it would be a moving range in memory.

There is a moving a discontinuity in the addressing when using the delay line.

The tricky part is how we read the data sequentially from each.
When we read a sample at normal rate the position changes.
When we read a delay line at normal rate the position stays the same because the cursor is changing.


### Extending GrainSource for Samples and DelayLines

We need to extend the GrainFarm to support random positions into the source material.
That is not needed for the Sinewaves, although we could use it to position the phases of the sine by +-1.

The GrainSource could have a new setposition(position: Double) method.
But extending an interface can break binary compatibility for users.
So it may be better to have a GrainSourceArray interface and not modify the GrainSource interface.

Implementation Strategy

We do not have to do any position smoothing like we do with the SampleScrubber
because the grain envelope starts and stops at zero. And we only set the position once when the grain is starting.

```kotlin
class GrainSourceSample: GrainSource {
    var frameRate: AudioSample
    var phase: Double = 0.0 // fractional address into the array
    /** use rate to calculate the next sample based on the phase and phaseIncrement */
    fun next(): AudioSample //  clip index at the array boundaries
    /** Reset the internal phase of the grain. */
    fun reset()
    fun setRate(rate: Double) // set phaseIncrement
    fun setposition(position: Double) // set phase sample size
}
```
For the GrainSourceDelayLine, the implementation will be very similar to the GrainSourceSample except
setposition() will set the phase based on current cursor position.
Also next() will wrap index at the array boundaries and clip at the cursor position.

## Unresolved Design Questions

How do we set the shared sample or delayLine for the GrainSource?
Do we just set a FloatArray, position and size in case we are not using the entire array.
Or do we pass a FractionalDelay?

How do we handle the guard point?

Currently, we use linear interpolation with sample lookup and all-pass or Lagrange interpolators with a FractionalDelay.
How do the sample and delayLine APIs differ and how can we make them more consistent?
