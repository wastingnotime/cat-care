# Mobile technology stack

Android owner app, version 0.1.0. Reviewed 2026-10-23.
This table describes `apps/mobile`; the web, API and simulation have separate stacks.

| Layer | Technology | Current usage and rationale |
| --- | --- | --- |
| Language | Kotlin | Application and test sources use Kotlin 2.2.21, targeting JVM 17. |
| UI | Jetpack Compose | Compose Material 3 screens and forms, with Compose Canvas artwork. |
| Architecture | Lightweight MVVM | Compose observes `CareViewModel`, which coordinates `CareStore` and domain rules. No additional repository/use-case layers are needed by the current scope. |
| Async | Coroutines + StateFlow | ViewModel-scoped coroutines move database/export work to `Dispatchers.IO`. Immutable snapshots and saved navigation/drafts are collected with lifecycle awareness. |
| Networking | Neither Retrofit nor Ktor | Intentionally absent: core Android workflows are entirely local, with no internet permission, account, server dependency or cloud sync. Revisit the client choice if network scope is authorized. |
| Dependency injection | Manual constructor injection | `MainActivity` supplies `CareStore` through a ViewModel factory. The factory uses `createSavedStateHandle()` to retain saved-state restoration. The ViewModel owns and closes its store when cleared. Hilt is unnecessary for the current small dependency graph. |
| Build | Gradle Kotlin DSL | Root, settings and app scripts use `.gradle.kts`. Gradle 8.11.1 and AGP 8.9.2 remain pinned; builds use JDK 21. |
| Testing | JUnit + Compose UI tests | JUnit 4 JVM tests cover domain rules; Android instrumentation covers SQLite, reminders and Compose interactions/restoration. |

The store remains a concrete SQLite dependency; constructor injection does not
introduce a storage abstraction or a mocking framework. Additional boundaries
can be introduced when a second implementation or isolated ViewModel tests need them.

See [ADR 0002](../../../docs/adr/0002-offline-owner-android.md) for the offline
boundary and [ADR 0003](../../../docs/adr/0003-kotlin-compose-android.md) for the
Kotlin/Compose decision. Dependency versions are declared in
[the app build](../app/build.gradle.kts) and [root build](../build.gradle.kts).
