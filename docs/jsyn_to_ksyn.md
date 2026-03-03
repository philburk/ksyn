# Porting JSyn directly to KSyn

This guide is for developers familiar with the JSyn audio synthesis library (built in Java) who are migrating their programs to KSyn (the modernized Kotlin Multiplatform native implementation).

## Why Move to KSyn?

The primary motivation for moving to KSyn is to take advantage of Kotlin Multiplatform. Java used to work on the web and desktop but that is no longer the case. KSyn is a native implementation of JSyn that is written in Kotlin and can be used on multiple platforms including Android, iOS, and WebAssembly (WASM).

Additional advantages:
* native code optimization for desktop and WASM,
* use more modern Kotlin features like coroutines and lambdas,
* achieve better performance by using Float instead of Double for most audio processing.

### 1. New Package Names

The root package has been changed to reflect the new Library and language.
**In JSyn**: The root package was `com.jsyn` (e.g., `com.jsyn.unitgen.UnitOscillator`).
**In KSyn**: The root package is now `com.softsynth.ksyn` (e.g., `com.softsynth.ksyn.unitgen.UnitOscillator`).

### 2. The `AudioSample` / `AudioBuffer` Paradigm

The most significant change in the transition from JSyn to KSyn is the data-type for audio calculations.

**In JSyn**: Audio signals were processed and passed natively using the `double` primitive type. Arrays of data used `double[]`.
**In KSyn**: To optimize for performance across native and mobile targets utilizing hardware SIMD pipelines when possible, all audio calculations and port values use the `AudioSample` typealias (which resolves to `Float`). Bulk audio data structures must use `AudioBuffer` (which wraps `FloatArray`).

**Key migration rules:**
1. Many method calls now use `AudioSample` or `Float` instead of `double`.
2. Use the `.toSample()` extension function to safely coerce `Double`s and `Float`s.
3. Use `AudioMath.xxx` or Kotlin's native `kotlin.math.xxx` instead of Java's `java.lang.Math.*`.

### 3. External Audio Hardware & I/O

**In JSyn**: The library shipped with `JavaSoundAudioDevice` and native implementations that directly hooked into the operating system's soundcard driver and managed audio threads.

**In KSyn**: **There is no internal device layer**. KSyn is strictly an audio calculation engine — numbers go in and numbers come out. It expects the host platform to provide the audio thread and request buffers of floats.

To actually play sound from KSyn, you must use an Audio I/O library. For KMP development, the officially supported bridge is **[kc-audio-bridge](https://github.com/philburk/kc-audio-bridge)**. It uses the native audio APIs of the platform: AudioTrack on Android, JavaSound on Desktop, and WebAudio on WASM.

#### JSyn Example
```java
Synthesizer synth = JSyn.createSynthesizer();
LineOut lineOut = new LineOut();
synth.add(lineOut);
synth.start();
```

#### KSyn Example
```kotlin
val synth = SynthesisEngine()
val lineOut = LineOut()
synth.add(lineOut)
synth.start()
```

#### Collecting Audio Output

In JSyn, the default behavior was to play the audio automatically.  In KSyn, you can use the audio output from KSyn in many different ways.
For example, you can hook KSyn's outputBuffer to your platform's audio callbacks.
Or you can use the audio output from KSyn as input to another program or write it to a file.

```kotlin
while(true) {
    val stereoBuffer = synth.renderBuffer()
    // Output the audio using some blocking write.
    if (myBlockingWrite(stereoBuffer) break;
}
```

### 4. Removal of Swing and UI Utilities

**In JSyn**: Classes like `PortFader`, `SoundBite`, and packages extending `java.awt`
and `javax.swing` were included directly for building user interfaces and oscilloscopes.

**In KSyn**: All UI code has been decoupled. UI logic is strictly the responsibility of
the host application, generally utilizing **Jetpack Compose / Compose Multiplatform**.

If you need a `PortFader` equivalent in KSyn, look at the `demo/src/commonMain/kotlin/com/softsynth/ksyn/PortFader.kt` and `UnitGeneratorFaders.kt` classes in the KSyn Demo Application repository. They provide drop-in replacements utilizing Compose `@Composable` elements.

### 5. Concurrency and Thread Safety

**In JSyn**: Locks, Monitors, and `java.util.concurrent` (like `ReentrantLock` and `Condition`) were heavily used for managing synchronous structures like queues.

**In KSyn**: Standard blocking synchronization is completely incompatible with the WASM target (which halts the UI/Main thread and crashes Web browsers when forced to block). As a result:
- KSyn uses single-producer, single-consumer lock-free ring-buffers utilizing `@Volatile` pointers for its data flows.
- Wait mechanism queues use KMP-safe spin-locks (`while(!lock.tryLock()) {}`) around Kotlin Coroutine `Mutex`es, or spin directly on the `Volatile` indices.
- Standard synthesis flow is entirely synchronous; `SynthesisEngine.queueCommand` places configuration events into a scheduled queue that the active audio thread executes immediately prior to the next block boundary, avoiding threading deadlocks.

### 6. Multiplatform Techniques for Assets (Loading Samples)

In Java, you might have used `java.io.File` or `Class.getResourceAsStream()` to load `.wav` files. In Kotlin Multiplatform, file I/O works differently depending on the deployment target (e.g., Android Assets vs iOS Bundles vs Web Fetch).

KSyn relies on the standard Compose Multiplatform `Res` object to read bytes from common resources asynchronously.

#### KSyn Example
```kotlin
import ksyn.demo.generated.resources.Res

// Inside a coroutine or suspend function:
val bytes = Res.readBytes("files/Clarinet.wav")
val sampleLoader = SampleLoader()
val floatSample = sampleLoader.loadFloatSample(bytes)

val reader = VariableRateMonoReader()
synth.add(reader)
reader.dataQueue.queue(floatSample)
```

### 7. Utilizing Kotlin Lambdas

Kotlin's first-class functions make working with the Synth Engine and custom data evaluations much cleaner than Java's anonymous inner classes.

#### Command Queue with Lambdas
Instead of implementing a `Runnable` or `Command`, you can schedule immediate or deferred Synth modifications directly on the audio thread using trailing lambdas:

**In JSyn**:
```java
synth.scheduleCommand( timeStamp, new ScheduledCommand() {
    public void run() {
        myOsc.amplitude.set( 0.5 );
    }
});
```

**In KSyn**:
```kotlin
synth.scheduleCommand(timeStamp) {
    myOsc.amplitude.set(0.5f)
}

// Or an immediate, thread-safe asynchronous queue:
synth.queueCommand {
    myOsc.frequency.set(440f)
}
```

#### FunctionEvaluator with Lambdas
Similarly, `FunctionEvaluator` has been streamlined.

**In JSyn**:
```java
FunctionEvaluator evaluator = new FunctionEvaluator();
evaluator.setFunction(new Function() {
    public double evaluate(double x) {
        return Math.sin(x) * Math.cos(x);
    }
});
```

**In KSyn**:
```kotlin
val evaluator = FunctionEvaluator()
// Pass a lambda directly:
evaluator.function = Function { x ->
    kotlin.math.sin(x) * kotlin.math.cos(x)
}
```

---

## Advanced: Creating Custom Unit Generators

When porting a custom unit generator, you must adapt the `generate()` method to use `AudioSample`/`AudioBuffer` and strip out the `start` and `limit` bounds in favor of whole-block processing.

#### Old JSyn Example
```java
package com.jsyn.examples;

import com.jsyn.ports.UnitInputPort;
import com.jsyn.ports.UnitOutputPort;
import com.jsyn.unitgen.UnitFilter;

/**
 * Custom unit generator that can be used with other JSyn units.
 * Cube the input value and write it to output port.
 */
public class CustomCubeUnit extends UnitFilter {

    /**
     * This is where the synthesis occurs.
     * The start and limit allow us to do either block or single sample processing.
     */
    @Override
    public void generate(int start, int limit) {
        // Get signal arrays from ports.
        double[] inputs = input.getValues();
        double[] outputs = output.getValues();

        for (int i = start; i < limit; i++) {
            double x = inputs[i];
            outputs[i] = x * x * x;  // x cubed
        }
    }
}
```

#### New KSyn Example
```kotlin
package com.softsynth.ksyn.examples

import com.softsynth.ksyn.Synthesizer
import com.softsynth.ksyn.ports.UnitInputPort
import com.softsynth.ksyn.ports.UnitOutputPort
import com.softsynth.ksyn.unitgen.UnitFilter

/**
 * Custom unit generator that can be used with other KSyn units.
 * Cube the input value and write it to output port.
 */
class CustomCubeUnit : UnitFilter() {

    /**
     * This is where the synthesis occurs.
     * KSyn's SIMD architecture demands generating the whole block simultaneously.
     * The `start` and `limit` boundary variables have been removed.
     */
    override fun generate() {
        // Ports now natively yield and expect AudioBuffers
        val inputs = input.values
        val outputs = output.values

        for (i in 0..<Synthesizer.FRAMES_PER_BLOCK) {
            val x = inputs[i]
            outputs[i] = x * x * x  // x cubed
        }
    }
}
```
