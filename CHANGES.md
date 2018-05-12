# changes

This document only highlights major changes which probably require some adjustments when upgrading.

## changes in v1.27.0

- adopts camel-case for operators, e.g. `ampDb` instead of `ampdb`, `linLin` instead of `linlin`
- `madd` becomes `mulAdd`, the backlash operator becomes `out`
- adds random unary and binary ops (e.g. `.coin`, `.expRand`)
- adds window unary ops (e.g. `.hannWindow`)
- fixes bug in `Curve.exp`

## changes in v1.26.0

- `Client.Config` has a latency parameter (not used by the core library)

## changes in v1.24.0

- resolves #49 by renaming overloaded `play` (the one with arguments for target, add-action etc.)
  to `playWith`.
- renames `ID` in argument-names to `Id` (e.g. `nodeId`, `bufId`).
- adds `copy` method to various server messages

## changes in v1.23.0

- adds `Bus.apply`
- adds `UnaryUGen.Op` `unary_~`
- adds `Env.test`
- adds implicit `Curve.fromDouble`
- adds `Env.step`, fixes `Env.adsr` and `Env.dadsr`
- fixes `PhysicalIn` outputs MCE

## changes in v1.22.0

- fixes a bug with `b_gen` marking as synchronous or asynchronous message.
- adds `ChannelRangeProxy` and corresponding backslash in `GEOps`
- adds `RepeatChannels`
- adds `b_gen` OSC message methods to `Buffer` and corresponding
  commands to `BufferOps`.

## changes in v1.21.0

- fixes `LocalIn` for SuperCollider 3.6.x. Beware that the argument
  changed from `numChannels: Int` to `init: GE`, whereby the number
  of channels in `init` determine the number of outputs of `LocalIn`.
- added `NodeID` UGen.
- added `graph` op that will be used by ScalaCollider-Swing
- removed a few occurrences of `Optional`, where other special values
  for `None` make sense
- added `Server.version`

## changes in v1.20.0

- some rich number ops are in the Numbers library now
- `stringControl` implicit for `"name".kr` must now be imported with `Ops._`.
  This is so we can use that syntax differently in SoundProcesses.
- server command line opts are pessimistic about defaults
 
## changes in v1.19.0

- changes license from GPL to LGPL
- uses `Optional` library

## changes in v1.14.0

- `LocalBuf` arguments are reversed, allowing to leave out the number of channels
- Added `Sum3`, `Sum4` UGens, cleaned up `Mix` implementation
- Fixed zero-outputs property of `SendTrig`, `SendReply`
- Fixed non-variadic input of `DC`

## changes in v1.13.0

- fixes a bug with `Ringz`; more UGen documentation
- `GraphFunction` (`play { ... }`) supports thunk ending in `()`
- the `play` command must be imported now from `Ops` like other imperative commands
- adds support for `LocalBuf`

## changes in v1.12.0

- fixes #31, #36, #40, UGens #10, more UGen docs and examples
- more consistent argument names, relaxes `Vec[Float]` to `IndexedSeq[Float]` in some places
- in the server configuration `programPath` was shorted to `program`
- `ControlSetMap` was renamed to `ControlSet`; `Single` became `Value` and `Multi` became `Vector`
- if `$SC_HOME` is not found, uses plain `"scsynth"` as program path which should correctly resolve to `$PATH` entries.
- the server port may be left at `0` in which case `pickPort()` is called automatically.
- the usage of callbacks (`Completion`) is replaced by futures where sensible.
- uses Scala 2.11 by default

## changes in v1.10.0

- fixes UGens #7 `Duty` and `TDuty` argument order was wrong. UGens #8 `Median` argument names were swapped.
- externalises number enrichment (project [Numbers](https://github.com/Sciss/Numbers)).
- GE op `round` became `roundTo`, `roundup` became `roundUpTo`. That way `round` is kept for the standard Scala operation

## changes in v1.9.0

- fixes #35: `BufferOps.close` should take `Optional` instead of `Option`
- removes duplicate class `AddAction` which is already provided by the UGens API (dependency)

## changes in v1.8.0

- several types moved from package `synth` to `synth.ugen` to simplify the new serialization mechanism used in SoundProcesses
- this includes `Env` and `Constant`
- `Env.ConstShape` became `Curve` and is separate from the conversion to graph elements
- the constant envelope shapes are now inside `Curve`. E.g. `linShape` became `Curve.linear` etc.
- named control generation uses magnet pattern, disallows varargs. Instead of `"freq".kr(400, 500, 600)` one must now write `"freq.kr(Seq(400, 500, 600))`.

## changes in v1.6.0

- drops dependency on scala-actors, uses futures and processor library instead.

## changes in v1.5.0

- package `osc` became `message` not to conflict with ScalaOSC. The `OSC` prefix was dropped from classes.

## changes in v1.4.0

- UGens have been externalised as a library dependency to ScalaColliderUGens project
- requires Scala 2.10 now

## changes in v0.34

- SynthGraph is serializable
- bit of clean up in rich numbers

## changes in v0.33

- fix for `LinLin` UGen removed from SuperCollider.

## changes in v0.32

- OSC array support (e.g. setting multi-channel controls directly in synth instantiation).
- fix for `SendTrig` argument names
- elimination of functionally equivalent UGens and removal of no-op subtrees re-enabled
- fix for `Silent` UGen removed from SuperCollider.

## changes in v0.31

- `ServerOptions` became `Server.Config`. And a config builder is created via `Server.Config()`. This makes it more regular with the nomenclature of ScalaOSC. Likewise, `ClientOptions` became `Client.Config`.
- `Server.test` which was originally really meant just for quick testing, was renamed to `Server.run`, because it is generally useful for running a server without caring too much about the booting process.

## changes in v0.30

__Note:__ There have been made several changes from 0.25 to 0.30...

- To facilitate future graph re-writing, elements are now _lazy_ which means they do not instantly (multi-channel) expand into UGens. The idea is to be able to capture the whole expression tree, which then could be used to transform it, to visualize it (before multi-channel expansion!), to persist it, etc.
- In the meantime, the UGens themselves are not represented any more by individual classes but only generic classes such as `UGen.SingleOut`. As we expect manipulations to happen at the `GE` level, this keeps the number of classes smaller.
- The SynthDef creation process is now two staged. The `GE` tree is captured as a `SynthGraph` which in turn expands into a `UGenGraph`.
- Automatic subtree omission for side-effect-free roots is currently disabled.
- the implicit conversion to `GraphFunction` has been disabled. so instead of `{ }.play`, use `play { }`
- Multi-channel nesting and expansion now behaves like in SCLang.
- Recursive graph generation may require type annotations. Specifically all UGen source classes now return the UGen source type itself instead of a generic `GE`, which comes with the need to annotate `var`s in certain cases.
- Methods `outputs` and `numOutputs` are not available for the lazy graph elements. You can mostly work around this by using special graph elements for channel handling, like `Zip`, `Reduce`, `Splay`, `Mix`, `Flatten`. Method `\` (backslash) is still preserved, but it may change in a future version if it causes problems with graph re-writing. If you really need to re-arrange the channels of an element, you can currently work around this by using the `MapExpanded` element.
