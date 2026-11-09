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
| `game/RulesTest` | Scoring with simulated presses: win at 8 (any kind of point), side-track contents, corrections, undo (also of the winning point), draws, timeline; best-of-1/3 flow, who's suggested to go first, logged matches |
| `game/LegendsTest` | Legend library: unique names/ids, lookup, picker grouping and search, recent legends; the shipped files cover Radiance with art |
| `stats/StatsTest` | Win rate (draw = a third), legends/opponents/rivals, best run, going first vs second, evenings and months, periods |
| `ui/AppTest` | Real app on Robolectric with a fixed clock: a full best-of-3 played through the counter, undo, restart mid-match (saved state), abandon, draw, history and stats screens, logging a past match, empty states; screenshots of each |

Robolectric quirk: at phone sizes, **dialogs containing a text field never go idle**. The app has
none: setup forms and the legend picker are screens/overlays, not dialogs. Dialogs without text fields
(abandon, delete) are fine; capture them with `captureScreenRoboImage`.

## Architecture

- `game/Model.kt`: `Match` → `Game` → `PointEvent` (side, kind, delta, time). Everything from the
  phone owner's point of view (`Side.ME` / `Side.OPP`).
- `game/Rules.kt`: **all scoring and match decisions as pure functions** (`GameRules`, `MatchRules`).
  Put new rules here and test them in `RulesTest`.
- `game/Legends.kt`: the built-in legend library (players pick, never type). New set: add it at the
  top of `sets` and its legends to `all`. Never rename a legend (matches store the name).
  Art: `app/src/main/assets/legends/<id>.webp|png|jpg` (id = `Legends.idOf(name)`, e.g.
  `kaisa`, `lee_sin`, `jarvan_iv`): the card's artwork only (no frame, name bar or text), ~720 px.
  A legend without art shows a monogram tile.
- **OpenRift** (`openrift.app`, free card catalog, no key) supplies all legend and battlefield art,
  domains and titles. The cloud dev machines can't reach it (blocked by the proxy), so
  `.github/workflows/update-legends.yml` (approved by the owner) runs
  `scripts/build_legend_assets.py --fetch` on GitHub every Monday and on demand: it downloads
  `GET /api/v1/catalog`, picks each champion's / battlefield's **original normal printing** (never
  overnumbered, alternate art, signed, promo-stamped or special-distribution; earliest English
  release; main set over supplemental on the same day), downloads
  `openrift.app/media/cards/<imageId>-full.webp`, crops the artwork and writes `assets/legends/<id>.jpg`
  + `legends.json` and `assets/battlefields/<id>.jpg` + `battlefields.json`, commits to the branch
  it ran on and starts the APK build. To try importer changes, push a branch and run the workflow
  on it (`gh workflow run update-legends.yml --ref <branch>`), then look at every image before
  merging. It refuses to change anything if it finds fewer than 20 legends. The champion of a legend
  card is its tag whose `tagCategoryMap` category is `legend`; names use straight apostrophes. The app
  merges `legends.json` into the built-in list at startup (`Legends.install`). Riftcodex was the
  earlier source (it lagged behind on Radiance and mixed in overnumbered art); it's no longer used.
- `stats/Stats.kt`: pure stats over finished matches.
- `data/Db.kt`: Room, one row per match with the match as JSON (new fields get defaults when old
  rows are read). The running match is a row with `finished = 0`, saved after every press, so it
  survives the app closing. A real schema change still needs a version bump and a migration
  (never destructive: it's the user's history).
- `ui/`: Compose, look "bold blocks" (`Theme.kt`: black bars, yellow highlights; teal = you and
  coral = opponent in lists). The counter uses design **B** (chosen from measured mockups): each half
  shows the legend's art with a dark radial veil, strongest behind the score, white digits, no name
  label, and three round icon-only point buttons (flag = conquer, shield = hold, spark = ability;
  `RiftIcons.kt`) filled with the legend's first domain colour. No −1 button and no pips under the
  score: a **side track** on the outer edge (design "C": slim glowing bar in the legend's two domain
  colours, filling smoothly, with each point's icon floating beside it) shows how each point was
  scored (`GameRules.scoredKinds`); **tapping a filled cell removes that point** (`GameRules.removePoint`,
  undo via the pill or the Undo button: `restorePoint`), and **tapping a score takes a point off**, after which the middle
  pill reads "−1 … · TAP TO UNDO" for 4 s (`GameRules.recentCorrection`). Without art: dark background washed with the two
  domain colours; without domains: the seat colour. Text on coloured buttons picks dark/white by
  luminance. Raw art behind the score failed the readability check (32% of pixels under 3:1); keep
  the veil. `MainViewModel` holds navigation (tab + screen stack), the running match and actions.
  `AppGraph` is where tests swap the clock and the coin-flip random.

## Product decisions

- No turn tracking (people forget to press it); only how long each game and match lasts.
- Who goes first is asked at the start of each game (loser of the previous game is suggested).
- No rules nagging: the owner removed the "winning point by conquering?" check (players know the
  rules). After a match, SAVE MATCH returns to New match (prefilled for a rematch); no review
  screen pops up: matches are reviewed from the Matches tab.
- **Settings** (gear on New match, or the counter's menu): "Background darkness" slider for the veil
  over legend art (`Veil.alphas`, `SettingsStore`, SharedPreferences). 0 = the veil that passed the
  readability check; it can only go darker. Default 0.35. "Light or dark": Phone (default) / Light /
  Dark (`ThemeMode`, applied in `MainActivity`). About shows only the version and the disclaimer
  (the owner didn't want signing-key details shown to users).
- **Extras panel** (Lotus-style): a handle at each half's inner edge, by the middle bar; drag from
  the centre towards your edge (or tap) to open, back to close. Holds XP for now (`Game.xp`); a
  badge shows XP while closed. Buttons keep `OUTER_EDGE_MARGIN` free at each half's outer edge for
  the home bar / camera notch.
- **Match review score chart** (`ScoreChart`): lanes per player, running score on a line coloured by
  the leader, lead changes outlined, breakdown by point kind, lead changes and longest run
  (`GameRules.chart/breakdown/longestRun`).
- **Battlefields**: each game stores the battlefield each player brought (`Game.battlefields`), picked
  (optional) on the "who goes first" card or later from the match review; stats per battlefield
  (counted per game) in Legends → Fields. Library from OpenRift via the same importer
  (`assets/battlefields/<id>.jpg` + `battlefields.json`, art = the band between the upside-down text
  and the name on the landscape card); no built-in list. Points are not tagged to battlefields.
- **Dark mode** follows the phone unless set in Settings: colours are roles in `Theme.kt` (`C.Paper/Card/Ink/Muted/Line/
  Track/Soft/Solid/OnSolid/Header`), each with a light and a dark value; `C.Black`, teal, coral and
  yellow are fixed. Use the roles in new UI, never `Color.White`/hex for surfaces or text.
  `AppDarkTest` reruns every UI flow in dark mode (screenshots `dark_*.png`).
- The counter is full screen: system bars hidden while it's open (swipe to show), art edge to edge.
- Play tab icon: minimal crossed swords (`RiftIcons.CrossedSwords`); keep icons simple.
- Deferred: decks with versions, battlefield tracking, XP/levels. Not wanted: 4-player boards,
  card scanner. Next design iteration may take cues from the MTG counter "Lotus" (random colours per
  game, central menu button, clean panels); ask the owner for a screen recording.
- No ads or tracking. Everything stays on the phone.

## Conventions

- Signing: `app/build.gradle.kts` signs debug and release with the key at `SIGNING_KEYSTORE_PATH`
  (alias `androiddebugkey`, password `android`, overridable with `SIGNING_KEY_ALIAS` /
  `SIGNING_PASSWORD`). CI decodes the `SIGNING_KEYSTORE` secret (base64) to a temp file and sets that
  path, then checks the APK's certificate SHA-256 is
  `f3a6406a295f2e5fd75a7706f49dbac721719c57164a3c0e5cf00684e4d84307` (fails otherwise). Writing the
  key to `~/.android/debug.keystore` was ignored on GitHub runners: don't go back to that. Never
  change the key, the applicationId or the signing setup; `versionCode` = `GITHUB_RUN_NUMBER` of
  the Build APK workflow (if it's ever renamed or recreated, add an offset).
- Develop on the branch the session names (or `main` for this repo while it's new); commit and
  push when done; don't open PRs unless asked.
