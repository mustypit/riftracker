# RiftTracker

An unofficial score tracker for the Riftbound card game. Not affiliated with Riot Games.

- **At the table:** the phone lies between you; tap Conquer, Hold or Ability to score. First to 8,
  best of 1 or best of 3, with undo, game timer and a check on winning by conquering.
- **Every match saved** on your phone, with a point-by-point timeline.
- **Stats:** win rate per legend, against each legend and against each rival, best winning run,
  going first vs second.
- Legends are picked from a built-in library (Origins to Radiance).
- No ads, no account; nothing leaves your phone.

## Install

Every push builds the app on GitHub Actions. Open the latest run under **Actions**, download the
`rifttracker-apk` artifact, unzip it and install the APK on your phone.

## Signing

To make every build install over the previous one, add a keystore (base64 text) as the
`SIGNING_KEYSTORE` repository secret (Settings → Secrets and variables → Actions). Until then builds
use a throwaway key: uninstall the old app before installing a new build (this deletes its match
history).

## Develop

See [CLAUDE.md](CLAUDE.md) for building and testing (JVM tests with Robolectric screenshots; no
emulator needed).
