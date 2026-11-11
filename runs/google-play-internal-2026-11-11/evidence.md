# Google Play internal release and setup

## Release

- Package `org.wastingnotime.catcare`.
- Published version 0.1.3, version code 5, to internal testing on 2026-11-11.
- Play Console confirms Available to internal testers.
- Tester URL https://play.google.com/apps/internaltest/4701027572839862558
- Signed AAB SHA-256 `29943d7dbfa95706f814b6fb981088f0238bca7224489cbe317937c07a83c01f`.
- Release includes cozy UX changes, centered history clock, smoother cat icon, and an About link to the public privacy policy.
- `testReleaseUnitTest`, `lintRelease`, and `bundleRelease` passed. Ten JVM tests passed.
- Play reports optional missing deobfuscation and native debug-symbol files. R8 is disabled.

## App setup

All 12 setup tasks are complete. After explicit user approval, IARC terms were accepted and the questionnaire saved. Ratings are All ages in Brazil, Everyone in North America, PEGI 3 in Europe, and 3+ elsewhere. All questionnaire answers are No, based on the offline care-record feature set. Android system sharing uses external apps.

The Dashboard setup checklist has cleared and release options are unlocked. Metadata changes remain saved for review. Publishing overview displays a disabled Send app for review button. No submission was made. Internal release 0.1.3 remains available to testers.

Completed privacy policy, app access, ads, target audience, data safety, government, financial, health, trader, category/contact, and default listing.

- Privacy policy https://github.com/wastingnotime/cat-care/blob/main/docs/privacy-policy.md
- Lifestyle category chosen for cat-care routines.
- Audience ages 13–15, 16–17, and 18+ follows Sliding Tasks.
- No ads, no restricted access, no collected/shared data.
- Health declaration covers human-health functionality. This app records animal care and has no user-health features.
- Trader declaration follows the developer account.
- Support email hriccio@wastingnotime.org and verified business phone were reused.
- Website uses the public repository.
- English default listing uses three current Brazilian Portuguese screenshots, a native-vector-derived icon, and a matching feature graphic.

## Evidence

- `published.jpg` shows the available internal release.
- `setup-status.jpg` shows the 11-of-12 checklist.
- `store-assets/` contains uploaded graphics and screenshots.

- `setup-complete.jpg` shows unlocked release options after completing setup.
- `publishing-status.jpg` shows saved metadata and the disabled review button.
