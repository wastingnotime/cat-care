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
| Direct care and observation | Histórico: avatar selection in registration, typed record with description and timestamp; comments on observations | `record`, `comment`, `comments`; never completes a task |
| Data stewardship | Dados: preview cat/all scope, JSON destination, confirmed local deletion | `export`, `delete`; SQLite snapshot and cascading deletion |

No veterinarian boundary exists in this app. A veterinary appointment is a care
category. Clinical triage and reminders are excluded from version 0.1.

## Persistence and transitions

SQLite schema version 3 stores cats (including `coat`), tasks, immutable history entries
and observation comments. Care mutations and their history commit in a single transaction.
Comments are append-only, timestamped replies linked to an existing observation ID;
blank text and non-observation parents are rejected. They do not add history entries
or increase observation counters. Completion and its
recurrence successor also commit together. Tasks use a version and planned-state
predicate so a repeated/stale completion cannot create another successor.
Invalid input is rejected before writes; storage exceptions roll back the full
transaction. A missing or changed record produces a visible error and requires
reopening the action. There are no stale server records or ambiguous network
commands. Successful care writes survive process/device restart.

Dates use strict Gregorian `dd/MM/yyyy` entry. Due times use `dd/MM/yyyy HH:mm`
and retain the creation zone ID; editing/deferral retains that zone. Recurrence
uses local wall time in that zone, calculated from the previous scheduled due
time (even when completing early/late). Calendar-month overflow clamps to the
last day, and subsequent recurrence starts from that clamped day. Daily
recurrence uses calendar days. Platform timezone rules resolve recurrence across
DST; manually entered nonexistent times are rejected, and repeated times use the
earlier offset. History timestamps are UTC instants, displayed in device time.
Changing device time changes derived scheduling labels, never clinical urgency.

## Export and deletion

Export format: UTF-8 JSON, `format=cat-care-offline`, `version=2`, `exported_at`
UTC timestamp, `scope=cat|all_local_data`, and arrays `cats`, `tasks`, `history`,
`observation_comments`. Comments retain `id`, `observation_id`, `description` and UTC `at`.
Rows retain stable UUID identifiers and cat references. Cat rows include `coat`,
one of `orange-tabby`, `brown-tabby`, `gray`, `black`, `white`, `tuxedo`, `calico`
or `siamese`; a new form defaults to `gray`. History includes typed
care, observations with `is_diagnosis=0`, profile/lifecycle changes and UTC
occurrence timestamps. Task rows include recurrence, state and stored zone-aware
deadline. Nullable task links in history are expected for direct care/profile
entries. Export uses a consistent database snapshot, captured after destination
selection. Scoped export includes only the named cat and its rows.

Android's document picker writes to a user-selected destination. A local document
provider works offline; a cloud document provider may require connectivity. No
broad storage permission is requested. Export success is shown only after the
stream closes successfully; failure warns that a partial file may need removal.
This version offers export for inspection, not import or restore.

Local cat deletion cascades to tasks/history and observation comments. Delete-all clears all cats and
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
8.0/API 26 is the minimum; compile/target API is 35. PT-BR is the current
presentation language. Native controls provide focus, labels and touch targets;
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
Only observations offer “Comentar”; replies appear oldest-first beneath their
observation. Saving a comment reveals its observation in Histórico.
