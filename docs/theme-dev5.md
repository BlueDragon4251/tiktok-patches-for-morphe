# Theme follow-up for TikTok 47.1.3

The 2026-10-06 13:54 recording shows dark feed-description text, invisible profile controls, white profile/inbox note bubbles, and slow Inbox scrolling. A successful catalog rebuild does not verify these device-rendered surfaces.

Native-owned Profile, Sidebar and navigation subtrees now include explicit neutral image tints and monochrome non-bitmap drawables inside clickable controls. Bitmap avatars/thumbnails and chromatic indicators remain native. Sampling happens once per drawable and restores its bounds. Default restores native tints; native drawable/tint rebinds are recognized.

Flat GradientDrawable fills are copied and recolored while retaining corners/strokes. Transparent native overlays remain transparent. Dark caption scrims no longer stop the search for a confirmed dark backdrop; neutral ForegroundColorSpan overrides are paired with the readable text color. Functional colored spans and native text rebinds are preserved. An unknown media backdrop is still not guessed.

Inbox cards reuse their drawable until a native repaint or palette change. The verified holder bind still styles immediately. Whole-window discovery runs at most once per 100 ms while layouts/scrolls change and once per 500 ms during idle rendering. A second full-window realtime pass and redundant delayed scroll pass are no longer installed. Native-owned page scans use one palette per pass and a 100 ms budget; known caption/backdrop changes still update every draw.

Regression tests exercise clickable icons versus photos/functional colors, rounded white surfaces, transparent overlays, formatted caption text/restoration, repeated recycler binds, idle-frame scan suppression and immediate palette changes. Phone responsiveness and complete native screen rendering require confirmation on the user's device.

The native `VideoDescAssem` getter returns the actual feed-description `TuxTextView` through a five-instruction lazy getter. It is `Br()` in 46.7.3 and `Nr()` in 47.1.3; both complete portable digests equal `8f0ec54edd39fe427c2bf22596f32ac2da41d95edac4a04ff42e51095aa66089`. The new hook requires the unique typed getter, exact receiver/result flow, TextView hierarchy and reviewed full portable body; it may insert only the specified return hook. That TextView's neutral text/spans remain white over native media, including after binding, independently of guessed page backgrounds.

`TuxIconView` delegates its public `setTintColorStateList$tux_theme_release` to the sole ColorStateList on `TuxIconDrawable`, whose `draw()` reapplies it to its inner drawable. Both pinned APKs were inspected; Android ImageView tint alone is insufficient. The bridge snapshots that native list as well as Android tint and preserves functional native rebinds. Reflection descriptor lookups, including misses, are cached.

47.1.3's `SocialThoughtBaseBubbleBackgroundView` owns its speech-bubble paint through public `getFillColor()/setFillColor(int)`; the geometry remains native. Profile and Inbox passes now style this exact native public API and restore Default without overwriting a later functional fill.

Stateful native icon and label palettes retain selected functional colors and disabled opacity while neutral states use the theme. The native draw test verifies actual pixels across state changes and restoration of the original palette.
