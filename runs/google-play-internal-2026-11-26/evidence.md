# Cat Care 0.1.6 internal candidate

Built November 26, 2026 for `org.wastingnotime.catcare`, version code `8`, with
target SDK 36. The candidate includes release optimization, edge-to-edge fixes,
and backup restore from the welcome screen.

## Bundle validation

- Release unit tests, release lint, and `bundleRelease` passed with AGP 8.11.1,
  Gradle 8.13, and R8 8.11.18.
- Uncompressed base DEX decreased from 19,646,884 bytes in production version 7
  to 2,422,768 bytes in the candidate, an 87.7% reduction. The R8 mapping records
  3,543 renamed classes out of 3,636 class mappings.
- The signed bundle passed `jarsigner -verify` and `bundletool validate`. Its
  signing certificate matches version 7. The standalone mapping matches the
  mapping embedded in the bundle.
- The local bundle, mapping, checksums, and device evidence are in
  `.local/play-release/0.1.6-8/`. The signed bundle SHA-256 is
  `aa7364626605791dd70031845516e285d49dfb584e834862460202bc964d7a4d`.

## Device validation

- Android 11, 15, and 16 emulators ran APKs generated from the optimized bundle.
  Checks covered gesture and three-button navigation, light and dark system-bar
  contrast, landscape navigation with a simulated cutout, and the editor with
  the keyboard open. Date and time input confirmations worked with the keyboard
  visible.
- A local version 7 to 8 upgrade preserved a cat profile and the theme setting.
  A version 4 backup created on Android 11 restored through Android 15 onboarding
  and prompted for renewed folder access. Android 16 scheduled a reminder after
  notification permission was granted.
- All 57 Android instrumentation tests passed across the candidate run and an
  isolated retry of the coat-selection test. The full run passed 56 tests.

## Play Console status

The browser connection was unavailable. Internal upload, version-code availability,
Play’s optimization scores, and the pre-launch report are pending. Production
promotion requires approval after the internal results are available.

[Cat Care internal testing](https://play.google.com/console/u/0/developers/8460748010140158851/app/4974189825752315200/tracks/4699764146893467561?tab=releases)

## Internal release notes

```text
<en-US>
Smaller app download.
Improved layouts around system bars, display cutouts, and the keyboard.
Restore a backup directly from the welcome screen.
</en-US>
<pt-BR>
Download do app menor.
Layout melhorado perto das barras do sistema, dos recortes da tela e do teclado.
Restaure um backup diretamente da tela de boas-vindas.
</pt-BR>
```
