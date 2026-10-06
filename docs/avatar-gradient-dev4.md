# Avatar gradient crash in 1.3.0-dev.3

The device report on TikTok 47.1.3 identifies `LinearGradient.<init>(FFFF[I[FShader.TileMode)` in
`X.0O5t.invoke`, reached while drawing a story avatar in the inbox. Android rejects fewer than
two color stops. This is a second native path, separate from generic TUX array conversion.

Reviewed original APKs:

- 46.7.3: `b9e96e64e94ac0f9ea229dd0ba743f1930121a8b6941cf9fd87191604da0129e`.
- 47.1.3: `8b5569f592a5534652ae460ef1d9e7f7394b5b7fdde44ae64f106d76767e2622`.

The native configuration loader converts `ShaderParam.colorList` through its TUX scalar
resolver, removes zero/unresolved colors, then constructs a result containing `int[]` colors
and `float[]` positions. It checks nonempty input, but does not check the filtered output has
at least two colors. Positions generated for one color can also contain NaN. The diagnostic
report does not contain the actual server configuration or resulting array contents.

The reviewed loaders are `X.0Ezq.LIZ(ColorConfig, Context):X.0Ebd` on 46.7.3 and
`X.0EvS.LIZ(ColorConfig, Context):X.0EWG` on 47.1.3. The latter contains 83 instructions;
the former contains 81. The baseline method/class digests are captured from the SHA-verified
46.7.3 APK. The full portable 47.1.3 body digest is separately pinned in
`AvatarGradientContracts`; it does not accept arbitrary shader loaders.

The guard runs immediately before the typed result constructor, using its actual color and
position registers. Invalid results return null. The native avatar consumer already interprets
null as its original three-color fallback and matching positions. Valid results retain their
complete original arrays. Selection requires the stable ColorConfig/ShaderParam relationship
and a result constructor that stores exactly the two correctly typed arrays. Mutation validates
the insertion point and generated code.

The startup context diagnostic is now emitted once per process, including concurrent reads.
Early callers still receive null until the existing context hook runs. These startup messages
alone do not prove why preferences reset. The independent settings checkpoint added for dev.4
recovers an empty primary preference file; it does not recover cleared application data.

Device verification still needs reopening the inbox with Arctic Blue and checking settings
after restarting TikTok. Automated patch/rebuild tests do not execute the full inbox UI.
