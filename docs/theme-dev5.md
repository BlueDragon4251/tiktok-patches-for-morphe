# Theme follow-up for TikTok 47.1.3

The 2026-10-06 13:54 recording shows dark feed-description text, invisible profile controls, white profile/inbox note bubbles, and slow Inbox scrolling. A successful catalog rebuild does not verify these device-rendered surfaces.

Native-owned Profile, Sidebar and navigation subtrees now include explicit neutral image tints and monochrome non-bitmap drawables inside clickable controls. Bitmap avatars/thumbnails and chromatic indicators remain native. Sampling happens once per drawable and restores its bounds. Default restores native tints; native drawable/tint rebinds are recognized.

Flat GradientDrawable fills are copied and recolored while retaining corners/strokes. Transparent native overlays remain transparent. Dark caption scrims no longer stop the search for a confirmed dark backdrop; neutral ForegroundColorSpan overrides are paired with the readable text color. Functional colored spans and native text rebinds are preserved. An unknown media backdrop is still not guessed.

Inbox cards reuse their drawable until a native repaint or palette change. The verified holder bind still styles immediately. Whole-window discovery runs at most once per 100 ms while layouts/scrolls change and once per 500 ms during idle rendering. A second full-window realtime pass and redundant delayed scroll pass are no longer installed. Native-owned page scans use one palette per pass and a 100 ms budget; known caption/backdrop changes still update every draw.

Regression tests exercise clickable icons versus photos/functional colors, rounded white surfaces, transparent overlays, formatted caption text/restoration, repeated recycler binds, idle-frame scan suppression and immediate palette changes. Phone responsiveness and complete native screen rendering require confirmation on the user's device.
