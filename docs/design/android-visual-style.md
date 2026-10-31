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

Cards have restrained borders, rounded corners and compact spacing. Keep the
warm identity and use screen space primarily for care content. The onboarding cat
illustration welcomes first-time owners; everyday screens use a compact app bar.

Today groups tasks into Overdue, Today, Upcoming (initially collapsed) and No date.
Deadlines are chronological, with localized Today/Tomorrow dates. Each card opens
details on tap, offers a compact Mark done button and an overflow menu for secondary
actions. Completion still requires confirmation. No essential action uses long press.

Gatos cards show only the coat avatar and name and open the profile editor directly with
name, coat avatar, birth date and adoption date. Edit remains available through
overflow. Care status, upcoming tasks, history and care creation actions belong to
Today and History rather than cat registration. History groups compact entries by date and supports cat,
type and period filters. Tapping opens the full entry, comments and sharing menu.

Primary navigation uses labeled Today, Cats, Notes and History icons, with a rail on windows
at least 600dp wide. Child screens have an Up button. Back restores the originating
screen. Floating labeled creation actions remain available as lists scroll, with
bottom content padding so they do not block the last item.

Editors fill phone windows and keep Save/Cancel visible above system/keyboard insets.
Changed drafts require confirmation before discarding. Cat pickers use wrapping avatar
chips; coat selection keeps a labeled grid with one column above 120% font scale.
Optional profile dates expand on demand. Validation identifies the field and brings it
into view. Calendar/time pickers and recurrence previews retain their existing behavior.
Direct records allow an occurrence date/time and distinguish it from the entry time.

Backup status and last successful time lead the Data screen. Operational details are
expandable; destructive replacement warnings remain visible in restore confirmation.

English and Brazilian Portuguese resources cover all copy and accessibility labels.
Date/time display uses the device zone and 12/24-hour preference without zone suffixes.
Below 360dp or above 120% font scale, the compact calendar supplies month/year navigation
and wrapping actions. Dark appearance uses deep green backgrounds, pale green accents
and warm error colors; coat artwork retains its colors.

Implementation: `CatCareApp` supplies the shell and editors, `CareScreens` the screens,
`CareAgenda` the deadline grouping, and `CareViewModel` the saved navigation/draft state.
See [ADR 0005](../adr/0005-daily-care-ux.md) and the
[validation evidence](../../runs/android-daily-ux-2026-10-23/evidence.md).

Notes is a primary destination for all cat notes, including completed care. The
Add note editor uses visual category tiles: Note, Measurement, Feeding, Medication,
Hygiene and Care. Note is selected initially. Each entry has a cat, description and
occurrence date/time. Existing observations and direct care records appear in the
same dated list, with cat filtering. Tapping any note opens Note details, comments
and sharing. History includes these same records alongside other events. No data
migration or duplicate timeline record is introduced. Today focuses on planned care.
