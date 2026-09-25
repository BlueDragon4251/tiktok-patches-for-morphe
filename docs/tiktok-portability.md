# TikTok hook portability inventory

The complete, line-addressed and machine-readable source inventory is in `docs/tiktok-source-inventory.json`. It contains every native type, member, literal, resource, register expression, instruction selection, control-flow term, version reference and runtime reflection in the TikTok patch and extension sources. The patch-time hook report and `46.7.3.json` reviewed contracts record the actual resolved methods and original method/class layouts.

## Reviewed patch catalog

| Patch | Baseline catalog | Native or version-sensitive source assumptions | Selection requiring review |
|---|---|---:|---:|
| Advanced feed filter | applied | 7 | 0 |
| Always show publish date | applied | 5 | 2 |
| Automatic clear display | applied | 7 | 1 |
| BlueIT Service | applied | 49 | 7 |
| Copy comments without username | applied | 1 | 2 |
| Custom offline videos limit | applied | 1 | 0 |
| Diagnostic tools | applied | 5 | 0 |
| Disable login requirement | applied | 3 | 0 |
| Disable long-press quick share | applied | 2 | 3 |
| Disable screen capture detection | applied | 1 | 0 |
| Download quality selector | applied | 1 | 0 |
| Downloads | applied | 20 | 1 |
| Enable Live search | applied | 2 | 0 |
| Enable non-personalized search | applied | 2 | 0 |
| Feature Gate Lab | applied | 68 | 0 |
| Feature Gate Recorder | applied | 1 | 0 |
| Feed filter | applied | 23 | 0 |
| Feed tab navigation | applied | 1 | 0 |
| Fix Google login | applied | 3 | 0 |
| Follow diagnostics | outside tested 37-patch catalog | 24 | 6 |
| Gesture remapper | applied | 68 | 2 |
| Hide CAPTCHA popups | applied | 41 | 0 |
| Hide already seen videos | applied | 10 | 0 |
| Hide floating promotions | applied | 1 | 1 |
| Hide quick comment reactions | applied | 3 | 0 |
| Hold-and-slide 2x lock | applied | 1 | 2 |
| Open external links directly | applied | 15 | 1 |
| Original Photo Mode downloader | applied | 1 | 0 |
| Playback speed | applied | 4 | 1 |
| Remember clear display | applied | 4 | 2 |
| Resume videos after scrolling | applied | 7 | 0 |
| SIM spoof | applied | 1 | 0 |
| Sanitize sharing links | applied | 1 | 0 |
| Show seekbar | applied | 7 | 0 |
| Show seekbar thumbnail | applied | 7 | 0 |
| Stop video looping | applied | 3 | 0 |
| Theme engine | applied | 67 | 0 |
| Translate comments | applied | 7 | 0 |

Counts are **source search hits in the patch definition only**, not distinct hooks or proven incompatibilities. The JSON includes sibling fingerprints, extension code, and cross-cutting helpers. Named TikTok anchors, structural selectors, hard-coded assumptions and ambiguous selectors are labeled per occurrence in its `category` field. The runtime hook report supplies `resolved`, `relocated`, `ambiguous`, `missing` and `contract-changed` states for every concrete APK.

## Review priorities

1. Obfuscated native classes, literal resource IDs, index and register assumptions in feed, theme, settings and downloads. Each mutation currently requires a uniquely resolved hook and the class/method contract for the exact verified APK.
2. Calls found by all-site scanning must validate the entire Android call descriptor. Screen-capture handling now checks the exact `Activity` APIs and locks all original callers.
3. The central TUX and Compose palette resolvers, native return and drawer paths, and default restoration must pass the existing runtime and bytecode tests for every new APK. Do not infer runtime behavior from a discovery match alone.
4. A different APK, even if it displays 46.7.3, fails SHA-256 verification before any native injection. To add a version: record its provenance and SHA-256 in `fixtures.json` as nonselectable, run the full matrix, review migrations and regenerated contracts, verify relevant runtime behavior, then qualify same-head acceptance and discovery.

A normalized structural match indicates a possible relocated hook for review. The original 46.7.3 class and method digest pins keep the tested catalog safe while unverified versions remain unavailable. Review and migrate the source hits above as new, hash-verified APKs expose actual differences; no other version is declared supported.
