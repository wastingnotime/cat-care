# Android visual style

The owner app uses warm cream, forest green, soft sage surfaces and simple cat
artwork. The presentation should feel calm and make daily care easy to scan.

## Palette and hierarchy

| Token | Color | Use |
| --- | --- | --- |
| Paper | `#F8F7F2` | Page background |
| Ink | `#243C32` | Headings and primary text |
| Forest | `#285345` | Primary actions, selected cat, brand |
| Muted | `#5C6B60` | Supporting text |
| Sage | `#E9EEE4` | Selected navigation and status backgrounds |
| Line | `#DFE5DA` | Card, input and secondary-action outlines |
| Rust | `#954C38` | Destructive actions and overdue labels |

Use system sans-serif with medium weights, 28sp screen headings, 20–21sp card
headings and 13–16sp body text. Keep buttons at least 48dp high. Primary actions
are filled forest green, secondary actions are white with an outline, and
removal actions have a pale rust background. Color supplements explicit labels.

Cards have restrained borders and rounded corners, with space between sections.
Hoje keeps its summary compact so care cards stay near the top. Its two entry
actions share a row at normal font size and stack above 120% font scale or on
screens narrower than 360dp. Cat selection uses horizontally scrollable named
chips with an explicit selected state.

Navigation uses local vector icons, visible labels and a sage selected surface.
At large font sizes, widths account for label length. Labels stay on one line
and may ellipsize on unusually narrow screens; accessibility descriptions keep
the complete label. Decorative illustrations/icons are excluded from the
accessibility reading order; navigation exposes button semantics.

The cat illustration is drawn with Android Canvas and scales without external
assets. It appears in onboarding and profiles. Form fields use filled cream
surfaces and persistent labels. Storage details are available from Dados in a
readable dialog; the export/restore limitation remains visible on the page.

Implementation: `MainActivity.java`, `CatIllustration.java`, native vector
resources and `styles.xml` under `apps/mobile/app/src/main/`.

See [visual evidence](../../runs/android-visual-2026-10-22/evidence.md) for
screenshots and checks.
