# Prompts used for Claude Code

## SegmentedEnvelopeEditor
```
I would like to add an editor for SegmentedEnvelope.kt in the ksyn-compose module.

The SegmentedEnvelope contains an array of AudioSample type numbers, which are Float data type.
The numbers are organized in pairs, called "Frames".
The first number in the frame is the duration in seconds.
The second number is the target value.
You can use read() or write() to access the data.

I would like a breakpoint editor that shows the data as an XY series of connected line segments.
The vertices are represented as circles.
The X dimension is time, based on the accumulated durations.
The Y dimension is the envelope value.

If the user clicks on a vertex circle then they can drag the vertex and thereby change the numbers in the associated frame.
If the user drags a vertex circle to the left then it decreases that
duration and all the points to the right will move left because their absolute time will decrease.
If the user clicks in another part of the editor then a new frame will be inserted
and the editor will behave as if the user had clicked on that new frames vertex.

Add the new envelope editor to the PlayEnvelopeScreen in the demo app in the PlayEnvelopeScreen.kt file so I can test it.
```
Add deletion
```
Modify the SegmentedEnvelopeEditor to allow deleting envelope frames.
If the user hold down the SHIFT key and clicks on a vertex then that frame should be deleted.
```
Add Sustain and Release Loops
```
The SegmentedEnvelope has two optional loops, a Sustain Loop and a Release Loop.
These are specified using the sustainBegin, sustainEnd and the releaseBegin, releaseEnd properties,
which contain frame indices.
If sustainBegin or sustainEnd is -1 then there is no corresponding loop.

The editor should have three modes:
Points - the original vertex editing mode
Sustain Loop - edit the sustainBegin, sustainEnd values
Release Loop - edit the releaseBegin and releaseEnd values

Add an optional toolbar at the bottom of the editor that lets the user select the editing mode using radio buttons.

To edit loop values, drag the mouse sideways. The first and last vertices in that drag range define the loop values.

To display a loop, if the begin and end frames are equal, then draw a full height vertical bar at the specified frame.
That indicates the envelope will hold at the value.
If the begin and end frames are not equal then draw a full height background rectangle under the associated line segments.
```

Handle confusing offsets.
```
I added ENV_OFFSET to handle the  fact that the value for the End point of a loop is one past the left index.
Now I want to improve the drag selection of a loop.
When dragging, the drag selection rectangle should show a normal selection rectangle based purely on the down position
and the drag position of the mouse.
Then when the mouse is released the loop selection should be applied and the current loop should be displayed.
```

## Oscilloscope
```
Add an oscilloscope feature in a package called com.softsynth.ksyn.compose.scope in ksyn-compose.
The scope use a new UnitGenerator called a ScopeProbe that has a multi-channel "in" port
and a single channel "trigger" port.
The number of channels is set when created.
The scope can operate in an automatic mode with auto-ranging for the Y scaling and trigger levels.
If nothing is connected to the trigger port then in[0] is used for triggering.
Data coming into the ScopeProbe is stored in a fixed-size multi-channel buffer until it is ready to display.
You may want to have a double buffered scheme with one buffer for capturing data while another is being displayed.
The scope has a coroutine job that monitors the buffered data.
A display of the data is triggered when an input value crosses the trigger threshold.
Add a Composable Oscilloscope function for that displays each of the incoming signals in a different color.

Add the Oscilloscope to the ChebyshevSongScreen with two channels.
Connect the output of the mixer to channel zero and the reverb output to channel 1.
```
