# RiftTracker (Android score tracker for the Riftbound card game)

Personal, sideloaded, unofficial companion app: counts points at the table (1v1, best of 1 or 3),
saves every match on the phone, and shows stats per legend and per opponent. Kotlin, Jetpack
Compose, Room. The owner installs APKs built by GitHub Actions; there is no Play Store release.
Not affiliated with Riot Games.

## Environment (Claude Code cloud sessions)

- **No Android emulator** (no KVM). All testing is on the JVM with Robolectric (below).
- The Android SDK needs `dl.google.com` (SDK downloads and Google's Maven repo). The **gps_tracker**
  cloud environment allows it; the Default one doesn't. If `/opt/android-sdk` is missing, install it:
  ```bash
  mkdir -p /opt/android-sdk/cmdline-tools && cd /tmp && curl -sSfLO https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip && unzip -q commandlinetools-linux-13114758_latest.zip -d /opt/android-sdk/cmdline-tools && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
  yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null; /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
  echo "sdk.dir=/opt/android-sdk" > local.properties
  ```
- Maven Central answers **429 Too Many Requests** to these machines. Always:
  1. `scripts/fetch-robolectric-runtime.sh` once per machine: curl-downloads (resumable, retried)
     the Robolectric Android runtimes into `app/build/robolectric-runtime/` and the ~160 MB
     graphics runtime into `~/.m2/repository`. `settings.gradle.kts` takes that one module from
     `~/.m2` when it's present (Gradle kept getting 429 on it); CI downloads it normally.
     `./gradlew clean` deletes the runtimes; avoid it or rerun the script.
  2. Run Gradle through `scripts/gradle-retry.sh <tasks>`, which reruns while failures are 429s.
- Robolectric runs **offline** from those jars. Each runtime jar has its own Gradle configuration:
  in one configuration, version conflict resolution would drop one of them.

## Build and test: do this before every push

```bash
scripts/gradle-retry.sh recordRoborazziDebug assembleDebug
```

Runs every test and renders screenshots to `app/screenshots/*.png` (gitignored). **Open and look at
the screenshots after every UI change.** CI runs the same and uploads the APK, screenshots and test
report as artifacts.

| Test | What it covers |
|---|---|
| `game/RulesTest` | Scoring with simulated presses: win at 8, final-point-by-conquer check, corrections, undo (also of the winning point), draws, timeline; best-of-1/3 flow, who's suggested to go first, logged matches |
| `game/LegendsTest` | Legend library: unique names/ids, lookup, picker grouping and search, recent legends |
| `stats/StatsTest` | Win rate (draw = a third), legends/opponents/rivals, best run, going first vs second, evenings and months, periods |
| `ui/AppTest` | Real app on Robolectric with a fixed clock: a full best-of-3 played through the counter, undo, restart mid-match (saved state), abandon, draw, history and stats screens, logging a past match, empty states; screenshots of each |

Robolectric quirk: at phone sizes, **dialogs containing a text field never go idle**. The app has
none: setup forms and the legend picker are screens/overlays, not dialogs. Dialogs without text fields
(final-point check, abandon, delete) are fine; capture them with `captureScreenRoboImage`.

## Architecture

- `game/Model.kt`: `Match` → `Game` → `PointEvent` (side, kind, delta, time). Everything from the
  phone owner's point of view (`Side.ME` / `Side.OPP`).
- `game/Rules.kt`: **all scoring and match decisions as pure functions** (`GameRules`, `MatchRules`).
  Put new rules here and test them in `RulesTest`.
- `game/Legends.kt`: the built-in legend library (players pick, never type). New set: add it at the
  top of `sets` and its legends to `all`. Never rename a legend (matches store the name).
  Art: drop `app/src/main/assets/legends/<id>.webp|png|jpg` (id = `Legends.idOf(name)`, e.g.
  `kaisa`, `lee_sin`, `jarvan_iv`); without it the app draws a monogram tile.
- `stats/Stats.kt`: pure stats over finished matches.
- `data/Db.kt`: Room, one row per match with the match as JSON (new fields get defaults when old
  rows are read). The running match is a row with `finished = 0`, saved after every press, so it
  survives the app closing. A real schema change still needs a version bump and a migration
  (never destructive: it's the user's history).
- `ui/`: Compose, look "bold blocks" (`Theme.kt`: teal = you, coral = opponent, black bars, yellow
  highlights). `MainViewModel` holds navigation (tab + screen stack), the running match and actions.
  `AppGraph` is where tests swap the clock and the coin-flip random.

## Product decisions

- No turn tracking (people forget to press it); only how long each game and match lasts.
- Who goes first is asked at the start of each game (loser of the previous game is suggested).
- Deferred: decks with versions, battlefield tracking, XP/levels. Not wanted: 4-player boards,
  card scanner. Next design iteration may take cues from the MTG counter "Lotus" (random colours per
  game, central menu button, clean panels); ask the owner for a screen recording.
- No ads or tracking. Everything stays on the phone.

## Conventions

- Signing: CI decodes the `SIGNING_KEYSTORE` secret (base64 debug keystore, alias
  `androiddebugkey`, password `android`) to `~/.android/debug.keystore`; without it the build is
  signed with a throwaway key (warning) and won't install over earlier builds. Never commit a key.
- Develop on the branch the session names (or `main` for this repo while it's new); commit and
  push when done; don't open PRs unless asked.
