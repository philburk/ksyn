# Design for KSyn - Kotlin Synthesizer

Kotlin was derived from JSyn, the Java Synthesizer.
The API is similar but not identical.

## Note Support

### Goals

1. Support building MIDI style synthesizers using KSyn.
2. Make it easier to compose note based music in Syntona.

### Requirements

* Simple interface that can be supported in Syntona.
* Support pitch modulation that not cause transposition.
* Pitch to frequency conversion should be efficient.
* Combine channel and perNote pitch modulation/bend.

### Pitch vs Frequency

The old UnitVoice interface in JSyn used frequency instead of pitch.
But it might be better to have a new PitchedVoice interface that uses MIDI pitch, ie.
semitones with Middle C equals 60.0.

#### Advantages of using Pitch

* Closer mapping to MIDI
* Some complex instruments may have an associated pitch but not an obvious associated frequency.
* Instruments that use wavetable zones are based on pitch.
* Vibrato must be applied in the pitch domain to avoid detuning.
* Pitch Bend is in the pitch domain.

#### Disadvantages of using Pitch

* Oscillators and phasers operate with frequency.
* Pitch to frequency conversion is a costly interpolated table lookup at audio rate.

### Existing Classes

* com.softsynth.ksyn.unitgen.UnitVoice - frequency based
* com.softsynth.ksyn.instruments.* - extend UnitVoice
* com.softsynth.ksyn.util.VoiceAllocator - uses UnitVoice
* com.softsynth.ksyn.util.VoiceDescription - for user menus, createUnitVoice()
* com.softsynth.ksyn.util.PolyphonicInstrument, one channel of a synth
* com.softsynth.ksyn.util.Instrument - interface used by PolyphonicInstrument
* com.softsynth.ksyn.util.InstrumentLibrary - array of VoiceDescriptions

####

* com.jsyn.util.MultiChannelSynthesizer - contains useful ChannelContext, needs breakout for Syntona
* com.jsyn.midi.MidiSynthesizer - mostly maps controller indices to methods

### Proposed Changes

* Start a new package called com.softsynth.ksyn.voices ???
* Add a PitchedVoice to replace UnitVoice.
* Change the WebDrum to use the new PitchedVoices
* Review/replace all of the voice management code

#### Hierarchy of Classes

* MidiSynthesizer - parses MIDI messages and maps to groups and channels
  * PolyphonicSynthesizer (was MultiChannelSynthesizer.ChannelContext) - LFOs, channel resources
  * PolyphonicInstrument - middle manager
  * VoiceAllocator - maybe does too much, can be called in command scope
    * PitchedVoice - interface for a single voice Circuit or UnitGenerator

### Design Questions

* Do we deprecate or remove the UnitVoice related code? Now might be a good time
to start fresh with KSyn. Can stagger the removal by leaving the deprecated code in place for a while.
Remove before Maven release.
* Should PitchedVoice extend UnitSource? That might make it harder to implement with a Syntona
patch without loading everything into a dynamic Circuit.
* Should we use the existing UnitVoice.setPort(name) method? The polyphonic
passthrough method interferes with that.
* Need pitchOffset for setting channel PitchBend.
* Need continuous audio rate pitchOffset port for LFO based vibrato.
* Does a MIDI 2.0 PerNoteControlChange override or add to the channel ControlChange? Add is recommended.
* If we use a Circuit then it must be "editable" so that the patch can be extended.
May need a default output PassThrough for internal mixing and for getOutput().
* The PolyphonicInstrument and the VoiceAllocator both implement Instrument. Should they be merged?
* 