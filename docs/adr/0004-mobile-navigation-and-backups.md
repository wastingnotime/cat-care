# ADR 0004: Mobile overflow navigation and user-selected backups

Status: accepted, 2026-10-23, following the owner’s requested mobile changes.

## Decision

Keep Today, Cats and History as icon-only tabs with accessible names and an
activity title. Put Data, Settings and About in a vertical ellipsis menu.
Settings owns language, appearance and reminders. About identifies Wasting No
Time and explains the app’s privacy behavior. Display local date/time without
zone IDs or UTC suffixes while preserving UTC storage and recurrence zones.

Data keeps export/delete and adds full-data version 3 restore, manual backup to
a chosen folder, and approximate daily automatic backup with seven-copy retention.
Restore previews counts and requires explicit replacement confirmation. Validate
before replacement and apply it transactionally. Keep settings and document-tree
permissions outside the data snapshot. This supersedes ADR 0002’s exclusion of
import/restore, while retaining its offline owner-only scope and disabled Android
OS backup/device transfer. No account, backend or internet permission is added.

## Shared-platform assessment

Consumers inspected: Sliding Tasks has its own JSON store, Compose overflow menu
and WorkManager-based daily folder backups; Cat Care has transactional SQLite
and its own care export contract. `wnt-android` currently has no published package
to adopt. The user-facing invariants are similar (explicit folder consent,
truthful status, bounded retention, confirmation before replacement), but the
storage validation and application screens are product-owned and still changing.
This is a local implementation, not shared extraction. No stable shared public
API or coordinated consumer migration is established by this change. A future
product-neutral document-backup surface would belong to `wnt-android` after a
separate proposal and consumer validation.

Cat Care uses Android’s existing JobScheduler for periodic work and AndroidX
DocumentFile for user-selected tree operations. This avoids adopting an entire
background-work framework for one daily job. Android controls delivery timing.
See [the mobile contract](../../contracts/mobile.md) for format, replacement,
retention, permission and failure semantics.
