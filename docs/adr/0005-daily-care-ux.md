# ADR 0005: Daily care agenda and explicit card interactions

Status: accepted, 2026-10-23, following the owner's UX review and implementation request.

Make the daily-care path scannable and its actions visible. Today groups planned care
by overdue, today, upcoming and undated status, with deadlines sorted chronologically.
Upcoming starts collapsed. Mark done is directly available on a task card and retains
confirmation because completion and recurring-successor creation are not yet undoable.
Secondary actions live in overflow; long press is not required.

Cat cards open profile overviews with upcoming care and recent history. Remove lifetime
responsibility/observation counters. History cards open entry details with comments and
sharing. Type/period filters and date groups support longer histories. Direct records
can specify past occurrence times, separate from insertion time, using existing history
metadata and backup format. Future occurrences are rejected.

Use full-screen editors, compact cat chips, inline errors, optional profile-date fields
and changed-draft discard confirmation. Preserve drafts through recreation. Keep creation
actions visible while scrolling. Child-screen Back/Up restores the originating screen;
primary destinations preserve their scroll/filter state. Use labeled navigation and an
adaptive rail, superseding ADR 0004's icon-only decision. Its backup/privacy semantics
remain in force. Backup status leads the Data page; technical details are expandable.

Offer reminders in the context of the first dated plan. Notifications carry the task ID
and open task details; a pending editor is never replaced. Completion, delete and restore
confirmations remain explicit. No backend, account or schema migration is introduced.

These are local product decisions; no shared WNT platform extraction is required.

## Amendment: registration-only Cats, 2026-10-31

Cats cards show name and coat avatar. Opening a card shows registration details
and editing, including optional birth/adoption dates. Remove care summaries,
upcoming tasks, recent history and care creation actions from this surface.
Today and History retain care planning and activity. This supersedes the profile
overview decision above following the owner's review.

Cat cards now open the profile editor directly. The intermediate read-only profile
screen is removed; saving or canceling returns to Cats.

Observation creation now has its own editor launched from Today, separate from
recording care. History only browses existing events; observation entries open
observation details and comments. The timeline projects the same stored record,
without duplication or a schema migration.

## Amendment: Notes destination, 2026-10-31

Add Notes to primary navigation. It lists the selected cat’s observations and
provides Add note; these are presented as notes throughout the UI. Existing
observations are reused without migration. Notes remain visible in History.
Creation moves from Today to Notes, superseding the preceding creation-placement
decision. Today retains Record care, and History remains a browsing surface.

Move Record care from Today to Notes and include direct care records in its list.
Use visual category tiles matching Plan care while retaining existing record types.

## Amendment: unified categorized notes, 2026-10-31

Treat completed care records as kinds of cat notes. Notes offers a single Add note
flow with visual categories Note, Measurement, Feeding, Medication, Hygiene and
Care. Note is the default category. Remove the separate Record care action.
All these entries open Note details and remain in History. Preserve existing
stored record types and backups; no migration is needed.
