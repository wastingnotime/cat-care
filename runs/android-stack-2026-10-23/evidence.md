# Mobile stack alignment validation

Date: 2026-10-23.

Migrated all three Android Gradle scripts from Groovy to Kotlin DSL without
changing dependency/toolchain versions. `MainActivity` now supplies the store
through a ViewModel initializer factory; `createSavedStateHandle()` preserves
navigation and editor restoration, and the ViewModel retains store ownership.

Validation from `apps/mobile`:

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`:
  passed; 8 JVM tests, lint and both debug APKs.
- `./gradlew connectedDebugAndroidTest`: passed; 31 tests on API 35, including
  activity recreation, drafts, SQLite persistence/migrations and Compose UI.
- Instrumentation used `cat-care-offline-35` with `-read-only -no-snapshot` so
  test changes were disposable. Airplane mode was enabled and Wi-Fi/data disabled.
- `git diff --check`: passed.

The SDK XML-version warning and native-library stripping notice did not prevent
successful builds. The stack table lives in
[the mobile documentation](../../apps/mobile/docs/technology-stack.md).
