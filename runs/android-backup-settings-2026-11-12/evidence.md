# Portable backup settings

Full-data exports and automatic copies now emit format version 4 with validated
language, theme, reminder preferences and backup folder/status. Version 3 remains
readable and does not replace installation settings. Scoped exports stay version 3.

Compared Sliding Tasks main/origin/main at `2e3d6e8`: its current backup codec
and contract retain task data only, with reminder and folder settings separate.
No settings-restoration implementation was present on that branch to reuse.

Validation on disposable `cat-care-offline-35` emulator (Android 15):
- `testDebugUnitTest lintDebug` passed.
- 21 instrumentation tests passed across CareBackupTest and CareStoreTest.
- New coverage restores theme/reminders/folder/last-success, simulates missing
  persisted folder permission, verifies no scheduled job before reauthorization,
  and resumes scheduling after selecting the same folder without losing status.
- Invalid settings reject before data replacement; version 3 preserves settings.
- Existing retention, failure, database migration and restore checks passed.

Android URI grants and notification permission are not portable file settings.
Reinstall requires a restore and renewed folder authorization; ordinary app updates
retain the existing preferences and grants. No Google Play release was published.
