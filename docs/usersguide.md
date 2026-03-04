[Docs Home](/docs/README.md)

# KSyn Programmer's Guide

KSyn is a library for creating audio using modules called "unit generators". It was derived from the Java library JSyn, and is written in Kotlin Multiplatform. If you already know how to program in JSyn, see [Porting JSyn directly to KSyn](/docs/jsyn_to_ksyn.md).

When you connect up a stereo system, you connect the various components so that sound can flow between them. Sound may flow, for example, from a CD player, to a graphic equalizer, to an amplifier, and then to a pair of speakers. In a similar manner, sound generating, and sound processing units are connected together in KSyn to create new sounds. These sound components are traditionally called "unit generators". The library of unit generators includes oscillators, filters, ramps and other functions that you would find on a modular analog synthesiser.

## Importing KSyn Packages

KSyn defines a number of Kotlin packages. You should import these packages at the start of your program. The most useful packages are:

```kotlin
import com.softsynth.ksyn.* // KSyn and Synthesizer classes
import com.softsynth.ksyn.unitgen.* // Unit generators like SineOscillator
```

## Starting the Synthesis Engine

Before making any other calls to KSyn, you must instantiate a Synthesizer object.

```kotlin
val synth = KSyn.createSynthesizer()
```

You can then start the synthesizer engine.

```kotlin
synth.start()
```

Note: **There is no internal device layer built-in to KSyn**. KSyn is strictly an audio calculation engine — numbers go in and numbers come out. It expects the host platform to provide the audio thread and request buffers of floats. To actually play sound from KSyn, you must attach it to an external bridge. For KMP development, the officially supported bridge is [kc-audio-bridge](https://github.com/philburk/kc-audio-bridge).

When your program finishes execution or gets destroyed, you should stop KSyn by calling:

```kotlin
synth.stop()
```

## Creating Unit Generators

The next step is to create the various unit generators needed to create the desired sounds. The unit generator classes are all subclasses of the `UnitGenerator` class.
One of the most important unit generators is the `LineOut`. It is used to send audio output to the speakers. The unit generators need to be added to the synthesizer.

```kotlin
val myOut = LineOut()
val myNoise = WhiteNoise()
val myFilter = FilterStateVariable()

synth.add(myOut)
synth.add(myNoise)
synth.add(myFilter)
```

## Connecting Unit Generators

Unit generators have input and output ports that can be connected together. This allows units such as filters to process the output of units like oscillators. To connect units together, use their port's `connect()` method as follows:

```kotlin
myNoise.output.connect(myFilter.input)
```

The above code will connect the output of the noise unit to the input of the filter. Input and Output are called "Ports".
Each `UnitOutputPort` can be connected to multiple `UnitInputPorts`. Each `UnitInputPort` can have multiple `UnitOutputPorts` connected to it. Everything connected to a `UnitInputPort` will be summed together.
Some units have multi-part ports. An example is the `LineOut` unit which has a stereo input. To connect to a specific part of a port use part indices:

```kotlin
myFilter.output.connect(0, myOut.input, 0) // Left side
myFilter.output.connect(0, myOut.input, 1) // Right side
```

## Setting Parameters

Most units have ports that control their operation. These include "Frequency", "Amplitude", etc. To set a port, call `set()` with the new Value. Frequency ports are set in Hertz. Amplitude ports are set as a fractional Amplitude between -1.0 and +1.0. For example:

```kotlin
myOsc.frequency.set(440.0) // 440 Hz
myOsc.amplitude.set(0.5)   // Half amplitude.
```

The `set()` method is supported by the `UnitInputPort` class.
You can connect units to the parameter ports of another unit instead of setting them to a constant value. Thus you can do FM by connecting to the Frequency port of a `SineOscillator` unit. Note that KSyn has a `SineOscillatorPhaseModulated` unit, which provides a better way to do FM.

## Starting and Stopping Units

The audio is synthesized in a background thread driven by your audio bridge. The Synthesizer calls any units that have been started. For example, to start a `LineOut` unit:

```kotlin
myOut.start()
```

Note that not every unit needs to be started. When a running unit is executed, it pulls data from every unit that is connected to its inputs. Each of those units pull from their inputs as well. So you only need to start the final unit in a graph and everything connected to it will also run. Feedback loops are detected and handled properly so that the Synthesizer will not blow up.

When you are finished making the sound you should stop any unit generators that you started:

```kotlin
myOut.stop()
```

## Schedule Events in the Future using Timestamps

When an application is busy doing many things, including garbage collection, it may not be available to perform some audio event at exactly the right time. This can result in some undesirable variations in the time that audio events occur. For example, if you are playing a melody, the note timing may be off. To address this problem, KSyn has a feature that allows you to schedule events in the future to be performed by the Synthesizer with very precise timing accuracy.

The events that can be scheduled include starting and stopping of units, setting of port values, and queueing of sample and envelope data. To use the "event buffer", create a `TimeStamp` object:

```kotlin
val timeStamp = synth.createTimeStamp()
val futureTime = timeStamp.makeRelative(5.0)

// Start 5 seconds in the future
myOsc.start(futureTime)
myOsc.amplitude.set(0.5, futureTime)
```

## Loading a Sample from a File

An `AudioSample` is a container for digital audio data. It typically contains a recording of a natural sound event such as a piano note, dog bark, or explosion. An `AudioSample` can also contain audio data that has been generated by a program.
We have multiple types of samples, but the most frequently used is `FloatSample` which contains 32-bit floating point data.

Samples are typically loaded from an AIFF or WAV file from resources. KSyn relies on the standard Compose Multiplatform `Res` object to read bytes from common resources asynchronously.

```kotlin
import ksyn.demo.generated.resources.Res

// Inside a coroutine or suspend function:
val bytes = Res.readBytes("files/clarinet.wav")
val sampleLoader = SampleLoader()
val clarinetSample = sampleLoader.loadFloatSample(bytes)
```

## Creating a Sample

You can also create a sample algorithmically by loading it with data from a program. To create a monophonic FloatSample of a certain size call:

```kotlin
val myMonoSample = FloatSample(numFrames)
```

A frame is one or more sample values that will play simultaneously. A monophonic sample has one sample value, or channel, per frame. A stereo sample has two sample values per frame. 

To load a sample with data, prepare an array containing the desired data, then write it to the sample.

```kotlin
// Create a float array to contain audio data.
val data = FloatArray(NUM_FRAMES)

// Fill it with sawtooth data.
var value = 0.0f
for (i in data.indices) {
    data[i] = value
    value += 0.01f
    if (value >= 1.0f) {
        value -= 2.0f
    }
}

// Generate sample directly from the data.
val mySample = FloatSample(data)
```

## Playing a Sample

There are a number of units that can play samples. The unit `VariableRateMonoReader` will read sample data at a variable rate and interpolate between adjacent values.

```kotlin
val samplePlayer = VariableRateMonoReader()
```

Sample players have a special port that you can "queue" samples to. You can queue up multiple portions of various samples on a sample queue and they will be played in order, one after the other. You can optionally specify that a portion of a sample be looped if it is the last thing in the queue. 

```kotlin
samplePlayer.dataQueue.queueLoop(mySample, 0, mySample.numFrames)
```

Imagine a violin sample that has an attack portion, a loop in the middle, and a release portion:

```kotlin
samplePlayer.dataQueue.queue(mySample, 0, attackSize)
samplePlayer.dataQueue.queueLoop(mySample, loopStart, loopSize)
```

When you want to release the note, simply call queue() for the release portion.

```kotlin
samplePlayer.dataQueue.queue(mySample, releaseStart, releaseSize)
```

The rate at which samples are played is controlled using the rate port. Note that KSyn expects fundamental native frequencies (like 44100Hz) when scaling:

```kotlin
samplePlayer.rate.set(mySample.frameRate) // play at original speed
```

## Defining an Arbitrary Function

If you need to calculate an arbitrary function, y=f(x), in KSyn, it is very easy to define a `Function` object using a lambda. You can then use the `FunctionEvaluator` unit generator in a patch.

```kotlin
val cubeFunction = Function { x -> x * x * x }

val cubeUnit = FunctionEvaluator()
synth.add(cubeUnit)
cubeUnit.function.set(cubeFunction)
```

You can now use the `cubeUnit` as a unit generator that will output the cube of its input.

## Creating a Lookup Table for Functions

If the function is very complex then it might be faster to precalculate the values and then just look them up at runtime. You can use a `DoubleTable` to contain the precalculated values. `DoubleTable` extends `Function` so it can be used in place of a `Function`. Note, however, that the input values for a `DoubleTable` must be between -1.0 and +1.0.

```kotlin
val CUBE_LENGTH = 1024
val TABLE_LENGTH = CUBE_LENGTH + 1
val data = DoubleArray(TABLE_LENGTH)

for (i in 0 until TABLE_LENGTH) {
    val x = (i - (CUBE_LENGTH / 2.0)) * 2.0 / CUBE_LENGTH
    data[i] = x * x * x
}

val cubeTable = DoubleTable(data)
val fastCuber = FunctionEvaluator()
fastCuber.function.set(cubeTable)
```

## Using a Table for Wave Table Synthesis

A unit called `FunctionOscillator` will generate repeating waveforms using a `Function` or `DoubleTable`. It is controlled using a frequency port. 

```kotlin
// Create waveform consisting of two sinewave partials.
// Add 1 for the guard point.
val WAVE_LENGTH = 1024
val data = DoubleArray(WAVE_LENGTH + 1)
for (i in data.indices) {
    data[i] = (0.5 * kotlin.math.sin(i * 2.0 * kotlin.math.PI / WAVE_LENGTH)) + 
              (0.5 * kotlin.math.sin(3.0 * i * 2.0 * kotlin.math.PI / WAVE_LENGTH))
}

val myTable = DoubleTable(data)
val myWaveOsc = FunctionOscillator()
myWaveOsc.function.set(myTable)
```
You can also load a table from a sample. For example we could put the clarinetSample into a table and play it with a FunctionOscillator.

```kotlin
val myTable = DoubleTable(clarinetSample)
val myWaveOsc = FunctionOscillator()
myWaveOsc.function.set(myTable)
```

## Creating Envelopes

An envelope is a common synthesis tool. It describes a shape or contour for a parameter. Consider the amplitude curve for a piano note when it is struck. It goes from silence to full volume and then slowly decays as long as the key is held down. When the key is lifted, it quickly drops back to silence.

Envelope frames, or segments, consist of a pair of double numbers that describe a duration and a value. The duration number describes how long it should take the envelope to reach the value number starting from the value of the previous frame.

```kotlin
// Create an envelope and fill it with recognizable data.
val data = doubleArrayOf(
    0.02, 1.0,  // duration,value pair for frame[0]
    0.30, 0.1,  // duration,value pair for frame[1]
    0.50, 0.7,  // duration,value pair for frame[2]
    0.50, 0.9,  // duration,value pair for frame[3]
    0.80, 0.0   // duration,value pair for frame[4]
)
val myEnvData = SegmentedEnvelope(data)
```
The first frame has a duration of 0.02 and a value of 1.0.  This means that when this envelope is started that it will take 0.02 seconds to go from its current value to a value of 1.0.  If you want to force an envelope to start immediately at a particular value then use a duration of 0.0.  When the envelope reaches 1.0 then it will take 0.30 seconds to reach a value of 0.1.  The final frame typically has a value of zero for envelopes that control amplitude.

The envelope can be modified using write() just like with a sample.

## Using Envelopes to Control Other Units

Envelopes can be used to control the parameters of various unit generators. They require an envelope player unit called a `VariableRateMonoReader`. You may recall that the same unit generator was used to play a sample! Envelopes are queued on an envelope just like samples are queued on a sample player.  Consider this example:

```kotlin
val envPlayer = VariableRateMonoReader()
envPlayer.dataQueue.clear()
envPlayer.dataQueue.queue(myEnvData)
envPlayer.start()
```

To simulate the attack and release characteristics of some instruments you could queue up the beginning portion of an envelope when the note is started, then queue the release portion when the note is released.

```kotlin
// Queue up all segments except last segment.
if (attack)
{
    envPlayer.dataQueue.clear()
    envPlayer.dataQueue.queue(myEnvData, 0, 3)
    envPlayer.dataQueue.queueLoop(myEnvData, 1, 2)
}
// Queue final segment. */
else if (release) {
    envPlayer.dataQueue.queue(myEnvData, 3, 2)
}
```

To control another unit's parameters using an envelope, simply connect the output of the envelope player to a port on the other unit.

```kotlin
envPlayer.output.connect(myOsc.amplitude)
```

You can adjust the rate of envelope playback using the rate port on the VariableRateMonoReader. Amplitude envelopes of acoustic instruments tend to get shorter as they go higher in pitch.  This rate parameter can be used to simulate that effect. If you use a very high rate, for example 44100.0, then the envelope can be used as an audible waveform. In fact you can queue samples and envelopes to the same unit. If you use a low rate, for example 1.0, then a sample can be used as a slowly varying control signal.

envPlayer.rate.set( 0.7 )

## Grouping Units together into a Circuit

You will often want to connect multiple units together to make a single complex sound effect. It would be nice to be able to treat these groups in a manner similar to the way that individual units are treated. This can all be done using the `Circuit` class.

To make a KSyn circuit, define a subclass of the `Circuit` class and add the units in the `init` block:

```kotlin
class WindSound : Circuit() {
    private val triOsc = TriangleOscillator()
    val frequency: UnitInputPort

    init {
        add(triOsc)
        
        // Export oscillator frequency port
        frequency = triOsc.frequency
        addPort(frequency, "Frequency")
    }
}
```

## Using a PassThrough in a Circuit

Sometimes you will want to have a single port on a circuit that connects internally to multiple ports. For example, suppose you have two oscillators in a circuit and you want to control them both using a single amplitude port. Use a `PassThrough` unit that distributes the incoming signal to multiple places.

```kotlin
val passThrough = PassThrough()
add(passThrough)

val amplitude = passThrough.input
addPort(amplitude, "Amplitude")

// connect the passThrough to as many internal ports as you want
passThrough.output.connect(squareOsc.amplitude)
passThrough.output.connect(triOsc.amplitude)
```

## Receiving Notification of Sample Playback Completion

When you queue envelope or sample data to a queue, it will sit in the queue for a while and then be processed later. It is sometimes handy to know when it finishes processing. We can pass a `UnitDataQueueCallback` object that will signal us when the queued data has started or finished, or looped.

```kotlin
// Create a command to queue the envelope.
val command = envelopePlayer.dataQueue.createQueueDataCommand(envelope, 0, envelope.numFrames)

// Set the callback to receive notifications.
command.callback = object : UnitDataQueueCallback {
    override fun started(event: QueueDataEvent) {
        println("CALLBACK: Envelope started.")
    }
    override fun looped(event: QueueDataEvent) {
        println("CALLBACK: Envelope looped.")
    }
    override fun finished(event: QueueDataEvent) {
        println("CALLBACK: Envelope finished.")
    }
}

command.numLoops = 2
synth.queueCommand(command)
```
// TODO: add section on custom unit generators
// TODO: common classes in KSyn
