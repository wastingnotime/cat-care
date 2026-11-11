# Android icon polish — 2026-11-11

The History return arc and clock hands now share the vector center (12, 12).
Previously, the arc endpoints placed the circle off-center relative to the hands.
The cat silhouette has rounded ear tips, smoother symmetric cheeks and chin,
centered elliptical eyes, and a softly rounded nose. Android uses this shared
`ic_cat` drawable for the app icon and the system splash screen.

Validation: `./gradlew lintDebug assembleDebug` and `git diff --check` passed.
The [before/after preview](comparison.png) was rendered from the actual vector
path data using librsvg and visually inspected. This is an enlarged asset
comparison, not a device screenshot. No runtime behavior changed.
