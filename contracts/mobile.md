# Android local care boundary

Authority: the Android application owns local care state through the device's
SQLite service (`platform-service`, local persistence). There is no remote API,
BFF, synchronization boundary or network permission. ADR 0002 governs scope.

## Product and implementation mapping

| Released semantics | Owner use case and visible state | Local boundary |
| --- | --- | --- |
| Cat profile/history | Gatos: all-cat cards, coat avatar, pending badge, responsibility/observation totals; edit name, birth/adoption dates and coat | `CareStore.saveCat`, `cats`, `history` |
| Responsibility status | Hoje across cats: identity, category, deadline, planned/soon/overdue/unknown | `tasks`, `CareRules.status`; two-day soon threshold |
| Responsibility lifecycle | Create/edit/complete/cancel/defer, optional days OR calendar months recurrence | `saveTask`, `transition`; state/version guard and atomic successor/history |
| Direct care and observation | Histórico: avatar selection in registration, typed record with description and timestamp; comments on every record | `record`, `comment`, `comments`; never completes a task |
| Data stewardship | Dados: preview cat/all scope, JSON destination, confirmed local deletion | `export`, `delete`; SQLite snapshot and cascading deletion |

No veterinarian boundary exists in this app. A veterinary appointment is a care
category. Clinical triage is excluded. Scheduled local reminders are part of the owner app.

## Persistence and transitions

SQLite schema version 5 stores cats (including `coat`), tasks, immutable history entries
and record comments. Care mutations and their history commit in a single transaction.
Comments are append-only, timestamped replies linked to an existing history ID;
blank text and missing parents are rejected. They do not add history entries
or increase observation counters. Completion and its
recurrence successor also commit together. Tasks use a version and planned-state
predicate so a repeated/stale completion cannot create another successor.
Invalid input is rejected before writes; storage exceptions roll back the full
transaction. A missing or changed record produces a visible error and requires
reopening the action. There are no stale server records or ambiguous network
commands. Successful care writes survive process/device restart.

Profile and due dates use calendar pickers. A separate time dialog offers numeric
entry and a clock, using the device’s 12/24-hour preference. Drafts store ISO
date/time parts; saving interprets them in the current device timezone and persists an ISO UTC instant ending in `Z`. Task `zone` separately stores the recurrence timezone. A selected
date requires a time. An undated, non-repeating task remains supported. Editing and deferral display local datetime. Title-only editing preserves the recurrence zone; changing the schedule in the editor establishes the current device zone. Deferral changes the instant while retaining the recurrence zone. Recurrence
uses local wall time in that zone, calculated from the previous scheduled due
time (even when completing early/late). Calendar-month overflow clamps to the
last day, and subsequent recurrence starts from that clamped day. Daily
recurrence uses calendar days. Platform timezone rules resolve recurrence across
DST; selected nonexistent times are rejected, and repeated times use the
earlier offset. History timestamps are UTC instants, displayed in device time.
Changing device time changes derived scheduling labels, never clinical urgency.

## Export and deletion

Export format: UTF-8 JSON, `format=cat-care-offline`, `version=3`, `exported_at`
UTC timestamp, `scope=cat|all_local_data`, and arrays `cats`, `tasks`, `history`,
`observation_comments`. Comments retain `id`, `observation_id`, `description` and UTC `at`.
Rows retain stable UUID identifiers and cat references. Cat rows include `coat`,
one of `orange-tabby`, `brown-tabby`, `gray`, `black`, `white`, `tuxedo`, `calico`
or `siamese`; a new form defaults to `gray`. History includes typed
care, observations with `is_diagnosis=0`, profile/lifecycle changes and UTC
occurrence timestamps. Task rows include recurrence, state, a UTC `due` timestamp (or empty string for undated care), and an explicit named recurrence `zone`. Version 3 documents UTC deadline storage and replies to any history entry. The legacy array/foreign-key names `observation_comments`/`observation_id` remain for continuity and refer to history entries of any type. Nullable task links in history are expected for direct care/profile
entries. Export uses a consistent database snapshot, captured after destination
selection. Scoped export includes only the named cat and its rows.

Android's document picker writes to a user-selected destination. A local document
provider works offline; a cloud document provider may require connectivity. No
broad storage permission is requested. Export success is shown only after the
stream closes successfully; failure warns that a partial file may need removal.
This version offers export for inspection, not import or restore.

Local cat deletion cascades to tasks/history and record comments. Delete-all clears all cats and
related care rows and resets selection. Deletion is logical removal from the
app's database, not a claim of forensic erasure. No identity, sessions or remote
copies exist. Exports are outside app ownership and must be deleted separately.
Automatic app backup is disabled; Android 12+ extraction rules exclude app data
from cloud backup and device transfer. Manufacturer behavior may vary. App data
loss, clearing storage or uninstalling may permanently remove care data; there
is no recovery service or retained app backup. For this development change, the
owner explicitly authorized recreating legacy schema 1 for coat selection. That
bounded reset remains when upgrading schema 1 directly to the current schema.
Schema 2 → 3 adds comments while preserving all existing cats, tasks and history.
Unsupported versions fail without a generic destructive fallback.

## Error and device assumptions

Validation errors keep entered values in the form and announce the error.
SQLite/storage errors show failure without claiming success or silently resetting
data. Dialog drafts (including the chosen coat, form cat and comment target) and selected cat/tab survive
activity recreation. Gatos shows one card per cat with an avatar and name,
responsibility totals (all states), observation totals (`observation` entries
only), and an edit button. Pending badges count only planned responsibilities;
o badge appears when that count is zero. Overdue counts use task deadlines,
not clinical urgency. New/edit forms use optional birth/adoption date pickers
and a labeled coat grid. Saving returns to Gatos and reveals the saved card. Android
8.0/API 26 is the minimum; compile/target API is 35. PT-BR and EN-US are supported presentation languages. Native controls provide focus, labels and touch targets;
TalkBack, large-font and physical-device acceptance require recorded evidence.
Emulators require no host URL, server credentials or seeded account. Release
acceptance is in `contracts/acceptance/android-owner-offline.md`.

Hoje lists planned care across all cats with a single “Planejar cuidado” entry
action, without a page-level cat selector or registration/observation section.
New care plans choose their cat from labeled coat avatars inside the form; with
multiple cats an explicit choice is required, while a sole cat is preselected.
Editing an existing task retains its cat. Histórico keeps its cat filter and
“Registrar” action; the registration form defaults to that cat and allows avatar
selection of another. Saving switches the history filter to the chosen cat.
Every History card offers “Comentar”; replies appear oldest-first beneath their record. Saving a comment reveals its parent in Histórico. Cat filters include coat avatars. Share uses Android’s text share sheet with the card identity, localized type, description, local timestamps and comments; other cards/cats are excluded. User chooses the destination app and confirms sending there.

## Android implementation

The app and its tests use Kotlin. Compose Material 3 owns every screen, editor,
date picker, avatar and illustration; the system document picker remains the
export destination boundary. `CareViewModel` owns StateFlow snapshots and
SavedStateHandle drafts, including fixed mutation targets and version guards.
SQLite reads, writes and exports run on the IO dispatcher. Busy actions disable
repeat submissions; errors retain form values. The Compose migration preserves
`care.cat.offline` and `cat-care.db` remain stable. The export is now version 3. Schema 3 → 4
adds a nullable `history.details` column without changing existing IDs or text.
Schema 2 → 5 also adds comments and metadata; the bounded schema-1 reset remains explicit.

## Scheduling and localized presentation

Planning and rescheduling use calendar and time controls. Repetition is one choice:
none, daily, weekly, monthly, or custom. Only custom repetition shows a positive integer interval and days/months unit; there is no second repeat-mode combo beside the presets. A next-occurrence preview uses
the same rule as actual completion. The form explains the month-end clamp and
that repeats are scheduled from the previous due date rather than completion.
Changing a repeat to none saves zero days and zero months.

Each Hoje task card and Histórico entry shows a coat-avatar/name cat badge, even
when history is already filtered to that cat. Comments remain scoped
to their immutable history ID and survive language changes.

Data offers device language, Português (Brasil), and English (US). Android 13+
also exposes the supported languages in per-app system settings; older versions
use the saved app preference. Both language resources ship together for offline
use, including app bundles. Navigation, forms, categories, coat labels, plural
counters, validation, accessibility labels and new generated history are localized.
Owner-entered names, titles, observations and comments are never translated.
Legacy history descriptions remain in their original language. New automatic
events additionally store language-neutral JSON text in nullable `details`:
`event`, name/title, ISO due/from_due, and recurrence days/months as relevant.
The original description is retained for readable exports and fallback. Export version 3 includes this optional field with UTC due/from_due timestamps.

PT-BR dates use day/month/year; EN-US uses month/day/year. Times follow the device
12/24-hour preference. Due moments, history timestamps and repeat previews are
converted to the current device timezone and show its ID and UTC offset, including
DST distinctions. Birth/adoption are calendar dates and never shift with timezone.
Scheduling retains the creation zone even when display uses a different device zone.

Narrow windows (below 360dp) and enlarged text use a compact calendar with month
and year navigation and a wrapping action row, avoiding Material’s fixed-width
dialog clipping. Its selected date and displayed month survive restoration.


## Theme, reminders and UTC upgrade

Data offers device theme, light and dark. The choice persists and applies to
cards, forms, calendars, status colours and system bars. Today’s summary displays
an explicit overdue count in the error palette whenever a planned deadline is
at or before now; it refreshes on resume and every 30 seconds while visible.
Each card places category/type on the right of its cat identity row. The care
action label is short; its accessibility description retains the task title.

Schema 4 → 5 normalizes task deadlines and structured history due/from_due values
to UTC while preserving their instants and extracting the prior named recurrence
zone. Earlier supported schemas migrate through the same upgrade. Birth/adoption
remain date-only values. Comments already store UTC instants. Historical prose
is immutable and is not parsed/reworded by migration.

Local reminders default to one hour before and at the scheduled instant. Data
allows advance notice of none, 15 minutes, one hour or one day, and disabling
reminders. Android 13+ notification permission is requested through an explicit
button. Disabled/denied notifications never mark events delivered; the UI links
to setup when dated care exists without notification permission. Scheduling uses
one pending AlarmManager RTC_WAKEUP alarm and `setAndAllowWhileIdle`; Android may
delay delivery for battery management, which the settings explain. No exact-alarm
special permission is requested. There is no clinical urgency or delivery guarantee.

The receiver rechecks SQLite before posting, skips completed/deleted/rescheduled
tasks, replaces the advance notification with the due notification, and records
sent task/due/stage keys to avoid duplicate alerts. Overdue items receive one due
alert, without a stale advance alert. Edits, completion, cancellation and deletion
reschedule the next alarm and remove obsolete notifications. Reboot, package
update, clock/timezone/locale changes and app resume reconcile the schedule.
Force-stopping the app suppresses Android alarms until it is reopened. Reminder
text follows the app language and device timezone. Tapping opens Today; it never
completes care automatically.


## Imported dates without a time

A date-only historical record can include `details.date_precision="day"` and
`details.occurred_on="YYYY-MM-DD"`, with the actual import timestamp in
`details.recorded_at`. `at` remains a UTC chronological sort anchor at the start
of that source date in `details.source_zone`; it does not assert an exact event
time. Cards, comment context and shared text display the reported calendar date
with “time not provided,” without timezone conversion or an invented clock time.
Ordinary timestamped records and comment timestamps retain their existing local
datetime presentation. Invalid/missing date metadata falls back to the normal
recorded timestamp. Original source notes can be retained in `details.source_line`.
