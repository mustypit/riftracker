# RiftTracker

An unofficial Android companion for the Riftbound card game: count points at the table, keep every
match, browse the cards and build decks. Not affiliated with Riot Games.

- **At the table:** the phone lies between you, each half facing its player with the legend's art.
  Tap Conquer, Hold or Ability to score; first to 8, best of 1 or best of 3. Tap a score to take a
  point off, undo anything, see how each point was scored on the side track. A 3D d20 roll decides
  who goes first. Battlefields and XP per game, a timer, and the screen stays on.
- **Matches and stats:** every match saved with a point-by-point chart. The Matches tab has your
  history plus win rates per legend you played, against each opposing legend and per battlefield,
  for all time or the last 30 or 7 days. Log matches played without the app.
- **Cards:** the whole card library with pictures, Scryfall-style filters (domains, type, energy /
  power / might, keywords, tags, rules text) and banned cards marked.
- **Decks:** build decks with the Riftbound rules checked as you go (copies, domains, signatures,
  runes, battlefields, banlist), with a sideboard and a maybeboard. View a deck as a list, a grid of
  every card, or stacked with counts; group by type or cost and sort by energy or name. Energy curve,
  power vs runes, and each battlefield with what it does. Import and export **deck codes** (the format
  Piltover Archive, Riftmana and other deck sites use) or plain card lists. Pick a deck when you start
  a match to track its record and games.
- Light and dark themes, your choice of accent colour, smooth sliding screens.
- No ads, no account, no internet access: everything stays on your phone.

## Install

Download the APK from the latest [release](https://github.com/mustypit/riftracker/releases) on your
phone and open it (allow installing from your browser when Android asks). Every new version installs
over the previous one and keeps your matches and decks.

Newer test builds are on the **Actions** tab: open the latest **Build APK** run on `main` and
download the `rifttracker-apk` artifact (needs a GitHub account).

## Credits

- Card data and images: [OpenRift](https://openrift.app), refreshed weekly.
- Deck codes: [Piltover Archive's RiftboundDeckCodes](https://github.com/Piltover-Archive/RiftboundDeckCodes)
  format (Apache 2.0), ported to Kotlin.
- RiftTracker isn't endorsed by Riot Games and doesn't reflect the views or opinions of Riot Games
  or anyone officially involved in producing or managing Riot Games properties. Riot Games, Riftbound
  and all associated properties are trademarks or registered trademarks of Riot Games, Inc.

## Develop

Kotlin, Jetpack Compose and Room. See [CLAUDE.md](CLAUDE.md) for building and testing (JVM tests
with Robolectric screenshots; no emulator needed) and for how the app is put together.
