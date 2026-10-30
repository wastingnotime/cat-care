# ADR 0006: WNT Android application identity

Date: 2026-10-24
Status: Accepted

Cat Care is a Wasting No Time application. Use `org.wastingnotime.catcare`
for its Android namespace, application ID, source packages and app-owned
reminder action. Test package and document-provider authorities follow that identity.
This supersedes ADR 0003's preservation of the initial `care.cat.offline` package.

The initial package already has an internal Google Play release. Android treats
the WNT package as a separate application; it cannot update that installation
or read its private database. Owners can transfer their records with full-data
export from the earlier app and restore into the WNT app. The database filename,
SQLite schema and export format remain unchanged by the identity change.

Use a separate Play Console app entry for the WNT package with the same existing
internal tester email list as Sliding Tasks. Retain the initial release record
as historical delivery evidence.
