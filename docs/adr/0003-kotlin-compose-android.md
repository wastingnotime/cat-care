# ADR 0003: Kotlin and Compose for Android

Status: accepted, 2026-10-23. Owner instruction: migrate the Java Android app to
Kotlin with Jetpack Compose.

## Decision

Use Kotlin for application and test sources, Compose Material 3 for screens and
forms, and Compose Canvas for cat artwork. `MainActivity` hosts the composition;
`CareViewModel` publishes immutable StateFlow snapshots and uses SavedStateHandle
for navigation, selected cats and editor drafts. Database access and export run
on the IO dispatcher. Keep the existing SQLite helper and domain rules, ported
to Kotlin with the same transactions, validation, recurrence and version guards.

Preserve package ID `care.cat.offline`, database `cat-care.db`, schema 3 and export
version 2. This change does not require a data reset. Retain the existing bounded
schema-1 development reset and additive schema-2 upgrade. The offline owner scope
in ADR 0002 remains authoritative.

## Build and validation

Keep AGP 8.9.2, Gradle 8.11.1, API 26 minimum and compile/target API 35. Pin Kotlin
and its Compose compiler plugin to 2.2.21, Compose BOM to 2025.04.01, Activity to
1.10.1 and Lifecycle to 2.9.0. Kotlin targets JVM 17, built with JDK 21. These
stable versions support the existing toolchain. Newer Android dependencies can
be evaluated alongside a future compile/target SDK update.

The compiler plugin follows the [official Kotlin/Compose setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler).
Compose versions are aligned with the [official BOM](https://developer.android.com/develop/ui/compose/bom).
The Kotlin plugin version supports the existing AGP and Gradle versions in the
[Kotlin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html).

Replace View hierarchy UI tests with Compose semantics tests using AndroidJUnitRunner.
Retain Kotlin tests for dates, recurrence, SQLite transactions, persistence,
validation, migration, export and deletion. Include activity recreation, avatar
selection, observation replies and confirmation flows. Verify installing over
the Java app with synthetic data retains all database rows.

## Consequences

AndroidX and Kotlin runtime dependencies now ship with the app. Screens are
composable functions with explicit state and event callbacks; custom Android
View rendering and the legacy instrumentation runner are removed. Debug APKs
are larger. Export still uses Android's system document picker. Release signing,
cloud sync, reminders and data import remain outside this implementation change.
