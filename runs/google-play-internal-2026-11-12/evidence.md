# Cat Care 0.1.4 internal release

Published November 12, 2026 at 10:13 America/Sao_Paulo using Brave's
Wasting No Time profile. Play Console confirms **Available to internal testers**
for release `6 (0.1.4) Settings in backups`, with one version code.

- Package `org.wastingnotime.catcare`, version code 6, version name 0.1.4.
- Source revision `af860e5`, with backup settings implementation `f43c211`.
- Full backups include language, appearance, reminder preferences and backup
  folder/status. Version 3 backups remain compatible. Reinstall requires renewed
  folder access before automatic backups resume.
- English and Brazilian Portuguese tester release notes provided.
- `testReleaseUnitTest lintRelease bundleRelease` passed with 10 JVM tests.
- Backup/storage emulator validation is recorded in
  `../android-backup-settings-2026-11-12/evidence.md` (21 tests passed).
- Bundle signed and verified using the retained upload key outside Git.
- Signed AAB SHA-256 `4f35bceb180973ed554716ff97ac9b40a4f9d8a148c010d8fd1901222c1441af`.
- Artifact `apps/mobile/app/build/outputs/bundle/release/cat-care-0.1.4-6-signed.aab`
  is ignored build output.
- Play showed two non-blocking warnings for missing deobfuscation mapping and
  native debug symbols, as in the previous release. R8 is disabled.
- Play says changes usually appear within one hour, occasionally longer.

Tester link https://play.google.com/apps/internaltest/4701027572839862558

Publication evidence [published.png](published.png).
